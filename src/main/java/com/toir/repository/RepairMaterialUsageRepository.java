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
    java.util.Optional<RepairMaterialUsage> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<RepairMaterialUsage> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<RepairMaterialUsage> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM repair_material_usages WHERE work_order_id = :workOrderId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<RepairMaterialUsage> findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("workOrderId") UUID workOrderId);
}
