package com.toir.entity;
import com.toir.entity.WorkOrderStatus;
import com.toir.entity.WorkOrderTask;
import com.toir.entity.WorkOrderType;

import com.toir.entity.PriorityLevel;
import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "work_orders")
public class WorkOrder extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String number;

    @Column(nullable = false)
    private String title;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "repair_request_id")
    private UUID repairRequestId;

    @Column(name = "ppr_task_id")
    private UUID pprTaskId;

    @Column(name = "contractor_id")
    private UUID contractorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkOrderStatus status = WorkOrderStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkOrderType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PriorityLevel priority = PriorityLevel.MEDIUM;

    @Column(name = "start_planned_at")
    private Instant startPlannedAt;

    @Column(name = "end_planned_at")
    private Instant endPlannedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(columnDefinition = "text")
    private String result;

    @Column(name = "closure_notes", columnDefinition = "text")
    private String closureNotes;

    @Column(name = "created_by_id", nullable = false)
    private UUID createdById;

    @Column(name = "approved_by_id")
    private UUID approvedById;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkOrderTask> tasks = new ArrayList<>();

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public UUID getRepairRequestId() { return repairRequestId; }
    public void setRepairRequestId(UUID repairRequestId) { this.repairRequestId = repairRequestId; }
    public UUID getPprTaskId() { return pprTaskId; }
    public void setPprTaskId(UUID pprTaskId) { this.pprTaskId = pprTaskId; }
    public UUID getContractorId() { return contractorId; }
    public void setContractorId(UUID contractorId) { this.contractorId = contractorId; }
    public WorkOrderStatus getStatus() { return status; }
    public void setStatus(WorkOrderStatus status) { this.status = status; }
    public WorkOrderType getType() { return type; }
    public void setType(WorkOrderType type) { this.type = type; }
    public PriorityLevel getPriority() { return priority; }
    public void setPriority(PriorityLevel priority) { this.priority = priority; }
    public Instant getStartPlannedAt() { return startPlannedAt; }
    public void setStartPlannedAt(Instant startPlannedAt) { this.startPlannedAt = startPlannedAt; }
    public Instant getEndPlannedAt() { return endPlannedAt; }
    public void setEndPlannedAt(Instant endPlannedAt) { this.endPlannedAt = endPlannedAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getClosureNotes() { return closureNotes; }
    public void setClosureNotes(String closureNotes) { this.closureNotes = closureNotes; }
    public UUID getCreatedById() { return createdById; }
    public void setCreatedById(UUID createdById) { this.createdById = createdById; }
    public UUID getApprovedById() { return approvedById; }
    public void setApprovedById(UUID approvedById) { this.approvedById = approvedById; }
    public List<WorkOrderTask> getTasks() { return tasks; }
    public void setTasks(List<WorkOrderTask> tasks) { this.tasks = tasks; }
}
