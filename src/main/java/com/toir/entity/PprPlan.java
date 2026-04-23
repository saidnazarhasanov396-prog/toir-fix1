package com.toir.entity;
import com.toir.entity.PlanStatus;
import com.toir.entity.PprTask;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ppr_plans")
public class PprPlan extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private int month;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status = PlanStatus.DRAFT;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "created_by_id", nullable = false)
    private UUID createdById;

    @Column(name = "approved_by_id")
    private UUID approvedById;

    private String notes;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PprTask> tasks = new ArrayList<>();

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }
    public PlanStatus getStatus() { return status; }
    public void setStatus(PlanStatus status) { this.status = status; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public UUID getCreatedById() { return createdById; }
    public void setCreatedById(UUID createdById) { this.createdById = createdById; }
    public UUID getApprovedById() { return approvedById; }
    public void setApprovedById(UUID approvedById) { this.approvedById = approvedById; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<PprTask> getTasks() { return tasks; }
    public void setTasks(List<PprTask> tasks) { this.tasks = tasks; }
}
