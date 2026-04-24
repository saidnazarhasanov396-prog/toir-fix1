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
    @Query(value = "SELECT * FROM work_executions WHERE work_order_id = :workOrderId AND is_deleted = false ORDER BY started_at ASC", nativeQuery = true)
    List<WorkExecution> findAllByWorkOrderIdOrderByStartedAtAsc(@Param("workOrderId") UUID workOrderId);

    @Query(value = "SELECT * FROM work_executions WHERE is_deleted = false ORDER BY started_at DESC",
            countQuery = "SELECT COUNT(*) FROM work_executions WHERE is_deleted = false",
            nativeQuery = true)
    Page<WorkExecution> findAllByOrderByStartedAtDesc(Pageable pageable);
}
