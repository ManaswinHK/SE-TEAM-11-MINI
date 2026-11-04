-- One tokenised exit-survey link per separation (AAS-F-030). The token is random and holds no personal data.
CREATE TABLE survey_invite (
    id                BIGSERIAL PRIMARY KEY,
    org_id            BIGINT       NOT NULL REFERENCES organization (id),
    separation_id     BIGINT       NOT NULL UNIQUE REFERENCES separation (id),
    token             VARCHAR(64)  NOT NULL UNIQUE,
    created_at        TIMESTAMP    NOT NULL,
    expires_at        TIMESTAMP    NOT NULL,
    sent_at           TIMESTAMP,
    last_reminder_at  TIMESTAMP,
    reminders_sent    INTEGER      NOT NULL DEFAULT 0,
    submitted_at      TIMESTAMP,
    cancelled_at      TIMESTAMP
);

CREATE INDEX idx_survey_invite_org ON survey_invite (org_id);
