package com.toir.repository;

import com.toir.entity.RepairCampaignStage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface RepairCampaignStageRepository extends JpaRepository<RepairCampaignStage, UUID> {
    @Query(value = "SELECT * FROM repair_campaign_stages WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<RepairCampaignStage> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM repair_campaign_stages WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<RepairCampaignStage> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM repair_campaign_stages WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<RepairCampaignStage> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM repair_campaign_stages WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM repair_campaign_stages WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

}
