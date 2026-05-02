package com.toir.repository;

import com.toir.entity.WorkExecution;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface WorkExecutionRepository extends JpaRepository<WorkExecution, UUID> {
    @Query(value = "SELECT * FROM work_executions WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<WorkExecution> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM work_executions WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<WorkExecution> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM work_executions WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<WorkExecution> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM work_executions WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM work_executions WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM work_executions WHERE work_order_id = :workOrderId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<WorkExecution> findAllByWorkOrderIdAndIsDeletedFalseOrderByStartedAtAsc(@Param("workOrderId") UUID workOrderId);

    @Query(value = """
            SELECT * FROM work_executions
            WHERE is_deleted = false
            AND (cast(:workOrderId as varchar) IS NULL OR work_order_id = cast(:workOrderId as uuid))
            ORDER BY updated_at DESC""",
            countQuery = """
            SELECT COUNT(*) FROM work_executions
            WHERE is_deleted = false
            AND (cast(:workOrderId as varchar) IS NULL OR work_order_id = cast(:workOrderId as uuid))""",
            nativeQuery = true)
    Page<WorkExecution> findExecutionLogs(@Param("workOrderId") UUID workOrderId, Pageable pageable);
}
