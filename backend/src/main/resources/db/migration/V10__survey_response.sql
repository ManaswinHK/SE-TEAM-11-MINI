-- Answers to the exit survey (AAS-F-031), stored against the separation. At most one per separation.
CREATE TABLE survey_response (
    id             BIGSERIAL PRIMARY KEY,
    org_id         BIGINT         NOT NULL REFERENCES organization (id),
    separation_id  BIGINT         NOT NULL UNIQUE REFERENCES separation (id),
    manager        INTEGER        NOT NULL,
    pay            INTEGER        NOT NULL,
    growth         INTEGER        NOT NULL,
    work_life      INTEGER        NOT NULL,
    culture        INTEGER        NOT NULL,
    comments       VARCHAR(2000),
    submitted_at   TIMESTAMP      NOT NULL
);

CREATE INDEX idx_survey_response_org ON survey_response (org_id);
