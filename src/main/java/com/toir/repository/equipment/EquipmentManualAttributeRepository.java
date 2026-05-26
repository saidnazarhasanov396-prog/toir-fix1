package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentManualAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EquipmentManualAttributeRepository extends JpaRepository<EquipmentManualAttribute, UUID> {

    @Query(value = """
            SELECT * FROM equipment_manual_attributes
            WHERE equipment_id = cast(:equipmentId as uuid) AND is_deleted = false
            ORDER BY attribute_key ASC
            """, nativeQuery = true)
    List<EquipmentManualAttribute> findByEquipmentIdAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);

    @Query(value = """
            SELECT EXISTS(
                SELECT 1 FROM equipment_manual_attributes
                WHERE equipment_id = cast(:equipmentId as uuid)
                  AND lower(attribute_key) = lower(:key)
                  AND is_deleted = false
            )
            """, nativeQuery = true)
    boolean existsByEquipmentIdAndKeyIgnoreCaseAndIsDeletedFalse(
            @Param("equipmentId") UUID equipmentId,
            @Param("key") String key
    );

    @Query(value = """
            SELECT * FROM equipment_manual_attributes
            WHERE id = cast(:id as uuid) AND is_deleted = false
            LIMIT 1
            """, nativeQuery = true)
    Optional<EquipmentManualAttribute> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = """
            SELECT * FROM equipment_manual_attributes
            WHERE equipment_id = cast(:equipmentId as uuid)
              AND lower(attribute_key) = lower(:key)
              AND is_deleted = false
            LIMIT 1
            """, nativeQuery = true)
    Optional<EquipmentManualAttribute> findByEquipmentIdAndKeyIgnoreCaseAndIsDeletedFalse(
            @Param("equipmentId") UUID equipmentId,
            @Param("key") String key
    );
}
