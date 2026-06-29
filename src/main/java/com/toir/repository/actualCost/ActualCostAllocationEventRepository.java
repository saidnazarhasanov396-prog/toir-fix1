package com.toir.repository.actualCost;

import com.toir.entity.projects.ActualCostAllocationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActualCostAllocationEventRepository extends JpaRepository<ActualCostAllocationEvent, UUID> {

    List<ActualCostAllocationEvent> findAllByActualCostIdAndIsDeletedFalseOrderByOccurredAtDesc(UUID actualCostId);
}
