package com.toir.entity.maintenance;
import com.toir.entity.BaseEntity;
import com.toir.enums.PriorityLevel;
import com.toir.enums.WorkOrderStatus;

import com.toir.entity.users.BrigadeMember;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "work_orders")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WorkOrder extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String number;

    @Column(nullable = false)
    private String title;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "equipment_node_id")
    private UUID equipmentNodeId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "repair_request_id")
    private UUID repairRequestId;

    @Column(name = "defect_id")
    private UUID defectId;

    @Column(name = "ppr_task_id")
    private UUID pprTaskId;

    @Column(name = "maintenance_due_event_id")
    private UUID maintenanceDueEventId;

    @Column(name = "cycle_key")
    private String cycleKey;

    @Column(name = "contractor_id")
    private UUID contractorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brigade_member_id")
    private BrigadeMember performer;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "replacement_equipment_id")
    private UUID replacementEquipmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkOrderStatus status = WorkOrderStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkOrderType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false)
    private WorkType workType = WorkType.REPAIR;

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
}
