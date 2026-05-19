package com.toir.security;

import com.toir.controller.AuditLogController;
import com.toir.controller.users.RoleController;
import com.toir.controller.users.UserController;
import com.toir.dto.role.RoleDto;
import com.toir.dto.user.UserDto;
import com.toir.enums.UserStatus;
import com.toir.service.AuditLogService;
import com.toir.service.users.RoleService;
import com.toir.service.users.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        UserController.class,
        RoleController.class,
        AuditLogController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacUsersRolesAuditSecurityTest.SecurityBeans.class
})
class RbacUsersRolesAuditSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    UserService userService;

    @MockBean
    RoleService roleService;

    @MockBean
    AuditLogService auditLogService;

    @TestConfiguration
    static class SecurityBeans {
        @Bean
        CorsProperties corsProperties() {
            CorsProperties properties = new CorsProperties();
            properties.setAllowedOriginPatterns(List.of("http://localhost:3000"));
            return properties;
        }
    }

    @Test
    void unauthenticatedCannotReadUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "VIEWER")
    void readOnlyRoleWithoutUserReadCannotReadUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadUsers() throws Exception {
        when(userService.search(any(), anyInt(), anyInt())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/users?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void exactUserReadPermissionCanReadUsers() throws Exception {
        when(userService.search(any(), anyInt(), anyInt())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/users?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.ROLE_READ)
    void unrelatedPermissionCannotReadUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadUsers() throws Exception {
        when(userService.search(any(), anyInt(), anyInt())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/users?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_CREATE)
    void exactUserCreatePermissionCanCreateUser() throws Exception {
        when(userService.create(any())).thenReturn(userDto());

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new.user",
                                  "email": "new.user@example.com",
                                  "fullName": "New User",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void readPermissionCannotCreateUser() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new.user",
                                  "email": "new.user@example.com",
                                  "fullName": "New User",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_UPDATE)
    void exactUserUpdatePermissionCanUpdateUser() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.update(eq(id), any())).thenReturn(userDto());

        mockMvc.perform(put("/api/v1/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "updated@example.com",
                                  "fullName": "Updated User"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_DELETE)
    void exactUserDeletePermissionCanDeleteUser() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.ROLE_READ)
    void exactRoleReadPermissionCanReadRoles() throws Exception {
        when(roleService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/roles?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadRoles() throws Exception {
        mockMvc.perform(get("/api/v1/roles?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.ROLE_CREATE)
    void exactRoleCreatePermissionCanCreateRole() throws Exception {
        when(roleService.create(any())).thenReturn(roleDto());

        mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "TEST_ROLE",
                                  "name": "Test role",
                                  "permissions": ["USER_READ"]
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.ROLE_UPDATE)
    void exactRoleUpdatePermissionCanUpdateRole() throws Exception {
        UUID id = UUID.randomUUID();
        when(roleService.update(eq(id), any())).thenReturn(roleDto());

        mockMvc.perform(put("/api/v1/roles/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "TEST_ROLE",
                                  "name": "Updated role",
                                  "permissions": ["USER_READ"]
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.ROLE_DELETE)
    void exactRoleDeletePermissionCanDeleteRole() throws Exception {
        mockMvc.perform(delete("/api/v1/roles/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.AUDIT_LOG_READ)
    void exactAuditLogReadPermissionCanReadAuditLog() throws Exception {
        when(auditLogService.find(anyInt(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/audit-log?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadAuditLog() throws Exception {
        mockMvc.perform(get("/api/v1/audit-log?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    private static UserDto userDto() {
        return new UserDto(
                UUID.randomUUID(),
                "user",
                "user@example.com",
                "User",
                null,
                null,
                UserStatus.ACTIVE,
                null,
                null,
                List.of(),
                null
        );
    }

    private static RoleDto roleDto() {
        return new RoleDto(
                UUID.randomUUID(),
                "TEST_ROLE",
                "Test role",
                null,
                null,
                null,
                false,
                List.of(PermissionConstants.USER_READ)
        );
    }
}
