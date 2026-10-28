package com.aas.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The signed-in user, rebuilt from the access token on every request. */
public record CurrentUser(Long userId, Long orgId, String email, String fullName, Role role, Long departmentId) {

    public static CurrentUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CurrentUser user)) {
            throw new IllegalStateException("No authenticated user");
        }
        return user;
    }
}
