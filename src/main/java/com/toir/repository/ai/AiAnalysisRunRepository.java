package com.toir.repository.ai;

import com.toir.ai.repair.AiRepairKind;
import com.toir.entity.ai.AiAnalysisRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiAnalysisRunRepository extends JpaRepository<AiAnalysisRun, UUID> {

    @Query("""
            select r from AiAnalysisRun r
            where r.isDeleted = false
              and r.jobId = :jobId
            """)
    Optional<AiAnalysisRun> findByJobIdAndIsDeletedFalse(@Param("jobId") UUID jobId);

    @Query("""
            select r from AiAnalysisRun r
            where r.isDeleted = false
              and r.equipmentId = :equipmentId
              and r.mediaSha256 = :mediaSha256
              and r.kind = :kind
            order by r.createdAt desc
            """)
    List<AiAnalysisRun> findByEquipmentMedia(
            @Param("equipmentId") UUID equipmentId,
            @Param("mediaSha256") String mediaSha256,
            @Param("kind") AiRepairKind kind
    );
}
