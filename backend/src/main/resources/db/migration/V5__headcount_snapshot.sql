-- Monthly headcount per department (AAS-F-013), taken on the 1st of every month.
CREATE TABLE headcount_snapshot (
    id             BIGSERIAL PRIMARY KEY,
    org_id         BIGINT    NOT NULL REFERENCES organization (id),
    department_id  BIGINT    NOT NULL REFERENCES department (id),
    snapshot_date  DATE      NOT NULL,
    headcount      INTEGER   NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_snapshot UNIQUE (org_id, department_id, snapshot_date)
);
