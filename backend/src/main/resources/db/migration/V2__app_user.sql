-- Users who sign in to AAS (HR staff, managers, leadership). Exiting employees never get accounts.
CREATE TABLE app_user (
    id               BIGSERIAL PRIMARY KEY,
    org_id           BIGINT       NOT NULL REFERENCES organization (id),
    email            VARCHAR(200) NOT NULL UNIQUE,
    full_name        VARCHAR(150) NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    role             VARCHAR(20)  NOT NULL,
    department_id    BIGINT,
    mfa_secret       VARCHAR(64),
    mfa_enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_attempts  INT          NOT NULL DEFAULT 0,
    locked_until     TIMESTAMP,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_app_user_org ON app_user (org_id);
