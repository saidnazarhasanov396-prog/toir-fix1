package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.RepairMaterialUsage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface RepairMaterialUsageRepository extends JpaRepository<RepairMaterialUsage, UUID> {
    @Query(value = "SELECT * FROM repair_material_usages WHERE work_order_id = :workOrderId AND is_deleted = false", nativeQuery = true)
    List<RepairMaterialUsage> findAllByWorkOrderId(@Param("workOrderId") UUID workOrderId);
}
