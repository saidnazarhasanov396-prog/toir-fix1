package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentNode;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface EquipmentNodeRepository extends JpaRepository<EquipmentNode, UUID> {
    @Query(value = "SELECT * FROM equipment_nodes WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<EquipmentNode> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM equipment_nodes WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EquipmentNode> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM equipment_nodes WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<EquipmentNode> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM equipment_nodes WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM equipment_nodes WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM equipment_nodes WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EquipmentNode> findAllByEquipmentIdAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM equipment_nodes WHERE parent_id = :parentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EquipmentNode> findAllByParentIdAndIsDeletedFalse(@Param("parentId") UUID parentId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM equipment_nodes WHERE equipment_id = :equipmentId AND code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByEquipmentIdAndCodeAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId, @Param("code") String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM equipment_nodes
            WHERE equipment_id = :equipmentId
              AND code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
              AND is_deleted = false
            """, nativeQuery = true)
    long maxSequenceByEquipmentIdAndCodePrefix(@Param("equipmentId") UUID equipmentId, @Param("prefix") String prefix);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM equipment_nodes WHERE equipment_id = :equipmentId AND serial_number = :serialNumber AND is_deleted = false)", nativeQuery = true)
    boolean existsByEquipmentIdAndSerialNumberAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId, @Param("serialNumber") String serialNumber);

    @Query(value = """
            SELECT EXISTS(
                SELECT 1
                FROM equipment_nodes
                WHERE equipment_id = :equipmentId
                  AND serial_number = :serialNumber
                  AND id <> cast(:id as uuid)
                  AND is_deleted = false
            )
            """, nativeQuery = true)
    boolean existsByEquipmentIdAndSerialNumberAndIdNotAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId,
                                                                        @Param("serialNumber") String serialNumber,
                                                                        @Param("id") UUID id);

    @Query(value = """
            SELECT *
            FROM equipment_nodes
            WHERE equipment_id = :equipmentId
              AND is_deleted = false
            ORDER BY code ASC, id ASC
            LIMIT :limitPlusOne
            """, nativeQuery = true)
    List<EquipmentNode> findLifecycleNodes(
            @Param("equipmentId") UUID equipmentId,
            @Param("limitPlusOne") int limitPlusOne);
}
