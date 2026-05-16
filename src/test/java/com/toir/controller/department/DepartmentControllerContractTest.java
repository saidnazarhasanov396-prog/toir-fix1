package com.toir.controller.department;

import com.toir.dto.department.DepartmentDto;
import com.toir.enums.DepartmentType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.department.DepartmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DepartmentControllerContractTest {

    @Mock
    DepartmentService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DepartmentController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createReturnsCreatedDepartment() throws Exception {
        DepartmentDto created = departmentDto("UI-E2E-20260516052136", "Workshop");
        when(service.create(org.mockito.ArgumentMatchers.any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/departments")
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "UI-E2E-20260516052136",
                                  "name": "Workshop",
                                  "type": "WORKSHOP",
                                  "description": "UI created"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(created.id().toString()))
                .andExpect(jsonPath("$.code").value("UI-E2E-20260516052136"))
                .andExpect(jsonPath("$.name").value("Workshop"))
                .andExpect(jsonPath("$.type").value("WORKSHOP"));
    }

    @Test
    void listAfterCreateCanReturnDepartment() throws Exception {
        DepartmentDto created = departmentDto("UI-E2E-20260516052136", "Workshop");
        when(service.findAll(null, "")).thenReturn(List.of(created));

        mockMvc.perform(get("/api/v1/departments")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(created.id().toString()))
                .andExpect(jsonPath("$.content[0].code").value("UI-E2E-20260516052136"));
    }

    @Test
    void listSearchFindsCreatedDepartment() throws Exception {
        DepartmentDto created = departmentDto("UI-E2E-20260516052136", "Workshop");
        when(service.findAll(null, "Workshop")).thenReturn(List.of(created));

        mockMvc.perform(get("/api/v1/departments")
                        .param("search", "Workshop")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Workshop"))
                .andExpect(jsonPath("$.content[0].code").value("UI-E2E-20260516052136"));
    }

    @Test
    void listWithBlankSearchDoesNotHideCreatedDepartment() throws Exception {
        DepartmentDto created = departmentDto("UI-E2E-20260516052136", "Workshop");
        when(service.findAll(null, "")).thenReturn(List.of(created));

        mockMvc.perform(get("/api/v1/departments")
                        .param("search", "")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Workshop"))
                .andExpect(jsonPath("$.content[0].code").value("UI-E2E-20260516052136"));
    }

    private DepartmentDto departmentDto(String code, String name) {
        return new DepartmentDto(
                UUID.randomUUID(),
                code,
                name,
                null,
                null,
                DepartmentType.WORKSHOP,
                null,
                "UI created"
        );
    }
}

