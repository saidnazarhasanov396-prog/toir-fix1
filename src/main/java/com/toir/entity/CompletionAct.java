package com.toir.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "completion_acts")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CompletionAct extends BaseEntity {

    @Column(name = "work_order_id", nullable = false, unique = true)
    private UUID workOrderId;

    @Column(name = "repair_acceptance_id")
    private UUID repairAcceptanceId;

    @Column(name = "act_number", nullable = false, unique = true)
    private String actNumber;

    @Column(name = "signed_by_id")
    private UUID signedById;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(columnDefinition = "text")
    private String summary;

}
