package com.toir.repository.equipmentlifecycleexport;

import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportJob;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EquipmentLifecycleExportJobRepository extends
        JpaRepository<EquipmentLifecycleExportJob, UUID>,
        JpaSpecificationExecutor<EquipmentLifecycleExportJob> {

    Optional<EquipmentLifecycleExportJob> findByCreatorIdAndIdempotencyKey(UUID creatorId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from EquipmentLifecycleExportJob job where job.id = :id")
    Optional<EquipmentLifecycleExportJob> findForUpdate(@Param("id") UUID id);

    Page<EquipmentLifecycleExportJob> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<EquipmentLifecycleExportJob> findAllByStatusOrderByCreatedAtDescIdDesc(
            EquipmentLifecycleExportStatus status,
            Pageable pageable
    );

    Page<EquipmentLifecycleExportJob> findAllByCreatorIdOrderByCreatedAtDescIdDesc(
            UUID creatorId,
            Pageable pageable
    );

    Page<EquipmentLifecycleExportJob> findAllByCreatorIdAndStatusOrderByCreatedAtDescIdDesc(
            UUID creatorId,
            EquipmentLifecycleExportStatus status,
            Pageable pageable
    );

    @Query(value = """
            SELECT *
            FROM equipment_lifecycle_export_jobs
            WHERE (
                (status = 'COMPLETED' AND (expires_at <= :now OR staging_cleanup_complete = false))
                OR (status = 'EXPIRED' AND cleanup_complete = false)
                OR (status IN ('FAILED', 'CANCELLED') AND updated_at <= :stagingBefore AND cleanup_complete = false)
            )
              AND (cleanup_claim_until IS NULL OR cleanup_claim_until < :now)
            ORDER BY updated_at ASC, id ASC
            FOR UPDATE SKIP LOCKED
            LIMIT :batchSize
            """, nativeQuery = true)
    List<EquipmentLifecycleExportJob> findCleanupCandidatesForUpdate(
            @Param("now") Instant now,
            @Param("stagingBefore") Instant stagingBefore,
            @Param("batchSize") int batchSize
    );
}
