package com.toir.repository.sparepartlifecycle;

import com.toir.entity.sparepartlifecycle.SparePartDueEvent;
import com.toir.enums.sparepartlifecycle.SparePartDueEventState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SparePartDueEventRepository extends JpaRepository<SparePartDueEvent, UUID>,
        JpaSpecificationExecutor<SparePartDueEvent> {

    Optional<SparePartDueEvent> findByIdAndIsDeletedFalse(UUID id);

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
}
