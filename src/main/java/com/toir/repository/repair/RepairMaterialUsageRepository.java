package com.toir.repository.repair;

import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.repository.projection.WorkOrderCountProjection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface RepairMaterialUsageRepository extends JpaRepository<RepairMaterialUsage, UUID> {
    @Query(value = "SELECT * FROM repair_material_usages WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<RepairMaterialUsage> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM repair_material_usages WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<RepairMaterialUsage> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM repair_material_usages WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<RepairMaterialUsage> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM repair_material_usages WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM repair_material_usages WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM repair_material_usages WHERE work_order_id = :workOrderId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<RepairMaterialUsage> findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("workOrderId") UUID workOrderId);

    @Query("""
            select u.workOrderId as workOrderId, count(u) as count
            from RepairMaterialUsage u
            where u.isDeleted = false
              and u.workOrderId in :workOrderIds
            group by u.workOrderId
            """)
    List<WorkOrderCountProjection> countByWorkOrderIds(@Param("workOrderIds") Collection<UUID> workOrderIds);
}
