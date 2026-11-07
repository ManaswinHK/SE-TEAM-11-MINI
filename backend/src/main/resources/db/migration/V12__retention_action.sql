-- Steps taken to keep an at-risk employee (AAS-F-052).
CREATE TABLE retention_action (
    id            BIGSERIAL PRIMARY KEY,
    org_id        BIGINT         NOT NULL REFERENCES organization (id),
    employee_id   BIGINT         NOT NULL REFERENCES employee (id),
    action        VARCHAR(200)   NOT NULL,
    owner_name    VARCHAR(150)   NOT NULL,
    due_date      DATE           NOT NULL,
    status        VARCHAR(10)    NOT NULL,
    outcome       VARCHAR(1000),
    created_by    BIGINT         REFERENCES app_user (id),
    created_at    TIMESTAMP      NOT NULL,
    completed_at  TIMESTAMP
);

CREATE INDEX idx_retention_action_org ON retention_action (org_id);
