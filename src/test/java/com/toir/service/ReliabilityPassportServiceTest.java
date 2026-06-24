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
import com.toir.exception.RestException;
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
import org.springframework.data.domain.Page;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void passportDoesNotPreferUnknownWhenCauseCountsTie() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId);

        Defect known = defect(DefectStatus.OPEN, "123", "123");
        Defect unknown = defect(DefectStatus.OPEN, null, null);

        stubPassport(equipment, List.of(unknown, known), List.of());

        ReliabilityPassport result = service.passport(equipmentId);

        assertThat(result.topRootCauses()).extracting(cause -> cause.cause())
                .containsExactly("123", "UNKNOWN");
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

    @Test
    void listFiltersAvailabilityBeforePaginationAndReportsFilteredTotals() {
        Instant now = Instant.now();
        LocalDate serviceStart = LocalDate.now(ZoneOffset.UTC).minusDays(100);
        Equipment highFirst = equipment(UUID.randomUUID(), "EQ-HIGH-1", serviceStart);
        Equipment medium = equipment(UUID.randomUUID(), "EQ-MEDIUM", serviceStart);
        Equipment low = equipment(UUID.randomUUID(), "EQ-LOW", serviceStart);
        Equipment highSecond = equipment(UUID.randomUUID(), "EQ-HIGH-2", serviceStart);
        List<Equipment> equipment = List.of(highFirst, medium, low, highSecond);
        List<UUID> ids = equipment.stream().map(Equipment::getId).toList();

        DowntimeEvent mediumDowntime = downtime(
                medium.getId(),
                now.minus(Duration.ofDays(70)),
                (int) Duration.ofHours(300).toMinutes(),
                DowntimeType.UNPLANNED);
        DowntimeEvent lowDowntime = downtime(
                low.getId(),
                now.minus(Duration.ofDays(80)),
                (int) Duration.ofHours(600).toMinutes(),
                DowntimeType.UNPLANNED);

        when(equipmentRepository.searchAllForPassport(null, null)).thenReturn(equipment);
        when(defectRepository.findAllByEquipmentIdInAndIsDeletedFalse(ids)).thenReturn(List.of());
        when(downtimeRepository.findAllByEquipmentIdInAndIsDeletedFalse(ids))
                .thenReturn(List.of(mediumDowntime, lowDowntime));
        when(workOrderRepository.findAllByEquipmentIdInAndIsDeletedFalse(ids)).thenReturn(List.of());
        when(repairRequestRepository.findAllByEquipmentIdInAndIsDeletedFalse(ids)).thenReturn(List.of());

        Page<ReliabilityPassport> highPage = service.list(null, null, "HIGH", 1, 1);
        Page<ReliabilityPassport> mediumPage = service.list(null, null, "medium", 0, 10);
        Page<ReliabilityPassport> lowPage = service.list(null, null, "LOW", 0, 10);

        assertThat(highPage.getTotalElements()).isEqualTo(2);
        assertThat(highPage.getContent()).extracting(ReliabilityPassport::equipmentId)
                .containsExactly(highSecond.getId());
        assertThat(mediumPage.getTotalElements()).isEqualTo(1);
        assertThat(mediumPage.getContent()).extracting(ReliabilityPassport::equipmentId)
                .containsExactly(medium.getId());
        assertThat(lowPage.getTotalElements()).isEqualTo(1);
        assertThat(lowPage.getContent()).extracting(ReliabilityPassport::equipmentId)
                .containsExactly(low.getId());
    }

    @Test
    void listSortsComputedMetricsBeforePagination() {
        Instant now = Instant.now();
        LocalDate serviceStart = LocalDate.now(ZoneOffset.UTC).minusDays(100);
        Equipment lowDowntime = equipment(UUID.randomUUID(), "EQ-LOW-DOWNTIME", serviceStart);
        Equipment highDowntime = equipment(UUID.randomUUID(), "EQ-HIGH-DOWNTIME", serviceStart);
        List<Equipment> equipment = List.of(lowDowntime, highDowntime);
        List<UUID> ids = equipment.stream().map(Equipment::getId).toList();

        DowntimeEvent small = downtime(
                lowDowntime.getId(),
                now.minus(Duration.ofDays(20)),
                (int) Duration.ofHours(2).toMinutes(),
                DowntimeType.UNPLANNED);
        DowntimeEvent large = downtime(
                highDowntime.getId(),
                now.minus(Duration.ofDays(20)),
                (int) Duration.ofHours(8).toMinutes(),
                DowntimeType.UNPLANNED);

        when(equipmentRepository.searchAllForPassport(null, null)).thenReturn(equipment);
        when(defectRepository.findAllByEquipmentIdInAndIsDeletedFalse(ids)).thenReturn(List.of());
        when(downtimeRepository.findAllByEquipmentIdInAndIsDeletedFalse(ids)).thenReturn(List.of(small, large));
        when(workOrderRepository.findAllByEquipmentIdInAndIsDeletedFalse(ids)).thenReturn(List.of());
        when(repairRequestRepository.findAllByEquipmentIdInAndIsDeletedFalse(ids)).thenReturn(List.of());

        Page<ReliabilityPassport> result = service.list(null, null, null, 0, 1, "totalDowntimeMinutes", "desc");

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(ReliabilityPassport::equipmentId)
                .containsExactly(highDowntime.getId());
        assertThat(result.getContent().getFirst().totalDowntimeMinutes()).isEqualTo(Duration.ofHours(8).toMinutes());
    }

    @Test
    void listRejectsUnknownAvailabilityFilter() {
        assertThatThrownBy(() -> service.list(null, null, "MEDUIM", 0, 10))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Unknown availability filter: MEDUIM")
                .hasMessageContaining("Allowed values: high, medium, low");
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
        return equipment(id, "EQ-1", null);
    }

    private Equipment equipment(UUID id, String code, LocalDate operationStartDate) {
        Equipment equipment = Equipment.builder()
                .code(code)
                .name(code)
                .inventoryNumber(code + "-INV")
                .operationStartDate(operationStartDate)
                .build();
        equipment.setId(id);
        equipment.setCreatedAt(Instant.now().minus(Duration.ofDays(365)));
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
        return downtime(null, start, minutes, type);
    }

    private DowntimeEvent downtime(UUID equipmentId, Instant start, int minutes, DowntimeType type) {
        return DowntimeEvent.builder()
                .equipmentId(equipmentId)
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
