# Deviations from the SRS, SAD and Test Plan

What was built differs from the submitted documents in the ways below. Final as of the v1.0 freeze, 7 Nov 2026.

## Technology

| Document said | What was built | Why |
|---|---|---|
| React 18, TypeScript, Material UI | React 19, JavaScript, plain CSS | Current React release; fewer dependencies and less setup for a two-sprint project |
| Spring Boot 3 | Spring Boot 4.1 | Current release when the project started |
| Separate Python service with a scikit-learn model for flight risk (ADR-01) | A points model inside the Spring Boot application (`RiskModel`), run weekly | There is no historical, labelled attrition data to train a model on. A points model over the same inputs (tenure, time since promotion, rating, team attrition) is explainable factor by factor, which AAS-F-051 needs, and avoids a second service |
| Redis cache for dashboard results (ADR-03) | In-memory cache, cleared whenever an employee, separation or survey answer changes, plus a 5-minute expiry | Same effect for one application instance, without running Redis. Added after the first load test failed (see `reports/performance-report.md`) |
| Redis-backed job queue for email, scoring and sync | Spring scheduled jobs | The jobs are few and simple; the email job runs every minute and also retries failed mail |
| Docker, Nginx reverse proxy with TLS | Not set up; runs as a jar plus a static frontend | No deployed environment in project scope. HSTS is sent by the application |
| PostgreSQL everywhere | H2 in PostgreSQL mode for development and tests; PostgreSQL 16 in the CI smoke test and `prod` profile | Starts with no installation |
| PostgreSQL pgcrypto for column encryption | AES-256-GCM in the application (`EncryptedStringConverter`) | Works on both H2 and PostgreSQL; the key stays out of the database |
| Swagger/OpenAPI documentation | Not added | Time; the API is described in the SAD and exercised by the tests |

## Design and behaviour

| Document said | What was built | Why |
|---|---|---|
| REST API under `/api/v1` | Under `/api` | One version exists; a version prefix can be added when a second is needed |
| Rate limiting at the REST API | Not built | Account lockout after 5 failures covers the login risk; other routes are not limited |
| Email delivered through a provider | Mail gateway sends by SMTP once `spring.mail.host` is set; with no mail server configured, messages go to the log and nothing is delivered | No mail account in the project. The application warns about this at startup |
| Refresh token revoked at logout (AAS-SR-008) | One 30-minute sliding access token, revoked through a denylist | Same outcome with one token type. The denylist is in memory, so it clears on restart |
| Authenticator handling | TOTP (RFC 6238) written in the project | Small, and covered by its own tests |
| Encrypt name, email, phone, salary band, gender and survey text (AAS-SR-003) | Email, gender and survey comments are encrypted; names are not, and phone and salary band are not stored | Employee search and sorting work on names in the database; encrypting them would need a separate search index |
| Exit-survey token | Stored as plain text, not hashed | Reminders must rebuild the link. The token is random, single-use and expires after 30 days |
| HRMS connector calling a vendor REST API (AAS-F-012) | Connector interface with a sandbox implementation that reads a CSV file | No HRMS is available to the project. A real connector replaces `StubHrmsClient` |
| Dashboard PDF export | The browser's Print to PDF with a print stylesheet | Same figures as on screen with no PDF library |
| Separation statuses | Added Closed (clearance done) and Retained (resignation withdrawn) | Needed by AAS-F-022 and AAS-F-023 |
| Closing a separation | Also requires the last working day to have passed | A person still serving notice cannot have finished clearance |
| Survey anonymity threshold (AAS-SR-006) | Applied to Department Managers, as the SRS says; HR sees scores from the first response | As specified |

## Testing

| Document said | What was done | Why |
|---|---|---|
| Jest for React | Vitest | Works directly with Vite |
| Selenium UI automation | Not used; flows checked by hand and recorded in the demo videos | Time. Behaviour is covered through API-level tests |
| JMeter | A Python load script (`docs/perf/load_test.py`) | JMeter was not available; the script measures the same p95 figures |
| TC-PERF-02: 200 users in each of 10 organizations | 200 users in one organization | One machine runs the application, database and load |
| WireMock and MailHog stubs | HRMS and mail stubs inside the application and test suite | Simpler; no extra services |
| OWASP ZAP scan, testssl.sh | Not run | No deployed HTTPS environment; input validation and formula injection are covered by tests |

Test cases changed by the sprints are in `test-cases.csv`, and the Test Plan is updated to match.

## Known failures and gaps

- **AAS-NF-001, other API calls:** with 50 concurrent users the separations list takes 3.7 s at p95 against a 0.5 s target, because it returns a whole year of separations (about 2,500 rows for 50,000 employees) at once. The fix is to page that list in the API and the screen.
- **Frontend tests:** only 3 tests; 19% line and 10% branch coverage. The backend carries the testing (94 tests, 83% line coverage).
- **Not in the backlog:** AAS-NF-003 (backup and restore), AAS-NF-004 (accessibility), AAS-NF-006 (cross-browser) and AAS-NF-007 (data retention) have test cases but were never scheduled, so they are not implemented or tested.

## Process

- Sprint 1 was planned for 27-31 Oct 2026. Its end date was extended to 2 Nov because the last stories were closed late.
- The Sprint 1 backlog was added just after the sprint started, so its burndown begins with a jump from 0 to 47.
- Sprint 1 code was finished on 28 Oct and released to `main` in stages through 3 Nov, and Jira cards moved with those merges.
- AAS-21 (Sprint 1 video) was moved to Done before the video was recorded; the video was added on 7 Nov.
- Sprint 2 work planned for 5-6 Nov was done on 7 Nov.
- The demo videos were recorded by a script that drives the real application in a browser (`demo/record_demo.py`), with on-screen captions instead of narration. The Sprint 1 video was recorded from the code as it was at the end of Sprint 1.
- PRs #1-#18 and the final Sprint 2 PRs were raised from the repository owner's account, with teammates listed as co-authors on commits, and were merged without a formal review.
- The backlog was managed in Jira, and was copied to the GitHub Project "AAS Backlog" on 4 Nov when the evaluation checklist arrived. Issue numbers there differ from the Jira keys, which are in each title.
