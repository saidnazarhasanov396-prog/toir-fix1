package com.toir.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "counteragent_bank_details")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CounteragentBankDetail extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "counteragent_id", nullable = false)
    private Counteragent counteragent;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "bank_account", nullable = false)
    private String bankAccount;

    @Column(nullable = false)
    private String mfo;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
