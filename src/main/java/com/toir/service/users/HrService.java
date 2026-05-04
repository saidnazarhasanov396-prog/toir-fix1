package com.toir.service.users;
import com.toir.entity.users.Employee;
import com.toir.entity.TimesheetEntry;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.TimesheetStatus;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.TimesheetEntryRepository;

import com.toir.exception.RestException;
import com.toir.dto.hr.EmployeeDto;
import com.toir.dto.hr.EmployeeRequest;
import com.toir.dto.hr.TimesheetEntryDto;
import com.toir.dto.hr.TimesheetEntryRequest;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class HrService {

    private final EmployeeRepository employeeRepository;
    private final TimesheetEntryRepository timesheetRepository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;

    @Transactional(readOnly = true)
    public Page<EmployeeDto> listEmployees(int page, int pageSize, String search, Boolean activeOnly) {
        String part1 = null;
        String part2 = null;

        if (search != null && !search.isBlank()) {
            String[] parts = search.trim().split("\\s+");
            if (parts.length >= 2) {
                part1 = parts[0];
                part2 = parts[1];
            }
        }

        return employeeRepository.searchEmployees(search, part1, part2, activeOnly, PaginationUtils.pageRequest(page, pageSize))
                .map(EmployeeDto::from);
    }

    @Transactional(readOnly = true)
    public EmployeeDto getEmployee(UUID id) {
        return EmployeeDto.from(getEmployeeOrThrow(id));
    }

    public EmployeeDto createEmployee(EmployeeRequest r) {
        if (employeeRepository.existsByPersonnelNumberAndIsDeletedFalse(r.personnelNumber())) {
            throw RestException.conflict("Personnel number already exists: " + r.personnelNumber());
        }
        Employee e = new Employee();
        applyEmployee(e, r);
        Employee saved = employeeRepository.save(e);
        auditEmployee(AuditAction.CREATE, saved.getId(), null, saved);
        return EmployeeDto.from(saved);
    }

    public EmployeeDto updateEmployee(UUID id, EmployeeRequest r) {
        Employee e = getEmployeeOrThrow(id);
        String oldJson = auditSerializationService.toJson(e);
        applyEmployee(e, r);
        auditEmployee(AuditAction.UPDATE, e.getId(), oldJson, e);
        return EmployeeDto.from(e);
    }

    public void deleteEmployee(UUID id) {
        var entity = getEmployeeOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        Employee saved = employeeRepository.save(entity);
        auditEmployee(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    @Transactional(readOnly = true)
    public List<TimesheetEntryDto> timesheetFor(UUID employeeId, LocalDate from, LocalDate to) {
        return timesheetRepository
                        .findAllByEmployeeIdAndWorkDateBetweenAndIsDeletedFalseOrderByWorkDateAsc(employeeId, from, to)
                .stream().map(TimesheetEntryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TimesheetEntryDto> timesheetRange(LocalDate from, LocalDate to) {
        return timesheetRepository.findAllByWorkDateBetweenAndIsDeletedFalse(from, to)
                .stream().map(TimesheetEntryDto::from).toList();
    }

    public TimesheetEntryDto createTimesheet(TimesheetEntryRequest r) {
        getEmployeeOrThrow(r.employeeId());
        TimesheetEntry e = new TimesheetEntry();
        applyTimesheet(e, r);
        TimesheetEntry saved = timesheetRepository.save(e);
        auditTimesheet(AuditAction.CREATE, saved.getId(), null, saved);
        return TimesheetEntryDto.from(saved);
    }

    public TimesheetEntryDto updateTimesheet(UUID id, TimesheetEntryRequest r) {
        TimesheetEntry e = timesheetRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Timesheet entry not found: " + id));
        String oldJson = auditSerializationService.toJson(e);
        applyTimesheet(e, r);
        auditTimesheet(AuditAction.UPDATE, e.getId(), oldJson, e);
        return TimesheetEntryDto.from(e);
    }

    public void deleteTimesheet(UUID id) {
        TimesheetEntry e = timesheetRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Timesheet entry not found: " + id));
        String oldJson = auditSerializationService.toJson(e);
        e.setDeleted(true);
        TimesheetEntry saved = timesheetRepository.save(e);
        auditTimesheet(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    public TimesheetEntryDto approveTimesheet(UUID id) {
        TimesheetEntry e = timesheetRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Timesheet entry not found: " + id));
        String oldJson = auditSerializationService.toJson(e);
        e.setStatus(TimesheetStatus.APPROVED);
        auditTimesheet(AuditAction.UPDATE, e.getId(), oldJson, e);
        return TimesheetEntryDto.from(e);
    }

    private Employee getEmployeeOrThrow(UUID id) {
        return employeeRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Employee not found: " + id));
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

    private void auditEmployee(AuditAction action, UUID id, String oldJson, Employee current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "employee",
                id != null ? id.toString() : null,
                action,
                AuditModule.EMPLOYEE,
                auditEmployeeMessage(action),
                oldJson,
                newJson
        );
    }

    private void auditTimesheet(AuditAction action, UUID id, String oldJson, TimesheetEntry current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "timesheet_entry",
                id != null ? id.toString() : null,
                action,
                AuditModule.TIMESHEET_ENTRY,
                auditTimesheetMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditEmployeeMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Сотрудник создан";
            case UPDATE -> "Сотрудник обновлен";
            case DELETE -> "Сотрудник удален";
            default -> "Действие выполнено над сотрудником";
        };
    }

    private String auditTimesheetMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Табельная запись создана";
            case UPDATE -> "Табельная запись обновлена";
            case DELETE -> "Табельная запись удалена";
            default -> "Действие выполнено над табельной записью";
        };
    }
}
