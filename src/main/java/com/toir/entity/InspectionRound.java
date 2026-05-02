package com.toir.entity;
import com.toir.enums.InspectionRoundStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.toir.enums.InspectionRoundStatus.IN_PROGRESS;

/**
 * Фактический проход по маршруту обхода — экземпляр выполнения InspectionRoute.
 */
@Entity
@Table(name = "inspection_rounds")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InspectionRound extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private InspectionRoute route;

    @Column(name = "performed_by", nullable = false)
    private UUID performedBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    /** IN_PROGRESS / COMPLETED / CANCELLED. */
    @Column(nullable = false)
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private InspectionRoundStatus status = IN_PROGRESS;

    @Column(name = "findings_count", nullable = false)
    private int findingsCount = 0;

    @Column(name = "alarm_count", nullable = false)
    private int alarmCount = 0;

    @Column(columnDefinition = "text")
    private String notes;

    @OneToMany(mappedBy = "round", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InspectionRoundResult> results = new ArrayList<>();
}
