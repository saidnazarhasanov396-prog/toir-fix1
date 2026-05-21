package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentAttributeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EquipmentAttributeDefinitionRepository extends JpaRepository<EquipmentAttributeDefinition, UUID> {

    @Query(value = "SELECT * FROM equipment_attribute_definitions WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<EquipmentAttributeDefinition> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = """
            SELECT * FROM equipment_attribute_definitions
            WHERE equipment_type_id = :equipmentTypeId AND is_deleted = false
            ORDER BY sort_order ASC, label ASC
            """, nativeQuery = true)
    List<EquipmentAttributeDefinition> findAllByEquipmentTypeIdAndIsDeletedFalse(@Param("equipmentTypeId") UUID equipmentTypeId);

    @Query(value = """
            SELECT * FROM equipment_attribute_definitions
            WHERE equipment_type_id IN (:equipmentTypeIds) AND is_deleted = false
            ORDER BY sort_order ASC, label ASC
            """, nativeQuery = true)
    List<EquipmentAttributeDefinition> findAllByEquipmentTypeIdInAndIsDeletedFalse(@Param("equipmentTypeIds") Collection<UUID> equipmentTypeIds);

    @Query(value = """
            SELECT EXISTS(
                SELECT 1 FROM equipment_attribute_definitions
                WHERE equipment_type_id = :equipmentTypeId
                  AND attribute_key = :key
                  AND is_deleted = false
            )
            """, nativeQuery = true)
    boolean existsActiveByEquipmentTypeIdAndKey(@Param("equipmentTypeId") UUID equipmentTypeId,
                                                @Param("key") String key);
}
