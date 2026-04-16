package com.toir.hr;

import com.toir.common.exception.RestException;
import com.toir.hr.dto.EmployeeDto;
import com.toir.hr.dto.EmployeeRequest;
import com.toir.hr.dto.TimesheetEntryDto;
import com.toir.hr.dto.TimesheetEntryRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class HrService {

    private final EmployeeRepository employeeRepository;
    private final TimesheetEntryRepository timesheetRepository;

    public HrService(EmployeeRepository employeeRepository,
                     TimesheetEntryRepository timesheetRepository) {
        this.employeeRepository = employeeRepository;
        this.timesheetRepository = timesheetRepository;
    }

    @Transactional(readOnly = true)
    public List<EmployeeDto> listEmployees(Boolean activeOnly) {
        List<Employee> all = Boolean.TRUE.equals(activeOnly)
                ? employeeRepository.findAllByActiveTrue()
                : employeeRepository.findAll();
        return all.stream().map(EmployeeDto::from).toList();
    }

    @Transactional(readOnly = true)
    public EmployeeDto getEmployee(UUID id) {
        return EmployeeDto.from(getEmployeeOrThrow(id));
    }

    public EmployeeDto createEmployee(EmployeeRequest r) {
        if (employeeRepository.existsByPersonnelNumber(r.personnelNumber())) {
            throw RestException.conflict("Personnel number already exists: " + r.personnelNumber());
        }
        Employee e = new Employee();
        applyEmployee(e, r);
        return EmployeeDto.from(employeeRepository.save(e));
    }

    public EmployeeDto updateEmployee(UUID id, EmployeeRequest r) {
        Employee e = getEmployeeOrThrow(id);
        applyEmployee(e, r);
        return EmployeeDto.from(e);
    }

    public void deleteEmployee(UUID id) {
        employeeRepository.delete(getEmployeeOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<TimesheetEntryDto> timesheetFor(UUID employeeId, LocalDate from, LocalDate to) {
        return timesheetRepository
                .findAllByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, from, to)
                .stream().map(TimesheetEntryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TimesheetEntryDto> timesheetRange(LocalDate from, LocalDate to) {
        return timesheetRepository.findAllByWorkDateBetween(from, to)
                .stream().map(TimesheetEntryDto::from).toList();
    }

    public TimesheetEntryDto createTimesheet(TimesheetEntryRequest r) {
        getEmployeeOrThrow(r.employeeId());
        TimesheetEntry e = new TimesheetEntry();
        applyTimesheet(e, r);
        return TimesheetEntryDto.from(timesheetRepository.save(e));
    }

    public TimesheetEntryDto updateTimesheet(UUID id, TimesheetEntryRequest r) {
        TimesheetEntry e = timesheetRepository.findById(id)
                .orElseThrow(() -> RestException.notFound("Timesheet entry not found: " + id));
        applyTimesheet(e, r);
        return TimesheetEntryDto.from(e);
    }

    public void deleteTimesheet(UUID id) {
        TimesheetEntry e = timesheetRepository.findById(id)
                .orElseThrow(() -> RestException.notFound("Timesheet entry not found: " + id));
        timesheetRepository.delete(e);
    }

    public TimesheetEntryDto approveTimesheet(UUID id) {
        TimesheetEntry e = timesheetRepository.findById(id)
                .orElseThrow(() -> RestException.notFound("Timesheet entry not found: " + id));
        e.setStatus(TimesheetStatus.APPROVED);
        return TimesheetEntryDto.from(e);
    }

    private Employee getEmployeeOrThrow(UUID id) {
        return employeeRepository.findById(id)
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
}
