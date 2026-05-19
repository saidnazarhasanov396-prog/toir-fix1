package com.toir.repository;

import com.toir.entity.PprTask;
import com.toir.enums.PprTaskStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface PprTaskRepository extends JpaRepository<PprTask, UUID> {
    @Query(value = "SELECT * FROM ppr_tasks WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<PprTask> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query("""
            select t
            from PprTask t
            join fetch t.plan p
            where t.id = :id
              and t.isDeleted = false
              and p.isDeleted = false
            """)
    Optional<PprTask> findByIdAndIsDeletedFalseWithPlan(@Param("id") UUID id);

    @Query(value = "SELECT * FROM ppr_tasks WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<PprTask> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM ppr_tasks WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<PprTask> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM ppr_tasks WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM ppr_tasks WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM ppr_tasks WHERE plan_id = cast(:planId as uuid) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<PprTask> findAllByPlanIdAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("planId") UUID planId);

    @Query(value = """
            SELECT *
            FROM ppr_tasks
            WHERE plan_id = cast(:planId as uuid)
              AND is_deleted = false
            ORDER BY scheduled_start ASC NULLS LAST, id ASC
            """, nativeQuery = true)
    List<PprTask> findAllByPlanIdAndIsDeletedFalseOrderByScheduledStartAscIdAsc(@Param("planId") UUID planId);

    boolean existsByCode(String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM ppr_tasks
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);

    @Query(value = "SELECT COUNT(*) FROM ppr_tasks WHERE status = :status AND is_deleted = false", nativeQuery = true)
    long countByStatusAndIsDeletedFalse(@Param("status") String status);
}
