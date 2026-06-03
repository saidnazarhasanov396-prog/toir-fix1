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

    Optional<MaintenanceDueEvent> findByEquipmentIdAndRegulationIdAndCycleKeyAndIsDeletedFalse(
            UUID equipmentId,
            UUID regulationId,
            String cycleKey);

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
}
