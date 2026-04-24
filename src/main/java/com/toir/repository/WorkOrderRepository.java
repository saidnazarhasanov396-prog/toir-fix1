package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.WorkOrder;
import com.toir.enums.WorkOrderStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {
    boolean existsByNumber(String number);

    long countByStatus(WorkOrderStatus status);

    @Query("SELECT w FROM WorkOrder w WHERE (:status IS NULL OR w.status = :status) " +
            "AND (:departmentId IS NULL OR w.departmentId = :departmentId) " +
            "AND (:equipmentId IS NULL OR w.equipmentId = :equipmentId)")
    List<WorkOrder> search(@Param("status") WorkOrderStatus status,
                           @Param("departmentId") UUID departmentId,
                           @Param("equipmentId") UUID equipmentId);
}
