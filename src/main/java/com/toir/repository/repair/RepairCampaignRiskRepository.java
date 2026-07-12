package com.toir.repository.repair;

import com.toir.entity.repair.RepairCampaignRisk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepairCampaignRiskRepository extends JpaRepository<RepairCampaignRisk, UUID> {
    @Query(value = "SELECT * FROM repair_campaign_risks WHERE campaign_id = cast(:campaignId as uuid) AND is_deleted = false ORDER BY created_at DESC", nativeQuery = true)
    List<RepairCampaignRisk> findAllByCampaignIdAndIsDeletedFalse(@Param("campaignId") UUID campaignId);

    @Query(value = "SELECT * FROM repair_campaign_risks WHERE id = cast(:id as uuid) AND campaign_id = cast(:campaignId as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<RepairCampaignRisk> findByIdAndCampaignIdAndIsDeletedFalse(@Param("id") UUID id,
                                                                         @Param("campaignId") UUID campaignId);
}
