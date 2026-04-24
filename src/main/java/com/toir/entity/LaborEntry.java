package com.toir.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "labor_entries")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LaborEntry extends BaseEntity {

    @Column(name = "work_order_id", nullable = false)
    private UUID workOrderId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "contractor_name")
    private String contractorName;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(nullable = false)
    private double hours;

    private Double rate;

    @Column(columnDefinition = "text")
    private String description;

}
