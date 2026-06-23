package com.toir.repository.actualCost;

import com.toir.entity.projects.ActualCostReviewEvent;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ActualCostReviewEventRepository extends JpaRepository<ActualCostReviewEvent, UUID> {

    @Query(value = "SELECT * FROM actual_cost_review_events WHERE is_deleted = false ORDER BY occurred_at DESC, created_at DESC", nativeQuery = true)
    List<ActualCostReviewEvent> findAllByIsDeletedFalseOrderByOccurredAtDesc();

    @Query(value = "SELECT * FROM actual_cost_review_events WHERE actual_cost_id IN (:actualCostIds) AND is_deleted = false ORDER BY occurred_at DESC, created_at DESC", nativeQuery = true)
    List<ActualCostReviewEvent> findAllByActualCostIdInAndIsDeletedFalseOrderByOccurredAtDesc(
            @Param("actualCostIds") Collection<UUID> actualCostIds
    );

    @Query(value = "SELECT * FROM actual_cost_review_events WHERE event_code = :eventCode AND is_deleted = false ORDER BY occurred_at DESC, created_at DESC", nativeQuery = true)
    List<ActualCostReviewEvent> findAllByEventCodeAndIsDeletedFalseOrderByOccurredAtDesc(@Param("eventCode") String eventCode);
}
