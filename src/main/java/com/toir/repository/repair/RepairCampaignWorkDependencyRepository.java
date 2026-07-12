package com.toir.repository.repair;
import com.toir.entity.repair.RepairCampaignWorkDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RepairCampaignWorkDependencyRepository extends JpaRepository<RepairCampaignWorkDependency,UUID> {
 List<RepairCampaignWorkDependency> findAllByRepairCampaignIdAndIsDeletedFalseOrderByPredecessorWorkItemIdAscSuccessorWorkItemIdAsc(UUID id);
 Optional<RepairCampaignWorkDependency> findByIdAndRepairCampaignIdAndIsDeletedFalse(UUID id,UUID campaignId);
 Optional<RepairCampaignWorkDependency> findByRepairCampaignIdAndPredecessorWorkItemIdAndSuccessorWorkItemId(UUID c,UUID p,UUID s);
 boolean existsByRepairCampaignIdAndPredecessorWorkItemIdAndIsDeletedFalse(UUID c,UUID item);
 boolean existsByRepairCampaignIdAndSuccessorWorkItemIdAndIsDeletedFalse(UUID c,UUID item);
}
