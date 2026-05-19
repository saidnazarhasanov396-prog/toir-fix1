package com.toir.entity;
import com.toir.enums.TimesheetStatus;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "hr_timesheet_entries", indexes = {
        @Index(name = "idx_timesheet_employee_date", columnList = "employee_id,work_date")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TimesheetEntry extends BaseEntity {

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "hours_regular", nullable = false)
    private double hoursRegular;

    @Column(name = "hours_overtime", nullable = false)
    private double hoursOvertime;

    @Column(name = "hours_night", nullable = false)
    private double hoursNight;

    @Column(name = "hours_holiday", nullable = false)
    private double hoursHoliday;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Column(name = "cost_category_id")
    private UUID costCategoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimesheetStatus status = TimesheetStatus.DRAFT;

    @Column(columnDefinition = "text")
    private String note;
}
