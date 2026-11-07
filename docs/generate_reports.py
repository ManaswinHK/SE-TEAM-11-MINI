"""Builds the test reports in docs/reports from the latest test run.

Run the tests first, from the repository root:
    cd backend && mvn test
    cd frontend && npm run coverage
Then:
    python docs/generate_reports.py
"""
import collections
import csv
import datetime
import glob
import json
import os
import shutil
import subprocess
import xml.etree.ElementTree as ET

DOCS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(DOCS)
OUT = os.path.join(DOCS, "reports")
JACOCO = os.path.join(ROOT, "backend", "target", "site", "jacoco", "jacoco.csv")
SUREFIRE = os.path.join(ROOT, "backend", "target", "surefire-reports", "TEST-*.xml")
FRONTEND = os.path.join(ROOT, "frontend", "coverage", "coverage-summary.json")
SERVICE_TARGET = 70.0  # AAS-NF-005


def percent(covered, missed):
    total = covered + missed
    return 100.0 * covered / total if total else 0.0


def header(title):
    commit = subprocess.check_output(["git", "rev-parse", "--short", "HEAD"], cwd=ROOT, text=True).strip()
    return f"# {title}\n\nGenerated {datetime.date.today()} from commit `{commit}`.\n\n"


def write(name, text):
    with open(os.path.join(OUT, name), "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def read_suites():
    suites = {}
    for path in glob.glob(SUREFIRE):
        root = ET.parse(path).getroot()
        name = root.get("name").rsplit(".", 1)[-1]
        suites[name] = {key: int(root.get(key, 0)) for key in ("tests", "failures", "errors", "skipped")}
        suites[name]["time"] = float(root.get("time", 0))
        suites[name]["package"] = root.get("name").rsplit(".", 1)[0]
    return suites


def unit_report(suites):
    total = sum(s["tests"] for s in suites.values())
    bad = sum(s["failures"] + s["errors"] for s in suites.values())
    text = header("Unit and Integration Test Report")
    text += f"**{total} backend tests, {total - bad} passed, {bad} failed.**\n\n"
    text += "These are JUnit 5 tests; most run the real API against an in-memory database through MockMvc.\n\n"
    text += "| Test class | Package | Tests | Failed | Skipped | Time (s) |\n|---|---|---:|---:|---:|---:|\n"
    for name, s in sorted(suites.items(), key=lambda kv: (kv[1]["package"], kv[0])):
        text += (f"| {name} | {s['package']} | {s['tests']} | {s['failures'] + s['errors']} | "
                 f"{s['skipped']} | {s['time']:.1f} |\n")
    write("unit-test-report.md", text)


def read_coverage():
    with open(JACOCO, encoding="utf-8") as f:
        return list(csv.DictReader(f))


def totals(rows, kind):
    return sum(int(r[kind + "_COVERED"]) for r in rows), sum(int(r[kind + "_MISSED"]) for r in rows)


def coverage_table(rows, kind):
    packages = collections.defaultdict(list)
    for r in rows:
        packages[r["PACKAGE"]].append(r)
    text = "| Package | Covered | Missed | Coverage |\n|---|---:|---:|---:|\n"
    for name, members in sorted(packages.items()):
        covered, missed = totals(members, kind)
        if covered + missed:
            text += f"| {name} | {covered} | {missed} | {percent(covered, missed):.1f}% |\n"
    covered, missed = totals(rows, kind)
    return text + f"| **Total** | **{covered}** | **{missed}** | **{percent(covered, missed):.1f}%** |\n"


def frontend_line(metric):
    if not os.path.exists(FRONTEND):
        return "Frontend coverage was not run.\n"
    with open(FRONTEND, encoding="utf-8") as f:
        total = json.load(f)["total"][metric]
    return f"Frontend (Vitest, V8): {total['covered']} of {total['total']} covered, **{total['pct']:.1f}%**.\n"


def coverage_reports(rows):
    services = [r for r in rows if r["CLASS"].endswith("Service")]
    service = percent(*totals(services, "LINE"))
    verdict = "meets" if service >= SERVICE_TARGET else "is below"
    text = header("Code Coverage Report")
    text += (f"Service-layer line coverage is **{service:.1f}%**, which {verdict} the {SERVICE_TARGET:.0f}% target "
             "in AAS-NF-005.\n\n## Backend lines (JaCoCo)\n\n" + coverage_table(rows, "LINE"))
    text += "\n## Service classes\n\n| Class | Line coverage |\n|---|---:|\n"
    for r in sorted(services, key=lambda x: x["CLASS"]):
        text += f"| {r['CLASS']} | {percent(*totals([r], 'LINE')):.1f}% |\n"
    text += "\n## Frontend\n\n" + frontend_line("lines")
    write("code-coverage-report.md", text)

    text = header("Branch Coverage Report")
    text += "Share of decision branches (if, switch, conditional) exercised by the tests.\n\n"
    text += "## Backend (JaCoCo)\n\n" + coverage_table(rows, "BRANCH") + "\n## Frontend\n\n" + frontend_line("branches")
    write("branch-coverage-report.md", text)
    shutil.copy(JACOCO, os.path.join(OUT, "jacoco.csv"))


def validation_report(suites):
    with open(os.path.join(DOCS, "test-cases.csv"), encoding="utf-8", newline="") as f:
        cases = list(csv.DictReader(f))
    for case in cases:
        # A case only counts as passed if every test class it relies on ran and passed in this run.
        for name in filter(None, case["Evidence"].split(";")):
            if name in ("JaCoCo", "performance-report"):
                continue
            suite = suites.get(name)
            if suite is None or suite["failures"] or suite["errors"]:
                case["Status"] = "Fail"
    counts = collections.Counter(c["Status"] for c in cases)
    text = header("System Validation Report")
    text += f"**{len(cases)} test cases: " + ", ".join(f"{counts[s]} {s}" for s in ("Pass", "Partial", "Fail", "Not run")) + ".**\n\n"
    text += ("Pass means an automated test covers the whole case and passed in this run. Partial means only part "
             "of it is covered, as the notes explain. Fail means it was run and missed its target. Not run means it "
             "has not been executed. Performance results are in performance-report.md.\n\n")
    text += "| TC ID | Requirement | Title | Status | Evidence | Notes |\n|---|---|---|---|---|---|\n"
    for c in cases:
        text += (f"| {c['TC ID']} | {c['Req ID']} | {c['Title']} | {c['Status']} | "
                 f"{c['Evidence'].replace(';', ', ')} | {c['Notes']} |\n")
    write("system-validation-report.md", text)


def main():
    os.makedirs(OUT, exist_ok=True)
    suites = read_suites()
    unit_report(suites)
    coverage_reports(read_coverage())
    validation_report(suites)
    print("Reports written to", OUT)


if __name__ == "__main__":
    main()
