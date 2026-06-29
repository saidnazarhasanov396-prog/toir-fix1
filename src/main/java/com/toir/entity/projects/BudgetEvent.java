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
@Table(name = "budget_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetEvent extends BaseEntity {

    @Column(name = "budget_id", nullable = false)
    private UUID budgetId;

    @Column(name = "budget_line_id")
    private UUID budgetLineId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "old_values", columnDefinition = "jsonb")
    private String oldValues;

    @Column(name = "new_values", columnDefinition = "jsonb")
    private String newValues;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}
