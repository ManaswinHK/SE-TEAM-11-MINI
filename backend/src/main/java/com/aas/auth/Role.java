package com.aas.auth;

public enum Role {
    HR_ADMIN,
    HR_MANAGER,
    DEPT_MANAGER,
    LEADERSHIP;

    /** HR roles handle personal data in bulk, so they must use a one-time code (AAS-F-001). */
    public boolean requiresMfa() {
        return this == HR_ADMIN || this == HR_MANAGER;
    }
}
