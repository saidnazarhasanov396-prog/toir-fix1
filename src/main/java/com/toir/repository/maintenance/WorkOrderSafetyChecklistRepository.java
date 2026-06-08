package com.toir.repository.maintenance;

import com.toir.entity.maintenance.WorkOrderSafetyChecklist;
import com.toir.enums.SafetyChecklistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkOrderSafetyChecklistRepository extends JpaRepository<WorkOrderSafetyChecklist, UUID> {

    Optional<WorkOrderSafetyChecklist> findByIdAndIsDeletedFalse(UUID id);

    List<WorkOrderSafetyChecklist> findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(UUID workOrderId);

    Optional<WorkOrderSafetyChecklist> findFirstByWorkOrderIdAndIsDeletedFalseAndStatusNotInOrderByUpdatedAtDesc(
            UUID workOrderId,
            Collection<SafetyChecklistStatus> statuses);

    boolean existsByWorkOrderIdAndIsDeletedFalseAndStatusNotIn(UUID workOrderId, Collection<SafetyChecklistStatus> statuses);
}
