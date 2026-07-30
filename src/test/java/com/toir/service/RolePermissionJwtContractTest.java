package com.toir.service;

import com.toir.dto.auth.LoginRequest;
import com.toir.dto.auth.LoginResponse;
import com.toir.entity.users.Role;
import com.toir.entity.users.User;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.UserStatus;
import com.toir.repository.users.RoleRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.JwtService;
import com.toir.security.PermissionConstants;
import com.toir.util.RequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionJwtContractTest {

    @Mock
    UserRepository userRepository;

    @Mock
    RoleRepository roleRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtService jwtService;

    @Mock
    AuditLogService auditLogService;

    @Mock
    RequestContext requestContext;

    @InjectMocks
    AuthService service;

    @Test
    void pprEngineerLoginIncludesSeededPprPermissions() {
        LoginResponse response = loginWith(primaryRole("PPR_ENGINEER", List.of(
                PermissionConstants.PPR_PLAN_READ,
                PermissionConstants.PPR_CALENDAR_READ,
                PermissionConstants.PPR_TASK_READ,
                PermissionConstants.PPR_TASK_START,
                PermissionConstants.EQUIPMENT_READ,
                PermissionConstants.READ_LEGACY
        )));

        assertThat(response.user().permissions()).contains(
                PermissionConstants.PPR_PLAN_READ,
                PermissionConstants.PPR_CALENDAR_READ,
                PermissionConstants.PPR_TASK_READ,
                PermissionConstants.PPR_TASK_START,
                PermissionConstants.EQUIPMENT_READ,
                PermissionConstants.READ_LEGACY
        );
        assertThat(response.user().permissions()).doesNotContain(
                PermissionConstants.PPR_PLAN_APPROVE
        );
        assertJwtPermissionsContain(
                PermissionConstants.PPR_PLAN_READ,
                PermissionConstants.PPR_CALENDAR_READ,
                PermissionConstants.PPR_TASK_READ,
                PermissionConstants.PPR_TASK_START,
                PermissionConstants.EQUIPMENT_READ
        );
    }

    @Test
    void pprPlanReadDoesNotDeriveIndependentCalendarPermissionFromRoleDefaults() {
        LoginResponse response = loginWith(primaryRole("PPR_ENGINEER", List.of(
                PermissionConstants.PPR_PLAN_READ
        )));

        assertThat(response.user().permissions()).contains(
                PermissionConstants.PPR_PLAN_READ,
                PermissionConstants.PPR_TASK_READ
        );
        assertThat(response.user().permissions())
                .doesNotContain(PermissionConstants.PPR_CALENDAR_READ);
        assertJwtPermissionsContain(
                PermissionConstants.PPR_PLAN_READ,
                PermissionConstants.PPR_TASK_READ
        );
    }

    @Test
    void pprPlanActionsDoNotDeriveIndependentCalendarActionsFromRoleDefaults() {
        LoginResponse response = loginWith(primaryRole("PPR_ENGINEER", List.of(
                PermissionConstants.PPR_PLAN_CREATE,
                PermissionConstants.PPR_PLAN_UPDATE,
                PermissionConstants.PPR_PLAN_DELETE,
                PermissionConstants.PPR_PLAN_APPROVE,
                PermissionConstants.PPR_PLAN_GENERATE
        )));

        assertThat(response.user().permissions()).doesNotContain(
                "PPR_CALENDAR_CREATE",
                "PPR_CALENDAR_UPDATE",
                "PPR_CALENDAR_DELETE",
                "PPR_CALENDAR_APPROVE",
                "PPR_CALENDAR_GENERATE"
        );
    }

    @Test
    void storekeeperLoginIncludesWarehouseAndStockPermissionsWithoutStockAdjust() {
        LoginResponse response = loginWith(primaryRole("STOREKEEPER", List.of(
                PermissionConstants.WAREHOUSE_READ,
                PermissionConstants.STOCK_READ,
                PermissionConstants.SPARE_PART_READ,
                PermissionConstants.MATERIAL_USAGE_ISSUE
        )));

        assertThat(response.user().permissions()).contains(
                PermissionConstants.WAREHOUSE_READ,
                PermissionConstants.STOCK_READ,
                PermissionConstants.SPARE_PART_READ,
                PermissionConstants.MATERIAL_USAGE_ISSUE
        );
        assertThat(response.user().permissions()).doesNotContain(PermissionConstants.STOCK_ADJUST);
        assertJwtPermissionsContain(PermissionConstants.WAREHOUSE_READ, PermissionConstants.STOCK_READ);
    }

    @Test
    void viewerLoginIncludesReadOnlyPermissionsWithoutMutationPermissions() {
        LoginResponse response = loginWith(primaryRole("VIEWER", List.of(
                PermissionConstants.EQUIPMENT_READ,
                PermissionConstants.REPAIR_REQUEST_READ,
                PermissionConstants.WORK_ORDER_READ,
                PermissionConstants.ANALYTICS_READ
        )));

        assertThat(response.user().permissions()).contains(
                PermissionConstants.EQUIPMENT_READ,
                PermissionConstants.REPAIR_REQUEST_READ,
                PermissionConstants.WORK_ORDER_READ,
                PermissionConstants.ANALYTICS_READ
        );
        assertThat(response.user().permissions()).noneMatch(permission ->
                permission.endsWith("_CREATE")
                        || permission.endsWith("_UPDATE")
                        || permission.endsWith("_DELETE")
                        || permission.endsWith("_APPROVE")
                        || permission.endsWith("_REJECT")
        );
        assertJwtPermissionsContain(PermissionConstants.WORK_ORDER_READ, PermissionConstants.ANALYTICS_READ);
    }

    @Test
    void departmentHeadLoginIncludesEquipmentAnalyticsPermission() {
        LoginResponse response = loginWith(primaryRole("WORKSHOP_HEAD", List.of(
                PermissionConstants.EQUIPMENT_READ,
                PermissionConstants.REPAIR_REQUEST_READ,
                PermissionConstants.ANALYTICS_READ
        )));

        assertThat(response.user().permissions()).contains(
                PermissionConstants.EQUIPMENT_READ,
                PermissionConstants.ANALYTICS_READ
        );
        assertJwtPermissionsContain(PermissionConstants.EQUIPMENT_READ, PermissionConstants.ANALYTICS_READ);
    }

    @Test
    void systemAdminLoginIncludesWildcard() {
        LoginResponse response = loginWith(primaryRole("SYSTEM_ADMIN", List.of(PermissionConstants.WILDCARD)));

        assertThat(response.user().primaryRoleCode()).isEqualTo("SYSTEM_ADMIN");
        assertThat(response.user().permissions()).containsExactly(PermissionConstants.WILDCARD);
        assertJwtPermissionsContain(PermissionConstants.WILDCARD);
    }

    @Test
    void primaryRoleAndAdditionalRolePermissionsAreUnionedAndDeduplicated() {
        Role primaryRole = primaryRole("PPR_ENGINEER", List.of(
                PermissionConstants.PPR_PLAN_READ,
                PermissionConstants.PPR_CALENDAR_READ,
                PermissionConstants.PPR_TASK_READ
        ));
        Role additionalRole = role("STOREKEEPER", List.of(
                PermissionConstants.PPR_TASK_READ,
                PermissionConstants.WAREHOUSE_READ,
                PermissionConstants.STOCK_READ
        ));

        LoginResponse response = loginWith(primaryRole, additionalRole);

        assertThat(response.user().permissions()).contains(
                PermissionConstants.PPR_PLAN_READ,
                PermissionConstants.PPR_CALENDAR_READ,
                PermissionConstants.PPR_TASK_READ,
                PermissionConstants.WAREHOUSE_READ,
                PermissionConstants.STOCK_READ
        );
        assertThat(response.user().permissions())
                .filteredOn(PermissionConstants.PPR_TASK_READ::equals)
                .hasSize(1);
        assertJwtPermissionsContain(
                PermissionConstants.PPR_PLAN_READ,
                PermissionConstants.PPR_CALENDAR_READ,
                PermissionConstants.PPR_TASK_READ,
                PermissionConstants.WAREHOUSE_READ,
                PermissionConstants.STOCK_READ
        );
    }

    private LoginResponse loginWith(Role primaryRole, Role... additionalRoles) {
        User user = user(primaryRole, additionalRoles);
        when(userRepository.findByUsernameAndIsDeletedFalse("role-user")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "encoded")).thenReturn(true);
        when(jwtService.generateToken(eq(user.getId().toString()), eq("role-user"), any(), any()))
                .thenReturn("token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);
        when(requestContext.getIpAddress()).thenReturn("127.0.0.1");
        when(requestContext.getUserAgent()).thenReturn("test");

        return service.login(new LoginRequest("role-user", "password"));
    }

    private void assertJwtPermissionsContain(String... permissions) {
        ArgumentCaptor<List<String>> authoritiesCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<Map<String, Object>> extraClaimsCaptor = ArgumentCaptor.captor();
        verify(jwtService).generateToken(anyString(), eq("role-user"), authoritiesCaptor.capture(), extraClaimsCaptor.capture());
        @SuppressWarnings("unchecked")
        List<Object> jwtPermissions = (List<Object>) extraClaimsCaptor.getValue().get("permissions");
        assertThat(jwtPermissions)
                .contains((Object[]) permissions);
        assertThat(authoritiesCaptor.getValue())
                .contains(permissions);
        verify(auditLogService).record(
                any(UUID.class),
                eq(AuditModule.USERS),
                eq("User"),
                anyString(),
                eq(AuditAction.LOGIN),
                anyString(),
                eq("127.0.0.1"),
                eq("test")
        );
    }

    private User user(Role primaryRole, Role... additionalRoles) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("role-user");
        user.setEmail("role-user@example.com");
        user.setFullName("Role User");
        user.setPasswordHash("encoded");
        user.setStatus(UserStatus.ACTIVE);
        user.setPrimaryRole(primaryRole);
        user.getRoles().add(primaryRole);
        for (Role additionalRole : additionalRoles) {
            user.getRoles().add(additionalRole);
        }
        return user;
    }

    private Role primaryRole(String code, List<String> permissions) {
        return role(code, permissions);
    }

    private Role role(String code, List<String> permissions) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setCode(code);
        role.setName(code);
        role.setPermissions(permissions);
        return role;
    }
}
