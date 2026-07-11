package com.toir.entity.maintenance;
import com.toir.entity.ActorStampedEntity;
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
public class WorkOrder extends ActorStampedEntity {

    @Column(nullable = false, unique = true)
    private String number;

    @Column(nullable = false)
    private String title;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "equipment_node_id")
    private UUID equipmentNodeId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "work_location_note", columnDefinition = "text")
    private String workLocationNote;

    @Column(name = "repair_request_id")
    private UUID repairRequestId;

    @Column(name = "defect_id")
    private UUID defectId;

    @Column(name = "defect_list_id")
    private UUID defectListId;

    @Column(name = "ppr_task_id")
    private UUID pprTaskId;

    @Column(name = "maintenance_due_event_id")
    private UUID maintenanceDueEventId;

    @Column(name = "repair_campaign_id")
    private UUID repairCampaignId;

    @Column(name = "repair_campaign_stage_id")
    private UUID repairCampaignStageId;

    @Column(name = "budget_line_id")
    private UUID budgetLineId;

    @Column(name = "cycle_key")
    private String cycleKey;

    @Column(name = "requires_shutdown", nullable = false)
    private boolean requiresShutdown;

    @Column(name = "requires_isolation", nullable = false)
    private boolean requiresIsolation;

    @Column(name = "generation_key", length = 512)
    private String generationKey;

    @Column(name = "counteragent_id")
    private UUID counteragentId;

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

    @Column(name = "repair_act_required", nullable = false, columnDefinition = "boolean default false")
    private Boolean repairActRequired = false;

    @Column(name = "stoppage_act_required", nullable = false, columnDefinition = "boolean default false")
    private Boolean stoppageActRequired = false;

    @Column(name = "repair_act_file_asset_id")
    private UUID repairActFileAssetId;

    @Column(name = "stoppage_act_file_asset_id")
    private UUID stoppageActFileAssetId;

    @Column(name = "closure_notes", columnDefinition = "text")
    private String closureNotes;

    @Column(name = "approved_by_id")
    private UUID approvedById;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkOrderTask> tasks = new ArrayList<>();
}
