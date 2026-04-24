package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.PprTask;
import com.toir.enums.PprTaskStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface PprTaskRepository extends JpaRepository<PprTask, UUID> {
    @Query(value = "SELECT * FROM ppr_tasks WHERE plan_id = :planId AND is_deleted = false", nativeQuery = true)
    List<PprTask> findAllByPlanId(@Param("planId") UUID planId);

    @Query(value = "SELECT COUNT(*) FROM ppr_tasks WHERE status = :status AND is_deleted = false", nativeQuery = true)
    long countByStatus(@Param("status") PprTaskStatus status);
}
