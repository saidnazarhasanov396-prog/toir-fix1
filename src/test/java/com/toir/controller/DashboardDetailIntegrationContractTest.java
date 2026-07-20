package com.toir.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toir.entity.ConditionReading;
import com.toir.entity.Location;
import com.toir.entity.Reservation;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.users.BrigadeMember;
import com.toir.repository.ConditionReadingRepository;
import com.toir.repository.LocationRepository;
import com.toir.repository.ReservationRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.WorkOrderSparePartRequirementRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.service.DashboardDetailIntegrationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DashboardDetailIntegrationContractTest {

    @Test
    void returnsMaintenanceDomainRowsRatherThanOverviewTotals() throws Exception {
        EquipmentRepository equipment = mock(EquipmentRepository.class);
        WorkOrderRepository workOrders = mock(WorkOrderRepository.class);
        ConditionReadingRepository readings = mock(ConditionReadingRepository.class);
        ReservationRepository reservations = mock(ReservationRepository.class);
        StockMovementRepository movements = mock(StockMovementRepository.class);
        LocationRepository locations = mock(LocationRepository.class);
        RepairRequestRepository repairRequests = mock(RepairRequestRepository.class);
        WorkOrderSparePartRequirementRepository requirements =
                mock(WorkOrderSparePartRequirementRepository.class);
        Equipment equipmentRow = equipment();
        WorkOrder workOrderRow = workOrder();
        Location locationRow = location();
        RepairRequest repairRequestRow = repairRequest();
        WorkOrderSparePartRequirement requirementRow = materialRequirement(workOrderRow);
        ConditionReading readingRow = reading();
        Reservation reservationRow = reservation();
        StockMovement movementRow = movement();
        when(equipment.findAllByIsDeletedFalseOrderByIdAsc()).thenReturn(List.of(equipmentRow));
        when(workOrders.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(workOrderRow));
        when(readings.findAll()).thenReturn(List.of(readingRow));
        when(reservations.findAll()).thenReturn(List.of(reservationRow));
        when(movements.findAll()).thenReturn(List.of(movementRow));
        when(equipment.findByIdAndIsDeletedFalse(workOrderRow.getEquipmentId()))
                .thenReturn(Optional.of(equipmentRow));
        when(locations.findByIdAndIsDeletedFalse(equipmentRow.getLocationId()))
                .thenReturn(Optional.of(locationRow));
        when(repairRequests.findByIdAndIsDeletedFalse(workOrderRow.getRepairRequestId()))
                .thenReturn(Optional.of(repairRequestRow));
        when(requirements.findAll()).thenReturn(List.of(requirementRow));

        DashboardDetailIntegrationService service = new DashboardDetailIntegrationService(
                equipment, workOrders, readings, reservations, movements,
                locations, repairRequests, requirements
        );
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DashboardDetailRecordsController(service)).build();

        mvc.perform(get("/api/integration/dashboard/v1/records").param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moduleCode").value("TOIR_GENERAL"))
                .andExpect(jsonPath("$.records.length()").value(6))
                .andExpect(jsonPath("$.records[0].datasetType").value("toir.condition-readings.v1"))
                .andExpect(jsonPath("$.records[1].datasetType").value("toir.equipment.v1"))
                .andExpect(jsonPath("$.records[1].payload.code").value("PUMP-204"))
                .andExpect(jsonPath("$.records[1].payload.locationCode").value("AREA-1"))
                .andExpect(jsonPath("$.records[2].datasetType").value("toir.material-requirements.v1"))
                .andExpect(jsonPath("$.records[2].payload.workOrderId")
                        .value("00000000-0000-0000-0000-000000000202"))
                .andExpect(jsonPath("$.records[2].payload.sparePartCode").value("BRG-204"))
                .andExpect(jsonPath("$.records[2].payload.requiredQuantity").value(2))
                .andExpect(jsonPath("$.records[5].datasetType").value("toir.work-orders.v1"))
                .andExpect(jsonPath("$.records[5].payload.number").value("WO-204"))
                .andExpect(jsonPath("$.records[5].payload.equipmentCode").value("PUMP-204"))
                .andExpect(jsonPath("$.records[5].payload.requestedAt").isNumber())
                .andExpect(jsonPath("$.records[5].payload.failureDescription").value("Bearing vibration"))
                .andExpect(jsonPath("$.records[5].payload.technicianId")
                        .value("00000000-0000-0000-0000-000000000299"))
                .andExpect(jsonPath("$.records[5].payload.total").doesNotExist());
    }

    private Equipment equipment() {
        Equipment row = mock(Equipment.class);
        common(row, "00000000-0000-0000-0000-000000000201");
        when(row.getCode()).thenReturn("PUMP-204");
        when(row.getName()).thenReturn("Pump M-204");
        when(row.getDepartmentId()).thenReturn(site());
        when(row.getLocationId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000210"));
        return row;
    }

    private WorkOrder workOrder() {
        WorkOrder row = mock(WorkOrder.class);
        BrigadeMember performer = mock(BrigadeMember.class);
        common(row, "00000000-0000-0000-0000-000000000202");
        when(row.getNumber()).thenReturn("WO-204");
        when(row.getTitle()).thenReturn("Pump repair");
        when(row.getDepartmentId()).thenReturn(site());
        when(row.getEquipmentId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000201"));
        when(row.getRepairRequestId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000220"));
        when(performer.getUserId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000299"));
        when(row.getPerformer()).thenReturn(performer);
        when(row.getStartPlannedAt()).thenReturn(Instant.parse("2026-07-18T07:00:00Z"));
        return row;
    }

    private Location location() {
        Location row = mock(Location.class);
        common(row, "00000000-0000-0000-0000-000000000210");
        when(row.getCode()).thenReturn("AREA-1");
        when(row.getName()).thenReturn("Насосная зона");
        return row;
    }

    private RepairRequest repairRequest() {
        RepairRequest row = mock(RepairRequest.class);
        common(row, "00000000-0000-0000-0000-000000000220");
        when(row.getDescription()).thenReturn("Bearing vibration");
        when(row.getDetectedAt()).thenReturn(Instant.parse("2026-07-18T06:30:00Z"));
        return row;
    }

    private WorkOrderSparePartRequirement materialRequirement(WorkOrder workOrder) {
        SparePart sparePart = mock(SparePart.class);
        UUID workOrderId = UUID.fromString("00000000-0000-0000-0000-000000000202");
        UUID sparePartId = UUID.fromString("00000000-0000-0000-0000-000000000230");
        when(sparePart.getId()).thenReturn(sparePartId);
        when(sparePart.getCode()).thenReturn("BRG-204");
        when(sparePart.getName()).thenReturn("Bearing M-204");
        WorkOrderSparePartRequirement row = mock(WorkOrderSparePartRequirement.class);
        common(row, "00000000-0000-0000-0000-000000000240");
        when(row.getWorkOrder()).thenReturn(workOrder);
        when(row.getWorkOrderId()).thenReturn(workOrderId);
        when(row.getSparePart()).thenReturn(sparePart);
        when(row.getSparePartId()).thenReturn(sparePartId);
        when(row.getRequiredQty()).thenReturn(new BigDecimal("2"));
        when(row.getUnit()).thenReturn("PCS");
        return row;
    }

    private ConditionReading reading() {
        ConditionReading row = mock(ConditionReading.class);
        common(row, "00000000-0000-0000-0000-000000000203");
        when(row.getEquipmentId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000201"));
        when(row.getValue()).thenReturn(7.4);
        when(row.getUnit()).thenReturn("mm/s");
        when(row.getRecordedAt()).thenReturn(Instant.parse("2026-07-18T08:00:00Z"));
        when(row.getSeverity()).thenReturn("ALARM");
        return row;
    }

    private Reservation reservation() {
        Reservation row = mock(Reservation.class);
        common(row, "00000000-0000-0000-0000-000000000204");
        when(row.getWarehouseId()).thenReturn(site());
        when(row.getQuantity()).thenReturn(new BigDecimal("2"));
        return row;
    }

    private StockMovement movement() {
        StockMovement row = mock(StockMovement.class);
        common(row, "00000000-0000-0000-0000-000000000205");
        when(row.getWarehouseId()).thenReturn(site());
        when(row.getQuantity()).thenReturn(new BigDecimal("1"));
        when(row.getOccurredAt()).thenReturn(Instant.parse("2026-07-18T08:00:00Z"));
        return row;
    }

    private void common(com.toir.entity.BaseEntity row, String id) {
        when(row.getId()).thenReturn(UUID.fromString(id));
        when(row.getCreatedAt()).thenReturn(Instant.parse("2026-07-18T06:00:00Z"));
        when(row.getUpdatedAt()).thenReturn(Instant.parse("2026-07-18T08:00:00Z"));
    }

    private UUID site() {
        return UUID.fromString("10000000-0000-0000-0000-000000000001");
    }
}
