package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentLocationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EquipmentLocationHistoryRepository extends JpaRepository<EquipmentLocationHistory, UUID> {

    Page<EquipmentLocationHistory> findAllByEquipmentIdAndIsDeletedFalseOrderByChangedAtDesc(
            UUID equipmentId,
            Pageable pageable
    );

    Optional<EquipmentLocationHistory> findFirstByEquipmentIdAndIsDeletedFalseAndChangedAtAfterOrderByChangedAtAsc(
            UUID equipmentId,
            Instant changedAt
    );
}
