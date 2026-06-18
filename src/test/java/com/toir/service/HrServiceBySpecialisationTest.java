package com.toir.service;

import com.toir.dto.hr.EmployeeDto;
import com.toir.entity.users.Employee;
import com.toir.entity.users.EmployeeSpecialisation;
import com.toir.repository.TimesheetEntryRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.projects.BrigadeRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.EmployeeSpecialisationRepository;
import com.toir.repository.users.EmployeeWorkRoleAssignmentRepository;
import com.toir.repository.users.EmployeeWorkRoleRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.users.HrService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrServiceBySpecialisationTest {

    @Mock EmployeeRepository employeeRepository;
    @Mock TimesheetEntryRepository timesheetRepository;
    @Mock AuditBuilderService auditBuilderService;
    @Mock DepartmentRepository departmentRepository;
    @Mock BrigadeRepository brigadeRepository;
    @Mock ScopeAccessService scopeAccessService;
    @Mock EmployeeWorkRoleRepository employeeWorkRoleRepository;
    @Mock EmployeeWorkRoleAssignmentRepository employeeWorkRoleAssignmentRepository;
    @Mock EmployeeSpecialisationRepository employeeSpecialisationRepository;

    @InjectMocks
    HrService service;

    @BeforeEach
    void setUp() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(employeeSpecialisationRepository.findByIdAndIsDeletedFalse(any(UUID.class)))
                .thenAnswer(inv -> Optional.of(specialisation(inv.getArgument(0))));
    }

    @Test
    void listEmployeesBySpecialisationDelegatesToRepository() {
        UUID specialisationId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Employee employee = employee(employeeId, UUID.randomUUID(), specialisationId);

        when(employeeRepository.findAllBySpecialisationIdAndIsDeletedFalseAndActiveTrue(specialisationId))
                .thenReturn(List.of(employee));
        when(employeeSpecialisationRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(specialisation(specialisationId)));

        List<EmployeeDto> result = service.listEmployeesBySpecialisation(specialisationId);

        verify(employeeRepository).findAllBySpecialisationIdAndIsDeletedFalseAndActiveTrue(specialisationId);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(employeeId);
    }

    @Test
    void listEmployeesBySpecialisationReturnsEmptyWhenNoMatch() {
        UUID specialisationId = UUID.randomUUID();
        when(employeeRepository.findAllBySpecialisationIdAndIsDeletedFalseAndActiveTrue(specialisationId))
                .thenReturn(List.of());

        List<EmployeeDto> result = service.listEmployeesBySpecialisation(specialisationId);

        assertThat(result).isEmpty();
    }

    @Test
    void listEmployeesBySpecialisationIncludesSpecialisationName() {
        UUID specialisationId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Employee employee = employee(employeeId, UUID.randomUUID(), specialisationId);
        EmployeeSpecialisation spec = specialisation(specialisationId);

        when(employeeRepository.findAllBySpecialisationIdAndIsDeletedFalseAndActiveTrue(specialisationId))
                .thenReturn(List.of(employee));
        when(employeeSpecialisationRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(spec));

        List<EmployeeDto> result = service.listEmployeesBySpecialisation(specialisationId);

        assertThat(result).hasSize(1);
        EmployeeDto dto = result.getFirst();
        assertThat(dto.specialisationId()).isEqualTo(specialisationId);
        assertThat(dto.specialisationNameRu()).isEqualTo("Механик");
        assertThat(dto.specialisationNameEn()).isEqualTo("Mechanic");
        assertThat(dto.specialisationNameUz()).isEqualTo("Mexanik");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Employee employee(UUID id, UUID departmentId, UUID specialisationId) {
        Employee e = new Employee();
        e.setId(id);
        e.setPersonnelNumber("EMP-001");
        e.setFirstName("Ali");
        e.setLastName("Valiyev");
        e.setPosition("Engineer");
        e.setDepartmentId(departmentId);
        e.setSpecialisationId(specialisationId);
        e.setHireDate(LocalDate.of(2025, 1, 1));
        e.setActive(true);
        e.setDeleted(false);
        return e;
    }

    private EmployeeSpecialisation specialisation(UUID id) {
        EmployeeSpecialisation s = new EmployeeSpecialisation();
        s.setId(id);
        s.setNameRu("Механик");
        s.setNameEn("Mechanic");
        s.setNameUz("Mexanik");
        s.setActive(true);
        s.setDeleted(false);
        return s;
    }
}
