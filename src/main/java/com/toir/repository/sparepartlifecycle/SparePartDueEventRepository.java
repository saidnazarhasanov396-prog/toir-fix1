package com.toir.repository.sparepartlifecycle;

import com.toir.entity.sparepartlifecycle.SparePartDueEvent;
import com.toir.enums.sparepartlifecycle.SparePartDueEventState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

@Repository
public interface SparePartDueEventRepository extends JpaRepository<SparePartDueEvent, UUID>,
        JpaSpecificationExecutor<SparePartDueEvent> {

    Optional<SparePartDueEvent> findByIdAndIsDeletedFalse(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from SparePartDueEvent event where event.id = :id and event.isDeleted = false")
    Optional<SparePartDueEvent> findByIdAndIsDeletedFalseForUpdate(@Param("id") UUID id);

    Optional<SparePartDueEvent> findByInstallationIdAndCycleKeyAndIsDeletedFalse(
            UUID installationId,
            String cycleKey
    );

    List<SparePartDueEvent> findAllByInstallationIdAndStateInAndIsDeletedFalse(
            UUID installationId,
            Collection<SparePartDueEventState> states
    );

    List<SparePartDueEvent> findAllByInstallationIdInAndIsDeletedFalse(Collection<UUID> installationIds);

    List<SparePartDueEvent> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select event
              from SparePartDueEvent event, SparePartInstallation installation
             where event.installationId = installation.id
               and installation.equipmentId = :equipmentId
               and installation.status = com.toir.enums.sparepartlifecycle.SparePartInstallationStatus.ACTIVE
               and installation.isDeleted = false
               and event.isDeleted = false
               and event.dueAction = com.toir.enums.sparepartlifecycle.SparePartDueAction.BLOCK_OPERATION
               and event.state in (
                   com.toir.enums.sparepartlifecycle.SparePartDueEventState.DUE,
                   com.toir.enums.sparepartlifecycle.SparePartDueEventState.OVERDUE
               )
            """)
    List<SparePartDueEvent> findBlockingForEquipmentForUpdate(@Param("equipmentId") UUID equipmentId);
}
