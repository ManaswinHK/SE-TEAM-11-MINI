# Attrition Analysis System (AAS)

A web application that lets any organization keep track of employees leaving, understand why they leave, and act early to retain people.

Software Engineering Lab mini project, Section B.

| Name | SRN |
|---|---|
| H K Manaswin | PES1UG24AM108 |
| Ganesh Aroda C | PES1UG24AM102 |
| Harsha R | PES1UG24AM115 |
| R Harshith | PES1UG24AM117 |

## Demo videos
- [Sprint 1 (1 minute)](demo/sprint1.mp4): sign-in with one-time code, dashboard, employees, recording a separation
- [Sprint 2 (2 minutes)](demo/sprint2.mp4): alerts, trend, exit reasons and survey scores, clearance, withdrawal, the exit survey, flight risk and retention actions, monthly report, a Department Manager's view

Both were recorded by `demo/record_demo.py`, which drives the running app in a browser and adds captions.

## Features
- Sign-in with password and authenticator code for HR roles; four roles, Department Managers limited to their department
- Employee records with CSV import; monthly headcount snapshots; nightly HRMS sync (sandbox connector)
- Separations with validation, exit clearance checklist and withdrawal
- Tokenised exit survey by email, with reminders; answers shown only in aggregate
- Dashboard: attrition, voluntary, regretted and early attrition; breakdowns; 12-month trend; exit reasons; survey scores; CSV and PDF export
- Weekly flight-risk score with its top 3 factors, and retention actions
- Threshold alerts and a monthly report email
- Personal data encrypted at rest (AES-256-GCM), audit log, tenant isolation

## Deliverables
- [Software Requirements Specification](docs/SRS_Attrition_Analysis_System.pdf)
- [Software Architecture & Design](docs/SAD_Attrition_Analysis_System.pdf)
- [Software Test Plan](docs/Test_Plan_Attrition_Analysis_System.pdf)
- [Deviations from the design documents](docs/DEVIATIONS.md)
- [Test cases with current status](docs/test-cases.csv)
- Test reports: [unit](docs/reports/unit-test-report.md), [system validation](docs/reports/system-validation-report.md), [code coverage](docs/reports/code-coverage-report.md), [branch coverage](docs/reports/branch-coverage-report.md), [performance](docs/reports/performance-report.md)

## Tech stack
| Layer | Technology |
|---|---|
| Frontend | React 19, Vite, React Router, Chart.js |
| Backend | Java 21, Spring Boot 4 (Web MVC, Security, Data JPA, Validation, Mail), Flyway |
| Database | H2 (local development), PostgreSQL 16 (CI and production) |
| CI/CD | GitHub Actions: build, test, PostgreSQL smoke test, release bundle |

## Running locally
Requirements: Java 21 and Node.js 22+.

```bash
# Terminal 1 - API on http://localhost:8080
cd backend
./mvnw spring-boot:run

# Terminal 2 - web app on http://localhost:5173
cd frontend
npm install
npm run dev
```

### Demo accounts (local development only)
The dev profile seeds **Acme Technologies** with one user per role. Password for all: `Demo@1234`.

| Email | Role | Sign-in |
|---|---|---|
| admin@acme.test | HR Admin | password + one-time code |
| hr@acme.test | HR Manager | password + one-time code |
| manager@acme.test | Department Manager | password |
| ceo@acme.test | Leadership | password |

Emails (survey links, alerts, reports) are sent by SMTP when `spring.mail.host` is set; otherwise they are written to the log. Personal data is encrypted with the key in `AAS_ENCRYPTION_KEY` (a development key is built in).

HR roles must enter a 6-digit code from an authenticator app. On first sign-in the app shows the key to add to Google/Microsoft Authenticator; in the dev profile it also shows the current code so demos work without a phone.

## Tests
```bash
cd backend && ./mvnw verify
cd frontend && npm run lint && npm run coverage && npm run build
python docs/generate_reports.py   # refreshes docs/reports from the latest run
```

Performance test (50,000 generated employees):
```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=perf
python docs/perf/load_test.py
```

## Workflow
- Work is tracked in Jira (space `AAS`). Sprint 1: 27–31 Oct 2026, Sprint 2: 2–7 Nov 2026.
- Burndown charts from Jira: [Sprint 1](docs/burndown/sprint1_burndown.png) (47 → 0 points), [Sprint 2](docs/burndown/sprint2_burndown.png) (58 → 0 points).
- Branch per issue, named with the Jira key: `AAS-10-login-otp`.
- Commit messages start with the key: `AAS-10 Add OTP verification`.
- Open a pull request to `main`. CI must pass and the repo owner reviews and merges.
