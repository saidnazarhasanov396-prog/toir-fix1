package com.toir.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Component
public class SecurityScope {

    private static final String ADMIN_ROLE = "SYSTEM_ADMIN";
    private static final String WILDCARD = "*";

    public AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        return user;
    }

    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        if (auth.getPrincipal() instanceof AuthenticatedUser user
                && (ADMIN_ROLE.equals(user.primaryRoleCode()) || hasWildcardPermission(user))) {
            return true;
        }
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (ADMIN_ROLE.equals(a.getAuthority()) || WILDCARD.equals(a.getAuthority())) return true;
        }
        return false;
    }

    /**
     * For non-admin users, force the departmentId filter to the user's own department.
     * Admins keep the requested filter (may be null = all).
     */
    public UUID enforceDepartmentScope(UUID requestedDepartmentId) {
        if (isAdmin()) return requestedDepartmentId;
        AuthenticatedUser user = currentUser();
        if (user == null || user.departmentId() == null) return requestedDepartmentId;
        return UUID.fromString(user.departmentId());
    }

    private boolean hasWildcardPermission(AuthenticatedUser user) {
        return user.permissions() != null
                && user.permissions().stream()
                .filter(StringUtils::hasText)
                .anyMatch(WILDCARD::equals);
    }
}
