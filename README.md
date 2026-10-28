# Attrition Analysis System (AAS)

A web application that lets any organization keep track of employees leaving, understand why they leave, and act early to retain people.

Software Engineering Lab mini project, Section B.

| Name | SRN |
|---|---|
| H K Manaswin | PES1UG24AM108 |
| Ganesh Aroda C | PES1UG24AM102 |
| Harsha R | PES1UG24AM115 |
| R Harshith | PES1UG24AM117 |

## Deliverables
- [Software Requirements Specification](docs/SRS_Attrition_Analysis_System.pdf)
- [Software Architecture & Design](docs/SAD_Attrition_Analysis_System.pdf)
- [Software Test Plan](docs/Test_Plan_Attrition_Analysis_System.pdf)

## Tech stack
| Layer | Technology |
|---|---|
| Frontend | React 19, Vite, React Router, Chart.js |
| Backend | Java 21, Spring Boot 4 (Web MVC, Security, Data JPA, Validation), Flyway |
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

HR roles must enter a 6-digit code from an authenticator app. On first sign-in the app shows the key to add to Google/Microsoft Authenticator; in the dev profile it also shows the current code so demos work without a phone.

## Tests
```bash
cd backend && ./mvnw verify
cd frontend && npm run lint && npm test && npm run build
```

## Workflow
- Work is tracked in Jira (space `AAS`). Sprint 1: 27–31 Oct 2026, Sprint 2: 2–7 Nov 2026.
- Branch per issue, named with the Jira key: `AAS-10-login-otp`.
- Commit messages start with the key: `AAS-10 Add OTP verification`.
- Open a pull request to `main`. CI must pass and the repo owner reviews and merges.
