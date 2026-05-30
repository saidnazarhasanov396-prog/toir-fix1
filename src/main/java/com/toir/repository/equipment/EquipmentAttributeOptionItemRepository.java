package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentAttributeOptionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface EquipmentAttributeOptionItemRepository extends JpaRepository<EquipmentAttributeOptionItem, UUID> {

    @Query(value = """
            SELECT * FROM equipment_attribute_option_items
            WHERE option_source_id = :sourceId AND is_deleted = false
            ORDER BY sort_order ASC, label ASC
            """, nativeQuery = true)
    List<EquipmentAttributeOptionItem> findAllBySourceIdAndIsDeletedFalse(@Param("sourceId") UUID sourceId);

    @Query(value = """
            SELECT * FROM equipment_attribute_option_items
            WHERE option_source_id = :sourceId
            ORDER BY sort_order ASC, label ASC
            """, nativeQuery = true)
    List<EquipmentAttributeOptionItem> findAllBySourceIdIncludingDeleted(@Param("sourceId") UUID sourceId);

    @Query(value = """
            SELECT * FROM equipment_attribute_option_items
            WHERE option_source_id IN (:sourceIds) AND is_deleted = false
            ORDER BY sort_order ASC, label ASC
            """, nativeQuery = true)
    List<EquipmentAttributeOptionItem> findAllBySourceIdInAndIsDeletedFalse(@Param("sourceIds") Collection<UUID> sourceIds);

    @Query(value = """
            SELECT EXISTS(
                SELECT 1 FROM equipment_attribute_option_items
                WHERE option_source_id = :sourceId
                  AND option_id = :optionId
                  AND active = true
                  AND is_deleted = false
            )
            """, nativeQuery = true)
    boolean existsActiveBySourceIdAndOptionId(@Param("sourceId") UUID sourceId, @Param("optionId") String optionId);
}
