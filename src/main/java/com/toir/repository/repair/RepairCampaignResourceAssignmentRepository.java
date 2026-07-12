package com.toir.repository.repair;
import com.toir.entity.repair.RepairCampaignResourceAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RepairCampaignResourceAssignmentRepository extends JpaRepository<RepairCampaignResourceAssignment,UUID> {
 List<RepairCampaignResourceAssignment> findAllByRepairCampaignIdAndIsDeletedFalseOrderByPlannedStartAtAscIdAsc(UUID id);
 Optional<RepairCampaignResourceAssignment> findByIdAndRepairCampaignIdAndIsDeletedFalse(UUID id,UUID campaignId);
 boolean existsByRepairCampaignIdAndWorkItemIdAndIsDeletedFalse(UUID c,UUID item);
}
