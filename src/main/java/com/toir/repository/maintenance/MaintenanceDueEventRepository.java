package com.toir.repository.maintenance;

import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MaintenanceDueEventRepository extends JpaRepository<MaintenanceDueEvent, UUID> {

    Optional<MaintenanceDueEvent> findByIdAndIsDeletedFalse(UUID id);

    Optional<MaintenanceDueEvent> findByCycleKeyAndIsDeletedFalse(String cycleKey);

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
            join Equipment eq on eq.id = e.equipmentId
            where e.isDeleted = false
              and eq.isDeleted = false
              and (:equipmentId is null or e.equipmentId = :equipmentId)
              and (:departmentId is null or coalesce(eq.responsibleDepartmentId, eq.departmentId) = :departmentId)
              and (:regulationId is null or e.regulationId = :regulationId)
              and (:status is null or e.status = :status)
              and (:dueStatus is null or e.dueStatus = :dueStatus)
              and (:from is null or e.dueAt >= :from)
              and (:to is null or e.dueAt <= :to)
            order by e.detectedAt desc, e.updatedAt desc
            """)
    Page<MaintenanceDueEvent> search(@Param("equipmentId") UUID equipmentId,
                                     @Param("departmentId") UUID departmentId,
                                     @Param("regulationId") UUID regulationId,
                                     @Param("status") MaintenanceDueEventStatus status,
                                     @Param("dueStatus") MaintenanceDueStatus dueStatus,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to,
                                     Pageable pageable);
}
