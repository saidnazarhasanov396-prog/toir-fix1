package com.toir.entity.maintenance;

import com.toir.entity.BaseEntity;
import com.toir.enums.RepairAcceptanceDefectStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repair_acceptance_defects")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RepairAcceptanceDefect extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "acceptance_id", nullable = false)
    private RepairAcceptance acceptance;

    @Column(name = "defect_id")
    private UUID defectId;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private boolean critical;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairAcceptanceDefectStatus status = RepairAcceptanceDefectStatus.OPEN;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(columnDefinition = "text")
    private String remarks;
}
