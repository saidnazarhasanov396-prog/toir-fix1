package com.toir.service;

import com.toir.dto.analytics.EquipmentAnalyticsResponse;
import com.toir.dto.analytics.AnalyticsDowntimeEventRow;
import com.toir.dto.analytics.AnalyticsOverview;
import com.toir.entity.Department;
import com.toir.entity.DowntimeEvent;
import com.toir.entity.ReliabilityMetric;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.DowntimeType;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkType;
import com.toir.exception.RestException;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.ReliabilityMetricRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.security.ScopeAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    RepairRequestRepository repairRequestRepository;

    @Mock
    DefectRepository defectRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    PprTaskRepository pprTaskRepository;

    @Mock
    DowntimeEventRepository downtimeEventRepository;

    @Mock
    ReliabilityMetricRepository reliabilityMetricRepository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    ActualCostRepository actualCostRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    @InjectMocks
    AnalyticsService service;

    @BeforeEach
    void setUpScope() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
    }

    @Test
    void analyticsForUnknownEquipmentReturns404() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.equipmentAnalytics(equipmentId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Equipment not found");
    }

    @Test
    void analyticsForEquipmentReturnsRequestDefectWorkOrderCounts() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        when(repairRequestRepository.search(null, null, equipmentId)).thenReturn(List.of(
                new RepairRequest(),
                new RepairRequest()
        ));
        when(defectRepository.findAllByEquipmentIdAndIsDeletedFalse(eq(equipmentId))).thenReturn(List.of(
                new Defect(),
                new Defect(),
                new Defect()
        ));
        when(workOrderRepository.search(null, null, equipmentId)).thenReturn(List.of(
                new WorkOrder(),
                new WorkOrder(),
                new WorkOrder(),
                new WorkOrder()
        ));
        when(reliabilityMetricRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByMetricDateDesc(eq(equipmentId)))
                .thenReturn(List.of());
        when(downtimeEventRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(eq(equipmentId)))
                .thenReturn(List.of());
        when(actualCostRepository.sumAmountByEquipmentId(equipmentId)).thenReturn(0.0);

        EquipmentAnalyticsResponse response = service.equipmentAnalytics(equipmentId);

        assertThat(response.requestCount()).isEqualTo(2);
        assertThat(response.defectCount()).isEqualTo(3);
        assertThat(response.workOrderCount()).isEqualTo(4);
    }

    @Test
    void analyticsForEquipmentReturnsDowntimeHours() {
        UUID equipmentId = UUID.randomUUID();
        when(repairRequestRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(defectRepository.findAllByEquipmentIdAndIsDeletedFalse(eq(equipmentId))).thenReturn(List.of());
        when(workOrderRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(reliabilityMetricRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByMetricDateDesc(eq(equipmentId)))
                .thenReturn(List.of(ReliabilityMetric.builder()
                        .equipmentId(equipmentId)
                        .metricDate(LocalDate.of(2026, 5, 1))
                        .mtbfHours(10.0)
                        .mttrHours(2.0)
                        .availability(95.0)
                        .build()));
        DowntimeEvent firstDowntime = DowntimeEvent.builder()
                .equipmentId(equipmentId)
                .departmentId(UUID.randomUUID())
                .startAt(Instant.parse("2026-05-01T10:00:00Z"))
                .endAt(Instant.parse("2026-05-01T10:30:00Z"))
                .durationMinutes(30)
                .type(DowntimeType.EMERGENCY)
                .description("Stop 1")
                .build();
        firstDowntime.setId(UUID.randomUUID());
        DowntimeEvent secondDowntime = DowntimeEvent.builder()
                .equipmentId(equipmentId)
                .departmentId(UUID.randomUUID())
                .startAt(Instant.parse("2026-05-02T10:00:00Z"))
                .endAt(Instant.parse("2026-05-02T11:30:00Z"))
                .durationMinutes(90)
                .type(DowntimeType.EMERGENCY)
                .description("Stop 2")
                .build();
        secondDowntime.setId(UUID.randomUUID());
        when(downtimeEventRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(eq(equipmentId)))
                .thenReturn(List.of(firstDowntime, secondDowntime));
        when(actualCostRepository.sumAmountByEquipmentId(equipmentId)).thenReturn(0.0);

        Equipment equipment = equipment(equipmentId);
        equipment.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        EquipmentAnalyticsResponse response = service.equipmentAnalytics(equipmentId);

        assertThat(response.downtimeMinutes()).isEqualTo(120);
        assertThat(response.downtimeHours()).isEqualTo(2.0);
        assertThat(response.availability()).isEqualTo(95.0);
    }

    @Test
    void analyticsUsesCalculatedAvailabilityWhenStoredMetricAvailabilityIsNull() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId);
        equipment.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(repairRequestRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(defectRepository.findAllByEquipmentIdAndIsDeletedFalse(eq(equipmentId))).thenReturn(List.of());
        when(workOrderRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(reliabilityMetricRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByMetricDateDesc(eq(equipmentId)))
                .thenReturn(List.of(ReliabilityMetric.builder()
                        .equipmentId(equipmentId)
                        .metricDate(LocalDate.of(2026, 5, 1))
                        .mtbfHours(10.0)
                        .mttrHours(2.0)
                        .availability(null)
                        .build()));
        DowntimeEvent downtime = DowntimeEvent.builder()
                .equipmentId(equipmentId)
                .departmentId(UUID.randomUUID())
                .startAt(Instant.parse("2026-05-01T10:00:00Z"))
                .endAt(Instant.parse("2026-05-01T11:00:00Z"))
                .durationMinutes(60)
                .type(DowntimeType.EMERGENCY)
                .description("Stop")
                .build();
        downtime.setId(UUID.randomUUID());
        when(downtimeEventRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(eq(equipmentId)))
                .thenReturn(List.of(downtime));
        when(actualCostRepository.sumAmountByEquipmentId(equipmentId)).thenReturn(0.0);

        EquipmentAnalyticsResponse response = service.equipmentAnalytics(equipmentId);

        assertThat(response.availability()).isGreaterThan(0.0);
        assertThat(response.availability()).isLessThan(100.0);
    }

    @Test
    void analyticsNormalizesFractionalStoredAvailabilityToPercent() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        when(repairRequestRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(defectRepository.findAllByEquipmentIdAndIsDeletedFalse(eq(equipmentId))).thenReturn(List.of());
        when(workOrderRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(reliabilityMetricRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByMetricDateDesc(eq(equipmentId)))
                .thenReturn(List.of(ReliabilityMetric.builder()
                        .equipmentId(equipmentId)
                        .metricDate(LocalDate.of(2026, 5, 1))
                        .availability(0.95)
                        .build()));
        when(downtimeEventRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(eq(equipmentId)))
                .thenReturn(List.of());
        when(actualCostRepository.sumAmountByEquipmentId(equipmentId)).thenReturn(0.0);

        EquipmentAnalyticsResponse response = service.equipmentAnalytics(equipmentId);

        assertThat(response.availability()).isEqualTo(95.0);
        assertThat(response.history()).singleElement().satisfies(row ->
                assertThat(row.availability()).isEqualTo(95.0));
    }

    @Test
    void analyticsForEquipmentReturnsTotalCost() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        when(repairRequestRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(defectRepository.findAllByEquipmentIdAndIsDeletedFalse(eq(equipmentId))).thenReturn(List.of());
        when(workOrderRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(reliabilityMetricRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByMetricDateDesc(eq(equipmentId)))
                .thenReturn(List.of());
        when(downtimeEventRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(eq(equipmentId)))
                .thenReturn(List.of());
        when(actualCostRepository.sumAmountByEquipmentId(equipmentId)).thenReturn(1250.75);

        EquipmentAnalyticsResponse response = service.equipmentAnalytics(equipmentId);

        assertThat(response.totalCost()).isEqualTo(1250.75);
    }

    @Test
    void analyticsForEquipmentWithNoDataReturnsZerosAndEmptyLists() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        when(repairRequestRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(defectRepository.findAllByEquipmentIdAndIsDeletedFalse(eq(equipmentId))).thenReturn(List.of());
        when(workOrderRepository.search(null, null, equipmentId)).thenReturn(List.of());
        when(reliabilityMetricRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByMetricDateDesc(eq(equipmentId)))
                .thenReturn(List.of());
        when(downtimeEventRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(eq(equipmentId)))
                .thenReturn(List.of());
        when(actualCostRepository.sumAmountByEquipmentId(equipmentId)).thenReturn(0.0);

        EquipmentAnalyticsResponse response = service.equipmentAnalytics(equipmentId);

        assertThat(response.equipmentId()).isEqualTo(equipmentId.toString());
        assertThat(response.requestCount()).isZero();
        assertThat(response.defectCount()).isZero();
        assertThat(response.workOrderCount()).isZero();
        assertThat(response.downtimeHours()).isZero();
        assertThat(response.totalCost()).isZero();
        assertThat(response.history()).isEmpty();
        assertThat(response.downtimes()).isEmpty();
        assertThat(response.events()).isEmpty();
        assertThat(response.downtimeMinutes()).isZero();
    }

    @Test
    void existingDowntimeHistoryArraysRemainNonNull() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        when(repairRequestRepository.search(null, null, equipmentId)).thenReturn(null);
        when(defectRepository.findAllByEquipmentIdAndIsDeletedFalse(eq(equipmentId))).thenReturn(null);
        when(workOrderRepository.search(null, null, equipmentId)).thenReturn(null);
        when(reliabilityMetricRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByMetricDateDesc(eq(equipmentId)))
                .thenReturn(null);
        when(downtimeEventRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(eq(equipmentId)))
                .thenReturn(null);
        when(actualCostRepository.sumAmountByEquipmentId(equipmentId)).thenReturn(0.0);

        EquipmentAnalyticsResponse response = service.equipmentAnalytics(equipmentId);

        assertThat(response.requestCount()).isZero();
        assertThat(response.defectCount()).isZero();
        assertThat(response.workOrderCount()).isZero();
        assertThat(response.history()).isNotNull().isEmpty();
        assertThat(response.downtimes()).isNotNull().isEmpty();
        assertThat(response.events()).isNotNull().isEmpty();
    }

    @Test
    void overviewUsesRepairRequestsAsFailureDowntimeWhenExplicitDowntimeEventsAreMissing() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Instant now = Instant.now();
        Equipment equipment = equipment(equipmentId, departmentId);
        equipment.setCreatedAt(now.minus(Duration.ofHours(100)));
        Department department = new Department();
        department.setId(departmentId);
        department.setCode("ENT-002");
        department.setName("Tenzorsoft");
        RepairRequest completedFailure = RepairRequest.builder()
                .equipmentId(equipmentId)
                .departmentId(departmentId)
                .status(RequestStatus.CLOSED)
                .detectedAt(now.minus(Duration.ofHours(10)))
                .actualCompletionAt(now.minus(Duration.ofHours(5)))
                .build();

        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(equipment));
        when(repairRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(completedFailure));
        when(workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(downtimeEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(pprTaskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(departmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(department));

        AnalyticsOverview overview = service.overview();

        assertThat(overview.kpis().downtimeHoursTotal()).isEqualTo(5.0);
        assertThat(overview.kpis().mttrAverage()).isCloseTo(5.0, offset(0.02));
        assertThat(overview.kpis().mtbfAverage()).isCloseTo(95.0, offset(0.05));
        assertThat(overview.downtimeByDepartment()).singleElement().satisfies(row -> {
            assertThat(row.departmentId()).isEqualTo(departmentId);
            assertThat(row.departmentName()).isEqualTo("Tenzorsoft");
            assertThat(row.downtimeMinutes()).isEqualTo(300);
        });
        assertThat(overview.reliabilitySnapshot()).singleElement().satisfies(row -> {
            assertThat(row.equipmentId()).isEqualTo(equipmentId);
            assertThat(row.mtbfHours()).isCloseTo(95.0, offset(0.05));
            assertThat(row.mttrHours()).isCloseTo(5.0, offset(0.02));
        });
    }

    @Test
    void downtimeEventsReturnsDashboardFailureSlicesWithSourceMetadataAndDedupe() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID representedWorkOrderId = UUID.randomUUID();
        UUID representedRepairRequestId = UUID.randomUUID();
        Instant now = Instant.now();
        Equipment equipment = equipment(equipmentId, departmentId);
        equipment.setCreatedAt(now.minus(Duration.ofDays(10)));
        Department department = new Department();
        department.setId(departmentId);
        department.setCode("MECH");
        department.setName("Mechanical");

        DowntimeEvent downtime = DowntimeEvent.builder()
                .equipmentId(equipmentId)
                .departmentId(departmentId)
                .workOrderId(representedWorkOrderId)
                .startAt(now.minus(Duration.ofHours(6)))
                .endAt(now.minus(Duration.ofHours(5)))
                .durationMinutes(60)
                .type(DowntimeType.EMERGENCY)
                .description("Emergency stoppage")
                .build();
        downtime.setId(UUID.randomUUID());

        WorkOrder representedWorkOrder = WorkOrder.builder()
                .number("WO-REPRESENTED")
                .title("Represented work order")
                .equipmentId(equipmentId)
                .departmentId(departmentId)
                .repairRequestId(representedRepairRequestId)
                .workType(WorkType.REPAIR)
                .status(WorkOrderStatus.CLOSED)
                .startedAt(now.minus(Duration.ofHours(6)))
                .completedAt(now.minus(Duration.ofHours(5)))
                .build();
        representedWorkOrder.setId(representedWorkOrderId);

        WorkOrder standaloneWorkOrder = WorkOrder.builder()
                .number("WO-100")
                .title("Standalone repair")
                .summary("Repair summary")
                .equipmentId(equipmentId)
                .departmentId(departmentId)
                .workType(WorkType.REPAIR)
                .status(WorkOrderStatus.COMPLETED)
                .startedAt(now.minus(Duration.ofHours(4)))
                .completedAt(now.minus(Duration.ofHours(3)))
                .build();
        standaloneWorkOrder.setId(UUID.randomUUID());

        RepairRequest representedRequest = RepairRequest.builder()
                .number("RR-REPRESENTED")
                .title("Represented request")
                .description("Already represented by downtime")
                .equipmentId(equipmentId)
                .departmentId(departmentId)
                .priority(PriorityLevel.HIGH)
                .status(RequestStatus.CLOSED)
                .detectedAt(now.minus(Duration.ofHours(6)))
                .actualCompletionAt(now.minus(Duration.ofHours(5)))
                .build();
        representedRequest.setId(representedRepairRequestId);

        RepairRequest standaloneRequest = RepairRequest.builder()
                .number("RR-200")
                .title("Standalone request")
                .description("Request description")
                .equipmentId(equipmentId)
                .departmentId(departmentId)
                .priority(PriorityLevel.MEDIUM)
                .status(RequestStatus.CLOSED)
                .detectedAt(now.minus(Duration.ofHours(2)))
                .actualCompletionAt(now.minus(Duration.ofHours(1)))
                .build();
        standaloneRequest.setId(UUID.randomUUID());

        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(equipment));
        when(repairRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                representedRequest,
                standaloneRequest
        ));
        when(workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                representedWorkOrder,
                standaloneWorkOrder
        ));
        when(downtimeEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(downtime));
        when(departmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(department));

        Page<AnalyticsDowntimeEventRow> result = service.downtimeEvents(departmentId, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent())
                .extracting(AnalyticsDowntimeEventRow::sourceType)
                .containsExactly("REPAIR_REQUEST", "WORK_ORDER", "DOWNTIME_EVENT");
        assertThat(result.getContent().get(0)).satisfies(row -> {
            assertThat(row.sourceId()).isEqualTo(standaloneRequest.getId());
            assertThat(row.number()).isEqualTo("RR-200");
            assertThat(row.title()).isEqualTo("Standalone request");
            assertThat(row.departmentName()).isEqualTo("Mechanical");
            assertThat(row.durationMinutes()).isEqualTo(60);
        });
        assertThat(result.getContent().get(1)).satisfies(row -> {
            assertThat(row.sourceId()).isEqualTo(standaloneWorkOrder.getId());
            assertThat(row.number()).isEqualTo("WO-100");
            assertThat(row.title()).isEqualTo("Standalone repair");
        });
    }

    private Equipment equipment(UUID id) {
        return equipment(id, UUID.randomUUID());
    }

    private Equipment equipment(UUID id, UUID departmentId) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("EQ-2026-0009");
        equipment.setName("Line Motor");
        equipment.setInventoryNumber("INV-300");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(departmentId);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }
}
