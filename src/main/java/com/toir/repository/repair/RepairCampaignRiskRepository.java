package com.toir.repository.repair;

import com.toir.entity.repair.RepairCampaignRisk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepairCampaignRiskRepository extends JpaRepository<RepairCampaignRisk, UUID> {
    List<RepairCampaignRisk> findAllByCampaignIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID campaignId);

    Optional<RepairCampaignRisk> findByIdAndCampaignIdAndIsDeletedFalse(UUID id, UUID campaignId);
}
