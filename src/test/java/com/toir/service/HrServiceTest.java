package com.toir.service;

import com.toir.dto.hr.EmployeeDto;
import com.toir.dto.hr.EmployeeRequest;
import com.toir.entity.Department;
import com.toir.entity.users.Brigade;
import com.toir.entity.users.Employee;
import com.toir.enums.DepartmentType;
import com.toir.repository.TimesheetEntryRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.projects.BrigadeRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.service.users.HrService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrServiceTest {

    @Mock
    EmployeeRepository employeeRepository;

    @Mock
    TimesheetEntryRepository timesheetRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    BrigadeRepository brigadeRepository;

    @InjectMocks
    HrService service;

    @Test
    void listEmployeesIncludesDepartmentNameAndBrigadeName() {
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        Employee employee = employee(employeeId, departmentId, brigadeId);

        Department department = department(departmentId, "Mechanical");
        Brigade brigade = brigade(brigadeId, "Repair Brigade A");

        when(employeeRepository.searchEmployees(
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(
                List.of(employee),
                PageRequest.of(0, 20),
                1
        ));

        when(departmentRepository.findAllByIdInAndIsDeletedFalse(List.of(departmentId)))
                .thenReturn(List.of(department));
        when(brigadeRepository.findAllByIdInAndIsDeletedFalse(List.of(brigadeId)))
                .thenReturn(List.of(brigade));

        var result = service.listEmployees(0, 20, null, null);

        assertThat(result.getContent()).hasSize(1);

        EmployeeDto dto = result.getContent().getFirst();

        assertThat(dto.id()).isEqualTo(employeeId);
        assertThat(dto.departmentId()).isEqualTo(departmentId);
        assertThat(dto.departmentName()).isEqualTo("Mechanical");
        assertThat(dto.brigadeId()).isEqualTo(brigadeId);
        assertThat(dto.brigadeName()).isEqualTo("Repair Brigade A");

        verify(departmentRepository).findAllByIdInAndIsDeletedFalse(List.of(departmentId));
        verify(brigadeRepository).findAllByIdInAndIsDeletedFalse(List.of(brigadeId));
    }

    @Test
    void listEmployeesWithBlankDepartmentAndBrigadeReturnsNullNames() {
        UUID employeeId = UUID.randomUUID();

        Employee employee = employee(employeeId, null, null);

        when(employeeRepository.searchEmployees(
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(
                List.of(employee),
                PageRequest.of(0, 20),
                1
        ));

        var result = service.listEmployees(0, 20, null, null);

        EmployeeDto dto = result.getContent().getFirst();

        assertThat(dto.id()).isEqualTo(employeeId);
        assertThat(dto.departmentId()).isNull();
        assertThat(dto.departmentName()).isNull();
        assertThat(dto.brigadeId()).isNull();
        assertThat(dto.brigadeName()).isNull();
    }

    @Test
    void getEmployeeIncludesDepartmentNameAndBrigadeName() {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();

        Employee employee = employee(employeeId, departmentId, brigadeId);
        Department department = department(departmentId, "Mechanical");
        Brigade brigade = brigade(brigadeId, "Repair Brigade A");

        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId))
                .thenReturn(Optional.of(employee));
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(List.of(departmentId)))
                .thenReturn(List.of(department));
        when(brigadeRepository.findAllByIdInAndIsDeletedFalse(List.of(brigadeId)))
                .thenReturn(List.of(brigade));

        EmployeeDto result = service.getEmployee(employeeId);

        assertThat(result.id()).isEqualTo(employeeId);
        assertThat(result.departmentId()).isEqualTo(departmentId);
        assertThat(result.departmentName()).isEqualTo("Mechanical");
        assertThat(result.brigadeId()).isEqualTo(brigadeId);
        assertThat(result.brigadeName()).isEqualTo("Repair Brigade A");

        verify(employeeRepository).findByIdAndIsDeletedFalse(employeeId);
        verify(departmentRepository).findAllByIdInAndIsDeletedFalse(List.of(departmentId));
        verify(brigadeRepository).findAllByIdInAndIsDeletedFalse(List.of(brigadeId));
    }

    @Test
    void createEmployeeReturnsDepartmentNameAndBrigadeName() {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();

        EmployeeRequest request = new EmployeeRequest(
                "EMP-001",
                "Ali",
                "Valiyev",
                "Akmalovich",
                "Engineer",
                departmentId,
                brigadeId,
                null,
                LocalDate.of(2025, 1, 10),
                null,
                "A",
                "+998901112233",
                "ali@example.com",
                true
        );

        Department department = department(departmentId, "Mechanical");
        Brigade brigade = brigade(brigadeId, "Repair Brigade A");

        when(employeeRepository.existsByPersonnelNumberAndIsDeletedFalse("EMP-001"))
                .thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee saved = invocation.getArgument(0);
            saved.setId(employeeId);
            return saved;
        });
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(List.of(departmentId)))
                .thenReturn(List.of(department));
        when(brigadeRepository.findAllByIdInAndIsDeletedFalse(List.of(brigadeId)))
                .thenReturn(List.of(brigade));

        EmployeeDto result = service.createEmployee(request);

        assertThat(result.id()).isEqualTo(employeeId);
        assertThat(result.departmentId()).isEqualTo(departmentId);
        assertThat(result.departmentName()).isEqualTo("Mechanical");
        assertThat(result.brigadeId()).isEqualTo(brigadeId);
        assertThat(result.brigadeName()).isEqualTo("Repair Brigade A");

        verify(employeeRepository).existsByPersonnelNumberAndIsDeletedFalse("EMP-001");
        verify(employeeRepository).save(any(Employee.class));
    }

    @Test
    void updateEmployeeReturnsDepartmentNameAndBrigadeName() {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();

        Employee existing = employee(employeeId, UUID.randomUUID(), null);

        EmployeeRequest request = new EmployeeRequest(
                "EMP-001",
                "Ali",
                "Valiyev",
                "Akmalovich",
                "Senior Engineer",
                departmentId,
                brigadeId,
                null,
                LocalDate.of(2025, 1, 10),
                null,
                "A",
                "+998901112233",
                "ali@example.com",
                true
        );

        Department department = department(departmentId, "Mechanical");
        Brigade brigade = brigade(brigadeId, "Repair Brigade A");

        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId))
                .thenReturn(Optional.of(existing));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(List.of(departmentId)))
                .thenReturn(List.of(department));
        when(brigadeRepository.findAllByIdInAndIsDeletedFalse(List.of(brigadeId)))
                .thenReturn(List.of(brigade));

        EmployeeDto result = service.updateEmployee(employeeId, request);

        assertThat(result.id()).isEqualTo(employeeId);
        assertThat(result.position()).isEqualTo("Senior Engineer");
        assertThat(result.departmentId()).isEqualTo(departmentId);
        assertThat(result.departmentName()).isEqualTo("Mechanical");
        assertThat(result.brigadeId()).isEqualTo(brigadeId);
        assertThat(result.brigadeName()).isEqualTo("Repair Brigade A");

        verify(employeeRepository).findByIdAndIsDeletedFalse(employeeId);
        verify(employeeRepository).save(any(Employee.class));
    }

    private Employee employee(UUID employeeId, UUID departmentId, UUID brigadeId) {
        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setPersonnelNumber("EMP-001");
        employee.setFirstName("Ali");
        employee.setLastName("Valiyev");
        employee.setMiddleName("Akmalovich");
        employee.setPosition("Engineer");
        employee.setDepartmentId(departmentId);
        employee.setBrigadeId(brigadeId);
        employee.setHireDate(LocalDate.of(2025, 1, 10));
        employee.setGrade("A");
        employee.setPhone("+998901112233");
        employee.setEmail("ali@example.com");
        employee.setActive(true);
        employee.setDeleted(false);
        return employee;
    }

    private Department department(UUID departmentId, String name) {
        Department department = new Department();
        department.setId(departmentId);
        department.setCode("DEP-001");
        department.setName(name);
        department.setType(DepartmentType.ADMINISTRATION);
        department.setDeleted(false);
        return department;
    }

    private Brigade brigade(UUID brigadeId, String name) {
        Brigade brigade = new Brigade();
        brigade.setId(brigadeId);
        brigade.setCode("BR-001");
        brigade.setName(name);
        brigade.setActive(true);
        brigade.setDeleted(false);
        return brigade;
    }
}