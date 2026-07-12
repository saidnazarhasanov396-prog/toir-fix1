package com.toir.repository.repair;

import com.toir.entity.repair.RepairCampaignWorkItemWindow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepairCampaignWorkItemWindowRepository extends JpaRepository<RepairCampaignWorkItemWindow, UUID> {
    Optional<RepairCampaignWorkItemWindow> findByRepairCampaignWorkItemIdAndPlannedShutdownIdAndShutdownWorkItemId(
            UUID campaignItemId, UUID shutdownId, UUID shutdownItemId);
    Optional<RepairCampaignWorkItemWindow> findByIdAndRepairCampaignIdAndPlannedShutdownIdAndIsDeletedFalse(
            UUID id, UUID campaignId, UUID shutdownId);
    List<RepairCampaignWorkItemWindow> findAllByRepairCampaignIdAndPlannedShutdownIdAndIsDeletedFalseOrderById(
            UUID campaignId, UUID shutdownId);
    boolean existsByRepairCampaignIdAndPlannedShutdownIdAndIsDeletedFalse(UUID campaignId, UUID shutdownId);
}
