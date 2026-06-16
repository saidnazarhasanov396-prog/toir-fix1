package com.toir.security;

import com.toir.dto.hr.EmployeeRequest;
import com.toir.entity.users.Employee;
import com.toir.exception.RestException;
import com.toir.repository.TimesheetEntryRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.projects.BrigadeRepository;
import com.toir.repository.projects.EmployeeStatsProjection;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.EmployeeWorkRoleAssignmentRepository;
import com.toir.repository.users.EmployeeWorkRoleRepository;
import com.toir.service.users.HrService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmployeePbacScopeTest {

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

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    EmployeeWorkRoleRepository employeeWorkRoleRepository;

    @Mock
    EmployeeWorkRoleAssignmentRepository employeeWorkRoleAssignmentRepository;

    @InjectMocks
    HrService service;

    @BeforeEach
    void setUp() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(false);
    }

    @Test
    void scopeAdminCanListAllEmployees() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(employeeRepository.searchEmployees(null, null, null, null, null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(
                        List.of(employee(UUID.randomUUID(), UUID.randomUUID(), null)),
                        PageRequest.of(0, 20),
                        1
                ));

        var result = service.listEmployees(0, 20, null, null, null, null);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(employeeRepository).searchEmployees(null, null, null, null, null, null, PageRequest.of(0, 20));
    }

    @Test
    void nonAdminListWithoutDepartmentIsRestrictedToCurrentDepartment() {
        UUID currentDepartmentId = UUID.randomUUID();
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(currentDepartmentId);
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(currentDepartmentId);
        when(employeeRepository.searchEmployees(null, null, null, null, currentDepartmentId, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(
                        List.of(employee(UUID.randomUUID(), currentDepartmentId, null)),
                        PageRequest.of(0, 20),
                        1
                ));

        var result = service.listEmployees(0, 20, null, null, null, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().departmentId()).isEqualTo(currentDepartmentId);
        verify(employeeRepository).searchEmployees(null, null, null, null, currentDepartmentId, null, PageRequest.of(0, 20));
    }

    @Test
    void nonAdminRequestedDepartmentIsClampedToCurrentDepartment() {
        UUID requestedDepartmentId = UUID.randomUUID();
        UUID currentDepartmentId = UUID.randomUUID();
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(currentDepartmentId);
        when(scopeAccessService.enforceDepartmentScope(requestedDepartmentId)).thenReturn(currentDepartmentId);
        when(employeeRepository.searchEmployees("Ali", null, null, true, currentDepartmentId, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.listEmployees(0, 20, "Ali", true, requestedDepartmentId, null);

        verify(employeeRepository).searchEmployees("Ali", null, null, true, currentDepartmentId, null, PageRequest.of(0, 20));
    }

    @Test
    void nonAdminWithoutDepartmentCannotListOrReadStatsGlobally() {
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(null);
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);

        assertThatThrownBy(() -> service.listEmployees(0, 20, null, null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getEmployeeStats(null, null, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(employeeRepository, never()).searchEmployees(any(), any(), any(), any(), any(), any(), any());
        verify(employeeRepository, never()).getEmployeeStats(any(), any(), any());
    }

    @Test
    void detailAllowsDepartmentOrOwnerAndDeniesDifferentDepartment() {
        UUID departmentId = UUID.randomUUID();
        UUID ownerEmployeeId = UUID.randomUUID();
        UUID outOfScopeEmployeeId = UUID.randomUUID();
        Employee departmentEmployee = employee(UUID.randomUUID(), departmentId, null);
        Employee ownerEmployee = employee(ownerEmployeeId, UUID.randomUUID(), UUID.randomUUID());
        Employee outOfScopeEmployee = employee(outOfScopeEmployeeId, UUID.randomUUID(), null);
        when(employeeRepository.findByIdAndIsDeletedFalse(departmentEmployee.getId())).thenReturn(Optional.of(departmentEmployee));
        when(employeeRepository.findByIdAndIsDeletedFalse(ownerEmployeeId)).thenReturn(Optional.of(ownerEmployee));
        when(employeeRepository.findByIdAndIsDeletedFalse(outOfScopeEmployeeId)).thenReturn(Optional.of(outOfScopeEmployee));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(scopeAccessService.canAccessEmployee(ownerEmployeeId)).thenReturn(true);

        assertThat(service.getEmployee(departmentEmployee.getId()).id()).isEqualTo(departmentEmployee.getId());
        assertThat(service.getEmployee(ownerEmployeeId).id()).isEqualTo(ownerEmployeeId);
        assertThatThrownBy(() -> service.getEmployee(outOfScopeEmployeeId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void currentUserCanReadOwnEmployeeRecordWithoutDepartment() {
        UUID employeeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Employee employee = employee(employeeId, null, userId);
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(Optional.of(employee));
        when(scopeAccessService.canAccessAssignedUser(userId)).thenReturn(true);

        assertThat(service.getEmployee(employeeId).id()).isEqualTo(employeeId);
    }

    @Test
    void selfReadDoesNotGrantSelfUpdateOrDelete() {
        UUID employeeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Employee employee = employee(employeeId, null, userId);
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(Optional.of(employee));
        when(scopeAccessService.canAccessAssignedUser(userId)).thenReturn(true);
        when(scopeAccessService.canAccessDepartment(null)).thenReturn(false);

        assertThat(service.getEmployee(employeeId).id()).isEqualTo(employeeId);
        assertThatThrownBy(() -> service.updateEmployee(employeeId, request("EMP-SELF", null, userId)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.deleteEmployee(employeeId))
                .isInstanceOf(AccessDeniedException.class);

        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void scopeAdminCanReadAndMutateEmployeeInDifferentDepartment() {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Employee employee = employee(employeeId, departmentId, null);
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.getEmployee(employeeId).id()).isEqualTo(employeeId);
        assertThat(service.updateEmployee(employeeId, request("EMP-ADMIN", departmentId, null)).departmentId())
                .isEqualTo(departmentId);
        service.deleteEmployee(employeeId);

        assertThat(employee.isDeleted()).isTrue();
    }

    @Test
    void missingEmployeeRemainsNotFound() {
        UUID employeeId = UUID.randomUUID();
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEmployee(employeeId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Employee not found");
    }

    @Test
    void createAllowsOwnDepartmentAndDeniesForbiddenDepartment() {
        UUID ownDepartmentId = UUID.randomUUID();
        UUID otherDepartmentId = UUID.randomUUID();
        when(scopeAccessService.canAccessDepartment(ownDepartmentId)).thenReturn(true);
        when(scopeAccessService.canAccessDepartment(otherDepartmentId)).thenReturn(false);
        when(employeeRepository.existsByPersonnelNumberAndIsDeletedFalse("EMP-OWN")).thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        assertThat(service.createEmployee(request("EMP-OWN", ownDepartmentId, null)).departmentId())
                .isEqualTo(ownDepartmentId);
        assertThatThrownBy(() -> service.createEmployee(request("EMP-OTHER", otherDepartmentId, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateRequiresCurrentAndTargetDepartmentScope() {
        UUID employeeId = UUID.randomUUID();
        UUID currentDepartmentId = UUID.randomUUID();
        UUID allowedDepartmentId = UUID.randomUUID();
        UUID forbiddenDepartmentId = UUID.randomUUID();
        Employee employee = employee(employeeId, currentDepartmentId, null);
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(Optional.of(employee));
        when(scopeAccessService.canAccessDepartment(currentDepartmentId)).thenReturn(true);
        when(scopeAccessService.canAccessDepartment(allowedDepartmentId)).thenReturn(true);
        when(scopeAccessService.canAccessDepartment(forbiddenDepartmentId)).thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.updateEmployee(employeeId, request("EMP-001", allowedDepartmentId, null)).departmentId())
                .isEqualTo(allowedDepartmentId);
        assertThatThrownBy(() -> service.updateEmployee(employeeId, request("EMP-001", forbiddenDepartmentId, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateAndDeleteOutOfScopeEmployeeAreDenied() {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId))
                .thenReturn(Optional.of(employee(employeeId, departmentId, null)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.updateEmployee(employeeId, request("EMP-001", departmentId, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.deleteEmployee(employeeId))
                .isInstanceOf(AccessDeniedException.class);

        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void statsUseScopedDepartmentAndDoNotLeakGlobalCounts() {
        UUID currentDepartmentId = UUID.randomUUID();
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(currentDepartmentId);
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(currentDepartmentId);
        when(employeeRepository.getEmployeeStats(eq(currentDepartmentId), eq(null), eq("%ali%")))
                .thenReturn(stats(2L, 1L, 1L, 0L));

        var result = service.getEmployeeStats(null, null, "Ali");

        assertThat(result.total()).isEqualTo(2);
        verify(employeeRepository).getEmployeeStats(currentDepartmentId, null, "%ali%");
    }

    private Employee employee(UUID employeeId, UUID departmentId, UUID userId) {
        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setPersonnelNumber("EMP-001");
        employee.setFirstName("Ali");
        employee.setLastName("Valiyev");
        employee.setPosition("Engineer");
        employee.setDepartmentId(departmentId);
        employee.setUserId(userId);
        employee.setHireDate(LocalDate.of(2025, 1, 10));
        employee.setActive(true);
        return employee;
    }

    private EmployeeRequest request(String personnelNumber, UUID departmentId, UUID userId) {
        return new EmployeeRequest(
                personnelNumber,
                "Ali",
                "Valiyev",
                null,
                "Engineer",
                departmentId,
                null,
                userId,
                LocalDate.of(2025, 1, 10),
                null,
                "A",
                "+998901112233",
                "ali@example.com",
                true
        );
    }

    private EmployeeStatsProjection stats(Long total, Long active, Long terminated, Long withoutEmail) {
        return new EmployeeStatsProjection() {
            @Override
            public Long getTotal() {
                return total;
            }

            @Override
            public Long getActive() {
                return active;
            }

            @Override
            public Long getTerminated() {
                return terminated;
            }

            @Override
            public Long getWithoutEmail() {
                return withoutEmail;
            }
        };
    }
}
