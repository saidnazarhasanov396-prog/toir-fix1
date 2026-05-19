package com.toir.security;

import com.toir.dto.hr.TimesheetEntryRequest;
import com.toir.entity.TimesheetEntry;
import com.toir.entity.users.Employee;
import com.toir.enums.TimesheetStatus;
import com.toir.exception.RestException;
import com.toir.repository.TimesheetEntryRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.projects.BrigadeRepository;
import com.toir.repository.users.EmployeeRepository;
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
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimesheetPbacScopeTest {

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

    @InjectMocks
    HrService service;

    @BeforeEach
    void setUp() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        lenient().when(scopeAccessService.currentEmployeeId()).thenReturn(Optional.empty());
    }

    @Test
    void scopeAdminCanListAllTimesheets() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID employeeId = UUID.randomUUID();
        when(timesheetRepository.findAllByWorkDateBetweenAndIsDeletedFalse(date(1), date(31)))
                .thenReturn(List.of(entry(UUID.randomUUID(), employeeId)));

        var result = service.timesheetRange(date(1), date(31));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().employeeId()).isEqualTo(employeeId);
    }

    @Test
    void nonAdminWithoutDepartmentAndEmployeeScopeGetsNoGlobalTimesheetData() {
        UUID employeeId = UUID.randomUUID();
        when(timesheetRepository.findAllByWorkDateBetweenAndIsDeletedFalse(date(1), date(31)))
                .thenReturn(List.of(entry(UUID.randomUUID(), employeeId)));
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId))
                .thenReturn(Optional.of(employee(employeeId, UUID.randomUUID())));
        when(scopeAccessService.canAccessEmployee(employeeId)).thenReturn(false);

        var result = service.timesheetRange(date(1), date(31));

        assertThat(result).isEmpty();
    }

    @Test
    void departmentScopedUserSeesOnlyTimesheetsForAllowedDepartmentEmployees() {
        UUID allowedDepartmentId = UUID.randomUUID();
        UUID allowedEmployeeId = UUID.randomUUID();
        UUID deniedEmployeeId = UUID.randomUUID();
        when(timesheetRepository.findAllByWorkDateBetweenAndIsDeletedFalse(date(1), date(31)))
                .thenReturn(List.of(
                        entry(UUID.randomUUID(), allowedEmployeeId),
                        entry(UUID.randomUUID(), deniedEmployeeId)
                ));
        when(employeeRepository.findByIdAndIsDeletedFalse(allowedEmployeeId))
                .thenReturn(Optional.of(employee(allowedEmployeeId, allowedDepartmentId)));
        when(employeeRepository.findByIdAndIsDeletedFalse(deniedEmployeeId))
                .thenReturn(Optional.of(employee(deniedEmployeeId, UUID.randomUUID())));
        when(scopeAccessService.canAccessDepartment(allowedDepartmentId)).thenReturn(true);

        var result = service.timesheetRange(date(1), date(31));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().employeeId()).isEqualTo(allowedEmployeeId);
    }

    @Test
    void ownerUserCanSeeOwnTimesheet() {
        UUID employeeId = UUID.randomUUID();
        when(scopeAccessService.canAccessEmployee(employeeId)).thenReturn(true);
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId))
                .thenReturn(Optional.of(employee(employeeId, UUID.randomUUID())));
        when(timesheetRepository.findAllByEmployeeIdAndWorkDateBetweenAndIsDeletedFalseOrderByWorkDateAsc(employeeId, date(1), date(31)))
                .thenReturn(List.of(entry(UUID.randomUUID(), employeeId)));

        var result = service.timesheetFor(employeeId, date(1), date(31));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().employeeId()).isEqualTo(employeeId);
    }

    @Test
    void nonOwnerNonDepartmentUserCannotListSpecificEmployeeTimesheet() {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId))
                .thenReturn(Optional.of(employee(employeeId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);
        when(scopeAccessService.canAccessEmployee(employeeId)).thenReturn(false);

        assertThatThrownBy(() -> service.timesheetFor(employeeId, date(1), date(31)))
                .isInstanceOf(AccessDeniedException.class);

        verify(timesheetRepository, never())
                .findAllByEmployeeIdAndWorkDateBetweenAndIsDeletedFalseOrderByWorkDateAsc(any(), any(), any());
    }

    @Test
    void missingEmployeeForTimesheetListRemainsNotFound() {
        UUID employeeId = UUID.randomUUID();
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.timesheetFor(employeeId, date(1), date(31)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Employee not found");
    }

    @Test
    void createAllowsAllowedEmployeeAndDeniesForbiddenEmployee() {
        UUID allowedDepartmentId = UUID.randomUUID();
        UUID allowedEmployeeId = UUID.randomUUID();
        UUID deniedEmployeeId = UUID.randomUUID();
        when(employeeRepository.findByIdAndIsDeletedFalse(allowedEmployeeId))
                .thenReturn(Optional.of(employee(allowedEmployeeId, allowedDepartmentId)));
        when(employeeRepository.findByIdAndIsDeletedFalse(deniedEmployeeId))
                .thenReturn(Optional.of(employee(deniedEmployeeId, UUID.randomUUID())));
        when(scopeAccessService.canAccessDepartment(allowedDepartmentId)).thenReturn(true);
        when(timesheetRepository.save(any(TimesheetEntry.class))).thenAnswer(invocation -> {
            TimesheetEntry saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        assertThat(service.createTimesheet(request(allowedEmployeeId)).employeeId()).isEqualTo(allowedEmployeeId);
        assertThatThrownBy(() -> service.createTimesheet(request(deniedEmployeeId)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ownerCanCreateOwnTimesheet() {
        UUID employeeId = UUID.randomUUID();
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId))
                .thenReturn(Optional.of(employee(employeeId, UUID.randomUUID())));
        when(scopeAccessService.canAccessEmployee(employeeId)).thenReturn(true);
        when(timesheetRepository.save(any(TimesheetEntry.class))).thenAnswer(invocation -> {
            TimesheetEntry saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        assertThat(service.createTimesheet(request(employeeId)).employeeId()).isEqualTo(employeeId);
    }

    @Test
    void updateRequiresCurrentAndTargetTimesheetScope() {
        UUID entryId = UUID.randomUUID();
        UUID currentDepartmentId = UUID.randomUUID();
        UUID currentEmployeeId = UUID.randomUUID();
        UUID allowedEmployeeId = UUID.randomUUID();
        UUID deniedEmployeeId = UUID.randomUUID();
        TimesheetEntry entry = entry(entryId, currentEmployeeId);
        when(timesheetRepository.findByIdAndIsDeletedFalse(entryId)).thenReturn(Optional.of(entry));
        when(employeeRepository.findByIdAndIsDeletedFalse(currentEmployeeId))
                .thenReturn(Optional.of(employee(currentEmployeeId, currentDepartmentId)));
        when(employeeRepository.findByIdAndIsDeletedFalse(allowedEmployeeId))
                .thenReturn(Optional.of(employee(allowedEmployeeId, currentDepartmentId)));
        when(employeeRepository.findByIdAndIsDeletedFalse(deniedEmployeeId))
                .thenReturn(Optional.of(employee(deniedEmployeeId, UUID.randomUUID())));
        when(scopeAccessService.canAccessDepartment(currentDepartmentId)).thenReturn(true);
        when(timesheetRepository.save(any(TimesheetEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.updateTimesheet(entryId, request(allowedEmployeeId)).employeeId()).isEqualTo(allowedEmployeeId);
        assertThatThrownBy(() -> service.updateTimesheet(entryId, request(deniedEmployeeId)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ownerCanUpdateOwnTimesheet() {
        UUID entryId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(timesheetRepository.findByIdAndIsDeletedFalse(entryId)).thenReturn(Optional.of(entry(entryId, employeeId)));
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId))
                .thenReturn(Optional.of(employee(employeeId, UUID.randomUUID())));
        when(scopeAccessService.canAccessEmployee(employeeId)).thenReturn(true);
        when(timesheetRepository.save(any(TimesheetEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.updateTimesheet(entryId, request(employeeId)).employeeId()).isEqualTo(employeeId);
    }

    @Test
    void ownerCanDeleteOwnTimesheet() {
        UUID entryId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        TimesheetEntry entry = entry(entryId, employeeId);
        when(timesheetRepository.findByIdAndIsDeletedFalse(entryId)).thenReturn(Optional.of(entry));
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId))
                .thenReturn(Optional.of(employee(employeeId, UUID.randomUUID())));
        when(scopeAccessService.canAccessEmployee(employeeId)).thenReturn(true);
        when(timesheetRepository.save(any(TimesheetEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.deleteTimesheet(entryId);

        assertThat(entry.isDeleted()).isTrue();
        verify(timesheetRepository).save(entry);
    }

    @Test
    void updateApproveAndDeleteForbiddenTimesheetAreDenied() {
        UUID entryId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(timesheetRepository.findByIdAndIsDeletedFalse(entryId))
                .thenReturn(Optional.of(entry(entryId, employeeId)));
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId))
                .thenReturn(Optional.of(employee(employeeId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);
        when(scopeAccessService.canAccessEmployee(employeeId)).thenReturn(false);

        assertThatThrownBy(() -> service.updateTimesheet(entryId, request(employeeId)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.approveTimesheet(entryId))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.deleteTimesheet(entryId))
                .isInstanceOf(AccessDeniedException.class);

        verify(timesheetRepository, never()).save(any(TimesheetEntry.class));
    }

    @Test
    void departmentScopedUserCanApproveDepartmentTimesheet() {
        UUID entryId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(timesheetRepository.findByIdAndIsDeletedFalse(entryId))
                .thenReturn(Optional.of(entry(entryId, employeeId)));
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId))
                .thenReturn(Optional.of(employee(employeeId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(scopeAccessService.currentEmployeeId()).thenReturn(Optional.empty());
        when(timesheetRepository.save(any(TimesheetEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.approveTimesheet(entryId).status()).isEqualTo(TimesheetStatus.APPROVED);
    }

    @Test
    void ownerCannotApproveOwnTimesheet() {
        UUID entryId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(timesheetRepository.findByIdAndIsDeletedFalse(entryId))
                .thenReturn(Optional.of(entry(entryId, employeeId)));
        when(scopeAccessService.currentEmployeeId()).thenReturn(Optional.of(employeeId));

        assertThatThrownBy(() -> service.approveTimesheet(entryId))
                .isInstanceOf(AccessDeniedException.class);

        verify(employeeRepository, never()).findByIdAndIsDeletedFalse(employeeId);
        verify(timesheetRepository, never()).save(any(TimesheetEntry.class));
    }

    @Test
    void scopeAdminCanCreateAndApproveTimesheetForDifferentDepartmentEmployee() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID entryId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        TimesheetEntry entry = entry(entryId, employeeId);
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId))
                .thenReturn(Optional.of(employee(employeeId, UUID.randomUUID())));
        when(timesheetRepository.findByIdAndIsDeletedFalse(entryId)).thenReturn(Optional.of(entry));
        when(timesheetRepository.save(any(TimesheetEntry.class))).thenAnswer(invocation -> {
            TimesheetEntry saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        assertThat(service.createTimesheet(request(employeeId)).employeeId()).isEqualTo(employeeId);
        assertThat(service.approveTimesheet(entryId).status()).isEqualTo(TimesheetStatus.APPROVED);
    }

    @Test
    void missingTimesheetRemainsNotFound() {
        UUID entryId = UUID.randomUUID();
        when(timesheetRepository.findByIdAndIsDeletedFalse(entryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approveTimesheet(entryId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Timesheet entry not found");
    }

    private LocalDate date(int day) {
        return LocalDate.of(2026, 5, day);
    }

    private Employee employee(UUID employeeId, UUID departmentId) {
        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setPersonnelNumber("EMP-001");
        employee.setFirstName("Ali");
        employee.setLastName("Valiyev");
        employee.setPosition("Engineer");
        employee.setDepartmentId(departmentId);
        employee.setHireDate(LocalDate.of(2025, 1, 10));
        employee.setActive(true);
        return employee;
    }

    private TimesheetEntry entry(UUID entryId, UUID employeeId) {
        TimesheetEntry entry = new TimesheetEntry();
        entry.setId(entryId);
        entry.setEmployeeId(employeeId);
        entry.setWorkDate(date(1));
        entry.setHoursRegular(8);
        entry.setStatus(TimesheetStatus.DRAFT);
        return entry;
    }

    private TimesheetEntryRequest request(UUID employeeId) {
        return new TimesheetEntryRequest(
                employeeId,
                date(1),
                8,
                0,
                0,
                0,
                null,
                null,
                TimesheetStatus.DRAFT,
                "ok"
        );
    }
}
