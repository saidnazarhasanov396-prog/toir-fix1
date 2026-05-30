package com.toir.config;

import com.toir.entity.users.Role;
import com.toir.repository.users.RoleRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.PermissionConstants;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataBootstrapRolePermissionTest {

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
        assertThat(roles.get("SYSTEM_ADMIN").getPermissions())
                .containsExactly(PermissionConstants.WILDCARD);
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
}
