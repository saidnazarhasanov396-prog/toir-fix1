package com.toir.repository.repair;

import com.toir.entity.repair.RepairCampaignWorkItem;
import com.toir.enums.RepairCampaignWorkItemSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepairCampaignWorkItemRepository extends JpaRepository<RepairCampaignWorkItem, UUID> {
    List<RepairCampaignWorkItem> findAllByCampaignIdAndIsDeletedFalseOrderByOrderNumberAsc(UUID campaignId);
    Optional<RepairCampaignWorkItem> findByIdAndCampaignIdAndIsDeletedFalse(UUID id, UUID campaignId);
    boolean existsByCampaignIdAndSourceTypeAndSourceIdAndIsDeletedFalse(
            UUID campaignId, RepairCampaignWorkItemSourceType sourceType, UUID sourceId);
    boolean existsByCampaignIdAndSourceTypeAndSourceIdAndIdNotAndIsDeletedFalse(
            UUID campaignId, RepairCampaignWorkItemSourceType sourceType, UUID sourceId, UUID id);
    boolean existsByCampaignIdAndOrderNumberAndIsDeletedFalse(UUID campaignId, Integer orderNumber);
    boolean existsByCampaignIdAndOrderNumberAndIdNotAndIsDeletedFalse(
            UUID campaignId, Integer orderNumber, UUID id);
}
