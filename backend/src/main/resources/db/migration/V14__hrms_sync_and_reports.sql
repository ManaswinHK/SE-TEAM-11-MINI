-- One row per HRMS synchronisation run (AAS-F-012).
CREATE TABLE hrms_sync_log (
    id           BIGSERIAL PRIMARY KEY,
    org_id       BIGINT         NOT NULL REFERENCES organization (id),
    started_at   TIMESTAMP      NOT NULL,
    finished_at  TIMESTAMP,
    status       VARCHAR(10)    NOT NULL,
    created      INTEGER        NOT NULL DEFAULT 0,
    updated      INTEGER        NOT NULL DEFAULT 0,
    failed       INTEGER        NOT NULL DEFAULT 0,
    message      VARCHAR(1000)
);

CREATE INDEX idx_hrms_sync_log_org ON hrms_sync_log (org_id, started_at);

-- Users who want the monthly attrition summary by email (AAS-F-062).
ALTER TABLE app_user ADD COLUMN monthly_report BOOLEAN NOT NULL DEFAULT FALSE;
