package com.toir.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterAuthorityMappingTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void permissionsClaimIsCopiedIntoGrantedAuthoritiesAndRolesArePreserved() throws Exception {
        Claims claims = claims(
                List.of("PPR_ENGINEER", PermissionConstants.PPR_TASK_START),
                List.of(PermissionConstants.PPR_TASK_START, PermissionConstants.PPR_PLAN_READ),
                "PPR_ENGINEER"
        );
        when(jwtService.parse("token")).thenReturn(claims);

        doFilter();

        assertThat(authorityNames()).contains(
                "PPR_ENGINEER",
                PermissionConstants.PPR_TASK_START,
                PermissionConstants.PPR_PLAN_READ
        );
    }

    @Test
    void primaryRoleCodeAuthorityIsPreservedWhenMissingFromAuthoritiesClaim() throws Exception {
        Claims claims = claims(
                List.of(PermissionConstants.PPR_PLAN_READ),
                List.of(PermissionConstants.PPR_TASK_START),
                "PPR_ENGINEER"
        );
        when(jwtService.parse("token")).thenReturn(claims);

        doFilter();

        assertThat(authorityNames()).contains(
                PermissionConstants.PPR_PLAN_READ,
                PermissionConstants.PPR_TASK_START,
                "PPR_ENGINEER"
        );
    }

    @Test
    void duplicateAuthoritiesAreDeduped() throws Exception {
        Claims claims = claims(
                List.of("PPR_ENGINEER", PermissionConstants.PPR_TASK_START),
                List.of(PermissionConstants.PPR_TASK_START),
                "PPR_ENGINEER"
        );
        when(jwtService.parse("token")).thenReturn(claims);

        doFilter();

        assertThat(authorityNames()).contains("PPR_ENGINEER", PermissionConstants.PPR_TASK_START);
    }

    @Test
    void missingPermissionsClaimDoesNotCrash() throws Exception {
        Claims claims = claims(List.of("VIEWER"), null, "VIEWER");
        when(jwtService.parse("token")).thenReturn(claims);

        doFilter();

        assertThat(authorityNames()).contains("VIEWER", PermissionConstants.ANALYTICS_READ);
    }

    @Test
    void malformedPermissionsClaimIsIgnored() throws Exception {
        Claims claims = claims(List.of("VIEWER"), null, "VIEWER");
        when(claims.get("permissions")).thenReturn("not-a-list");
        when(jwtService.parse("token")).thenReturn(claims);

        doFilter();

        assertThat(authorityNames()).contains("VIEWER", PermissionConstants.ANALYTICS_READ);
    }

    @Test
    void roleOnlyTokenIsExpandedWithKnownRolePermissionsAndWildcard() throws Exception {
        Claims claims = claims(List.of("SYSTEM_ADMIN"), null, "SYSTEM_ADMIN");
        when(jwtService.parse("token")).thenReturn(claims);

        doFilter();

        assertThat(authorityNames()).contains("SYSTEM_ADMIN", PermissionConstants.WILDCARD);
    }

    @Test
    void knownRoleDefaultsAreExposedOnPrincipalForAuthMeConsumers() throws Exception {
        Claims claims = claims(
                List.of("PPR_ENGINEER", PermissionConstants.PPR_PLAN_READ),
                List.of(PermissionConstants.PPR_PLAN_READ),
                "PPR_ENGINEER"
        );
        when(jwtService.parse("token")).thenReturn(claims);

        doFilter();

        assertThat(authenticatedUser().permissions()).contains(
                PermissionConstants.PPR_PLAN_READ,
                PermissionConstants.PPR_TASK_READ
        );
    }

    @Test
    void roleOnlyTokenIsExpandedWithAnalyticsPermissionForKnownAllowedRole() throws Exception {
        Claims claims = claims(List.of("WORKSHOP_HEAD"), null, "WORKSHOP_HEAD");
        when(jwtService.parse("token")).thenReturn(claims);

        doFilter();

        assertThat(authorityNames()).contains("WORKSHOP_HEAD", PermissionConstants.ANALYTICS_READ);
    }

    @Test
    void unknownRoleOnlyTokenDoesNotGainAnalyticsPermission() throws Exception {
        Claims claims = claims(List.of("CUSTOM_ROLE"), null, "CUSTOM_ROLE");
        when(jwtService.parse("token")).thenReturn(claims);

        doFilter();

        assertThat(authorityNames()).containsExactly("CUSTOM_ROLE");
        assertThat(authorityNames()).doesNotContain(PermissionConstants.ANALYTICS_READ);
        assertThat(authenticatedUser().permissions()).isEmpty();
    }

    private Claims claims(List<String> authorities, List<String> permissions, String primaryRoleCode) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("user-id");
        when(claims.get("username", String.class)).thenReturn("user");
        when(claims.get("email", String.class)).thenReturn("user@example.com");
        when(claims.get("fullName", String.class)).thenReturn("User");
        when(claims.get("departmentId", String.class)).thenReturn(null);
        when(claims.get("primaryRoleCode", String.class)).thenReturn(primaryRoleCode);
        when(claims.get("authorities")).thenReturn(authorities);
        when(claims.get("permissions")).thenReturn(permissions);
        return claims;
    }

    private void doFilter() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private List<String> authorityNames() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        return authentication.getAuthorities().stream().map(Object::toString).toList();
    }

    private AuthenticatedUser authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
