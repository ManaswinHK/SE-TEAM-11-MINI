-- An employee leaving the organization (AAS-F-020).
CREATE TABLE separation (
    id                BIGSERIAL PRIMARY KEY,
    org_id            BIGINT        NOT NULL REFERENCES organization (id),
    employee_id       BIGINT        NOT NULL REFERENCES employee (id),
    resignation_date  DATE          NOT NULL,
    last_working_day  DATE          NOT NULL,
    type              VARCHAR(20)   NOT NULL,
    reason_id         BIGINT        NOT NULL REFERENCES exit_reason (id),
    regretted         BOOLEAN       NOT NULL DEFAULT FALSE,
    status            VARCHAR(20)   NOT NULL,
    remarks           VARCHAR(1000),
    recorded_by       BIGINT        REFERENCES app_user (id),
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_separation_org_lwd ON separation (org_id, last_working_day);
CREATE INDEX idx_separation_employee ON separation (employee_id);

-- Who changed what and when (AAS-SR-005). Rows are only ever inserted.
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    org_id      BIGINT       NOT NULL,
    user_id     BIGINT,
    user_email  VARCHAR(200),
    action      VARCHAR(40)  NOT NULL,
    entity      VARCHAR(40)  NOT NULL,
    entity_id   BIGINT,
    details     VARCHAR(1000),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_org_created ON audit_log (org_id, created_at);
