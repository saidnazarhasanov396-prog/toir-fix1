package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentStatusHistory;
import com.toir.enums.EquipmentStatusSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    @Query(value = """
            SELECT *
            FROM equipment_status_history
            WHERE equipment_id = :equipmentId
              AND changed_at >= :historyStart
              AND changed_at <= :asOf
              AND is_deleted = false
            ORDER BY changed_at ASC, id ASC
            LIMIT :limitPlusOne
            """, nativeQuery = true)
    List<EquipmentStatusHistory> findLifecycleHistory(
            @Param("equipmentId") UUID equipmentId,
            @Param("historyStart") java.time.Instant historyStart,
            @Param("asOf") java.time.Instant asOf,
            @Param("limitPlusOne") int limitPlusOne);
}
