package com.toir.service;

import com.toir.dto.analytics.DashboardDetailResponse;
import com.toir.entity.BaseEntity;
import com.toir.entity.ConditionReading;
import com.toir.entity.Location;
import com.toir.entity.Reservation;
import com.toir.entity.StockMovement;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.entity.repair.RepairRequest;
import com.toir.repository.ConditionReadingRepository;
import com.toir.repository.LocationRepository;
import com.toir.repository.ReservationRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.WorkOrderSparePartRequirementRepository;
import com.toir.repository.repair.RepairRequestRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
public class DashboardDetailIntegrationService {

    private final EquipmentRepository equipmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final ConditionReadingRepository conditionReadingRepository;
    private final ReservationRepository reservationRepository;
    private final StockMovementRepository stockMovementRepository;
    private final LocationRepository locationRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final WorkOrderSparePartRequirementRepository materialRequirementRepository;
    private UUID canonicalSiteId;

    @Value("${app.dashboard-detail.canonical-site-id:}")
    void setCanonicalSiteId(String value) {
        canonicalSiteId = value == null || value.isBlank() ? null : UUID.fromString(value.trim());
    }

    @Transactional(readOnly = true)
    public DashboardDetailResponse records(String cursor, int limit) {
        List<DashboardDetailResponse.DetailRecord> rows = new ArrayList<>();
        equipmentRepository.findAllByIsDeletedFalseOrderByIdAsc().forEach(row -> rows.add(equipment(row)));
        workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().forEach(row -> rows.add(workOrder(row)));
        conditionReadingRepository.findAll().stream().filter(row -> !row.isDeleted()).forEach(row -> rows.add(reading(row)));
        reservationRepository.findAll().stream().filter(row -> !row.isDeleted()).forEach(row -> rows.add(reservation(row)));
        stockMovementRepository.findAll().stream().filter(row -> !row.isDeleted()).forEach(row -> rows.add(movement(row)));
        materialRequirementRepository.findAll().stream().filter(row -> !row.isDeleted())
                .forEach(row -> rows.add(materialRequirement(row)));
        rows.sort(Comparator.comparing(DashboardDetailResponse.DetailRecord::datasetType)
                .thenComparing(DashboardDetailResponse.DetailRecord::recordId));
        long revision = rows.stream().mapToLong(DashboardDetailResponse.DetailRecord::recordRevision).max().orElse(0);
        return DashboardDetailResponse.page("TOIR_GENERAL", revision, rows, cursor, limit);
    }

    private DashboardDetailResponse.DetailRecord equipment(Equipment row) {
        Map<String, Object> payload = map();
        put(payload, "code", row.getCode()); put(payload, "name", row.getName());
        put(payload, "inventoryNumber", row.getInventoryNumber()); put(payload, "model", row.getModel());
        put(payload, "equipmentTypeId", row.getEquipmentTypeId());
        put(payload, "status", name(row.getStatus())); put(payload, "category", name(row.getCategory()));
        put(payload, "departmentId", row.getDepartmentId()); put(payload, "responsibleDepartmentId", row.getResponsibleDepartmentId());
        put(payload, "locationId", row.getLocationId());
        Location location = location(row.getLocationId());
        put(payload, "locationCode", location == null ? null : location.getCode());
        put(payload, "locationName", location == null ? null : location.getName());
        put(payload, "daysOfResourceRemaining", row.getDaysOfResourceRemaining());
        return record("toir.equipment.v1", row, row.getDepartmentId(), first(row.getUpdatedAt(), row.getCreatedAt()), payload);
    }

    private DashboardDetailResponse.DetailRecord workOrder(WorkOrder row) {
        Map<String, Object> payload = map();
        put(payload, "number", row.getNumber()); put(payload, "title", row.getTitle());
        put(payload, "status", name(row.getStatus())); put(payload, "priority", name(row.getPriority()));
        put(payload, "type", name(row.getType())); put(payload, "equipmentId", row.getEquipmentId());
        Equipment equipment = equipment(row.getEquipmentId());
        put(payload, "equipmentCode", equipment == null ? null : equipment.getCode());
        put(payload, "equipmentName", equipment == null ? null : equipment.getName());
        put(payload, "departmentId", row.getDepartmentId()); put(payload, "plannedStartAt", row.getStartPlannedAt());
        put(payload, "warehouseId", row.getWarehouseId());
        put(payload, "dueAt", row.getEndPlannedAt()); put(payload, "startedAt", row.getStartedAt());
        put(payload, "completedAt", row.getCompletedAt()); put(payload, "requiresShutdown", row.isRequiresShutdown());
        put(payload, "locationId", row.getLocationId());
        Location location = location(row.getLocationId() == null && equipment != null
                ? equipment.getLocationId() : row.getLocationId());
        put(payload, "locationCode", location == null ? null : location.getCode());
        put(payload, "locationName", location == null ? null : location.getName());
        RepairRequest request = repairRequest(row.getRepairRequestId());
        put(payload, "requestedAt", request == null ? null : request.getDetectedAt());
        put(payload, "failureDescription", request == null ? null : request.getDescription());
        put(payload, "technicianId", row.getPerformer() == null ? null : row.getPerformer().getUserId());
        return record("toir.work-orders.v1", row, row.getDepartmentId(),
                first(row.getStartedAt(), row.getStartPlannedAt(), row.getCreatedAt()), payload);
    }

