# Performance Test Report

Run 2026-11-07 16:13 against http://localhost:8080 with the `perf` profile: 50,000 employee records and five years of separations (35050 active today).

Machine: Intel64 Family 6 Model 165 Stepping 2, GenuineIntel, 8 logical CPUs, Windows 11. Database: H2 file database on the same machine. Load generator: this script (Python threads), on the same machine.

| Scenario | Requests | Errors | Median (s) | p95 (s) | Target | Result |
|---|---:|---:|---:|---:|---|---|
| Dashboard load, first view after a data change (cache empty) | 5 | 0 | 0.629 | 0.698 | p95 <= 3 s | Pass |
| Dashboard load, 1 user | 20 | 0 | 0.008 | 0.034 | p95 <= 3 s | Pass |
| Dashboard load, 50 concurrent users | 250 | 0 | 0.135 | 0.506 | p95 <= 3 s | Pass |
| Other API calls, 50 concurrent users | 250 | 0 | 1.533 | 3.717 | p95 <= 0.5 s | Fail |
| Attrition API, 1 user (baseline) | 20 | 0 | 0.019 | 0.031 | - | - |
| Attrition API, 200 concurrent users | 400 | 0 | 0.008 | 0.014 | - | - |

**TC-PERF-02:** p95 of the attrition API rose by -57% from 1 to 200 concurrent users (target: under 20%), with 0 errors out of 400 requests. Result: Pass.

## Limits of this test

- JMeter was not available; this script measures the same things with Python threads.
- One machine runs the application, the database and the load, so they compete for CPU.
- TC-PERF-02 asks for 200 users in each of 10 organizations; this run uses 200 users in one.
- Repeat dashboard views are served from a 5-minute in-memory cache that any data change clears; the first row shows the uncached time.
