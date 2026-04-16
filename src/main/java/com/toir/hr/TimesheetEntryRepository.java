package com.toir.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TimesheetEntryRepository extends JpaRepository<TimesheetEntry, UUID> {
    List<TimesheetEntry> findAllByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
            UUID employeeId, LocalDate from, LocalDate to);
    List<TimesheetEntry> findAllByWorkDateBetween(LocalDate from, LocalDate to);
    List<TimesheetEntry> findAllByWorkOrderId(UUID workOrderId);
}
