package com.toir.service.maintenanceworkspace;

import com.toir.dto.maintenanceworkspace.MaintenanceDispatcherActionRequest;
import com.toir.dto.maintenanceworkspace.MaintenanceDispatcherActionResponse;
import com.toir.dto.maintenanceworkspace.MaintenanceDispatcherQueues;
import com.toir.dto.maintenanceworkspace.MaintenanceDispatcherSummary;
import com.toir.dto.maintenanceworkspace.MaintenanceWorkspaceItem;
import com.toir.entity.defects.Defect;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.DefectStatus;
import com.toir.enums.RequestStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.repair.RepairRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MaintenanceDispatcherService {

    private final RepairRequestRepository repairRequestRepository;
    private final DefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;

    @Transactional(readOnly = true)
    public MaintenanceDispatcherSummary summary() {
        return queues().summary();
    }

    @Transactional(readOnly = true)
    public MaintenanceDispatcherQueues queues() {
        Instant now = Instant.now();
        List<RepairRequest> requests = repairRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        List<Defect> defects = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        List<WorkOrder> workOrders = workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();

        List<MaintenanceWorkspaceItem> emergency = requests.stream()
                .filter(this::isOpenRequest)
                .filter(r -> r.getEmergencyReason() != null && !r.getEmergencyReason().isBlank())
                .map(r -> requestItem(r, now, "Emergency repair request", "Assign master"))
                .toList();
        List<MaintenanceWorkspaceItem> unassigned = requests.stream()
                .filter(this::isOpenRequest)
                .filter(r -> r.getAssignedToId() == null)
                .map(r -> requestItem(r, now, "Owner is not assigned", "Assign master"))
                .toList();
        List<MaintenanceWorkspaceItem> overdue = requests.stream()
                .filter(this::isOpenRequest)
                .filter(r -> r.getTargetCompletionAt() != null && r.getTargetCompletionAt().isBefore(now))
                .map(r -> requestItem(r, now, "Target completion is overdue", "Escalate"))
                .toList();
        List<MaintenanceWorkspaceItem> newDefects = defects.stream()
                .filter(d -> d.getStatus() == DefectStatus.OPEN || d.getStatus() == DefectStatus.IN_ANALYSIS)
                .map(d -> defectItem(d, now, "Defect needs triage", "Create work order"))
                .toList();
        List<MaintenanceWorkspaceItem> blockedWorkOrders = workOrders.stream()
                .filter(this::isActiveWorkOrder)
                .filter(this::hasBlocker)
                .map(w -> workOrderItem(w, now, blockerReason(w), "Open detail"))
                .toList();
        MaintenanceDispatcherSummary summary = new MaintenanceDispatcherSummary(
                emergency.size() + newDefects.size() + unassigned.size() + blockedWorkOrders.size() + overdue.size(),
                emergency.size(),
                newDefects.size(),
                unassigned.size(),
                blockedWorkOrders.size(),
                overdue.size(),
                0,
                0
        );
        return new MaintenanceDispatcherQueues(summary, emergency, newDefects, unassigned, blockedWorkOrders, overdue, List.of(), List.of());
    }

    @Transactional
    public MaintenanceDispatcherActionResponse assign(MaintenanceDispatcherActionRequest request) {
        return new MaintenanceDispatcherActionResponse("ASSIGN", request.objectType(), request.objectId(), request.ownerId(), "ACCEPTED", request.comment(), Instant.now());
    }

    @Transactional
    public MaintenanceDispatcherActionResponse escalate(MaintenanceDispatcherActionRequest request) {
        return new MaintenanceDispatcherActionResponse("ESCALATE", request.objectType(), request.objectId(), request.ownerId(), "ACCEPTED", request.comment(), Instant.now());
    }

    private boolean isOpenRequest(RepairRequest request) {
        return request.getStatus() != RequestStatus.CLOSED && request.getStatus() != RequestStatus.CANCELLED && request.getStatus() != RequestStatus.REJECTED;
    }

    private boolean isActiveWorkOrder(WorkOrder workOrder) {
        return workOrder.getStatus() != WorkOrderStatus.CLOSED && workOrder.getStatus() != WorkOrderStatus.CANCELLED;
    }

    private boolean hasBlocker(WorkOrder workOrder) {
        return workOrder.getStatus() == WorkOrderStatus.SUSPENDED
                || (workOrder.getStatus() == WorkOrderStatus.COMPLETED && Boolean.TRUE.equals(workOrder.getRepairActRequired()) && workOrder.getRepairActFileAssetId() == null)
                || (workOrder.getStatus() == WorkOrderStatus.COMPLETED && Boolean.TRUE.equals(workOrder.getStoppageActRequired()) && workOrder.getStoppageActFileAssetId() == null);
    }

    private String blockerReason(WorkOrder workOrder) {
        if (workOrder.getStatus() == WorkOrderStatus.SUSPENDED) {
            return "Work order is suspended";
        }
        if (Boolean.TRUE.equals(workOrder.getRepairActRequired()) && workOrder.getRepairActFileAssetId() == null) {
            return "Repair act evidence is missing";
        }
        if (Boolean.TRUE.equals(workOrder.getStoppageActRequired()) && workOrder.getStoppageActFileAssetId() == null) {
            return "Stoppage act evidence is missing";
        }
        return "Blocked by completion requirements";
    }

    private MaintenanceWorkspaceItem requestItem(RepairRequest r, Instant now, String blocker, String nextAction) {
        return new MaintenanceWorkspaceItem("REPAIR_REQUEST", r.getId(), r.getNumber(), r.getTitle(), r.getEquipmentId(), r.getDepartmentId(), r.getLocationId(),
                value(r.getPriority()), value(r.getCriticality()), value(r.getStatus()), ageHours(r.getDetectedAt(), now), r.getAssignedToId(), blocker, nextAction,
                "/repair-requests/" + r.getId(), r.getCreatedAt(), r.getTargetCompletionAt());
    }

    private MaintenanceWorkspaceItem defectItem(Defect d, Instant now, String blocker, String nextAction) {
        return new MaintenanceWorkspaceItem("DEFECT", d.getId(), d.getCode(), d.getTitle(), d.getEquipmentId(), null, null, d.getSeverity(), null, value(d.getStatus()),
                ageHours(d.getDetectedAt(), now), null, blocker, nextAction, "/defects/" + d.getId(), d.getCreatedAt(), null);
    }

    private MaintenanceWorkspaceItem workOrderItem(WorkOrder w, Instant now, String blocker, String nextAction) {
        return new MaintenanceWorkspaceItem("WORK_ORDER", w.getId(), w.getNumber(), w.getTitle(), w.getEquipmentId(), w.getDepartmentId(), w.getLocationId(),
                value(w.getPriority()), null, value(w.getStatus()), ageHours(w.getCreatedAt(), now), performerUserId(w), blocker, nextAction,
                "/work-orders/" + w.getId(), w.getCreatedAt(), w.getEndPlannedAt());
    }

    private java.util.UUID performerUserId(WorkOrder workOrder) {
        return workOrder.getPerformer() == null ? null : workOrder.getPerformer().getUserId();
    }

    private Long ageHours(Instant start, Instant now) {
        return start == null ? null : Math.max(0, Duration.between(start, now).toHours());
    }

    private String value(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
