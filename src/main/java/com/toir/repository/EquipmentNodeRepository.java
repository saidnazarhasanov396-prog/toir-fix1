package com.toir.repository;
import com.toir.entity.EquipmentNode;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EquipmentNodeRepository extends JpaRepository<EquipmentNode, UUID> {
    List<EquipmentNode> findAllByEquipmentId(UUID equipmentId);
    List<EquipmentNode> findAllByParentId(UUID parentId);
    boolean existsByEquipmentIdAndCode(UUID equipmentId, String code);
}
