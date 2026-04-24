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
    @Query(value = "SELECT COUNT(*) > 0 FROM work_orders WHERE number = :number AND is_deleted = false", nativeQuery = true)
    boolean existsByNumber(@Param("number") String number);

    @Query(value = "SELECT COUNT(*) FROM work_orders WHERE status = :status AND is_deleted = false", nativeQuery = true)
    long countByStatus(@Param("status") WorkOrderStatus status);

    @Query(value = "SELECT * FROM work_orders WHERE (:status IS NULL OR status = :status) " +
            "AND (:departmentId IS NULL OR department_id = :departmentId) " +
            "AND (:equipmentId IS NULL OR equipment_id = :equipmentId) AND is_deleted = false", nativeQuery = true)
    List<WorkOrder> search(@Param("status") WorkOrderStatus status,
                           @Param("departmentId") UUID departmentId,
                           @Param("equipmentId") UUID equipmentId);
}
