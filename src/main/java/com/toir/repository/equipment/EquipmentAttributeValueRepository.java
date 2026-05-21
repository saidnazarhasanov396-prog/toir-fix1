package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EquipmentAttributeValueRepository extends JpaRepository<EquipmentAttributeValue, UUID> {

    @Query(value = """
            SELECT * FROM equipment_attribute_values
            WHERE equipment_id = :equipmentId AND is_deleted = false
            """, nativeQuery = true)
    List<EquipmentAttributeValue> findAllByEquipmentIdAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);

    @Query(value = """
            SELECT * FROM equipment_attribute_values
            WHERE equipment_id IN (:equipmentIds) AND is_deleted = false
            """, nativeQuery = true)
    List<EquipmentAttributeValue> findAllByEquipmentIdInAndIsDeletedFalse(@Param("equipmentIds") Collection<UUID> equipmentIds);

    @Query(value = """
            SELECT * FROM equipment_attribute_values
            WHERE equipment_id = :equipmentId
              AND attribute_definition_id = :attributeDefinitionId
              AND is_deleted = false
            LIMIT 1
            """, nativeQuery = true)
    Optional<EquipmentAttributeValue> findByEquipmentIdAndDefinitionIdAndIsDeletedFalse(
            @Param("equipmentId") UUID equipmentId,
            @Param("attributeDefinitionId") UUID attributeDefinitionId);
}
