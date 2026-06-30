package com.toir.service.maintenanceworkspace;

import com.toir.dto.maintenanceworkspace.MaintenancePlannerBacklogItem;
import com.toir.dto.maintenanceworkspace.MaintenancePlannerCapacityResponse;
import com.toir.dto.maintenanceworkspace.MaintenancePlannerScheduleRequest;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.WorkOrderStatus;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MaintenancePlannerService {

    private final WorkOrderRepository workOrderRepository;

    @Transactional(readOnly = true)
    public List<MaintenancePlannerBacklogItem> backlog() {
        return workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(this::isBacklog)
                .map(this::item)
                .toList();
    }

    @Transactional(readOnly = true)
    public MaintenancePlannerCapacityResponse capacity() {
        List<MaintenancePlannerBacklogItem> items = backlog();
        long scheduled = items.stream().filter(i -> i.scheduledStart() != null).count();
        return new MaintenancePlannerCapacityResponse(items.size(), scheduled, items.size() - scheduled, 0, "Capacity uses current work-order schedule until brigade calendars are modeled.");
    }

    @Transactional(readOnly = true)
    public List<MaintenancePlannerBacklogItem> materialReadiness() {
        return backlog().stream()
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
}
