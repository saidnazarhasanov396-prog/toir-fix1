package com.toir.repository.maintenance;

import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import java.util.Collection;
import java.time.Instant;
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

    @Query(value = """
            SELECT *
            FROM maintenance_due_events
            WHERE equipment_id = :equipmentId
              AND detected_at <= :asOf
              AND (due_at IS NULL OR due_at <= :planningEnd)
              AND is_deleted = false
            ORDER BY COALESCE(due_at, detected_at) ASC, id ASC
            LIMIT :limitPlusOne
            """, nativeQuery = true)
    List<MaintenanceDueEvent> findLifecycleDueEvents(
            @Param("equipmentId") UUID equipmentId,
            @Param("asOf") Instant asOf,
            @Param("planningEnd") Instant planningEnd,
            @Param("limitPlusOne") int limitPlusOne);

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

    List<MaintenanceDueEvent> findAllByEquipmentIdAndStatusInAndIsDeletedFalseOrderByUpdatedAtDesc(
            UUID equipmentId,
            Collection<MaintenanceDueEventStatus> statuses
    );

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

    @Query("""
            select e
            from MaintenanceDueEvent e, Equipment equipment
            where e.isDeleted = false
              and equipment.isDeleted = false
              and equipment.id = e.equipmentId
              and e.templateId is not null
              and e.dueAt between :from and :to
              and e.status in :statuses
              and e.dueStatus in :dueStatuses
              and (:departmentId is null or coalesce(equipment.responsibleDepartmentId, equipment.departmentId) = :departmentId)
              and (:equipmentId is null or e.equipmentId = :equipmentId)
              and (:templateId is null or e.templateId = :templateId)
            order by e.dueAt asc, e.updatedAt desc
            """)
    List<MaintenanceDueEvent> findForecastCandidates(@Param("from") Instant from,
                                                     @Param("to") Instant to,
                                                     @Param("departmentId") UUID departmentId,
                                                     @Param("equipmentId") UUID equipmentId,
                                                     @Param("templateId") UUID templateId,
                                                     @Param("statuses") Collection<MaintenanceDueEventStatus> statuses,
                                                     @Param("dueStatuses") Collection<MaintenanceDueStatus> dueStatuses);

    long countByDueStatusAndIsDeletedFalse(MaintenanceDueStatus dueStatus);

    long countByStatusAndIsDeletedFalse(MaintenanceDueEventStatus status);

    @Query("""
            select count(e)
            from MaintenanceDueEvent e, Equipment equipment
            where e.isDeleted = false
              and equipment.isDeleted = false
              and equipment.id = e.equipmentId
              and e.dueStatus = :dueStatus
              and (
                    :departmentId is null
                 or coalesce(equipment.responsibleDepartmentId, equipment.departmentId) = :departmentId
              )
            """)
    long countByDueStatusAndDepartment(@Param("dueStatus") MaintenanceDueStatus dueStatus,
                                       @Param("departmentId") UUID departmentId);

    @Query("""
            select count(e)
            from MaintenanceDueEvent e, Equipment equipment
            where e.isDeleted = false
              and equipment.isDeleted = false
              and equipment.id = e.equipmentId
              and e.status = :status
              and (
                    :departmentId is null
                 or coalesce(equipment.responsibleDepartmentId, equipment.departmentId) = :departmentId
              )
            """)
    long countByStatusAndDepartment(@Param("status") MaintenanceDueEventStatus status,
                                    @Param("departmentId") UUID departmentId);
}
