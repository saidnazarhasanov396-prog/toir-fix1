package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.WorkExecution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface WorkExecutionRepository extends JpaRepository<WorkExecution, UUID> {
    java.util.Optional<WorkExecution> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<WorkExecution> findAllByIsDeletedFalse();

    java.util.List<WorkExecution> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM work_executions WHERE work_order_id = :workOrderId AND is_deleted = false ORDER BY started_at ASC", nativeQuery = true)
    List<WorkExecution> findAllByWorkOrderIdAndIsDeletedFalseOrderByStartedAtAsc(@Param("workOrderId") UUID workOrderId);

    @Query(value = """
            SELECT * FROM work_executions
            WHERE is_deleted = false
            AND (cast(:workOrderId as varchar) IS NULL OR work_order_id = cast(:workOrderId as uuid))
            ORDER BY started_at DESC""",
            countQuery = """
            SELECT COUNT(*) FROM work_executions
            WHERE is_deleted = false
            AND (cast(:workOrderId as varchar) IS NULL OR work_order_id = cast(:workOrderId as uuid))""",
            nativeQuery = true)
    Page<WorkExecution> findExecutionLogs(@Param("workOrderId") UUID workOrderId, Pageable pageable);
}
