package com.toir.repository.repair;

import com.toir.entity.repair.RepairCampaignMaterialRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepairCampaignMaterialRequirementRepository extends JpaRepository<RepairCampaignMaterialRequirement,UUID> {
    Optional<RepairCampaignMaterialRequirement> findByIdAndIsDeletedFalse(UUID id);
    List<RepairCampaignMaterialRequirement> findAllByRepairCampaignIdAndIsDeletedFalseOrderByWorkItemIdAscSparePartIdAsc(UUID campaignId);
    Optional<RepairCampaignMaterialRequirement> findByIdAndRepairCampaignIdAndIsDeletedFalse(UUID id,UUID campaignId);
    boolean existsByRepairCampaignIdAndWorkItemIdAndIsDeletedFalse(UUID campaignId,UUID workItemId);
    List<RepairCampaignMaterialRequirement> findAllByRepairCampaignIdAndWorkItemIdAndIsDeletedFalseOrderBySparePartIdAsc(
            UUID campaignId,UUID workItemId);
}
