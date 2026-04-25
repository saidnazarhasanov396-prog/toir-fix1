package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.EquipmentNode;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface EquipmentNodeRepository extends JpaRepository<EquipmentNode, UUID> {
    java.util.Optional<EquipmentNode> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<EquipmentNode> findAllByIsDeletedFalse();

    java.util.List<EquipmentNode> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM equipment_nodes WHERE equipment_id = :equipmentId AND is_deleted = false", nativeQuery = true)
    List<EquipmentNode> findAllByEquipmentIdAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM equipment_nodes WHERE parent_id = :parentId AND is_deleted = false", nativeQuery = true)
    List<EquipmentNode> findAllByParentIdAndIsDeletedFalse(@Param("parentId") UUID parentId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM equipment_nodes WHERE equipment_id = :equipmentId AND code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByEquipmentIdAndCodeAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId, @Param("code") String code);
}
