package com.toir.service;

import com.toir.controller.ReliabilityPassportController.ReliabilityPassport;
import com.toir.entity.DowntimeEvent;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.DefectStatus;
import com.toir.enums.DowntimeType;
import com.toir.enums.RequestStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkType;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReliabilityPassportServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    DefectRepository defectRepository;

    @Mock
    DowntimeEventRepository downtimeRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    RepairRequestRepository repairRequestRepository;

    @InjectMocks
    ReliabilityPassportService service;

    @Test
    void passportUsesOnlyActiveDefectsAndFailureDowntime() {
        UUID equipmentId = UUID.randomUUID();
        Instant now = Instant.now();
        Equipment equipment = equipment(equipmentId);

        Defect open = defect(DefectStatus.OPEN, "Wear", null);
        Defect resolved = defect(DefectStatus.RESOLVED, null, "Overload");
        Defect cancelled = defect(DefectStatus.CANCELLED, "Noise", null);

        DowntimeEvent planned = downtime(now.minus(Duration.ofDays(10)), 600, DowntimeType.PLANNED);
        DowntimeEvent unplanned = downtime(now.minus(Duration.ofDays(5)), 120, DowntimeType.UNPLANNED);

        stubPassport(equipment, List.of(open, resolved, cancelled), List.of(planned, unplanned));

        ReliabilityPassport result = service.passport(equipmentId);

        assertThat(result.totalDefects()).isEqualTo(2);
        assertThat(result.openDefects()).isEqualTo(1);
        assertThat(result.totalDowntimeEvents()).isEqualTo(1);
        assertThat(result.totalDowntimeMinutes()).isEqualTo(120);
        assertThat(result.mttrHours()).isEqualTo(2.0);
        assertThat(result.mtbfHours()).isCloseTo(8758.0, withinOneMinute());
        assertThat(result.availabilityPct()).isCloseTo(8758.0 / 8760.0 * 100.0, withinOneMinute());
        assertThat(result.topRootCauses()).extracting(cause -> cause.cause())
                .containsExactlyInAnyOrder("Wear", "Overload");
    }

    @Test
    void passportClipsBoundaryEventsAndCountsOpenDowntimeThroughNow() {
        UUID equipmentId = UUID.randomUUID();
        Instant now = Instant.now();
        Equipment equipment = equipment(equipmentId);

        DowntimeEvent boundary = downtime(now.minus(Duration.ofDays(365)).minus(Duration.ofHours(1)),
                120, DowntimeType.UNPLANNED);
        DowntimeEvent open = DowntimeEvent.builder()
                .equipmentId(equipmentId)
                .startAt(now.minus(Duration.ofMinutes(90)))
                .type(DowntimeType.EMERGENCY)
                .build();

        stubPassport(equipment, List.of(), List.of(boundary, open));

        ReliabilityPassport result = service.passport(equipmentId);

        assertThat(result.totalDowntimeEvents()).isEqualTo(2);
        assertThat(result.totalDowntimeMinutes()).isBetween(149L, 151L);
        assertThat(result.mttrHours()).isBetween(59.0 / 60.0, 1.0);
        assertThat(result.mtbfHours()).isNotNull();
        assertThat(result.availabilityPct()).isLessThan(100.0);
    }

    @Test
    void passportReturnsNullMttrWhenFailureDowntimeIsStillOpen() {
        UUID equipmentId = UUID.randomUUID();
        Instant now = Instant.now();
        Equipment equipment = equipment(equipmentId);
        DowntimeEvent open = DowntimeEvent.builder()
                .equipmentId(equipmentId)
                .startAt(now.minus(Duration.ofHours(2)))
                .type(DowntimeType.UNPLANNED)
                .build();

        stubPassport(equipment, List.of(), List.of(open));

        ReliabilityPassport result = service.passport(equipmentId);

        assertThat(result.totalDowntimeMinutes()).isBetween(119L, 121L);
        assertThat(result.mttrHours()).isNull();
        assertThat(result.mtbfHours()).isNotNull();
    }

    @Test
    void repairWorkOrderLowersAvailabilityWhenDowntimeEventIsMissing() {
        UUID equipmentId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Instant now = Instant.now();
        Equipment equipment = equipment(equipmentId);
        WorkOrder repair = WorkOrder.builder()
                .equipmentId(equipmentId)
                .repairRequestId(requestId)
                .workType(WorkType.REPAIR)
                .status(WorkOrderStatus.COMPLETED)
                .startedAt(now.minus(Duration.ofHours(8)))
                .completedAt(now.minus(Duration.ofHours(2)))
                .build();
        RepairRequest request = RepairRequest.builder()
                .equipmentId(equipmentId)
                .status(RequestStatus.CLOSED)
                .detectedAt(now.minus(Duration.ofHours(12)))
                .actualCompletionAt(now.minus(Duration.ofHours(1)))
                .build();
        request.setId(requestId);

        stubPassport(equipment, List.of(), List.of(), List.of(repair), List.of(request));

        ReliabilityPassport result = service.passport(equipmentId);

        assertThat(result.totalDowntimeEvents()).isEqualTo(1);
        assertThat(result.totalDowntimeMinutes()).isBetween(359L, 360L);
        assertThat(result.mttrHours()).isBetween(359.0 / 60.0, 6.0);
        assertThat(result.availabilityPct()).isLessThan(100.0);
    }

    @Test
    void linkedDowntimeTakesPrecedenceOverRepairWorkOrder() {
        UUID equipmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Instant now = Instant.now();
        Equipment equipment = equipment(equipmentId);
        WorkOrder repair = WorkOrder.builder()
                .equipmentId(equipmentId)
                .repairRequestId(requestId)
                .workType(WorkType.REPAIR)
                .status(WorkOrderStatus.COMPLETED)
                .startedAt(now.minus(Duration.ofHours(10)))
                .completedAt(now.minus(Duration.ofHours(2)))
                .build();
        repair.setId(workOrderId);
        DowntimeEvent downtime = downtime(now.minus(Duration.ofHours(6)), 120, DowntimeType.UNPLANNED);
        downtime.setWorkOrderId(workOrderId);
        RepairRequest request = RepairRequest.builder()
                .equipmentId(equipmentId)
                .status(RequestStatus.CLOSED)
                .detectedAt(now.minus(Duration.ofHours(12)))
                .actualCompletionAt(now.minus(Duration.ofHours(1)))
                .build();
        request.setId(requestId);

        stubPassport(equipment, List.of(), List.of(downtime), List.of(repair), List.of(request));

        ReliabilityPassport result = service.passport(equipmentId);

        assertThat(result.totalDowntimeEvents()).isEqualTo(1);
        assertThat(result.totalDowntimeMinutes()).isEqualTo(120);
        assertThat(result.mttrHours()).isEqualTo(2.0);
    }

    private void stubPassport(Equipment equipment, List<Defect> defects, List<DowntimeEvent> downtimes) {
        stubPassport(equipment, defects, downtimes, List.of(), List.of());
    }

    private void stubPassport(Equipment equipment,
                              List<Defect> defects,
                              List<DowntimeEvent> downtimes,
                              List<WorkOrder> workOrders,
                              List<RepairRequest> repairRequests) {
        UUID equipmentId = equipment.getId();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(defectRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(defects);
        when(downtimeRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(equipmentId))
                .thenReturn(downtimes);
        when(workOrderRepository.search(null, null, equipmentId)).thenReturn(workOrders);
        when(repairRequestRepository.search(null, null, equipmentId)).thenReturn(repairRequests);
    }

    private Equipment equipment(UUID id) {
        Equipment equipment = Equipment.builder()
                .code("EQ-1")
                .name("Pump")
                .inventoryNumber("INV-1")
                .build();
        equipment.setId(id);
        return equipment;
    }

    private Defect defect(DefectStatus status, String rootCause, String failureReason) {
        return Defect.builder()
                .status(status)
                .rootCause(rootCause)
                .failureReason(failureReason)
                .build();
    }

    private DowntimeEvent downtime(Instant start, int minutes, DowntimeType type) {
        return DowntimeEvent.builder()
                .startAt(start)
                .endAt(start.plus(Duration.ofMinutes(minutes)))
                .durationMinutes(minutes)
                .type(type)
                .build();
    }

    private org.assertj.core.data.Offset<Double> withinOneMinute() {
        return org.assertj.core.data.Offset.offset(1.0 / 60.0);
    }
}
