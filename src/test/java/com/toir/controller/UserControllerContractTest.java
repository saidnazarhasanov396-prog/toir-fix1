package com.toir.controller;

import com.toir.controller.users.UserController;
import com.toir.dto.user.UserFilterRequest;
import com.toir.dto.user.UserDto;
import com.toir.enums.UserStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.users.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerContractTest {

    @Mock
    UserService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listWithSearchReturnsMatchingUsers() throws Exception {
        UserDto dto = userDto("ali.worker", "ali@example.com", "Ali Worker", "+998901112233");
        when(service.searchWithFilters(any(UserFilterRequest.class), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/users")
                        .param("search", "ali")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].username").value("ali.worker"))
                .andExpect(jsonPath("$.content[0].fullName").value("Ali Worker"));

        ArgumentCaptor<UserFilterRequest> filterCaptor = ArgumentCaptor.forClass(UserFilterRequest.class);
        verify(service).searchWithFilters(filterCaptor.capture(), eq(0), eq(20));
        assertThat(filterCaptor.getValue().search()).isEqualTo("ali");
    }

    @Test
    void listWithSearchIsCaseInsensitive() throws Exception {
        UserDto dto = userDto("ali.worker", "ali@example.com", "Ali Worker", "+998901112233");
        when(service.searchWithFilters(any(UserFilterRequest.class), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/users")
                        .param("search", "ALI")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].email").value("ali@example.com"));

        ArgumentCaptor<UserFilterRequest> filterCaptor = ArgumentCaptor.forClass(UserFilterRequest.class);
        verify(service).searchWithFilters(filterCaptor.capture(), eq(0), eq(20));
        assertThat(filterCaptor.getValue().search()).isEqualTo("ALI");
    }

    @Test
    void listWithBlankSearchReturnsAllNonDeleted() throws Exception {
        UserDto dto1 = userDto("ali.worker", "ali@example.com", "Ali Worker", "+998901112233");
        UserDto dto2 = userDto("john.viewer", "john@example.com", "John Viewer", "+998901112244");
        when(service.searchWithFilters(any(UserFilterRequest.class), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(dto1, dto2), PageRequest.of(0, 20), 2));

        mockMvc.perform(get("/api/v1/users")
                        .param("search", "")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        ArgumentCaptor<UserFilterRequest> filterCaptor = ArgumentCaptor.forClass(UserFilterRequest.class);
        verify(service).searchWithFilters(filterCaptor.capture(), eq(0), eq(20));
        assertThat(filterCaptor.getValue().search()).isEqualTo("");
    }

    @Test
    void listExcludesDeletedUsers() throws Exception {
        UserDto dto = userDto("active.user", "active@example.com", "Active User", "+998901112255");
        when(service.searchWithFilters(any(UserFilterRequest.class), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/users")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].username").value("active.user"));

        ArgumentCaptor<UserFilterRequest> filterCaptor = ArgumentCaptor.forClass(UserFilterRequest.class);
        verify(service).searchWithFilters(filterCaptor.capture(), eq(0), eq(20));
        assertThat(filterCaptor.getValue().search()).isNull();
    }

    @Test
    void listPreservesPaginationMetadata() throws Exception {
        UserDto dto = userDto("paged.user", "paged@example.com", "Paged User", "+998901112266");
        Page<UserDto> page = new PageImpl<>(List.of(dto), PageRequest.of(1, 1), 3);
        when(service.searchWithFilters(any(UserFilterRequest.class), eq(1), eq(1))).thenReturn(page);

        mockMvc.perform(get("/api/v1/users")
                        .param("search", "ali")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3));

        ArgumentCaptor<UserFilterRequest> filterCaptor = ArgumentCaptor.forClass(UserFilterRequest.class);
        verify(service).searchWithFilters(filterCaptor.capture(), eq(1), eq(1));
        assertThat(filterCaptor.getValue().search()).isEqualTo("ali");
    }

    @Test
    void listBindsAdvancedUserFilters() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID primaryRoleId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(service.searchWithFilters(any(UserFilterRequest.class), eq(2), eq(50)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 50), 0));

        mockMvc.perform(get("/api/v1/users")
                        .param("page", "2")
                        .param("size", "50")
                        .param("username", "dispatcher")
                        .param("email", "ops@example.com")
                        .param("fullName", "Dispatch")
                        .param("position", "Operator")
                        .param("phone", "+998")
                        .param("status", "ACTIVE")
                        .param("departmentId", departmentId.toString())
                        .param("primaryRoleId", primaryRoleId.toString())
                        .param("primaryRoleCode", "DISPATCHER")
                        .param("roleId", roleId.toString())
                        .param("roleCode", "MECHANIC")
                        .param("lastLoginFrom", "2026-06-01T00:00:00Z")
                        .param("lastLoginTo", "2026-06-19T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        ArgumentCaptor<UserFilterRequest> filterCaptor = ArgumentCaptor.forClass(UserFilterRequest.class);
        verify(service).searchWithFilters(filterCaptor.capture(), eq(2), eq(50));
        UserFilterRequest filter = filterCaptor.getValue();
        assertThat(filter.username()).isEqualTo("dispatcher");
        assertThat(filter.email()).isEqualTo("ops@example.com");
        assertThat(filter.fullName()).isEqualTo("Dispatch");
        assertThat(filter.position()).isEqualTo("Operator");
        assertThat(filter.phone()).isEqualTo("+998");
        assertThat(filter.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(filter.departmentId()).isEqualTo(departmentId);
        assertThat(filter.primaryRoleId()).isEqualTo(primaryRoleId);
        assertThat(filter.primaryRoleCode()).isEqualTo("DISPATCHER");
        assertThat(filter.roleId()).isEqualTo(roleId);
        assertThat(filter.roleCode()).isEqualTo("MECHANIC");
        assertThat(filter.lastLoginFrom()).isEqualTo(java.time.Instant.parse("2026-06-01T00:00:00Z"));
        assertThat(filter.lastLoginTo()).isEqualTo(java.time.Instant.parse("2026-06-19T23:59:59Z"));
    }

    private static UserDto userDto(String username, String email, String fullName, String phone) {
        return new UserDto(
                UUID.randomUUID(),
                username,
                email,
                fullName,
                "Technician",
                phone,
                UserStatus.ACTIVE,
                null,
                null,
                List.of(),
                null
        );
    }
}
