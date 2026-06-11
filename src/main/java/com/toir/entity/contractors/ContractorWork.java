package com.toir.entity.contractors;

import com.toir.entity.ActorStampedEntity;
import com.toir.enums.ContractorWorkStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contractor_works")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ContractorWork extends ActorStampedEntity {

    @Column(name = "contractor_id", nullable = false)
    private UUID contractorId;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractorWorkStatus status = ContractorWorkStatus.DRAFT;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    private Double cost;

    @Column(columnDefinition = "text")
    private String result;

    @Column(name = "acceptance_comment", columnDefinition = "text")
    private String acceptanceComment;

    @Column(name = "accepted_by_id")
    private UUID acceptedById;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

}
