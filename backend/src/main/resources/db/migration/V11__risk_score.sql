-- Weekly flight-risk score per active employee (AAS-F-050). One row per employee per scoring run.
CREATE TABLE risk_score (
    id           BIGSERIAL PRIMARY KEY,
    org_id       BIGINT        NOT NULL REFERENCES organization (id),
    employee_id  BIGINT        NOT NULL REFERENCES employee (id),
    score        INTEGER       NOT NULL,
    band         VARCHAR(10)   NOT NULL,
    scored_on    DATE          NOT NULL,
    factors      VARCHAR(1000)
);

CREATE INDEX idx_risk_score_org_date ON risk_score (org_id, scored_on);
