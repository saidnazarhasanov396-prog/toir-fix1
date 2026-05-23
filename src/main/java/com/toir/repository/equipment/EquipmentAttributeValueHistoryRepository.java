package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentAttributeValueHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EquipmentAttributeValueHistoryRepository extends JpaRepository<EquipmentAttributeValueHistory, UUID> {

    Page<EquipmentAttributeValueHistory> findAllByEquipmentIdAndIsDeletedFalseOrderByChangedAtDesc(UUID equipmentId,
                                                                                                    Pageable pageable);

    Page<EquipmentAttributeValueHistory> findAllByEquipmentIdAndAttributeDefinitionIdAndIsDeletedFalseOrderByChangedAtDesc(
            UUID equipmentId,
            UUID attributeDefinitionId,
            Pageable pageable
    );
}
