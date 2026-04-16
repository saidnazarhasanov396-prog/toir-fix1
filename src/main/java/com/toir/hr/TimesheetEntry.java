package com.toir.hr;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "hr_timesheet_entries", indexes = {
        @Index(name = "idx_timesheet_employee_date", columnList = "employee_id,work_date")
})
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

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }
    public double getHoursRegular() { return hoursRegular; }
    public void setHoursRegular(double hoursRegular) { this.hoursRegular = hoursRegular; }
    public double getHoursOvertime() { return hoursOvertime; }
    public void setHoursOvertime(double hoursOvertime) { this.hoursOvertime = hoursOvertime; }
    public double getHoursNight() { return hoursNight; }
    public void setHoursNight(double hoursNight) { this.hoursNight = hoursNight; }
    public double getHoursHoliday() { return hoursHoliday; }
    public void setHoursHoliday(double hoursHoliday) { this.hoursHoliday = hoursHoliday; }
    public UUID getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(UUID workOrderId) { this.workOrderId = workOrderId; }
    public UUID getCostCategoryId() { return costCategoryId; }
    public void setCostCategoryId(UUID costCategoryId) { this.costCategoryId = costCategoryId; }
    public TimesheetStatus getStatus() { return status; }
    public void setStatus(TimesheetStatus status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
