-- Employee master data from which headcount and attrition are calculated (AAS-F-010).
CREATE TABLE employee (
    id                   BIGSERIAL PRIMARY KEY,
    org_id               BIGINT       NOT NULL REFERENCES organization (id),
    employee_code        VARCHAR(30)  NOT NULL,
    full_name            VARCHAR(150) NOT NULL,
    email                VARCHAR(200),
    department_id        BIGINT       NOT NULL REFERENCES department (id),
    designation          VARCHAR(100),
    manager_name         VARCHAR(150),
    location             VARCHAR(100),
    grade                VARCHAR(30),
    gender               VARCHAR(20),
    join_date            DATE         NOT NULL,
    performance_rating   INTEGER,
    last_promotion_date  DATE,
    status               VARCHAR(20)  NOT NULL,
    -- Filled when a separation is recorded; the employee counts in headcount until this day.
    last_working_day     DATE,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_employee_org_code UNIQUE (org_id, employee_code)
);

CREATE INDEX idx_employee_org_dept ON employee (org_id, department_id);
CREATE INDEX idx_employee_org_status ON employee (org_id, status);