    private DashboardDetailResponse.DetailRecord reading(ConditionReading row) {
        Map<String, Object> payload = map();
        put(payload, "equipmentId", row.getEquipmentId()); put(payload, "parameter", name(row.getParameter()));
        put(payload, "value", row.getValue()); put(payload, "unit", row.getUnit());
        put(payload, "severity", row.getSeverity()); put(payload, "warnHigh", row.getWarnHigh());
        put(payload, "alarmHigh", row.getAlarmHigh());
        return record("toir.condition-readings.v1", row, null, first(row.getRecordedAt(), row.getCreatedAt()), payload);
    }

    private DashboardDetailResponse.DetailRecord reservation(Reservation row) {
        Map<String, Object> payload = map();
        put(payload, "warehouseId", row.getWarehouseId()); put(payload, "sparePartId", row.getSparePartId());
        put(payload, "workOrderId", row.getWorkOrderId()); put(payload, "quantity", row.getQuantity());
        put(payload, "status", name(row.getStatus())); put(payload, "stockStatus", name(row.getStockStatus()));
        return record("toir.reservations.v1", row, row.getWarehouseId(), first(row.getUpdatedAt(), row.getCreatedAt()), payload);
    }

    private DashboardDetailResponse.DetailRecord movement(StockMovement row) {
        Map<String, Object> payload = map();
        put(payload, "warehouseId", row.getWarehouseId()); put(payload, "sparePartId", row.getSparePartId());
        put(payload, "workOrderId", row.getWorkOrderId()); put(payload, "type", name(row.getType()));
        put(payload, "quantity", row.getQuantity()); put(payload, "unit", row.getUnit());
        put(payload, "documentNumber", row.getDocumentNumber()); put(payload, "stockStatus", name(row.getStockStatus()));
        return record("toir.stock-movements.v1", row,
                row.getDepartmentId() == null ? row.getWarehouseId() : row.getDepartmentId(),
                first(row.getOccurredAt(), row.getCreatedAt()), payload);
    }

    private DashboardDetailResponse.DetailRecord materialRequirement(WorkOrderSparePartRequirement row) {
        Map<String, Object> payload = map();
        put(payload, "workOrderId", row.getWorkOrderId());
        put(payload, "sparePartId", row.getSparePartId());
        put(payload, "sparePartCode", row.getSparePart() == null ? null : row.getSparePart().getCode());
        put(payload, "sparePartName", row.getSparePart() == null ? null : row.getSparePart().getName());
        put(payload, "warehouseId", row.getWarehouseId());
        put(payload, "requiredQuantity", row.getRequiredQty());
        put(payload, "unit", row.getUnit()); put(payload, "criticality", row.getCriticality());
        put(payload, "status", name(row.getStatus())); put(payload, "sourceType", name(row.getSourceType()));
        WorkOrder workOrder = row.getWorkOrder();
        UUID siteId = workOrder == null ? null : workOrder.getDepartmentId();
        return record("toir.material-requirements.v1", row, siteId,
                first(row.getUpdatedAt(), row.getCreatedAt()), payload);
    }

    private Equipment equipment(UUID id) {
        return id == null ? null : equipmentRepository.findByIdAndIsDeletedFalse(id).orElse(null);
    }

    private Location location(UUID id) {
        return id == null ? null : locationRepository.findByIdAndIsDeletedFalse(id).orElse(null);
    }

    private RepairRequest repairRequest(UUID id) {
        return id == null ? null : repairRequestRepository.findByIdAndIsDeletedFalse(id).orElse(null);
    }

    private DashboardDetailResponse.DetailRecord record(
            String type, BaseEntity row, UUID siteId, Instant eventAt, Map<String, Object> payload
    ) {
        Instant updated = first(row.getUpdatedAt(), eventAt, row.getCreatedAt(), Instant.EPOCH);
        long revision = Math.max(0, updated.toEpochMilli());
        return new DashboardDetailResponse.DetailRecord(
                type, row.getId().toString(), revision, "UPSERT", canonicalSiteId == null ? siteId : canonicalSiteId,
                first(eventAt, updated), updated, Map.copyOf(payload));
    }

    private Map<String, Object> map() { return new LinkedHashMap<>(); }
    private void put(Map<String, Object> map, String key, Object value) { if (value != null) map.put(key, value); }
    private String name(Enum<?> value) { return value == null ? null : value.name(); }
    private Instant first(Instant... values) {
        for (Instant value : values) if (value != null) return value;
        return Instant.EPOCH;
    }
}
