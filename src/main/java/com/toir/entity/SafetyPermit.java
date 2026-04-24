package com.toir.entity;
import com.toir.enums.SafetyPermitStatus;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "safety_permits")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SafetyPermit extends BaseEntity {

    @Column(name = "work_order_id", nullable = false, unique = true)
    private UUID workOrderId;

    @Column(name = "permit_number", nullable = false, unique = true)
    private String permitNumber;

    @Column(name = "issued_by_id")
    private UUID issuedById;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SafetyPermitStatus status = SafetyPermitStatus.DRAFT;

    @Column(columnDefinition = "text")
    private String notes;
}
