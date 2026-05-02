package com.toir.repository;

import com.toir.entity.EquipmentNode;
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
}
