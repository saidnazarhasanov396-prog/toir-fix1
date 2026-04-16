package com.toir.inspection;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Фактический проход по маршруту обхода — экземпляр выполнения InspectionRoute.
 */
@Entity
@Table(name = "inspection_rounds")
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
    private String status = "IN_PROGRESS";

    @Column(name = "findings_count", nullable = false)
    private int findingsCount = 0;

    @Column(name = "alarm_count", nullable = false)
    private int alarmCount = 0;

    @Column(columnDefinition = "text")
    private String notes;

    @OneToMany(mappedBy = "round", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InspectionRoundResult> results = new ArrayList<>();

    public InspectionRoute getRoute() { return route; }
    public void setRoute(InspectionRoute route) { this.route = route; }
    public UUID getPerformedBy() { return performedBy; }
    public void setPerformedBy(UUID performedBy) { this.performedBy = performedBy; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getFindingsCount() { return findingsCount; }
    public void setFindingsCount(int findingsCount) { this.findingsCount = findingsCount; }
    public int getAlarmCount() { return alarmCount; }
    public void setAlarmCount(int alarmCount) { this.alarmCount = alarmCount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<InspectionRoundResult> getResults() { return results; }
    public void setResults(List<InspectionRoundResult> results) { this.results = results; }
}
