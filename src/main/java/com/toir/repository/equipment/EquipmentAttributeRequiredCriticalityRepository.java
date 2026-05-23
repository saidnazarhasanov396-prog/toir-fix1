package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentAttributeRequiredCriticality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface EquipmentAttributeRequiredCriticalityRepository
        extends JpaRepository<EquipmentAttributeRequiredCriticality, UUID> {

    @Query(value = """
            SELECT * FROM equipment_attribute_required_criticality
            WHERE attribute_definition_id = :attributeDefinitionId
              AND is_deleted = false
            ORDER BY created_at ASC
            """, nativeQuery = true)
    List<EquipmentAttributeRequiredCriticality> findAllByAttributeDefinitionIdAndIsDeletedFalse(
            @Param("attributeDefinitionId") UUID attributeDefinitionId);

    @Query(value = """
            SELECT * FROM equipment_attribute_required_criticality
            WHERE attribute_definition_id IN (:attributeDefinitionIds)
              AND is_deleted = false
            ORDER BY created_at ASC
            """, nativeQuery = true)
    List<EquipmentAttributeRequiredCriticality> findAllByAttributeDefinitionIdInAndIsDeletedFalse(
            @Param("attributeDefinitionIds") Collection<UUID> attributeDefinitionIds);

    @Modifying
    @Query(value = """
            UPDATE equipment_attribute_required_criticality
            SET is_deleted = true, updated_at = now()
            WHERE attribute_definition_id = :attributeDefinitionId
              AND is_deleted = false
            """, nativeQuery = true)
    void softDeleteByAttributeDefinitionId(@Param("attributeDefinitionId") UUID attributeDefinitionId);
}
