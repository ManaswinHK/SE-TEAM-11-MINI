-- A department's rolling 3-month attrition went over the organization's threshold (AAS-F-060).
-- resolved_at is set when the rate falls back to or below the threshold; a new breach then raises a new alert.
CREATE TABLE attrition_alert (
    id             BIGSERIAL PRIMARY KEY,
    org_id         BIGINT         NOT NULL REFERENCES organization (id),
    department_id  BIGINT         NOT NULL REFERENCES department (id),
    rate           NUMERIC(7, 2)  NOT NULL,
    threshold      NUMERIC(5, 2)  NOT NULL,
    period_from    DATE           NOT NULL,
    period_to      DATE           NOT NULL,
    created_at     TIMESTAMP      NOT NULL,
    resolved_at    TIMESTAMP
);

CREATE INDEX idx_attrition_alert_org ON attrition_alert (org_id);
