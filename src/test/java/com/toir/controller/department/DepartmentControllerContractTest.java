package com.toir.controller.department;

import com.toir.dto.department.DepartmentDto;
import com.toir.dto.hr.EmployeeDto;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
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
        DepartmentDto created = departmentDto("WS-001", "Workshop");
        when(service.create(org.mockito.ArgumentMatchers.any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/departments")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Workshop",
                                  "type": "WORKSHOP",
                                  "description": "UI created"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(created.id().toString()))
                .andExpect(jsonPath("$.code").value("WS-001"))
                .andExpect(jsonPath("$.name").value("Workshop"))
                .andExpect(jsonPath("$.type").value("WORKSHOP"));
    }

    @Test
    void createIgnoresClientSentCode() throws Exception {
        DepartmentDto created = departmentDto("WS-001", "Workshop");
        when(service.create(org.mockito.ArgumentMatchers.any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/departments")
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "CLIENT-CODE",
                                  "name": "Workshop",
                                  "type": "WORKSHOP",
                                  "description": "UI created"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("WS-001"));
    }

    @Test
    void getEmployeesByDepartmentReturnsEmployees() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        EmployeeDto employee = new EmployeeDto(
                employeeId,
                "EMP-001",
                "Ali",
                "Valiyev",
                "Akmalovich",
                "Engineer",
                departmentId,
                "Mechanical",
                null,
                "Repair Brigade A",
                null,
                LocalDate.of(2025, 1, 10),
                null,
                "A",
                "+998901112233",
                "ali@example.com",
                true
        );

        when(service.findEmployeesByDepartment(departmentId))
                .thenReturn(List.of(employee));

        mockMvc.perform(get("/api/v1/departments/{departmentId}/employees", departmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(employeeId.toString()))
                .andExpect(jsonPath("$[0].personnelNumber").value("EMP-001"))
                .andExpect(jsonPath("$[0].firstName").value("Ali"))
                .andExpect(jsonPath("$[0].lastName").value("Valiyev"))
                .andExpect(jsonPath("$[0].position").value("Engineer"))
                .andExpect(jsonPath("$[0].departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].departmentName").value("Mechanical"))
                .andExpect(jsonPath("$[0].brigadeName").value("Repair Brigade A"));
        verify(service).findEmployeesByDepartment(departmentId);
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
    void listWithBlankSearchReturns200() throws Exception {
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

    @Test
    void listWithWhitespaceSearchReturns200() throws Exception {
        DepartmentDto created = departmentDto("UI-E2E-20260516052136", "Workshop");
        when(service.findAll(null, "   ")).thenReturn(List.of(created));

        mockMvc.perform(get("/api/v1/departments")
                        .param("search", "   ")
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
