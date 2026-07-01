package com.toir.service.maintenanceworkspace;

import com.toir.dto.maintenanceworkspace.MaintenancePlannerBacklogItem;
import com.toir.dto.maintenanceworkspace.MaintenancePlannerCapacityResponse;
import com.toir.dto.maintenanceworkspace.MaintenancePlannerScheduleRequest;
import com.toir.dto.maintenanceworkspace.MaintenanceWorkspaceFilter;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.WorkOrderStatus;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenancePlannerService {

    private final WorkOrderRepository workOrderRepository;

    @Transactional(readOnly = true)
    public List<MaintenancePlannerBacklogItem> backlog() {
        return backlog(MaintenanceWorkspaceFilter.empty());
    }

    @Transactional(readOnly = true)
    public List<MaintenancePlannerBacklogItem> backlog(MaintenanceWorkspaceFilter filter) {
        return workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(this::isBacklog)
                .map(this::item)
                .filter(item -> matchesFilter(item, filter))
                .toList();
    }

    @Transactional(readOnly = true)
    public MaintenancePlannerCapacityResponse capacity() {
        return capacity(MaintenanceWorkspaceFilter.empty());
    }

    @Transactional(readOnly = true)
    public MaintenancePlannerCapacityResponse capacity(MaintenanceWorkspaceFilter filter) {
        List<MaintenancePlannerBacklogItem> items = backlog(filter);
        long scheduled = items.stream().filter(i -> i.scheduledStart() != null).count();
        return new MaintenancePlannerCapacityResponse(items.size(), scheduled, items.size() - scheduled, 0, "Capacity uses current work-order schedule until brigade calendars are modeled.");
    }

    @Transactional(readOnly = true)
    public List<MaintenancePlannerBacklogItem> materialReadiness() {
        return materialReadiness(MaintenanceWorkspaceFilter.empty());
    }

    @Transactional(readOnly = true)
    public List<MaintenancePlannerBacklogItem> materialReadiness(MaintenanceWorkspaceFilter filter) {
        return backlog(filter).stream()
                .filter(item -> !"READY".equals(item.materialReadiness()))
                .toList();
    }

    @Transactional
    public MaintenancePlannerBacklogItem schedule(MaintenancePlannerScheduleRequest request) {
        if (request.workOrderId() == null) {
            throw RestException.badRequest("workOrderId is required");
        }
        WorkOrder workOrder = workOrderRepository.findByIdAndIsDeletedFalse(request.workOrderId())
                .orElseThrow(() -> RestException.notFound("Work order not found: " + request.workOrderId()));
        workOrder.setStartPlannedAt(request.scheduledStart());
        workOrder.setEndPlannedAt(request.scheduledEnd());
        if (workOrder.getStatus() == WorkOrderStatus.DRAFT) {
            workOrder.setStatus(WorkOrderStatus.PLANNED);
        }
        return item(workOrderRepository.save(workOrder));
    }

    private boolean isBacklog(WorkOrder workOrder) {
        return workOrder.getStatus() != WorkOrderStatus.CLOSED && workOrder.getStatus() != WorkOrderStatus.CANCELLED;
    }

    private MaintenancePlannerBacklogItem item(WorkOrder workOrder) {
        String material = workOrder.getWarehouseId() == null ? "BLOCKED" : "READY";
        String labor = workOrder.getPerformer() == null ? "BLOCKED" : "READY";
        String approval = workOrder.getStatus() == WorkOrderStatus.DRAFT ? "BLOCKED" : "READY";
        String downtime = workOrder.getStartPlannedAt() == null || workOrder.getEndPlannedAt() == null ? "PENDING" : "READY";
        String blocker = firstBlocker(material, labor, approval, downtime);
        return new MaintenancePlannerBacklogItem(
                workOrder.getId(),
                workOrder.getNumber(),
                workOrder.getTitle(),
                workOrder.getEquipmentId(),
                workOrder.getDepartmentId(),
                workOrder.getPriority() == null ? null : workOrder.getPriority().name(),
                workOrder.getStatus() == null ? null : workOrder.getStatus().name(),
                workOrder.getStartPlannedAt(),
                workOrder.getEndPlannedAt(),
                "READY",
                material,
                labor,
                "READY",
                approval,
                downtime,
                blocker == null ? "READY" : "BLOCKED",
                blocker
        );
    }

    private String firstBlocker(String material, String labor, String approval, String downtime) {
        if (!"READY".equals(material)) {
            return "Warehouse is not selected for material readiness";
        }
        if (!"READY".equals(labor)) {
            return "Performer is not assigned";
        }
        if (!"READY".equals(approval)) {
            return "Work order is still draft";
        }
        if (!"READY".equals(downtime)) {
            return "Schedule window is not selected";
        }
        return null;
    }

    private boolean matchesFilter(MaintenancePlannerBacklogItem item, MaintenanceWorkspaceFilter filter) {
        if (filter == null) {
            return true;
        }
        return matchesSearch(item, filter.search())
                && matchesUuid(item.departmentId(), filter.departmentId())
                && matchesUuid(item.equipmentId(), filter.equipmentId())
                && matchesText(item.priority(), filter.priority())
                && matchesText(item.status(), filter.status())
                && matchesText(item.readinessStatus(), filter.readinessStatus())
                && matchesInstantRange(scheduledAt(item), filter.scheduledFrom(), filter.scheduledTo());
    }

    private Instant scheduledAt(MaintenancePlannerBacklogItem item) {
        return item.scheduledStart() != null ? item.scheduledStart() : item.scheduledEnd();
    }

    private boolean matchesSearch(MaintenancePlannerBacklogItem item, String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery == null) {
            return true;
        }
        return contains(item.number(), normalizedQuery)
                || contains(item.title(), normalizedQuery)
                || contains(item.priority(), normalizedQuery)
                || contains(item.status(), normalizedQuery)
                || contains(item.readinessStatus(), normalizedQuery)
                || contains(item.blockerReason(), normalizedQuery);
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
