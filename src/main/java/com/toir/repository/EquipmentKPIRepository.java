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
    java.util.Optional<EquipmentKPI> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<EquipmentKPI> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<EquipmentKPI> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM equipment_kpis WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EquipmentKPI> findAllByEquipmentIdAndIsDeletedFalseOrderByPeriodStartDesc(@Param("equipmentId") UUID equipmentId);
}
