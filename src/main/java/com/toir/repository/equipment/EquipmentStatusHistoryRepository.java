package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentStatusHistory;
import com.toir.enums.EquipmentStatusSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EquipmentStatusHistoryRepository extends JpaRepository<EquipmentStatusHistory, UUID> {

    Page<EquipmentStatusHistory> findAllByEquipmentIdAndIsDeletedFalseOrderByChangedAtDesc(UUID equipmentId,
                                                                                           Pageable pageable);

    Optional<EquipmentStatusHistory> findTopByEquipmentIdAndSourceAndRelatedEntityTypeAndRelatedEntityIdAndIsDeletedFalseOrderByChangedAtDesc(
            UUID equipmentId,
            EquipmentStatusSource source,
            String relatedEntityType,
            UUID relatedEntityId
    );
}
