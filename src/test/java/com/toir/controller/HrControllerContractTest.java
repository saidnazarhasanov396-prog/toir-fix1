package com.toir.controller;

import com.toir.controller.users.HrController;
import com.toir.dto.hr.EmployeeDto;
import com.toir.exception.GlobalExceptionHandler;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new HrController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listEmployeesReturnsDepartmentNameAndBrigadeName() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();

        Page<EmployeeDto> page = getEmployeeDtos(employeeId, departmentId, brigadeId);

        when(service.listEmployees(-1, 20, null, null)).thenReturn(page);

        mockMvc.perform(get("/api/v1/hr/employees")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(employeeId.toString()))
                .andExpect(jsonPath("$.content[0].departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.content[0].departmentName").value("Mechanical"))
                .andExpect(jsonPath("$.content[0].brigadeId").value(brigadeId.toString()))
                .andExpect(jsonPath("$.content[0].brigadeName").value("Repair Brigade A"));

        verify(service).listEmployees(-1, 20, null, null);
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