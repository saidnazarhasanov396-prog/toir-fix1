package com.toir.entity.contractors;

import com.toir.entity.BaseEntity;
import com.toir.enums.ContractStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "contractor_contracts")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ContractorContract extends BaseEntity {

    @Column(name = "contractor_id", nullable = false)
    private UUID contractorId;

    @Column(nullable = false, unique = true)
    private String number;

    @Column(nullable = false)
    private String subject;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    private Double amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus status = ContractStatus.DRAFT;

}
