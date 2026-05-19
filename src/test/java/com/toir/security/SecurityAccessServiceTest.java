package com.toir.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAccessServiceTest {

    private final SecurityAccessService service = new SecurityAccessService();

    @Test
    void nullAuthenticationReturnsFalse() {
        assertThat(service.hasPermission(null, PermissionConstants.PPR_TASK_START)).isFalse();
        assertThat(service.hasAnyPermission(null, List.of(PermissionConstants.PPR_TASK_START))).isFalse();
        assertThat(service.hasAllPermissions(null, List.of(PermissionConstants.PPR_TASK_START))).isFalse();
        assertThat(service.isSystemAdmin(null)).isFalse();
    }

    @Test
    void systemAdminPassesAnyPermission() {
        Authentication auth = auth("SYSTEM_ADMIN");

        assertThat(service.isSystemAdmin(auth)).isTrue();
        assertThat(service.hasPermission(auth, PermissionConstants.PPR_TASK_START)).isTrue();
        assertThat(service.hasAllPermissions(auth, List.of(
                PermissionConstants.PPR_PLAN_APPROVE,
                PermissionConstants.STOCK_ISSUE
        ))).isTrue();
    }

    @Test
    void wildcardPassesAnyPermission() {
        Authentication auth = auth(PermissionConstants.WILDCARD);

        assertThat(service.hasPermission(auth, PermissionConstants.WORK_ORDER_CLOSE)).isTrue();
    }

    @Test
    void exactPermissionPassesAndUnrelatedPermissionFails() {
        Authentication auth = auth(PermissionConstants.PPR_TASK_START);

        assertThat(service.hasPermission(auth, PermissionConstants.PPR_TASK_START)).isTrue();
        assertThat(service.hasPermission(auth, PermissionConstants.PPR_TASK_COMPLETE)).isFalse();
    }

    @Test
    void legacyReadDoesNotPassMutationPermission() {
        Authentication auth = auth(PermissionConstants.READ_LEGACY);

        assertThat(service.hasPermission(auth, PermissionConstants.PPR_TASK_START)).isFalse();
    }

    @Test
    void anyAndAllPermissionChecksUseExactAuthorities() {
        Authentication auth = auth(PermissionConstants.PPR_TASK_START, PermissionConstants.PPR_TASK_COMPLETE);

        assertThat(service.hasAnyPermission(auth, List.of(
                PermissionConstants.PPR_PLAN_APPROVE,
                PermissionConstants.PPR_TASK_START
        ))).isTrue();
        assertThat(service.hasAllPermissions(auth, List.of(
                PermissionConstants.PPR_TASK_START,
                PermissionConstants.PPR_TASK_COMPLETE
        ))).isTrue();
        assertThat(service.hasAllPermissions(auth, List.of(
                PermissionConstants.PPR_TASK_START,
                PermissionConstants.PPR_TASK_CANCEL
        ))).isFalse();
    }

    private Authentication auth(String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                "user",
                null,
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()
        );
    }
}
