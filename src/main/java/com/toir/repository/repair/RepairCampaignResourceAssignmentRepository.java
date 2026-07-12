package com.toir.repository.repair;
import com.toir.entity.repair.RepairCampaignResourceAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RepairCampaignResourceAssignmentRepository extends JpaRepository<RepairCampaignResourceAssignment,UUID> {
 List<RepairCampaignResourceAssignment> findAllByRepairCampaignIdAndIsDeletedFalseOrderByPlannedStartAtAscIdAsc(UUID id);
 Optional<RepairCampaignResourceAssignment> findByIdAndRepairCampaignIdAndIsDeletedFalse(UUID id,UUID campaignId);
 boolean existsByRepairCampaignIdAndWorkItemIdAndIsDeletedFalse(UUID c,UUID item);
 @org.springframework.data.jpa.repository.Query("""
 select a from RepairCampaignResourceAssignment a where a.isDeleted=false
 and (:excludeId is null or a.id<>:excludeId)
 and ((:employeeId is not null and a.employeeId=:employeeId) or (:brigadeId is not null and a.brigadeId=:brigadeId)
 or (:counteragentId is not null and a.counteragentId=:counteragentId))
 and a.plannedStartAt < :endAt and a.plannedEndAt > :startAt order by a.plannedStartAt,a.id
 """)
 List<RepairCampaignResourceAssignment> findActiveOverlaps(
     @org.springframework.data.repository.query.Param("employeeId") UUID employeeId,
     @org.springframework.data.repository.query.Param("brigadeId") UUID brigadeId,
     @org.springframework.data.repository.query.Param("counteragentId") UUID counteragentId,
     @org.springframework.data.repository.query.Param("startAt") java.time.Instant startAt,
     @org.springframework.data.repository.query.Param("endAt") java.time.Instant endAt,
     @org.springframework.data.repository.query.Param("excludeId") UUID excludeId);
}
