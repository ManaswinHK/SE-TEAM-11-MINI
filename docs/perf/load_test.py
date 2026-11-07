"""Load test for AAS-NF-001 / AAS-NF-002 (TC-PERF-01, TC-PERF-02). Standard library only.

Start the API with the perf profile first (50,000 generated employees):
    cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=perf
Then, from the repository root:
    python docs/perf/load_test.py
Results go to docs/reports/performance-report.md.
"""
import concurrent.futures
import datetime
import json
import os
import platform
import statistics
import time
import urllib.request

BASE = os.environ.get("AAS_URL", "http://localhost:8080")
PASSWORD = "Demo@1234"
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "reports", "performance-report.md")
YEAR = datetime.date.today().year
DASHBOARD = [f"/api/analytics/attrition?from={YEAR}-01-01", f"/api/analytics/exit-reasons?from={YEAR}-01-01",
             "/api/analytics/trend?months=12", "/api/departments"]
OTHER = ["/api/employees?size=25", f"/api/separations?from={YEAR}-01-01&to={YEAR}-12-31", "/api/exit-reasons",
         "/api/org"]


def call(path, token=None, body=None):
    req = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json",
                                          **({"Authorization": "Bearer " + token} if token else {})})
    start = time.perf_counter()
    with urllib.request.urlopen(req, timeout=60) as res:
        data = res.read()
    return time.perf_counter() - start, json.loads(data) if data else None


def call_put(path, token, body):
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode(), method="PUT",
                                 headers={"Content-Type": "application/json", "Authorization": "Bearer " + token})
    with urllib.request.urlopen(req, timeout=60) as res:
        res.read()


def login(email):
    _, r = call("/api/auth/login", body={"email": email, "password": PASSWORD})
    if r.get("accessToken"):
        return r["accessToken"]
    _, v = call("/api/auth/verify-otp", body={"challengeToken": r["challengeToken"], "code": r["devOtp"]})
    return v["accessToken"]


def dashboard_load(token, pool):
    """One dashboard view: the page fires its four API calls at once, so the load time is the slowest."""
    start = time.perf_counter()
    list(pool.map(lambda p: call(p, token), DASHBOARD))
    return time.perf_counter() - start


def p95(samples):
    return statistics.quantiles(samples, n=100)[94] if len(samples) >= 20 else max(samples)


def run_users(users, iterations, work):
    samples, errors = [], 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=users) as ex:
        futures = [ex.submit(work) for _ in range(users * iterations)]
        for f in futures:
            try:
                samples.append(f.result())
            except Exception:  # noqa: BLE001 - every failure counts as an error
                errors += 1
    return samples, errors


def main():
    ceo = login("perf-ceo@perf.test")
    hr = login("perf-hr@perf.test")
    inner = concurrent.futures.ThreadPoolExecutor(max_workers=400)
    _, k = call(DASHBOARD[0], ceo)
    print(f"Organization: {k['kpis']['closingHeadcount']} active of 50,000 records")
    for _ in range(3):
        dashboard_load(ceo, inner)  # warm-up

    # First view after a data change: any employee or separation write empties the dashboard cache.
    _, page = call("/api/employees?size=1", hr)
    e = page["content"][0]
    edit = {key: e[key] for key in ("employeeCode", "fullName", "departmentId", "joinDate")}
    cold = []
    for i in range(5):
        edit["designation"] = f"Associate {i}"  # a real change each time, so the cache is cleared
        call_put(f"/api/employees/{e['id']}", hr, edit)
        cold.append(dashboard_load(ceo, inner))
    for _ in range(3):
        dashboard_load(ceo, inner)

    single, _ = run_users(1, 20, lambda: dashboard_load(ceo, inner))
    fifty, err50 = run_users(50, 5, lambda: dashboard_load(ceo, inner))
    other, err_other = run_users(50, 5, lambda: max(call(p, hr)[0] for p in OTHER))
    attr_one, _ = run_users(1, 20, lambda: call(DASHBOARD[0], ceo)[0])
    attr_200, err200 = run_users(200, 2, lambda: call(DASHBOARD[0], ceo)[0])

    rows = [
        ("Dashboard load, first view after a data change (cache empty)", cold, 0, 3.0),
        ("Dashboard load, 1 user", single, 0, 3.0),
        ("Dashboard load, 50 concurrent users", fifty, err50, 3.0),
        ("Other API calls, 50 concurrent users", other, err_other, 0.5),
        ("Attrition API, 1 user (baseline)", attr_one, 0, None),
        ("Attrition API, 200 concurrent users", attr_200, err200, None),
    ]
    degradation = (p95(attr_200) / p95(attr_one) - 1) * 100
    lines = [
        "# Performance Test Report", "",
        f"Run {datetime.datetime.now():%Y-%m-%d %H:%M} against {BASE} with the `perf` profile: 50,000 employee records "
        f"and five years of separations ({k['kpis']['closingHeadcount']} active today).", "",
        f"Machine: {platform.processor() or platform.machine()}, {os.cpu_count()} logical CPUs, {platform.system()} "
        f"{platform.release()}. Database: H2 file database on the same machine. Load generator: this script "
        "(Python threads), on the same machine.", "",
        "| Scenario | Requests | Errors | Median (s) | p95 (s) | Target | Result |",
        "|---|---:|---:|---:|---:|---|---|",
    ]
    for name, samples, errors, target in rows:
        verdict = "-" if target is None else ("Pass" if p95(samples) <= target else "Fail")
        lines.append(f"| {name} | {len(samples) + errors} | {errors} | {statistics.median(samples):.3f} | "
                     f"{p95(samples):.3f} | {'-' if target is None else f'p95 <= {target:g} s'} | {verdict} |")
    lines += ["", f"**TC-PERF-02:** p95 of the attrition API rose by {degradation:.0f}% from 1 to 200 concurrent users "
              f"(target: under 20%), with {err200} errors out of {len(attr_200) + err200} requests. "
              f"Result: {'Pass' if degradation < 20 and err200 == 0 else 'Fail'}.", "",
              "## Limits of this test", "",
              "- JMeter was not available; this script measures the same things with Python threads.",
              "- One machine runs the application, the database and the load, so they compete for CPU.",
              "- TC-PERF-02 asks for 200 users in each of 10 organizations; this run uses 200 users in one.",
              "- Repeat dashboard views are served from a 5-minute in-memory cache that any data change clears; the "
              "first row shows the uncached time.", ""]
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))
    print("\n".join(lines))


if __name__ == "__main__":
    main()
