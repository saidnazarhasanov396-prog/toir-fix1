package com.toir.entity;

import com.toir.enums.DowntimeType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "downtime_events")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DowntimeEvent extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DowntimeType type;

    @Column(columnDefinition = "text")
    private String description;

}
