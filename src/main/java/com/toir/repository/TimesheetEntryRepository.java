package com.toir.repository;

import com.toir.entity.TimesheetEntry;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface TimesheetEntryRepository extends JpaRepository<TimesheetEntry, UUID> {
    @Query(value = "SELECT * FROM hr_timesheet_entries WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<TimesheetEntry> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM hr_timesheet_entries WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<TimesheetEntry> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM hr_timesheet_entries WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<TimesheetEntry> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM hr_timesheet_entries WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM hr_timesheet_entries WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM hr_timesheet_entries WHERE employee_id = :employeeId AND work_date BETWEEN :from AND :to AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<TimesheetEntry> findAllByEmployeeIdAndWorkDateBetweenAndIsDeletedFalseOrderByWorkDateAsc(
            @Param("employeeId") UUID employeeId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = "SELECT * FROM hr_timesheet_entries WHERE work_date BETWEEN :from AND :to AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<TimesheetEntry> findAllByWorkDateBetweenAndIsDeletedFalse(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = "SELECT * FROM hr_timesheet_entries WHERE work_order_id = :workOrderId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<TimesheetEntry> findAllByWorkOrderIdAndIsDeletedFalse(@Param("workOrderId") UUID workOrderId);
}
