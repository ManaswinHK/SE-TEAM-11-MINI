CREATE TABLE department (
    id         BIGSERIAL PRIMARY KEY,
    org_id     BIGINT       NOT NULL REFERENCES organization (id),
    name       VARCHAR(100) NOT NULL,
    location   VARCHAR(100),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_department_org_name UNIQUE (org_id, name)
);

-- Exit reason categories an organization can choose from when recording a separation.
CREATE TABLE exit_reason (
    id         BIGSERIAL PRIMARY KEY,
    org_id     BIGINT       NOT NULL REFERENCES organization (id),
    name       VARCHAR(100) NOT NULL,
    category   VARCHAR(20)  NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_exit_reason_org_name UNIQUE (org_id, name)
);

ALTER TABLE app_user ADD CONSTRAINT fk_app_user_department FOREIGN KEY (department_id) REFERENCES department (id);
