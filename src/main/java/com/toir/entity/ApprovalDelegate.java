package com.toir.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_delegates")
@Getter
@Setter
public class ApprovalDelegate extends BaseEntity {

    @Column(name = "approver_id", nullable = false)
    private UUID approverId;

    @Column(name = "delegate_id", nullable = false)
    private UUID delegateId;

    @Column(name = "active_from", nullable = false)
    private Instant activeFrom;

    @Column(name = "active_until")
    private Instant activeUntil;
}
