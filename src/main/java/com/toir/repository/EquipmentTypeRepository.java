package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.EquipmentType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface EquipmentTypeRepository extends JpaRepository<EquipmentType, UUID> {
    boolean existsByCode(String code);
}
