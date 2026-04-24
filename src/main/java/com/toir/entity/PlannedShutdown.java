package com.toir.entity;

import com.toir.enums.PlanStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "planned_shutdowns")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlannedShutdown extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status = PlanStatus.DRAFT;
}
