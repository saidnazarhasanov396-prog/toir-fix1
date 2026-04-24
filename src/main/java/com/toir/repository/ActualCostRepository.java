package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.ActualCost;
import com.toir.enums.ActualCostStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface ActualCostRepository extends JpaRepository<ActualCost, UUID> {
    @Query(value = "SELECT * FROM actual_costs WHERE work_order_id = :workOrderId AND is_deleted = false", nativeQuery = true)
    List<ActualCost> findAllByWorkOrderId(@Param("workOrderId") UUID workOrderId);

    @Query(value = "SELECT * FROM actual_costs WHERE status = :status AND is_deleted = false", nativeQuery = true)
    List<ActualCost> findAllByStatus(@Param("status") ActualCostStatus status);
}
