package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.EquipmentKPI;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface EquipmentKPIRepository extends JpaRepository<EquipmentKPI, UUID> {
    List<EquipmentKPI> findAllByEquipmentIdOrderByPeriodStartDesc(UUID equipmentId);
}
