package com.toir.controller;

import com.toir.controller.users.UserController;
import com.toir.dto.user.UserDto;
import com.toir.enums.UserStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.users.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

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
        when(service.search("ali", 0, 20))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/users")
                        .param("search", "ali")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].username").value("ali.worker"))
                .andExpect(jsonPath("$.content[0].fullName").value("Ali Worker"));

        verify(service).search("ali", 0, 20);
    }

    @Test
    void listWithSearchIsCaseInsensitive() throws Exception {
        UserDto dto = userDto("ali.worker", "ali@example.com", "Ali Worker", "+998901112233");
        when(service.search("ALI", 0, 20))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/users")
                        .param("search", "ALI")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].email").value("ali@example.com"));

        verify(service).search("ALI", 0, 20);
    }

    @Test
    void listWithBlankSearchReturnsAllNonDeleted() throws Exception {
        UserDto dto1 = userDto("ali.worker", "ali@example.com", "Ali Worker", "+998901112233");
        UserDto dto2 = userDto("john.viewer", "john@example.com", "John Viewer", "+998901112244");
        when(service.search("", 0, 20))
                .thenReturn(new PageImpl<>(List.of(dto1, dto2), PageRequest.of(0, 20), 2));

        mockMvc.perform(get("/api/v1/users")
                        .param("search", "")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        verify(service).search("", 0, 20);
    }

    @Test
    void listExcludesDeletedUsers() throws Exception {
        UserDto dto = userDto("active.user", "active@example.com", "Active User", "+998901112255");
        when(service.search(null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/users")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].username").value("active.user"));

        verify(service).search(null, 0, 20);
    }

    @Test
    void listPreservesPaginationMetadata() throws Exception {
        UserDto dto = userDto("paged.user", "paged@example.com", "Paged User", "+998901112266");
        Page<UserDto> page = new PageImpl<>(List.of(dto), PageRequest.of(1, 1), 3);
        when(service.search("ali", 1, 1)).thenReturn(page);

        mockMvc.perform(get("/api/v1/users")
                        .param("search", "ali")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3));

        verify(service).search("ali", 1, 1);
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
