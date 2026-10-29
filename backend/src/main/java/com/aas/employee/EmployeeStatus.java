package com.aas.employee;

public enum EmployeeStatus {
    /** Currently employed. */
    ACTIVE,
    /** Resigned or notified; still employed until the last working day. */
    NOTICE_PERIOD,
    /** Left the organization (counts as attrition). */
    SEPARATED,
    /** Record deactivated by HR (e.g. created by mistake); excluded from all counts. */
    INACTIVE
}
