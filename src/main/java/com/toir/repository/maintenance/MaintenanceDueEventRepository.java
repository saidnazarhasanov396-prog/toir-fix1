package com.toir.repository.maintenance;

import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.enums.MaintenanceDueEventStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MaintenanceDueEventRepository extends JpaRepository<MaintenanceDueEvent, UUID>,
        JpaSpecificationExecutor<MaintenanceDueEvent> {

    Optional<MaintenanceDueEvent> findByIdAndIsDeletedFalse(UUID id);

    @Query("""
            select e
            from MaintenanceDueEvent e
            where e.isDeleted = false
              and e.equipmentId = :equipmentId
              and e.cycleKey = :cycleKey
              and (
                    (:regulationId is null and e.regulationId is null)
                 or (:regulationId is not null and e.regulationId = :regulationId)
              )
              and (
                    (:ruleId is null and e.equipmentMaintenanceRuleId is null)
                 or (:ruleId is not null and e.equipmentMaintenanceRuleId = :ruleId)
              )
            """)
    Optional<MaintenanceDueEvent> findByScopeAndCycleKey(
            @Param("equipmentId") UUID equipmentId,
            @Param("regulationId") UUID regulationId,
            @Param("ruleId") UUID ruleId,
            @Param("cycleKey") String cycleKey);

    List<MaintenanceDueEvent> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query("""
            select e
            from MaintenanceDueEvent e
            where e.isDeleted = false
              and e.cycleKey = :cycleKey
              and e.status in :statuses
            order by e.updatedAt desc
            """)
    List<MaintenanceDueEvent> findOpenByCycleKey(@Param("cycleKey") String cycleKey,
                                                 @Param("statuses") Collection<MaintenanceDueEventStatus> statuses);

    @Query("""
            select e
            from MaintenanceDueEvent e
            where e.isDeleted = false
              and e.equipmentId = :equipmentId
              and e.status in :statuses
              and (
                    (:regulationId is null and e.regulationId is null)
                 or (:regulationId is not null and e.regulationId = :regulationId)
              )
              and (
                    (:ruleId is null and e.equipmentMaintenanceRuleId is null)
                 or (:ruleId is not null and e.equipmentMaintenanceRuleId = :ruleId)
              )
            order by e.updatedAt desc
            """)
    List<MaintenanceDueEvent> findOpenByScope(@Param("equipmentId") UUID equipmentId,
                                              @Param("regulationId") UUID regulationId,
                                              @Param("ruleId") UUID ruleId,
                                              @Param("statuses") Collection<MaintenanceDueEventStatus> statuses);
}
