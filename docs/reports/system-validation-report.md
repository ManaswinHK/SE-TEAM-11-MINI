# System Validation Report

Generated 2026-11-07 from commit `6943f09`.

**40 test cases: 29 Pass, 6 Partial, 1 Fail, 4 Not run.**

Pass means an automated test covers the whole case and passed in this run. Partial means only part of it is covered, as the notes explain. Fail means it was run and missed its target. Not run means it has not been executed. Performance results are in performance-report.md.

| TC ID | Requirement | Title | Status | Evidence | Notes |
|---|---|---|---|---|---|
| TC-AUTH-01 | AAS-F-001 | Login with MFA | Pass | AuthFlowTest |  |
| TC-AUTH-02 | AAS-F-002 | Department scoping | Pass | RoleBasedAccessTest, EmployeeRecordsTest |  |
| TC-ORG-01 | AAS-F-003 | Configure organization | Pass | OrgSetupTest |  |
| TC-EMP-01 | AAS-F-010 | Employee CRUD | Pass | EmployeeRecordsTest |  |
| TC-EMP-02 | AAS-F-011 | CSV bulk import | Pass | EmployeeImportTest |  |
| TC-EMP-03 | AAS-F-012 | HRMS nightly sync | Pass | HrmsSyncAndReportTest | Runs against the sandbox HRMS stub; no real HRMS is available. |
| TC-EMP-04 | AAS-F-013 | Headcount snapshot | Pass | HeadcountSnapshotTest |  |
| TC-SEP-01 | AAS-F-020 | Record resignation | Pass | SeparationTest |  |
| TC-SEP-02 | AAS-F-021 | Invalid separation dates | Pass | SeparationTest |  |
| TC-SEP-03 | AAS-F-022 | Clearance checklist | Pass | ClearanceTest |  |
| TC-SEP-04 | AAS-F-023 | Withdraw resignation | Pass | WithdrawResignationTest |  |
| TC-SRV-01 | AAS-F-030 | Survey email | Pass | SurveyInviteTest |  |
| TC-SRV-02 | AAS-F-031 | Submit survey once | Pass | PublicSurveyTest |  |
| TC-SRV-03 | AAS-F-032 | Survey reminders | Pass | SurveyReminderTest |  |
| TC-ANA-01 | AAS-F-040 | Attrition rate formula | Pass | AttritionAnalyticsTest |  |
| TC-ANA-02 | AAS-F-041 | Breakdowns | Pass | AttritionAnalyticsTest |  |
| TC-ANA-03 | AAS-F-042 | Trend chart | Pass | AttritionTrendTest |  |
| TC-ANA-04 | AAS-F-043 | Exit reasons ranking | Pass | ExitReasonRankingTest |  |
| TC-ANA-05 | AAS-F-044 | Early attrition | Pass | AttritionTrendTest |  |
| TC-RSK-01 | AAS-F-050 | Risk score & band | Pass | RiskScoreTest |  |
| TC-RSK-02 | AAS-F-051 | Risk factors | Pass | RiskScoreTest |  |
| TC-RSK-03 | AAS-F-052 | Retention action | Pass | RetentionActionTest |  |
| TC-REP-01 | AAS-F-060 | Threshold alert | Pass | AttritionAlertTest |  |
| TC-REP-02 | AAS-F-061 | Export PDF/CSV | Partial | DashboardExportTest | CSV figures checked against the dashboard API. PDF is the browser's print of the same page, checked by hand. |
| TC-REP-03 | AAS-F-062 | Scheduled report | Pass | HrmsSyncAndReportTest |  |
| TC-PERF-01 | AAS-NF-001 | Dashboard response time | Fail | performance-report | Dashboard passes (p95 0.5 s at 50 users); other API calls fail (p95 3.7 s against 0.5 s) because the separations list is not paged. |
| TC-PERF-02 | AAS-NF-002 | Concurrency | Partial | performance-report | No slowdown and no errors at 200 concurrent users, but in one organization instead of ten. |
| TC-OPS-01 | AAS-NF-003 | Backup & restore | Not run |  | No deployed environment or backup job, and no backlog item covers AAS-NF-003. |
| TC-UX-01 | AAS-NF-004 | Accessibility & task time | Not run |  | Not run; no backlog item covers AAS-NF-004. |
| TC-UX-02 | AAS-NF-006 | Cross-browser | Not run |  | Not run; no backlog item covers AAS-NF-006. |
| TC-QA-01 | AAS-NF-005 | Coverage gate | Pass | JaCoCo | Measured by the report; CI does not fail the build on it yet. |
| TC-SEC-01 | AAS-SR-001 | TLS configuration | Partial | AuthFlowTest | HSTS header tested. No TLS scan: there is no deployed HTTPS environment yet. |
| TC-SEC-02 | AAS-SR-002 | Password storage & lockout | Pass | AuthFlowTest |  |
| TC-SEC-03 | AAS-SR-003 | Encryption at rest | Partial | SecurityHardeningTest | Email, gender and survey comments are ciphertext in the database. Names are not encrypted (see DEVIATIONS.md). |
| TC-SEC-04 | AAS-SR-004 | Tenant isolation (IDOR) | Pass | SecurityHardeningTest, OrgSetupTest, ClearanceTest |  |
| TC-SEC-05 | AAS-SR-005 | Audit trail | Pass | SecurityHardeningTest, SeparationTest |  |
| TC-SEC-06 | AAS-NF-007 | Data retention | Not run |  | No retention job exists, and no backlog item covers AAS-NF-007. |
| TC-SEC-07 | AAS-SR-006 | Survey anonymity threshold | Pass | ExitReasonRankingTest |  |
| TC-SEC-08 | AAS-SR-007 | Injection & XSS | Partial | EmployeeImportTest | CSV formula injection tested. OWASP ZAP scan not run yet (AAS-36). |
| TC-SEC-09 | AAS-SR-008 | Session expiry | Partial | AuthFlowTest | Token rejected after logout and 30-minute lifetime tested; a real 31-minute idle wait is not. |
