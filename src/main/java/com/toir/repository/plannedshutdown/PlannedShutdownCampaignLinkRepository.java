package com.toir.repository.plannedshutdown;

import com.toir.entity.plannedshutdown.PlannedShutdownCampaignLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlannedShutdownCampaignLinkRepository extends JpaRepository<PlannedShutdownCampaignLink, UUID> {
    Optional<PlannedShutdownCampaignLink> findByPlannedShutdownIdAndRepairCampaignId(
            UUID plannedShutdownId, UUID repairCampaignId);
    Optional<PlannedShutdownCampaignLink> findByPlannedShutdownIdAndRepairCampaignIdAndIsDeletedFalse(
            UUID plannedShutdownId, UUID repairCampaignId);
    List<PlannedShutdownCampaignLink> findAllByRepairCampaignIdAndIsDeletedFalseOrderByPlannedShutdownId(UUID campaignId);
    List<PlannedShutdownCampaignLink> findAllByPlannedShutdownIdAndIsDeletedFalseOrderByRepairCampaignId(UUID shutdownId);
}
