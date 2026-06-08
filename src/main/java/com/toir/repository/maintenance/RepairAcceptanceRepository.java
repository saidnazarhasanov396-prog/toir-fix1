package com.toir.repository.maintenance;

import com.toir.entity.maintenance.RepairAcceptance;
import com.toir.enums.RepairAcceptanceStage;
import com.toir.enums.RepairAcceptanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepairAcceptanceRepository extends JpaRepository<RepairAcceptance, UUID> {

    Optional<RepairAcceptance> findByIdAndIsDeletedFalse(UUID id);

    List<RepairAcceptance> findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(UUID workOrderId);

    boolean existsByWorkOrderIdAndStageAndStatusAndIsDeletedFalse(
            UUID workOrderId,
            RepairAcceptanceStage stage,
            RepairAcceptanceStatus status
    );

    default boolean existsAcceptedFinalByWorkOrderId(UUID workOrderId) {
        return existsByWorkOrderIdAndStageAndStatusAndIsDeletedFalse(
                workOrderId,
                RepairAcceptanceStage.FINAL,
                RepairAcceptanceStatus.ACCEPTED
        );
    }
}
