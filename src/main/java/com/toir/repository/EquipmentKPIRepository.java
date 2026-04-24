package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.EquipmentKPI;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface EquipmentKPIRepository extends JpaRepository<EquipmentKPI, UUID> {
    @Query(value = "SELECT * FROM equipment_kpis WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY period_start DESC", nativeQuery = true)
    List<EquipmentKPI> findAllByEquipmentIdOrderByPeriodStartDesc(@Param("equipmentId") UUID equipmentId);
}
