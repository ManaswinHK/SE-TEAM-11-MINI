# Deviations from the SRS, SAD and Test Plan

What was built differs from the submitted documents in the ways below. This list is updated as Sprint 2 finishes.

## Technology

| Document said | What was built | Why |
|---|---|---|
| React 18, TypeScript, Material UI | React 19, JavaScript, plain CSS | Current React release; fewer dependencies and less setup for a two-sprint project |
| Spring Boot 3 | Spring Boot 4.1 | Current release when the project started |
| Redis cache for dashboard results (ADR-03) | No cache; figures are computed on request | Demo data is small; caching adds invalidation risk with no measured need. To be re-checked by the performance test (AAS-37) |
| Redis-backed job queue for email and scoring | Spring scheduled jobs | The jobs are few and simple; a job that runs every minute also retries failed mail |
| Docker, Nginx reverse proxy with TLS | Not set up; runs as a jar plus a Vite or static frontend | No deployed environment in project scope. HSTS is sent by the application |
| PostgreSQL everywhere | H2 in PostgreSQL mode for development and tests; PostgreSQL 16 in the CI smoke test and `prod` profile | Starts with no installation |
| Swagger/OpenAPI documentation | Not added yet | Planned with final documentation |

## Design and behaviour

| Document said | What was built | Why |
|---|---|---|
| REST API under `/api/v1` | Under `/api` | One version exists; a version prefix can be added when a second is needed |
| Rate limiting at the REST API | Not built | Account lockout after 5 failures covers the login risk; other routes are not limited |
| Email delivered through a provider | Mail gateway sends by SMTP once `spring.mail.host` is set; with no mail server configured, messages go to the log and nothing is delivered | No mail account in the project. The application warns about this at startup |
| Refresh token revoked at logout (AAS-SR-008) | One 30-minute sliding access token, revoked through a denylist | Same outcome with one token type. The denylist is in memory, so it clears on restart |
| Authenticator handling | TOTP (RFC 6238) written in the project | Small, and covered by its own tests |
| Exit-survey token | Stored as plain text, not hashed | Reminders must rebuild the link. To be reviewed under AAS-36 |
| Separation statuses | Added Closed (clearance done) and Retained (resignation withdrawn) | Needed by AAS-F-022 and AAS-F-023 |
| Closing a separation | Also requires the last working day to have passed | A person still serving notice cannot have finished clearance |

## Testing

| Document said | What was done | Why |
|---|---|---|
| Jest for React | Vitest | Works directly with Vite |
| Selenium UI automation | Not used; flows checked by hand in a browser | Time. Most behaviour is covered through API-level tests |
| WireMock and MailHog stubs | Mail stub inside the test suite | Simpler; no extra services |
| 50,000-employee synthetic dataset | About 240 seeded employees so far | Larger data is planned for the performance test (AAS-37) |

Test cases changed by the sprints are in `test-cases.csv`, and the Test Plan is updated to match.

## Not yet covered by the backlog

These requirements have test cases but no backlog item: AAS-NF-003 (backup and restore), AAS-NF-004 (accessibility), AAS-NF-006 (cross-browser) and AAS-NF-007 (data retention).

## Process

- Sprint 1 was planned for 27-31 Oct 2026. Its end date was extended to 2 Nov because the last stories were closed late.
- The Sprint 1 backlog was added just after the sprint started, so its burndown begins with a jump from 0 to 47.
- Sprint 1 code was finished on 28 Oct and released to `main` in stages through 3 Nov, and Jira cards moved with those merges.
- AAS-21 (Sprint 1 video) was moved to Done before the video was recorded.
- PRs #1-#18 were raised from the repository owner's account, with teammates listed as co-authors on commits, and were merged without a formal review.
- The backlog was managed in Jira, and was copied to the GitHub Project "AAS Backlog" on 4 Nov when the evaluation checklist arrived. Issue numbers there differ from the Jira keys, which are in each title.
