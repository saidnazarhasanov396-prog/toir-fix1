package com.toir.defect;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DefectRepository extends JpaRepository<Defect, UUID> {
    boolean existsByCode(String code);
    List<Defect> findAllByEquipmentId(UUID equipmentId);
    long countByStatus(DefectStatus status);
}
