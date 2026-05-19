package com.toir.service.users;

import com.toir.dto.hr.EmployeeDto;
import com.toir.dto.hr.EmployeeRequest;
import com.toir.dto.hr.TimesheetEntryDto;
import com.toir.dto.hr.TimesheetEntryRequest;
import com.toir.entity.Department;
import com.toir.entity.TimesheetEntry;
import com.toir.entity.users.Brigade;
import com.toir.entity.users.Employee;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.TimesheetStatus;
import com.toir.exception.RestException;
import com.toir.repository.TimesheetEntryRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.projects.BrigadeRepository;
import com.toir.repository.projects.EmployeeStatsProjection;
import com.toir.repository.users.EmployeeRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.toir.dto.hr.EmployeeStatsResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HrService {

    private final EmployeeRepository employeeRepository;
    private final TimesheetEntryRepository timesheetRepository;
    private final AuditBuilderService auditBuilderService;
    private final DepartmentRepository departmentRepository;
    private final BrigadeRepository brigadeRepository;
    private final ScopeAccessService scopeAccessService;

    @Transactional(readOnly = true)
    public Page<EmployeeDto> listEmployees(
            int page,
            int pageSize,
            String search,
            Boolean activeOnly,
            UUID departmentId,
            UUID brigadeId
    ) {
        String part1 = null;
        String part2 = null;
        UUID scopedDepartmentId = enforceEmployeeListDepartmentScope(departmentId);

        if (search != null && !search.isBlank()) {
            String[] parts = search.trim().split("\\s+");
            if (parts.length >= 2) {
                part1 = parts[0];
                part2 = parts[1];
            }
        }

        Page<Employee> employeePage = employeeRepository.searchEmployees(
                search,
                part1,
                part2,
                activeOnly,
                scopedDepartmentId,
                brigadeId,
                PaginationUtils.pageRequest(page, pageSize)
        );
        return toDtoPage(employeePage);
    }

    @Transactional(readOnly = true)
    public EmployeeStatsResponse getEmployeeStats(
            UUID departmentId,
            UUID brigadeId,
            String search
    ) {
        UUID scopedDepartmentId = enforceEmployeeListDepartmentScope(departmentId);
        String searchPattern = toSearchPattern(search);

        EmployeeStatsProjection stats = employeeRepository.getEmployeeStats(
                scopedDepartmentId,
                brigadeId,
                searchPattern
        );

        return new EmployeeStatsResponse(
                safe(stats.getTotal()),
                safe(stats.getActive()),
                safe(stats.getTerminated()),
                safe(stats.getWithoutEmail())
        );
    }


    @Transactional(readOnly = true)
    public EmployeeDto getEmployee(UUID id) {
        Employee employee = getEmployeeOrThrow(id);
        assertCanReadEmployee(employee);
        return toDto(employee);
    }
    @Transactional
    public EmployeeDto createEmployee(EmployeeRequest r) {
        assertCanAccessEmployeeRequestDepartment(r);
        if (employeeRepository.existsByPersonnelNumberAndIsDeletedFalse(r.personnelNumber())) {
            throw RestException.conflict("Personnel number already exists: " + r.personnelNumber());
        }
        Employee e = new Employee();
        applyEmployee(e, r);
        Employee saved = employeeRepository.save(e);

        auditBuilderService.log(
                "employee",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.EMPLOYEE,
                "Сотрудник создан",
                null,
                saved
        );

        return toDto(saved);
    }

    @Transactional
    public EmployeeDto updateEmployee(UUID id, EmployeeRequest r) {
        Employee e = getEmployeeOrThrow(id);
        assertCanMutateEmployee(e);
        assertCanAccessEmployeeRequestDepartment(r);
        applyEmployee(e, r);

        Employee save = employeeRepository.save(e);

        auditBuilderService.log(
                "employee",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.EMPLOYEE,
                "Сотрудник обновлен",
                e,
                save
        );
        return toDto(save);
    }

    @Transactional
    public void deleteEmployee(UUID id) {
        var entity = getEmployeeOrThrow(id);
        assertCanMutateEmployee(entity);
        entity.setDeleted(true);
        Employee saved = employeeRepository.save(entity);

        auditBuilderService.log(
                "employee",
                id != null ? id.toString() : null,
                AuditAction.DELETE,
                AuditModule.EMPLOYEE,
                "Сотрудник удален",
                saved,
                null
        );
    }

    @Transactional(readOnly = true)
    public List<TimesheetEntryDto> timesheetFor(UUID employeeId, LocalDate from, LocalDate to) {
        Employee employee = getEmployeeOrThrow(employeeId);
        assertCanReadEmployee(employee);
        return timesheetRepository
                        .findAllByEmployeeIdAndWorkDateBetweenAndIsDeletedFalseOrderByWorkDateAsc(employeeId, from, to)
                .stream().map(TimesheetEntryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TimesheetEntryDto> timesheetRange(LocalDate from, LocalDate to) {
        return timesheetRepository.findAllByWorkDateBetweenAndIsDeletedFalse(from, to)
                .stream()
                .filter(this::canAccessTimesheet)
                .map(TimesheetEntryDto::from).toList();
    }

    @Transactional
    public TimesheetEntryDto createTimesheet(TimesheetEntryRequest r) {
        Employee employee = getEmployeeOrThrow(r.employeeId());
        assertCanReadEmployee(employee);
        TimesheetEntry e = new TimesheetEntry();
        applyTimesheet(e, r);
        TimesheetEntry saved = timesheetRepository.save(e);

        auditBuilderService.log(
                "timesheet_entry",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.TIMESHEET_ENTRY,
                "Табельная запись создана",
                null,
                saved
        );
        return TimesheetEntryDto.from(saved);
    }

    @Transactional
    public TimesheetEntryDto updateTimesheet(UUID id, TimesheetEntryRequest r) {
        TimesheetEntry e = timesheetRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Timesheet entry not found: " + id));
        assertCanAccessTimesheet(e);
        Employee targetEmployee = getEmployeeOrThrow(r.employeeId());
        assertCanReadEmployee(targetEmployee);
        applyTimesheet(e, r);

        TimesheetEntry save = timesheetRepository.save(e);

        auditBuilderService.log(
                "timesheet_entry",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.TIMESHEET_ENTRY,
                "Табельная запись обновлена",
                e,
                save
        );
        return TimesheetEntryDto.from(e);
    }

    @Transactional
    public void deleteTimesheet(UUID id) {
        TimesheetEntry e = timesheetRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Timesheet entry not found: " + id));
        assertCanAccessTimesheet(e);
        e.setDeleted(true);
        TimesheetEntry saved = timesheetRepository.save(e);

        auditBuilderService.log(
                "timesheet_entry",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.TIMESHEET_ENTRY,
                "Табельная запись удалена",
                saved,
                null
        );
    }

    @Transactional
    public TimesheetEntryDto approveTimesheet(UUID id) {
        TimesheetEntry e = timesheetRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Timesheet entry not found: " + id));
        assertCanApproveTimesheet(e);

        e.setStatus(TimesheetStatus.APPROVED);

        TimesheetEntry save = timesheetRepository.save(e);

        auditBuilderService.log(
                "timesheet_entry",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.TIMESHEET_ENTRY,
                "Табельная запись обновлена",
                e,
                save
        );
        return TimesheetEntryDto.from(e);
    }

    private Employee getEmployeeOrThrow(UUID id) {
        return employeeRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Employee not found: " + id));
    }

    private UUID enforceEmployeeListDepartmentScope(UUID requestedDepartmentId) {
        if (scopeAccessService.isScopeAdmin()) {
            return requestedDepartmentId;
        }
        if (scopeAccessService.currentDepartmentIdOrNull() == null) {
            throwAccessDenied();
        }
        return scopeAccessService.enforceDepartmentScope(requestedDepartmentId);
    }

    private void assertCanReadEmployee(Employee employee) {
        if (!canReadEmployee(employee)) {
            throwAccessDenied();
        }
    }

    private boolean canReadEmployee(Employee employee) {
        if (employee == null) {
            return false;
        }
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        return scopeAccessService.canAccessDepartment(employee.getDepartmentId())
                || scopeAccessService.canAccessEmployee(employee.getId())
                || scopeAccessService.canAccessAssignedUser(employee.getUserId());
    }

    private void assertCanMutateEmployee(Employee employee) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (employee == null || !scopeAccessService.canAccessDepartment(employee.getDepartmentId())) {
            throwAccessDenied();
        }
    }

    private void assertCanAccessEmployeeRequestDepartment(EmployeeRequest request) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        UUID departmentId = resolveEmployeeRequestDepartmentId(request);
        if (departmentId == null || !scopeAccessService.canAccessDepartment(departmentId)) {
            throwAccessDenied();
        }
    }

    private UUID resolveEmployeeRequestDepartmentId(EmployeeRequest request) {
        if (request.departmentId() != null) {
            return request.departmentId();
        }
        if (request.brigadeId() == null) {
            return null;
        }
        return brigadeRepository.findByIdAndIsDeletedFalse(request.brigadeId())
                .map(Brigade::getDepartmentId)
                .orElse(null);
    }

    private void assertCanAccessTimesheet(TimesheetEntry entry) {
        if (!canAccessTimesheet(entry)) {
            throwAccessDenied();
        }
    }

    private void assertCanApproveTimesheet(TimesheetEntry entry) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (scopeAccessService.currentEmployeeId()
                .map(entry.getEmployeeId()::equals)
                .orElse(false)) {
            throwAccessDenied();
        }
        assertCanAccessTimesheet(entry);
    }

    private boolean canAccessTimesheet(TimesheetEntry entry) {
        if (entry == null) {
            return false;
        }
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        return employeeRepository.findByIdAndIsDeletedFalse(entry.getEmployeeId())
                .map(this::canReadEmployee)
                .orElse(false);
    }

    private void throwAccessDenied() {
        throw new AccessDeniedException("Access denied by data scope");
    }

    private void applyEmployee(Employee e, EmployeeRequest r) {
        e.setPersonnelNumber(r.personnelNumber());
        e.setFirstName(r.firstName());
        e.setLastName(r.lastName());
        e.setMiddleName(r.middleName());
        e.setPosition(r.position());
        e.setDepartmentId(r.departmentId());
        e.setBrigadeId(r.brigadeId());
        e.setUserId(r.userId());
        e.setHireDate(r.hireDate());
        e.setTerminatedDate(r.terminatedDate());
        e.setGrade(r.grade());
        e.setPhone(r.phone());
        e.setEmail(r.email());
        if (r.active() != null) e.setActive(r.active());
    }

    private void applyTimesheet(TimesheetEntry e, TimesheetEntryRequest r) {
        e.setEmployeeId(r.employeeId());
        e.setWorkDate(r.workDate());
        e.setHoursRegular(r.hoursRegular());
        e.setHoursOvertime(r.hoursOvertime());
        e.setHoursNight(r.hoursNight());
        e.setHoursHoliday(r.hoursHoliday());
        e.setWorkOrderId(r.workOrderId());
        e.setCostCategoryId(r.costCategoryId());
        if (r.status() != null) e.setStatus(r.status());
        e.setNote(r.note());
    }

    private Page<EmployeeDto> toDtoPage(Page<Employee> employeePage) {
        List<EmployeeDto> content = toDtos(employeePage.getContent());

        return new PageImpl<>(
                content,
                employeePage.getPageable(),
                employeePage.getTotalElements()
        );
    }

    private EmployeeDto toDto(Employee employee) {
        return toDtos(List.of(employee)).getFirst();
    }

    private List<EmployeeDto> toDtos(List<Employee> employees) {
        if (employees == null || employees.isEmpty()) {
            return List.of();
        }

        List<UUID> departmentIds = employees.stream()
                .map(Employee::getDepartmentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<UUID> brigadeIds = getBrigadeIds(employees);

        Map<UUID, String> departmentNameById = departmentIds.isEmpty()
                ? Map.of()
                : departmentRepository.findAllByIdInAndIsDeletedFalse(departmentIds)
                .stream()
                .collect(Collectors.toMap(
                        Department::getId,
                        Department::getName,
                        (a, b) -> a
                ));

        Map<UUID, String> brigadeNameById = brigadeIds.isEmpty()
                ? Map.of()
                : brigadeRepository.findAllByIdInAndIsDeletedFalse(brigadeIds)
                .stream()
                .collect(Collectors.toMap(
                        Brigade::getId,
                        Brigade::getName,
                        (a, b) -> a
                ));

        return employees.stream()
                .map(employee -> EmployeeDto.from(
                        employee,
                        resolveName(departmentNameById, employee.getDepartmentId()),
                        resolveName(brigadeNameById, employee.getBrigadeId())
                ))
                .toList();
    }

    public static  List<UUID> getBrigadeIds(List<Employee> employees){
        return employees.stream()
                .map(Employee::getBrigadeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }


    private String resolveName(Map<UUID, String> namesById, UUID id) {
        if (id == null) {
            return null;
        }
        return namesById.get(id);
    }

    private String toSearchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        return "%" + search.trim().toLowerCase() + "%";
    }

    private long safe(Long value) {
        return value == null ? 0L : value;
    }

}
