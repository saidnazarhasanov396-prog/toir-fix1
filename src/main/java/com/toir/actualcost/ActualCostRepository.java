package com.toir.actualcost;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActualCostRepository extends JpaRepository<ActualCost, UUID> {
    List<ActualCost> findAllByWorkOrderId(UUID workOrderId);
    List<ActualCost> findAllByStatus(ActualCostStatus status);
}
