package com.aas.auth;

import com.aas.common.ApiException;
import org.springframework.stereotype.Component;

/**
 * Department scoping for AAS-F-002: Department Managers only ever see their own department.
 * Every list or analytics query passes the requested department through here.
 */
@Component
public class AccessScope {

    /**
     * Returns the department filter to apply: the requested one (or null for "all") for organization-wide
     * roles, and always the manager's own department for Department Managers.
     *
     * @throws ApiException 403 if a Department Manager asks for another department
     */
    public Long departmentFilter(CurrentUser user, Long requestedDepartmentId) {
        if (user.role() != Role.DEPT_MANAGER) {
            return requestedDepartmentId;
        }
        if (user.departmentId() == null) {
            throw ApiException.forbidden("Your account is not linked to a department");
        }
        if (requestedDepartmentId != null && !requestedDepartmentId.equals(user.departmentId())) {
            throw ApiException.forbidden("You can only view your own department");
        }
        return user.departmentId();
    }

    /** For single records: Department Managers may only open records of their own department. */
    public void checkDepartment(CurrentUser user, Long recordDepartmentId) {
        if (user.role() == Role.DEPT_MANAGER && (recordDepartmentId == null || !recordDepartmentId.equals(user.departmentId()))) {
            throw ApiException.forbidden("You can only view your own department");
        }
    }
}
