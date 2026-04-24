package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Defect;
import com.toir.enums.DefectStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface DefectRepository extends JpaRepository<Defect, UUID> {
    boolean existsByCode(String code);

    List<Defect> findAllByEquipmentId(UUID equipmentId);

    long countByStatus(DefectStatus status);
}
