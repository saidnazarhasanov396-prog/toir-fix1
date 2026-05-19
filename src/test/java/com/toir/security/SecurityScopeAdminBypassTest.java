package com.toir.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityScopeAdminBypassTest {

    private final SecurityScope securityScope = new SecurityScope();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void systemAdminPrimaryRoleIsScopeAdmin() {
        authenticate(user(UUID.randomUUID(), UUID.randomUUID(), "SYSTEM_ADMIN"), List.of());

        assertThat(securityScope.isAdmin()).isTrue();
    }

    @Test
    void systemAdminAuthorityIsScopeAdmin() {
        authenticate(user(UUID.randomUUID(), UUID.randomUUID(), "VIEWER"), List.of("SYSTEM_ADMIN"));

        assertThat(securityScope.isAdmin()).isTrue();
    }

    @Test
    void wildcardAuthorityIsScopeAdmin() {
        authenticate(user(UUID.randomUUID(), UUID.randomUUID(), "VIEWER"), List.of(PermissionConstants.WILDCARD));

        assertThat(securityScope.isAdmin()).isTrue();
    }

    @Test
    void wildcardPermissionOnPrincipalIsScopeAdmin() {
        UUID userId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(
                userId.toString(),
                "viewer",
                "viewer@example.com",
                "Viewer",
                departmentId.toString(),
                "VIEWER",
                List.of(PermissionConstants.WILDCARD)
        );
        authenticate(user, List.of());

        assertThat(securityScope.isAdmin()).isTrue();
    }

    @Test
    void normalUserIsNotScopeAdmin() {
        authenticate(user(UUID.randomUUID(), UUID.randomUUID(), "VIEWER"), List.of(PermissionConstants.READ_LEGACY));

        assertThat(securityScope.isAdmin()).isFalse();
    }

    @Test
    void nullAuthenticationIsNotScopeAdmin() {
        SecurityContextHolder.clearContext();

        assertThat(securityScope.isAdmin()).isFalse();
    }

    private void authenticate(AuthenticatedUser user, List<String> authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user,
                null,
                authorities.stream().map(SimpleGrantedAuthority::new).toList()
        ));
    }

    private AuthenticatedUser user(UUID userId, UUID departmentId, String primaryRoleCode) {
        return new AuthenticatedUser(
                userId.toString(),
                "user",
                "user@example.com",
                "User",
                departmentId.toString(),
                primaryRoleCode,
                List.of()
        );
    }
}
