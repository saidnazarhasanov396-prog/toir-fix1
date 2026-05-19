package com.toir.service.department;

import com.toir.dto.department.DepartmentDto;
import com.toir.dto.department.DepartmentRequest;
import com.toir.dto.hr.EmployeeDto;
import com.toir.entity.Department;
import com.toir.entity.users.Brigade;
import com.toir.entity.users.Employee;
import com.toir.enums.DepartmentType;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.projects.BrigadeRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    DepartmentRepository repository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    EmployeeRepository employeeRepository;

    @Mock
    BrigadeRepository brigadeRepository;


    @InjectMocks
    DepartmentService service;

    @Test
    void createPersistsNonDeletedDepartment() {
        DepartmentRequest request = new DepartmentRequest(
                "UI-E2E-20260516052136",
                "Workshop",
                DepartmentType.WORKSHOP,
                null,
                "UI created"
        );
        when(repository.existsByCodeAndIsDeletedFalse(request.code())).thenReturn(false);
        when(repository.save(any(Department.class))).thenAnswer(invocation -> {
            Department saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        DepartmentDto created = service.create(request);

        ArgumentCaptor<Department> captor = ArgumentCaptor.forClass(Department.class);
        verify(repository).save(captor.capture());
        Department persisted = captor.getValue();
        assertThat(persisted.isDeleted()).isFalse();
        assertThat(persisted.getCode()).isEqualTo(request.code());
        assertThat(persisted.getName()).isEqualTo(request.name());
        assertThat(created.code()).isEqualTo(request.code());
        assertThat(created.name()).isEqualTo(request.name());
    }

    @Test
    void searchFindsCreatedDepartment() {
        Department department = department("UI-E2E-20260516052136", "Workshop");
        when(repository.findAllByIsDeletedFalseAndByType(null, "%ui-e2e%"))
                .thenReturn(List.of(department));

        List<DepartmentDto> results = service.findAll(null, "UI-E2E");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().code()).isEqualTo("UI-E2E-20260516052136");
        assertThat(results.getFirst().name()).isEqualTo("Workshop");
        verify(repository).findAllByIsDeletedFalseAndByType(null, "%ui-e2e%");
    }

    @Test
    void blankSearchFallbackWorks() {
        Department department = department("UI-E2E-20260516052136", "Workshop");
        when(repository.findAllByIsDeletedFalseAndByType(null, null))
                .thenReturn(List.of(department));

        List<DepartmentDto> results = service.findAll(null, "");

        assertThat(results).hasSize(1);
        verify(repository).findAllByIsDeletedFalseAndByType(null, null);
    }

    @Test
    void whitespaceSearchFallbackWorks() {
        Department department = department("UI-E2E-20260516052136", "Workshop");
        when(repository.findAllByIsDeletedFalseAndByType(null, null))
                .thenReturn(List.of(department));

        List<DepartmentDto> results = service.findAll(null, "   ");

        assertThat(results).hasSize(1);
        verify(repository).findAllByIsDeletedFalseAndByType(null, null);
    }

    @Test
    void searchBuildsLowercasePattern() {
        Department department = department("UI-E2E-20260516052136", "Workshop");
        when(repository.findAllByIsDeletedFalseAndByType(null, "%workshop%"))
                .thenReturn(List.of(department));

        List<DepartmentDto> results = service.findAll(null, "  WoRkShOp  ");

        assertThat(results).hasSize(1);
        verify(repository).findAllByIsDeletedFalseAndByType(null, "%workshop%");
    }

    @Test
    void findEmployeesByDepartmentReturnsEmployeesForExistingDepartment() {
        UUID departmentId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();

        Department department = new Department();
        department.setId(departmentId);
        department.setCode("DEP-001");
        department.setName("Mechanical");
        department.setType(DepartmentType.ADMINISTRATION);
        department.setDeleted(false);

        Brigade brigade = new Brigade();
        brigade.setId(brigadeId);
        brigade.setCode("BR-001");
        brigade.setName("Repair Brigade A");
        brigade.setDepartmentId(departmentId);
        brigade.setActive(true);
        brigade.setDeleted(false);

        Employee employee = getEmployee(employeeId, departmentId, brigadeId);

        when(repository.findByIdAndIsDeletedFalse(departmentId))
                .thenReturn(Optional.of(department));
        when(employeeRepository.findAllByDepartmentIdAndIsDeletedFalse(departmentId))
                .thenReturn(List.of(employee));
        when(brigadeRepository.findAllByIdInAndIsDeletedFalse(List.of(brigadeId)))
                .thenReturn(List.of(brigade));

        List<EmployeeDto> result = service.findEmployeesByDepartment(departmentId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(employeeId);
        assertThat(result.getFirst().personnelNumber()).isEqualTo("EMP-001");
        assertThat(result.getFirst().firstName()).isEqualTo("Ali");
        assertThat(result.getFirst().lastName()).isEqualTo("Valiyev");
        assertThat(result.getFirst().departmentId()).isEqualTo(departmentId);
        assertThat(result.getFirst().departmentName()).isEqualTo("Mechanical");
        assertThat(result.getFirst().brigadeId()).isEqualTo(brigadeId);
        assertThat(result.getFirst().brigadeName()).isEqualTo("Repair Brigade A");
        assertThat(result.getFirst().active()).isTrue();

        verify(repository).findByIdAndIsDeletedFalse(departmentId);
        verify(employeeRepository).findAllByDepartmentIdAndIsDeletedFalse(departmentId);
        verify(brigadeRepository).findAllByIdInAndIsDeletedFalse(List.of(brigadeId));
    }

    private static Employee getEmployee(UUID employeeId, UUID departmentId, UUID brigadeId) {
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

    private Department department(String code, String name) {
        Department department = new Department();
        ReflectionTestUtils.setField(department, "id", UUID.randomUUID());
        department.setCode(code);
        department.setName(name);
        department.setType(DepartmentType.WORKSHOP);
        department.setDeleted(false);
        return department;
    }
}
