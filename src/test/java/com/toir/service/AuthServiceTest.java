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
class AuthServiceTest {

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
    void loginResponseAndJwtClaimsStillContainRolePermissions() {
        Role role = role("PPR_ENGINEER", List.of(
                PermissionConstants.PPR_PLAN_READ,
                PermissionConstants.PPR_TASK_START
        ));
        User user = user(role);
        when(userRepository.findByUsernameAndIsDeletedFalse("engineer")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "encoded")).thenReturn(true);
        when(jwtService.generateToken(eq(user.getId().toString()), eq("engineer"), any(), any()))
                .thenReturn("token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);
        when(requestContext.getIpAddress()).thenReturn("127.0.0.1");
        when(requestContext.getUserAgent()).thenReturn("test");

        LoginResponse response = service.login(new LoginRequest("engineer", "password"));

        assertThat(response.accessToken()).isEqualTo("token");
        assertThat(response.user().primaryRoleCode()).isEqualTo("PPR_ENGINEER");
        assertThat(response.user().permissions()).containsExactly(
                PermissionConstants.PPR_PLAN_READ,
                PermissionConstants.PPR_TASK_START
        );

        ArgumentCaptor<Map<String, Object>> extraClaimsCaptor = ArgumentCaptor.captor();
        verify(jwtService).generateToken(
                eq(user.getId().toString()),
                eq("engineer"),
                eq(List.of(
                        "PPR_ENGINEER",
                        PermissionConstants.PPR_PLAN_READ,
                        PermissionConstants.PPR_TASK_START
                )),
                extraClaimsCaptor.capture()
        );
        assertThat(extraClaimsCaptor.getValue())
                .containsEntry("primaryRoleCode", "PPR_ENGINEER")
                .containsEntry("permissions", List.of(
                        PermissionConstants.PPR_PLAN_READ,
                        PermissionConstants.PPR_TASK_START
                ));
        verify(auditLogService).record(
                eq(user.getId()),
                eq(AuditModule.USERS),
                eq("User"),
                eq(user.getId().toString()),
                eq(AuditAction.LOGIN),
                anyString(),
                eq("127.0.0.1"),
                eq("test")
        );
    }

    @Test
    void loginTokenIncludesAnalyticsReadForAllowedRole() {
        Role role = role("WORKSHOP_HEAD", List.of(
                PermissionConstants.READ_LEGACY,
                PermissionConstants.EQUIPMENT_READ,
                PermissionConstants.ANALYTICS_READ
        ));
        User user = user(role);
        when(userRepository.findByUsernameAndIsDeletedFalse("engineer")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "encoded")).thenReturn(true);
        when(jwtService.generateToken(eq(user.getId().toString()), eq("engineer"), any(), any()))
                .thenReturn("token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        LoginResponse response = service.login(new LoginRequest("engineer", "password"));

        assertThat(response.user().permissions()).contains(PermissionConstants.ANALYTICS_READ);
        ArgumentCaptor<Map<String, Object>> extraClaimsCaptor = ArgumentCaptor.captor();
        verify(jwtService).generateToken(
                eq(user.getId().toString()),
                eq("engineer"),
                eq(List.of(
                        "WORKSHOP_HEAD",
                        PermissionConstants.READ_LEGACY,
                        PermissionConstants.EQUIPMENT_READ,
                        PermissionConstants.ANALYTICS_READ
                )),
                extraClaimsCaptor.capture()
        );
        @SuppressWarnings("unchecked")
        List<Object> jwtPermissions = (List<Object>) extraClaimsCaptor.getValue().get("permissions");
        assertThat(jwtPermissions).contains(PermissionConstants.ANALYTICS_READ);
    }

    private User user(Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("engineer");
        user.setEmail("engineer@example.com");
        user.setFullName("PPR Engineer");
        user.setPasswordHash("encoded");
        user.setStatus(UserStatus.ACTIVE);
        user.setPrimaryRole(role);
        user.getRoles().add(role);
        return user;
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
