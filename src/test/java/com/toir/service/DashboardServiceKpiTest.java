package com.toir.service;

import com.toir.dto.dashboard.DashboardEmergencyEventDto;
import com.toir.entity.Department;
import com.toir.entity.DowntimeEvent;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.warehouse.Warehouse;
import com.toir.enums.DefectStatus;
import com.toir.enums.DowntimeType;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.enums.StockMovementType;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.repository.SparePartsWarehouseStatsProjection;
import com.toir.repository.CalibrationRecordRepository;
import com.toir.repository.ConditionReadingRepository;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.ReliabilityMetricRepository;
import com.toir.repository.ReservationRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.actualCost.ActualCostReviewEventRepository;
import com.toir.repository.contarctor.ContractorContractRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.users.UserCertificationRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.warehouse.LegacyStockProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceKpiTest {

    @Mock RepairRequestRepository repairRequestRepository;
    @Mock DefectRepository defectRepository;
    @Mock PprTaskRepository pprTaskRepository;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock EquipmentRepository equipmentRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock WarehouseStockRepository warehouseStockRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock SparePartRepository sparePartRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock DowntimeEventRepository downtimeEventRepository;
    @Mock ReliabilityMetricRepository reliabilityMetricRepository;
    @Mock ContractorWorkRepository contractorWorkRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock ActualCostRepository actualCostRepository;
    @Mock ActualCostReviewEventRepository actualCostReviewEventRepository;
    @Mock ContractorContractRepository contractorContractRepository;
    @Mock ConditionReadingRepository conditionReadingRepository;
    @Mock UserCertificationRepository userCertificationRepository;
    @Mock CalibrationRecordRepository calibrationRecordRepository;
    @Mock MaintenanceDueEventRepository maintenanceDueEventRepository;
    @Mock UserRepository userRepository;
    @Mock ScopeAccessService scopeAccessService;
    @Mock LegacyStockProjectionService legacyStockProjectionService;
    @Mock CounteragentService counteragentService;

    @InjectMocks DashboardService service;

    @BeforeEach
    void stubEmptyDependencies() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        lenient().when(departmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        lenient().when(warehouseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        lenient().when(sparePartRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        lenient().when(userRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        lenient().when(repairRequestRepository.search(any(), any(), any())).thenReturn(List.of());
        lenient().when(pprTaskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        lenient().when(workOrderRepository.search(any(), any(), any())).thenReturn(List.of());
        lenient().when(reservationRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(any())).thenReturn(List.of());
        lenient().when(warehouseStockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        lenient().when(legacyStockProjectionService.currentAll()).thenReturn(java.util.Map.of());
        lenient().when(stockMovementRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        lenient().when(actualCostRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(any())).thenReturn(List.of());
        lenient().when(actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        lenient().when(actualCostReviewEventRepository.findAllByActualCostIdInAndIsDeletedFalseOrderByOccurredAtDesc(any()))
                .thenReturn(List.of());
        lenient().when(contractorWorkRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        lenient().when(contractorContractRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        lenient().when(conditionReadingRepository.countBySeveritiesAndDepartment(any(), any())).thenReturn(0L);
        lenient().when(userCertificationRepository.findAllByExpiresAtBeforeAndIsDeletedFalse(any())).thenReturn(List.of());
        lenient().when(calibrationRecordRepository.findAllByNextDueAtBeforeAndIsDeletedFalse(any())).thenReturn(List.of());
        lenient().when(reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        lenient().when(downtimeEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        lenient().when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        lenient().when(counteragentService.load(org.mockito.ArgumentMatchers.<java.util.Collection<UUID>>any())).thenReturn(List.of());
    }

    @Test
    void overviewReturnsIndustrialToirKpis() {
        UUID departmentA = UUID.randomUUID();
        UUID departmentB = UUID.randomUUID();
        UUID equipmentA = UUID.randomUUID();
        UUID equipmentB = UUID.randomUUID();
        UUID warehouseA = UUID.randomUUID();
        UUID warehouseB = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        Instant currentMonth = monthStart().plusSeconds(3600);
        Instant previousMonth = monthStart().minusSeconds(3600);

        when(departmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                department(departmentA, "Workshop A"),
                department(departmentB, "Workshop B")));
        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                equipment(equipmentA, departmentA, "Pump A"),
                equipment(equipmentB, departmentB, "Pump B")));
        when(warehouseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                warehouse(warehouseA, departmentA),
                warehouse(warehouseB, departmentB)));
        SparePart sparePart = new SparePart();
        sparePart.setId(sparePartId);
        sparePart.setAverageCost(new BigDecimal("12.50"));
        when(sparePartRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(sparePart));

        when(repairRequestRepository.search(null, null, null)).thenReturn(List.of(
                request(departmentA, PriorityLevel.EMERGENCY, RequestStatus.OPEN),
                request(departmentA, PriorityLevel.EMERGENCY, RequestStatus.CLOSED),
                request(departmentB, PriorityLevel.HIGH, RequestStatus.OPEN)));
        when(workOrderRepository.search(null, null, null)).thenReturn(List.of(
                workOrder(departmentA, equipmentA, WorkType.REPAIR, WorkOrderStatus.COMPLETED, currentMonth),
                workOrder(departmentB, equipmentB, WorkType.REPAIR, WorkOrderStatus.CLOSED, previousMonth),
                workOrder(departmentA, equipmentA, WorkType.DIAGNOSTICS, WorkOrderStatus.COMPLETED, currentMonth)));
        when(downtimeEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                downtime(departmentA, equipmentA, currentMonth, 120),
                downtimeWithInterval(departmentA, equipmentA, previousMonth, 60),
                downtime(departmentB, equipmentB, currentMonth, 30)));
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                defect(equipmentA, DefectStatus.OPEN),
                defect(equipmentA, DefectStatus.CLOSED),
                defect(equipmentB, DefectStatus.CLOSED)));
        when(stockMovementRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                issue(warehouseA, departmentA, sparePartId, currentMonth, 2, new BigDecimal("50.00"), null),
                issue(warehouseA, departmentA, sparePartId, previousMonth, 3, null, 10.0),
                issue(warehouseB, departmentB, sparePartId, currentMonth, 4, null, null)));

        var result = service.overview(null);

        assertThat(result.counters().activeEmergencyRequests()).isEqualTo(1);
        assertThat(result.counters().emergencyRequests()).isEqualTo(1);
        assertThat(result.counters().totalEmergencyRequests()).isEqualTo(2);
        assertThat(result.counters().repairsThisMonth()).isEqualTo(1);
        assertThat(result.counters().completedOrClosedWorkOrders()).isEqualTo(3);
        assertThat(result.counters().completedRepairs()).isEqualTo(2);
        assertThat(result.counters().closedWorkOrders()).isEqualTo(1);
        assertThat(result.kpis().downtimeHoursTotal()).isEqualTo(3.5);
        assertThat(result.kpis().downtimeEventsCount()).isEqualTo(3);
        assertThat(result.kpis().downtimeThisMonth()).isEqualTo(2.5);
        assertThat(result.counters().totalSparePartsCost()).isEqualByComparingTo("130.00");
        assertThat(result.counters().sparePartsCostThisMonth()).isEqualByComparingTo("100.00");

        assertThat(result.problemDepartments()).hasSize(2);
        assertThat(result.problemDepartments().getFirst().departmentId()).isEqualTo(departmentA);
        assertThat(result.problemDepartments().getFirst().downtimeHours()).isEqualTo(3.0);
        assertThat(result.problemDepartments().getFirst().downtimeEvents()).isEqualTo(2);
        assertThat(result.problemDepartments().getFirst().emergencyCount()).isEqualTo(2);
        assertThat(result.problemDepartments().getFirst().repairCount()).isEqualTo(1);

        assertThat(result.topProblemEquipment()).hasSize(2);
        assertThat(result.topProblemEquipment().getFirst().id()).isEqualTo(equipmentA);
        assertThat(result.topProblemEquipment().getFirst().failureCount()).isEqualTo(2);
        assertThat(result.topProblemEquipment().getFirst().openDefects()).isEqualTo(1);
        assertThat(result.topProblemEquipment().getFirst().downtimeHours()).isEqualTo(3.0);
    }

    @Test
    void emergencyEventsDeduplicateLinkedRepairRequestWorkOrderAndDowntime() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-03T04:00:00Z");

        RepairRequest request = request(departmentId, PriorityLevel.EMERGENCY, RequestStatus.CLOSED);
        request.setNumber("RR-1");
        request.setTitle("Emergency request");
        request.setEquipmentId(equipmentId);
        request.setDetectedAt(now.minus(Duration.ofHours(3)));

        WorkOrder linkedWorkOrder = workOrder(
                departmentId, equipmentId, WorkType.REPAIR, WorkOrderStatus.CLOSED, now.minus(Duration.ofHours(2)));
        linkedWorkOrder.setType(WorkOrderType.EMERGENCY);
        linkedWorkOrder.setRepairRequestId(request.getId());
        linkedWorkOrder.setNumber("WO-LINKED");

        DowntimeEvent linkedDowntime = downtime(departmentId, equipmentId, now.minus(Duration.ofHours(1)), 60);
        linkedDowntime.setType(DowntimeType.EMERGENCY);
        linkedDowntime.setWorkOrderId(linkedWorkOrder.getId());

        WorkOrder standaloneWorkOrder = workOrder(
                departmentId, equipmentId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, now.minus(Duration.ofMinutes(30)));
        standaloneWorkOrder.setType(WorkOrderType.EMERGENCY);
        standaloneWorkOrder.setNumber("WO-STANDALONE");
        standaloneWorkOrder.setTitle("Standalone work order");

        DowntimeEvent standaloneDowntime = downtime(departmentId, equipmentId, now, 30);
        standaloneDowntime.setType(DowntimeType.EMERGENCY);
        standaloneDowntime.setDescription("Standalone downtime");

        when(repairRequestRepository.search(null, null, null)).thenReturn(List.of(request));
        when(workOrderRepository.search(null, null, null))
                .thenReturn(List.of(linkedWorkOrder, standaloneWorkOrder));
        when(downtimeEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(linkedDowntime, standaloneDowntime));

        var overview = service.overview(null);
        var events = service.emergencyEvents(null, 0, 10, null, null);

        assertThat(overview.counters().totalEmergencyRequests()).isEqualTo(3);
        assertThat(events.getTotalElements()).isEqualTo(3);
        assertThat(events.getContent()).extracting(DashboardEmergencyEventDto::eventKey)
                .containsExactlyInAnyOrder(
                        "rr:" + request.getId(),
                        "wo:" + standaloneWorkOrder.getId(),
                        "dt:" + standaloneDowntime.getId());
        assertThat(events.getContent()).extracting(DashboardEmergencyEventDto::sourceType)
                .containsExactlyInAnyOrder("REPAIR_REQUEST", "WORK_ORDER", "DOWNTIME");
    }

    @Test
    void emergencyEventsApplySourceDepartmentAndSearchFilters() {
        UUID departmentA = UUID.randomUUID();
        UUID departmentB = UUID.randomUUID();
        UUID equipmentA = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-03T05:00:00Z");

        RepairRequest pumpRequest = request(departmentA, PriorityLevel.EMERGENCY, RequestStatus.OPEN);
        pumpRequest.setNumber("RR-PUMP");
        pumpRequest.setTitle("Pump fire alarm");
        pumpRequest.setEquipmentId(equipmentA);
        pumpRequest.setDetectedAt(now);


        when(repairRequestRepository.search(null, departmentA, null)).thenReturn(List.of(pumpRequest));
        when(workOrderRepository.search(null, departmentA, null)).thenReturn(List.of());
        when(downtimeEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                emergencyDowntime(departmentA, equipmentA, now.minus(Duration.ofMinutes(5)), "Pump downtime"),
                emergencyDowntime(departmentB, UUID.randomUUID(), now, "Other department downtime")
        ));

        var repairRequestEvents = service.emergencyEvents(departmentA, 0, 10, "repair_request", "pump");
        var downtimeEvents = service.emergencyEvents(departmentA, 0, 10, "DOWNTIME", null);

        assertThat(repairRequestEvents.getTotalElements()).isEqualTo(1);
        assertThat(repairRequestEvents.getContent().getFirst().sourceType()).isEqualTo("REPAIR_REQUEST");
        assertThat(repairRequestEvents.getContent().getFirst().departmentId()).isEqualTo(departmentA);
        assertThat(downtimeEvents.getTotalElements()).isEqualTo(1);
        assertThat(downtimeEvents.getContent().getFirst().title()).isEqualTo("Pump downtime");
    }

    @Test
    void departmentScopeUsesConsumptionDepartmentBeforeWarehouseDepartment() {
        UUID consumingDepartment = UUID.randomUUID();
        UUID warehouseDepartment = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        SparePart sparePart = new SparePart();
        sparePart.setId(sparePartId);
        sparePart.setAverageCost(new BigDecimal("8.00"));
        when(warehouseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(warehouse(warehouseId, warehouseDepartment)));
        when(sparePartRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(sparePart));
        when(stockMovementRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                issue(warehouseId, consumingDepartment, sparePartId, Instant.now(), 2, null, null)));

        var result = service.overview(consumingDepartment);

        assertThat(result.counters().totalSparePartsCost()).isEqualByComparingTo("16.00");
    }

    @Test
    void overviewUsesRepairRequestsAsReliabilityDowntimeWhenExplicitDowntimeEventsAreMissing() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Instant now = Instant.now();
        Equipment equipment = equipment(equipmentId, departmentId, "Pump A");
        equipment.setCreatedAt(now.minus(Duration.ofHours(100)));
        RepairRequest closedFailure = request(departmentId, PriorityLevel.HIGH, RequestStatus.CLOSED);
        closedFailure.setEquipmentId(equipmentId);
        closedFailure.setDetectedAt(now.minus(Duration.ofHours(10)));
        closedFailure.setActualCompletionAt(now.minus(Duration.ofHours(5)));

        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(equipment));
        when(repairRequestRepository.search(null, null, null)).thenReturn(List.of(closedFailure));

        var result = service.overview(null);

        assertThat(result.kpis().downtimeHoursTotal()).isCloseTo(5.0, within(0.05));
        assertThat(result.kpis().downtimeEventsCount()).isEqualTo(1);
        assertThat(result.kpis().mttrAverage()).isCloseTo(5.0, within(0.05));
        assertThat(result.kpis().mtbfAverage()).isCloseTo(95.0, within(0.1));
    }

    @Test
    void overviewCountsCanonicalEmergencyAndActiveRequestCounters() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        RepairRequest openEmergency = request(departmentId, PriorityLevel.EMERGENCY, RequestStatus.OPEN);
        RepairRequest assignedRequest = request(departmentId, PriorityLevel.HIGH, RequestStatus.ASSIGNED);
        RepairRequest completedRequest = request(departmentId, PriorityLevel.HIGH, RequestStatus.COMPLETED);
        WorkOrder emergencyWorkOrder = workOrder(
                departmentId,
                equipmentId,
                WorkType.REPAIR,
                WorkOrderStatus.IN_PROGRESS,
                null);
        emergencyWorkOrder.setType(WorkOrderType.EMERGENCY);

        when(repairRequestRepository.search(null, null, null))
                .thenReturn(List.of(openEmergency, assignedRequest, completedRequest));
        when(workOrderRepository.search(null, null, null)).thenReturn(List.of(emergencyWorkOrder));

        var result = service.overview(null);

        assertThat(result.counters().openRequests()).isEqualTo(1);
        assertThat(result.counters().activeRepairRequests()).isEqualTo(2);
        assertThat(result.counters().activeEmergencyRequests()).isEqualTo(1);
        assertThat(result.counters().totalEmergencyRequests()).isEqualTo(2);
    }

    @Test
    void overviewUsesCanonicalWarehouseStatsForCounters() {
        when(warehouseStockRepository.getSparePartsWarehouseStats(null, null, null, null))
                .thenReturn(warehouseStats(10L, 4L, 3L, 25.0));

        var result = service.overview(null);

        assertThat(result.counters().activeReservations()).isEqualTo(4);
        assertThat(result.counters().lowStockItems()).isEqualTo(3);
    }

    @Test
    void overviewDoesNotTreatPostponedPprTasksAsOverdueByDueDate() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                equipment(equipmentId, departmentId, "Pump A")));
        com.toir.entity.PprTask postponed = new com.toir.entity.PprTask();
        postponed.setId(UUID.randomUUID());
        postponed.setEquipmentId(equipmentId);
        postponed.setStatus(com.toir.enums.PprTaskStatus.POSTPONED);
        postponed.setDueDate(LocalDateTime.now().minusDays(1));
        com.toir.entity.PprTask overdue = new com.toir.entity.PprTask();
        overdue.setId(UUID.randomUUID());
        overdue.setEquipmentId(equipmentId);
        overdue.setStatus(com.toir.enums.PprTaskStatus.OVERDUE);

        when(pprTaskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(postponed, overdue));

        var result = service.overview(null);

        assertThat(result.counters().overduePpr()).isEqualTo(1);
    }

    private Instant monthStart() {
        return LocalDate.now(ZoneId.of("Asia/Tashkent"))
                .withDayOfMonth(1)
                .atStartOfDay(ZoneId.of("Asia/Tashkent"))
                .toInstant();
    }

    private Department department(UUID id, String name) {
        Department department = new Department();
        department.setId(id);
        department.setName(name);
        department.setCode(name.replace(" ", "-"));
        return department;
    }

    private Equipment equipment(UUID id, UUID departmentId, String name) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setDepartmentId(departmentId);
        equipment.setCode(name.replace(" ", "-"));
        equipment.setName(name);
        equipment.setCreatedAt(monthStart().minus(Duration.ofDays(30)));
        return equipment;
    }

    private Warehouse warehouse(UUID id, UUID departmentId) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setDepartmentId(departmentId);
        warehouse.setCode("WH");
        warehouse.setName("Warehouse");
        return warehouse;
    }

    private RepairRequest request(UUID departmentId, PriorityLevel priority, RequestStatus status) {
        RepairRequest request = new RepairRequest();
        request.setId(UUID.randomUUID());
        request.setDepartmentId(departmentId);
        request.setEquipmentId(UUID.randomUUID());
        request.setPriority(priority);
        request.setStatus(status);
        return request;
    }

    private WorkOrder workOrder(
            UUID departmentId,
            UUID equipmentId,
            WorkType workType,
            WorkOrderStatus status,
            Instant completedAt
    ) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(UUID.randomUUID());
        workOrder.setDepartmentId(departmentId);
        workOrder.setEquipmentId(equipmentId);
        workOrder.setWorkType(workType);
        workOrder.setType(WorkOrderType.DEFECT);
        workOrder.setStatus(status);
        workOrder.setCompletedAt(completedAt);
        return workOrder;
    }

    private SparePartsWarehouseStatsProjection warehouseStats(
            Long nomenclature,
            Long activeReservations,
            Long lowStockItems,
            Double issuedToWork
    ) {
        return new SparePartsWarehouseStatsProjection() {
            @Override
            public Long getNomenclature() {
                return nomenclature;
            }

            @Override
            public Long getActiveReservations() {
                return activeReservations;
            }

            @Override
            public Long getLowStockItems() {
                return lowStockItems;
            }

            @Override
            public Double getIssuedToWork() {
                return issuedToWork;
            }
        };
    }

    private DowntimeEvent downtime(
            UUID departmentId,
            UUID equipmentId,
            Instant startAt,
            int durationMinutes
    ) {
        DowntimeEvent event = new DowntimeEvent();
        event.setId(UUID.randomUUID());
        event.setDepartmentId(departmentId);
        event.setEquipmentId(equipmentId);
        event.setStartAt(startAt);
        event.setDurationMinutes(durationMinutes);
        event.setType(DowntimeType.UNPLANNED);
        return event;
    }

    private DowntimeEvent emergencyDowntime(
            UUID departmentId,
            UUID equipmentId,
            Instant startAt,
            String description
    ) {
        DowntimeEvent event = downtime(departmentId, equipmentId, startAt, 30);
        event.setType(DowntimeType.EMERGENCY);
        event.setDescription(description);
        return event;
    }

    private DowntimeEvent downtimeWithInterval(
            UUID departmentId,
            UUID equipmentId,
            Instant startAt,
            int durationMinutes
    ) {
        DowntimeEvent event = downtime(departmentId, equipmentId, startAt, 0);
        event.setDurationMinutes(null);
        event.setEndAt(startAt.plusSeconds(durationMinutes * 60L));
        return event;
    }

    private Defect defect(UUID equipmentId, DefectStatus status) {
        Defect defect = new Defect();
        defect.setId(UUID.randomUUID());
        defect.setEquipmentId(equipmentId);
        defect.setStatus(status);
        return defect;
    }

    private StockMovement issue(
            UUID warehouseId,
            UUID departmentId,
            UUID sparePartId,
            Instant occurredAt,
            double quantity,
            BigDecimal totalAmount,
            Double unitCost
    ) {
        StockMovement movement = new StockMovement();
        movement.setId(UUID.randomUUID());
        movement.setWarehouseId(warehouseId);
        movement.setDepartmentId(departmentId);
        movement.setSparePartId(sparePartId);
        movement.setType(StockMovementType.ISSUE);
        movement.setOccurredAt(occurredAt);
        movement.setQuantity(quantity);
        movement.setTotalAmount(totalAmount);
        movement.setUnitCost(unitCost);
        return movement;
    }
}
