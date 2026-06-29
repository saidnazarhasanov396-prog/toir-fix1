package com.toir.entity.projects;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "actual_cost_allocation_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActualCostAllocationEvent extends BaseEntity {

    @Column(name = "actual_cost_id", nullable = false)
    private UUID actualCostId;

    @Column(name = "old_budget_line_id")
    private UUID oldBudgetLineId;

    @Column(name = "new_budget_line_id")
    private UUID newBudgetLineId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(nullable = false, columnDefinition = "text")
    private String comment;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}
