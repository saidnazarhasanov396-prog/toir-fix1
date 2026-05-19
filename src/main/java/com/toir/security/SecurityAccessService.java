package com.toir.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SecurityAccessService {

    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    public boolean hasPermission(Authentication authentication, String permission) {
        if (!StringUtils.hasText(permission)) {
            return false;
        }
        Set<String> authorities = authorityNames(authentication);
        return authorities.contains(SYSTEM_ADMIN)
                || authorities.contains(PermissionConstants.WILDCARD)
                || authorities.contains(permission);
    }

    public boolean hasAnyPermission(Authentication authentication, Collection<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        return permissions.stream().anyMatch(permission -> hasPermission(authentication, permission));
    }

    public boolean hasAllPermissions(Authentication authentication, Collection<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        return permissions.stream().allMatch(permission -> hasPermission(authentication, permission));
    }

    public boolean isSystemAdmin(Authentication authentication) {
        return authorityNames(authentication).contains(SYSTEM_ADMIN);
    }

    private Set<String> authorityNames(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }
}
