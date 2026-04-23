package com.toir.entity;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "contractor_contracts")
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

    public UUID getContractorId() { return contractorId; }
    public void setContractorId(UUID contractorId) { this.contractorId = contractorId; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public ContractStatus getStatus() { return status; }
    public void setStatus(ContractStatus status) { this.status = status; }
}
