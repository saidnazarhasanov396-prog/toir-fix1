package com.toir.repository;
import com.toir.entity.EquipmentType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EquipmentTypeRepository extends JpaRepository<EquipmentType, UUID> {
    boolean existsByCode(String code);
}
