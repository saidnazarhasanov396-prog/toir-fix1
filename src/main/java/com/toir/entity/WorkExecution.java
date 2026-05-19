package com.toir.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work_executions")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WorkExecution extends BaseEntity {

    @Column(name = "work_order_id", nullable = false)
    private UUID workOrderId;

    @Column(name = "performer_id")
    private UUID performerId;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(columnDefinition = "text")
    private String result;

}
