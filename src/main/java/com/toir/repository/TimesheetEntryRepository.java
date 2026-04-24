package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.TimesheetEntry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


@Repository
public interface TimesheetEntryRepository extends JpaRepository<TimesheetEntry, UUID> {
    @Query(value = "SELECT * FROM hr_timesheet_entries WHERE employee_id = :employeeId AND work_date BETWEEN :from AND :to AND is_deleted = false ORDER BY work_date ASC", nativeQuery = true)
    List<TimesheetEntry> findAllByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
            @Param("employeeId") UUID employeeId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = "SELECT * FROM hr_timesheet_entries WHERE work_date BETWEEN :from AND :to AND is_deleted = false", nativeQuery = true)
    List<TimesheetEntry> findAllByWorkDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = "SELECT * FROM hr_timesheet_entries WHERE work_order_id = :workOrderId AND is_deleted = false", nativeQuery = true)
    List<TimesheetEntry> findAllByWorkOrderId(@Param("workOrderId") UUID workOrderId);
}
