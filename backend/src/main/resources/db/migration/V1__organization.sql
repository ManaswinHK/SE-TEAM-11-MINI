-- Tenant table: every other table carries org_id so organizations never see each other's data.
CREATE TABLE organization (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(150)  NOT NULL,
    industry        VARCHAR(100),
    risk_threshold  NUMERIC(5, 2) NOT NULL DEFAULT 4.00,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
