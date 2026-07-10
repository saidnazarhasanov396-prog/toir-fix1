package com.toir.service.maintenanceworkspace;

import com.toir.dto.maintenanceworkspace.MaintenanceDispatcherActionRequest;
import com.toir.dto.maintenanceworkspace.MaintenanceDispatcherActionResponse;
import com.toir.dto.maintenanceworkspace.MaintenanceDispatcherQueues;
import com.toir.dto.maintenanceworkspace.MaintenanceDispatcherSummary;
import com.toir.dto.maintenanceworkspace.MaintenanceWorkspaceItem;
import com.toir.dto.maintenanceworkspace.MaintenanceWorkspaceFilter;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.users.BrigadeMember;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.DefectStatus;
import com.toir.enums.RequestStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.PriorityLevel;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.projects.BrigadeMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class MaintenanceDispatcherService {

    private final RepairRequestRepository repairRequestRepository;
    private final DefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final BrigadeMemberRepository brigadeMemberRepository;
    private final EquipmentRepository equipmentRepository;

    @Transactional(readOnly = true)
    public MaintenanceDispatcherSummary summary() {
        return summary(MaintenanceWorkspaceFilter.empty());
    }

    @Transactional(readOnly = true)
    public MaintenanceDispatcherSummary summary(MaintenanceWorkspaceFilter filter) {
        return queues(filter).summary();
    }

    @Transactional(readOnly = true)
    public MaintenanceDispatcherQueues queues() {
        return queues(MaintenanceWorkspaceFilter.empty());
    }

    @Transactional(readOnly = true)
    public MaintenanceDispatcherQueues queues(MaintenanceWorkspaceFilter filter) {
        Instant now = Instant.now();

        List<RepairRequest> requests = repairRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        List<Defect> defects = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        List<WorkOrder> workOrders = workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        Map<UUID, String> equipmentNames = loadEquipmentNames(requests,defects, workOrders);

        List<MaintenanceWorkspaceItem> emergency = requests.stream()
                .filter(this::isOpenRequest)
                .filter(r -> r.getEmergencyReason() != null && !r.getEmergencyReason().isBlank())
                .map(r -> requestItem(r, now, "Emergency repair request", "Assign master", equipmentNames))
                .toList();
        List<MaintenanceWorkspaceItem> unassigned = requests.stream()
                .filter(this::isOpenRequest)
                .filter(r -> r.getAssignedToId() == null)
                .map(r -> requestItem(r, now, "Owner is not assigned", "Assign master", equipmentNames))
                .toList();
        List<MaintenanceWorkspaceItem> overdue = requests.stream()
                .filter(this::isOpenRequest)
                .filter(r -> r.getTargetCompletionAt() != null && r.getTargetCompletionAt().isBefore(now))
                .map(r -> requestItem(r, now, "Target completion is overdue", "Escalate", equipmentNames))
                .toList();
        List<MaintenanceWorkspaceItem> newDefects = defects.stream()
                .filter(d -> d.getStatus() == DefectStatus.OPEN || d.getStatus() == DefectStatus.IN_ANALYSIS)
                .map(d -> defectItem(d, now, "Defect needs triage", "Create work order", equipmentNames))
                .toList();
        List<MaintenanceWorkspaceItem> blockedWorkOrders = workOrders.stream()
                .filter(this::isActiveWorkOrder)
                .filter(this::hasBlocker)
                .map(w -> workOrderItem(w, now, blockerReason(w), "Open detail", equipmentNames))
                .toList();
        emergency = filterItems(emergency, filter);
        newDefects = filterItems(newDefects, filter);
        unassigned = filterItems(unassigned, filter);
        blockedWorkOrders = filterItems(blockedWorkOrders, filter);
        overdue = filterItems(overdue, filter);
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
        validateActionRequest(request, true);
        String objectType = normalizeObjectType(request.objectType());
        if ("REPAIR_REQUEST".equals(objectType)) {
            RepairRequest repairRequest = repairRequestRepository.findByIdAndIsDeletedFalse(request.objectId())
                    .orElseThrow(() -> RestException.notFound("Repair request not found: " + request.objectId()));
            repairRequest.setAssignedToId(request.ownerId());
            repairRequestRepository.save(repairRequest);
            return actionResponse("ASSIGN", objectType, request);
        }
        if ("WORK_ORDER".equals(objectType)) {
            WorkOrder workOrder = workOrderRepository.findByIdAndIsDeletedFalse(request.objectId())
                    .orElseThrow(() -> RestException.notFound("Work order not found: " + request.objectId()));
            BrigadeMember performer = brigadeMemberRepository.findAllByUserIdAndIsDeletedFalse(request.ownerId()).stream()
                    .findFirst()
                    .orElseThrow(() -> RestException.badRequest("Performer is not linked to an active brigade member: " + request.ownerId()));
            workOrder.setPerformer(performer);
            workOrderRepository.save(workOrder);
            return actionResponse("ASSIGN", objectType, request);
        }
        if ("DEFECT".equals(objectType)) {
            throw RestException.badRequest("Defects do not support owner assignment; create or assign a linked work order instead");
        }
        throw RestException.badRequest("Unsupported dispatcher object type: " + request.objectType());
    }

    @Transactional
    public MaintenanceDispatcherActionResponse escalate(MaintenanceDispatcherActionRequest request) {
        validateActionRequest(request, false);
        String objectType = normalizeObjectType(request.objectType());
        if ("REPAIR_REQUEST".equals(objectType)) {
            RepairRequest repairRequest = repairRequestRepository.findByIdAndIsDeletedFalse(request.objectId())
                    .orElseThrow(() -> RestException.notFound("Repair request not found: " + request.objectId()));
            repairRequest.setPriority(PriorityLevel.EMERGENCY);
            if (repairRequest.getEmergencyReason() == null || repairRequest.getEmergencyReason().isBlank()) {
                repairRequest.setEmergencyReason(hasText(request.comment()) ? request.comment().trim() : "Dispatcher escalation");
            }
            repairRequestRepository.save(repairRequest);
            return actionResponse("ESCALATE", objectType, request);
        }
        if ("WORK_ORDER".equals(objectType)) {
            WorkOrder workOrder = workOrderRepository.findByIdAndIsDeletedFalse(request.objectId())
                    .orElseThrow(() -> RestException.notFound("Work order not found: " + request.objectId()));
            workOrder.setPriority(PriorityLevel.EMERGENCY);
            workOrderRepository.save(workOrder);
            return actionResponse("ESCALATE", objectType, request);
        }
        if ("DEFECT".equals(objectType)) {
            Defect defect = defectRepository.findByIdAndIsDeletedFalse(request.objectId())
                    .orElseThrow(() -> RestException.notFound("Defect not found: " + request.objectId()));
            defect.setSeverity("CRITICAL");
            defectRepository.save(defect);
            return actionResponse("ESCALATE", objectType, request);
        }
        throw RestException.badRequest("Unsupported dispatcher object type: " + request.objectType());
    }


    private void validateActionRequest(MaintenanceDispatcherActionRequest request, boolean ownerRequired) {
        if (request == null) {
            throw RestException.badRequest("Dispatcher action request is required");
        }
        if (!hasText(request.objectType())) {
            throw RestException.badRequest("objectType is required");
        }
        if (request.objectId() == null) {
            throw RestException.badRequest("objectId is required");
        }
        if (ownerRequired && request.ownerId() == null) {
            throw RestException.badRequest("ownerId is required");
        }
    }

    private MaintenanceDispatcherActionResponse actionResponse(String action, String objectType, MaintenanceDispatcherActionRequest request) {
        return new MaintenanceDispatcherActionResponse(action, objectType, request.objectId(), request.ownerId(), "APPLIED", request.comment(), Instant.now());
    }

    private String normalizeObjectType(String objectType) {
        return objectType == null ? null : objectType.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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

    private MaintenanceWorkspaceItem requestItem(
            RepairRequest r,
            Instant now,
            String blocker,
            String nextAction,
            Map<UUID, String> equipmentNames
    ) {
        return new MaintenanceWorkspaceItem(
                "REPAIR_REQUEST",
                r.getId(),
                r.getNumber(),
                r.getTitle(),
                r.getEquipmentId(),
                equipmentName(equipmentNames, r.getEquipmentId()),
                r.getDepartmentId(),
                r.getLocationId(),
                value(r.getPriority()),
                value(r.getCriticality()),
                value(r.getStatus()),
                ageHours(r.getDetectedAt(), now),
                r.getAssignedToId(),
                blocker,
                nextAction,
                "/repair-requests/" + r.getId(),
                r.getCreatedAt(),
                r.getTargetCompletionAt()
        );
    }

    private MaintenanceWorkspaceItem defectItem(
            Defect d,
            Instant now,
            String blocker,
            String nextAction,
            Map<UUID, String> equipmentNames
    ) {
        return new MaintenanceWorkspaceItem(
                "DEFECT",
                d.getId(),
                d.getCode(),
                d.getTitle(),
                d.getEquipmentId(),
                equipmentName(equipmentNames, d.getEquipmentId()),
                null,
                null,
                d.getSeverity(),
                null,
                value(d.getStatus()),
                ageHours(d.getDetectedAt(), now),
                null,
                blocker,
                nextAction,
                "/defects/" + d.getId(),
                d.getCreatedAt(),
                null
        );
    }

    private MaintenanceWorkspaceItem workOrderItem(
            WorkOrder w,
            Instant now,
            String blocker,
            String nextAction,
            Map<UUID, String> equipmentNames
    ) {
        return new MaintenanceWorkspaceItem(
                "WORK_ORDER",
                w.getId(),
                w.getNumber(),
                w.getTitle(),
                w.getEquipmentId(),
                equipmentName(equipmentNames, w.getEquipmentId()),
                w.getDepartmentId(),
                w.getLocationId(),
                value(w.getPriority()),
                null,
                value(w.getStatus()),
                ageHours(w.getCreatedAt(), now),
                performerUserId(w),
                blocker,
                nextAction,
                "/work-orders/" + w.getId(),
                w.getCreatedAt(),
                w.getEndPlannedAt()
        );
    }


    private Map<UUID, String> loadEquipmentNames(
            List<RepairRequest> requests,
            List<Defect> defects,
            List<WorkOrder> workOrders
    ) {
        Set<UUID> equipmentIds = Stream.of(
                        requests.stream().map(RepairRequest::getEquipmentId),
                        defects.stream().map(Defect::getEquipmentId),
                        workOrders.stream().map(WorkOrder::getEquipmentId)
                )
                .flatMap(stream -> stream)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (equipmentIds.isEmpty()) {
            return Map.of();
        }

        return equipmentRepository.findAllByIdInAndIsDeletedFalse(equipmentIds).stream()
                .collect(Collectors.toMap(
                        Equipment::getId,
                        this::equipmentDisplayName,
                        (left, right) -> left
                ));
    }

    private String equipmentName(Map<UUID, String> equipmentNames, UUID equipmentId) {
        return equipmentId == null ? null : equipmentNames.get(equipmentId);
    }

    private String equipmentDisplayName(Equipment equipment) {
        return equipment.getName(); // Equipment.java ga qarab aniq fieldni qo‘yamiz
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

    private List<MaintenanceWorkspaceItem> filterItems(List<MaintenanceWorkspaceItem> items, MaintenanceWorkspaceFilter filter) {
        if (filter == null) {
            return items;
        }
        return items.stream()
                .filter(item -> matchesUuid(item.departmentId(), filter.departmentId()))
                .filter(item -> matchesUuid(item.equipmentId(), filter.equipmentId()))
                .filter(item -> matchesText(item.priority(), filter.priority()))
                .filter(item -> matchesText(item.status(), filter.status()))
                .filter(item -> matchesText(item.objectType(), filter.objectType()))
                .filter(item -> matchesInstantRange(item.dueAt(), filter.dueFrom(), filter.dueTo()))
                .filter(item -> matchesSearch(item, filter.search()))
                .toList();
    }

    private boolean matchesSearch(MaintenanceWorkspaceItem item, String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery == null) {
            return true;
        }
        return contains(item.code(), normalizedQuery)
                || contains(item.title(), normalizedQuery)
                || contains(item.equipmentName(), normalizedQuery)
                || contains(item.objectType(), normalizedQuery)
                || contains(item.priority(), normalizedQuery)
                || contains(item.status(), normalizedQuery)
                || contains(item.blockerReason(), normalizedQuery)
                || contains(item.nextAction(), normalizedQuery);

    }

    private boolean matchesUuid(UUID actual, UUID expected) {
        return expected == null || expected.equals(actual);
    }

    private boolean matchesText(String actual, String expected) {
        String normalizedExpected = normalize(expected);
        return normalizedExpected == null || normalizedExpected.equals(normalize(actual));
    }

    private boolean matchesInstantRange(Instant actual, Instant from, Instant to) {
        if (from == null && to == null) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        return (from == null || !actual.isBefore(from)) && (to == null || !actual.isAfter(to));
    }

    private boolean contains(String actual, String normalizedQuery) {
        String normalizedActual = normalize(actual);
        return normalizedActual != null && normalizedActual.contains(normalizedQuery);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
