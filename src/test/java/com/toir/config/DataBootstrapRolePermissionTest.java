package com.toir.config;

import com.toir.entity.users.Role;
import com.toir.repository.users.RoleRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.PermissionConstants;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataBootstrapRolePermissionTest {

    private static final Path DATA_BOOTSTRAP = Path.of("src/main/java/com/toir/config/DataBootstrap.java");

    private final Map<String, Role> roles = new ConcurrentHashMap<>();
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final BootstrapProperties bootstrapProperties = new BootstrapProperties();

    @Test
    void bootstrapCreatesBuiltInDepartmentRolesWithAnalyticsPermission() {
        bootstrapProperties.setCreateDefaultAdmin(false);
        stubRoleRepository();

        bootstrap().run();

        assertThat(roles.get("WORKSHOP_HEAD").getPermissions())
                .contains(PermissionConstants.READ_LEGACY, PermissionConstants.EQUIPMENT_READ, PermissionConstants.ANALYTICS_READ);
        assertThat(roles.get("SECTION_HEAD").getPermissions())
                .contains(PermissionConstants.ANALYTICS_READ);
        assertThat(roles.get("RELIABILITY_ENGINEER").getPermissions())
                .contains(PermissionConstants.ANALYTICS_READ, PermissionConstants.ANALYTICS_EXPORT);
        assertThat(roles.get("TECHNICIAN").getPermissions())
                .contains(
                        PermissionConstants.NOTIFICATION_READ,
                        PermissionConstants.NOTIFICATION_MARK_READ,
                        PermissionConstants.WORK_ORDER_READ,
                        PermissionConstants.WORK_ORDER_START,
                        PermissionConstants.WORK_ORDER_COMPLETE
                );
        assertThat(roles.get("MECHANIC").getPermissions())
                .contains(
                        PermissionConstants.NOTIFICATION_READ,
                        PermissionConstants.NOTIFICATION_MARK_READ,
                        PermissionConstants.WORK_ORDER_READ,
                        PermissionConstants.WORK_ORDER_START,
                        PermissionConstants.WORK_ORDER_COMPLETE
                );
        assertThat(roles.get("FINANCE_MANAGER").getPermissions())
                .contains(
                        PermissionConstants.ACTUAL_COST_APPROVE,
                        PermissionConstants.BUDGET_APPROVE,
                        PermissionConstants.APPROVAL_APPROVE
                );
        assertThat(roles.get("SYSTEM_ADMIN").getPermissions())
                .containsExactly(PermissionConstants.WILDCARD);
    }

    @Test
    void baseRolesDoNotDefineAnyRoleMoreThanOnce() throws Exception {
        List<String> roleCodes = baseRoleCodes();

        assertThat(roleCodes).doesNotHaveDuplicates();
    }

    @Test
    void bootstrapCreatesTechnicianRoleWithOperationalNotificationPermissions() {
        bootstrapProperties.setCreateDefaultAdmin(false);
        stubRoleRepository();

        bootstrap().run();

        assertThat(roles.get("TECHNICIAN").getPermissions())
                .contains(
                        PermissionConstants.REPAIR_REQUEST_READ,
                        PermissionConstants.WORK_ORDER_READ,
                        PermissionConstants.WORK_ORDER_START,
                        PermissionConstants.WORK_ORDER_COMPLETE,
                        PermissionConstants.WORK_ORDER_UPDATE,
                        PermissionConstants.NOTIFICATION_READ,
                        PermissionConstants.NOTIFICATION_MARK_READ
                )
                .doesNotContain(
                        PermissionConstants.REPAIR_REQUEST_ASSIGN,
                        PermissionConstants.WORK_ORDER_CREATE,
                        PermissionConstants.WORK_ORDER_APPROVE,
                        PermissionConstants.WORK_ORDER_CLOSE
                );
    }

    @Test
    void bootstrapCreatesTechnicianRoleWithMaintenanceExecutionPermissions() {
        bootstrapProperties.setCreateDefaultAdmin(false);
        stubRoleRepository();

        bootstrap().run();

        assertThat(roles.get("TECHNICIAN").getPermissions())
                .contains(
                        PermissionConstants.READ_LEGACY,
                        PermissionConstants.REPAIR_REQUEST_READ,
                        PermissionConstants.WORK_ORDER_READ,
                        PermissionConstants.WORK_ORDER_UPDATE,
                        PermissionConstants.WORK_ORDER_START,
                        PermissionConstants.WORK_ORDER_COMPLETE,
                        PermissionConstants.EQUIPMENT_READ,
                        PermissionConstants.DEFECT_READ,
                        PermissionConstants.PPR_TASK_READ,
                        PermissionConstants.INSPECTION_READ,
                        PermissionConstants.INSPECTION_START,
                        PermissionConstants.INSPECTION_COMPLETE,
                        PermissionConstants.TIMESHEET_CREATE,
                        PermissionConstants.KNOWLEDGE_READ,
                        PermissionConstants.NOTIFICATION_READ,
                        PermissionConstants.NOTIFICATION_MARK_READ
                );
    }

    @Test
    void bootstrapRepairsExistingLegacyReadOnlyRolesWithoutDeletingCustomPermissions() {
        bootstrapProperties.setCreateDefaultAdmin(false);
        roles.put("WORKSHOP_HEAD", role("WORKSHOP_HEAD", PermissionConstants.READ_LEGACY, "CUSTOM_PERMISSION"));
        stubRoleRepository();

        bootstrap().run();

        assertThat(roles.get("WORKSHOP_HEAD").getPermissions())
                .contains(PermissionConstants.READ_LEGACY, "CUSTOM_PERMISSION", PermissionConstants.ANALYTICS_READ);
    }

    private DataBootstrap bootstrap() {
        return new DataBootstrap(roleRepository, userRepository, passwordEncoder, bootstrapProperties);
    }

    private void stubRoleRepository() {
        when(roleRepository.findByCodeAndIsDeletedFalse(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            return Optional.ofNullable(roles.get(code));
        });
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            roles.put(role.getCode(), role);
            return role;
        });
    }

    private Role role(String code, String... permissions) {
        Role role = new Role();
        role.setCode(code);
        role.setName(code);
        role.setSystem(true);
        role.setPermissions(new ArrayList<>(List.of(permissions)));
        return role;
    }

    private List<String> baseRoleCodes() throws Exception {
        String source = Files.readString(DATA_BOOTSTRAP);
        Matcher matcher = Pattern.compile("\\{\\s*\\\"([A-Z_]+)\\\",").matcher(source);
        List<String> roleCodes = new ArrayList<>();
        while (matcher.find()) {
            roleCodes.add(matcher.group(1));
        }
        return roleCodes;
    }
}
