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
    java.util.Optional<TimesheetEntry> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<TimesheetEntry> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<TimesheetEntry> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM hr_timesheet_entries WHERE employee_id = :employeeId AND work_date BETWEEN :from AND :to AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<TimesheetEntry> findAllByEmployeeIdAndWorkDateBetweenAndIsDeletedFalseOrderByWorkDateAsc(
            @Param("employeeId") UUID employeeId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = "SELECT * FROM hr_timesheet_entries WHERE work_date BETWEEN :from AND :to AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<TimesheetEntry> findAllByWorkDateBetweenAndIsDeletedFalse(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = "SELECT * FROM hr_timesheet_entries WHERE work_order_id = :workOrderId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<TimesheetEntry> findAllByWorkOrderIdAndIsDeletedFalse(@Param("workOrderId") UUID workOrderId);
}
