package com.toir.workorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {
    boolean existsByNumber(String number);
    long countByStatus(WorkOrderStatus status);

    @Query("select w from WorkOrder w where (:status is null or w.status = :status) " +
            "and (:departmentId is null or w.departmentId = :departmentId) " +
            "and (:equipmentId is null or w.equipmentId = :equipmentId)")
    List<WorkOrder> search(@Param("status") WorkOrderStatus status,
                           @Param("departmentId") UUID departmentId,
                           @Param("equipmentId") UUID equipmentId);
}
