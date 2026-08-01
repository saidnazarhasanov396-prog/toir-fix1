package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentLocationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
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

    @Query(value = """
            SELECT *
            FROM equipment_location_history
            WHERE equipment_id = :equipmentId
              AND changed_at >= :historyStart
              AND changed_at <= :asOf
              AND is_deleted = false
            ORDER BY changed_at ASC, id ASC
            LIMIT :limitPlusOne
            """, nativeQuery = true)
    List<EquipmentLocationHistory> findLifecycleHistory(
            @Param("equipmentId") UUID equipmentId,
            @Param("historyStart") Instant historyStart,
            @Param("asOf") Instant asOf,
            @Param("limitPlusOne") int limitPlusOne);
}
