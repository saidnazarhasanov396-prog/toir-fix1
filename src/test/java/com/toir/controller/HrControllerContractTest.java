package com.toir.controller;

import com.toir.controller.users.HrController;
import com.toir.dto.hr.EmployeeDto;
import com.toir.dto.hr.EmployeeStatsResponse;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.security.SecurityScope;
import com.toir.service.users.HrService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HrControllerContractTest {

    @Mock
    HrService service;

    @Mock
    SecurityScope securityScope;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new HrController(service, securityScope))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listEmployeesReturnsDepartmentNameAndBrigadeName() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();

        Page<EmployeeDto> page = getEmployeeDtos(employeeId, departmentId, brigadeId);

        when(securityScope.enforceDepartmentScope(null)).thenReturn(null);
        when(service.listEmployees(-1, 20, null, null, null, null)).thenReturn(page);

        mockMvc.perform(get("/api/v1/hr/employees")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(employeeId.toString()))
                .andExpect(jsonPath("$.content[0].departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.content[0].departmentName").value("Mechanical"))
                .andExpect(jsonPath("$.content[0].brigadeId").value(brigadeId.toString()))
                .andExpect(jsonPath("$.content[0].brigadeName").value("Repair Brigade A"));

        verify(service).listEmployees(-1, 20, null, null, null, null);
    }
    @Test
    void getEmployeeReturnsDepartmentNameAndBrigadeName() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();

        EmployeeDto employee = new EmployeeDto(
                employeeId,
                "EMP-001",
                "Ali",
                "Valiyev",
                "Akmalovich",
                "Engineer",
                departmentId,
                "Mechanical",
                brigadeId,
                "Repair Brigade A",
                null,
                LocalDate.of(2025, 1, 10),
                null,
                "A",
                "+998901112233",
                "ali@example.com",
                true
        );

        when(service.getEmployee(employeeId)).thenReturn(employee);

        mockMvc.perform(get("/api/v1/hr/employees/{id}", employeeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(employeeId.toString()))
                .andExpect(jsonPath("$.departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.departmentName").value("Mechanical"))
                .andExpect(jsonPath("$.brigadeId").value(brigadeId.toString()))
                .andExpect(jsonPath("$.brigadeName").value("Repair Brigade A"));

        verify(service).getEmployee(employeeId);
    }

    @Test
    void listEmployeesPassesDepartmentAndBrigadeFiltersToService() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();

        Page<EmployeeDto> page = getEmployeeDtos(employeeId, departmentId, brigadeId);

        when(securityScope.enforceDepartmentScope(departmentId)).thenReturn(departmentId);
        when(service.listEmployees(-1, 20, "Ali", true, departmentId, brigadeId))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/hr/employees")
                        .param("page", "0")
                        .param("size", "20")
                        .param("search", "Ali")
                        .param("activeOnly", "true")
                        .param("departmentId", departmentId.toString())
                        .param("brigadeId", brigadeId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.content[0].brigadeId").value(brigadeId.toString()));

        verify(service).listEmployees(-1, 20, "Ali", true, departmentId, brigadeId);
    }

    @Test
    void listEmployeesPassesWorkRoleFilterToService() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        Page<EmployeeDto> page = getEmployeeDtos(employeeId, departmentId, null);

        when(securityScope.enforceDepartmentScope(departmentId)).thenReturn(departmentId);
        when(service.listEmployees(-1, 20, null, true, departmentId, null, "DRIVER"))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/hr/employees")
                        .param("page", "0")
                        .param("size", "20")
                        .param("activeOnly", "true")
                        .param("departmentId", departmentId.toString())
                        .param("workRoleCode", "DRIVER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].departmentId").value(departmentId.toString()));

        verify(service).listEmployees(-1, 20, null, true, departmentId, null, "DRIVER");
    }

    @Test
    void listEmployeesUsesScopedDepartmentFilter() throws Exception {
        UUID requestedDepartmentId = UUID.randomUUID();
        UUID scopedDepartmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        Page<EmployeeDto> page = getEmployeeDtos(employeeId, scopedDepartmentId, brigadeId);

        when(securityScope.enforceDepartmentScope(requestedDepartmentId))
                .thenReturn(scopedDepartmentId);
        when(service.listEmployees(-1, 20, null, null, scopedDepartmentId, brigadeId))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/hr/employees")
                        .param("page", "0")
                        .param("size", "20")
                        .param("departmentId", requestedDepartmentId.toString())
                        .param("brigadeId", brigadeId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].departmentId").value(scopedDepartmentId.toString()));

        verify(securityScope).enforceDepartmentScope(requestedDepartmentId);
        verify(service).listEmployees(-1, 20, null, null, scopedDepartmentId, brigadeId);
    }


    @Test
    void employeeStatsWithoutFiltersReturnsStats() throws Exception {
        EmployeeStatsResponse response = new EmployeeStatsResponse(
                27,
                10,
                0,
                1
        );

        when(securityScope.enforceDepartmentScope(null)).thenReturn(null);
        when(service.getEmployeeStats(null, null, null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/hr/employees/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(27))
                .andExpect(jsonPath("$.active").value(10))
                .andExpect(jsonPath("$.terminated").value(0))
                .andExpect(jsonPath("$.withoutEmail").value(1));

        verify(service).getEmployeeStats(null, null, null);
    }

    @Test
    void employeeStatsWithFiltersPassesParamsToService() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();

        EmployeeStatsResponse response = new EmployeeStatsResponse(
                5,
                4,
                1,
                2
        );

        when(securityScope.enforceDepartmentScope(departmentId)).thenReturn(departmentId);
        when(service.getEmployeeStats(departmentId, brigadeId, "Ali"))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/hr/employees/stats")
                        .param("departmentId", departmentId.toString())
                        .param("brigadeId", brigadeId.toString())
                        .param("search", "Ali"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.active").value(4))
                .andExpect(jsonPath("$.terminated").value(1))
                .andExpect(jsonPath("$.withoutEmail").value(2));

        verify(service).getEmployeeStats(departmentId, brigadeId, "Ali");
    }

    

    private static Page<EmployeeDto> getEmployeeDtos(UUID employeeId, UUID departmentId, UUID brigadeId) {
        EmployeeDto employee = new EmployeeDto(
                employeeId,
                "EMP-001",
                "Ali",
                "Valiyev",
                "Akmalovich",
                "Engineer",
                departmentId,
                "Mechanical",
                brigadeId,
                "Repair Brigade A",
                null,
                LocalDate.of(2025, 1, 10),
                null,
                "A",
                "+998901112233",
                "ali@example.com",
                true
        );

        return new PageImpl<>(
                List.of(employee),
                PageRequest.of(0, 20),
                1
        );


    }
}
