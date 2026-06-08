package com.toir.repository.maintenance;

import com.toir.entity.maintenance.WorkOrderSafetyChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkOrderSafetyChecklistItemRepository extends JpaRepository<WorkOrderSafetyChecklistItem, UUID> {

    Optional<WorkOrderSafetyChecklistItem> findByIdAndIsDeletedFalse(UUID id);

    List<WorkOrderSafetyChecklistItem> findAllByChecklistIdAndIsDeletedFalseOrderBySequenceAsc(UUID checklistId);
}
