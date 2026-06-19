package com.toir.security;

import com.toir.dto.analytics.EquipmentAnalyticsResponse;
import com.toir.entity.ReliabilityMetric;
import com.toir.entity.StockMovement;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.warehouse.Warehouse;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.RequestStatus;
import com.toir.enums.StockMovementType;
import com.toir.enums.WorkOrderStatus;
import com.toir.exception.RestException;
import com.toir.repository.CalibrationRecordRepository;
import com.toir.repository.ConditionReadingRepository;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.ReservationRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.users.UserCertificationRepository;
import com.toir.repository.users.UserRepository;
import com.toir.service.AnalyticsService;
import com.toir.service.DashboardService;
import com.toir.service.warehouse.LegacyStockProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsPbacScopeTest {

    RepairRequestRepository repairRequestRepository;
    DefectRepository defectRepository;
    PprTaskRepository pprTaskRepository;
    WorkOrderRepository workOrderRepository;
    EquipmentRepository equipmentRepository;
    DepartmentRepository departmentRepository;
    WarehouseStockRepository warehouseStockRepository;
    WarehouseRepository warehouseRepository;
    SparePartRepository sparePartRepository;
    StockMovementRepository stockMovementRepository;
    DowntimeEventRepository downtimeEventRepository;
    com.toir.repository.ReliabilityMetricRepository reliabilityMetricRepository;
    ContractorRepository contractorRepository;
    ContractorWorkRepository contractorWorkRepository;
    ReservationRepository reservationRepository;
    ActualCostRepository actualCostRepository;
    ConditionReadingRepository conditionReadingRepository;
    UserCertificationRepository userCertificationRepository;
    CalibrationRecordRepository calibrationRecordRepository;
    MaintenanceDueEventRepository maintenanceDueEventRepository;
    UserRepository userRepository;
    ScopeAccessService scopeAccessService;
    LegacyStockProjectionService legacyStockProjectionService;
    DashboardService dashboardService;
    AnalyticsService analyticsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void setUp() {
        repairRequestRepository = mock(RepairRequestRepository.class);
        defectRepository = mock(DefectRepository.class);
        pprTaskRepository = mock(PprTaskRepository.class);
        workOrderRepository = mock(WorkOrderRepository.class);
        equipmentRepository = mock(EquipmentRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        warehouseStockRepository = mock(WarehouseStockRepository.class);
        warehouseRepository = mock(WarehouseRepository.class);
        sparePartRepository = mock(SparePartRepository.class);
        stockMovementRepository = mock(StockMovementRepository.class);
        downtimeEventRepository = mock(DowntimeEventRepository.class);
        reliabilityMetricRepository = mock(com.toir.repository.ReliabilityMetricRepository.class);
        contractorRepository = mock(ContractorRepository.class);
        contractorWorkRepository = mock(ContractorWorkRepository.class);
        reservationRepository = mock(ReservationRepository.class);
        actualCostRepository = mock(ActualCostRepository.class);
        conditionReadingRepository = mock(ConditionReadingRepository.class);
        userCertificationRepository = mock(UserCertificationRepository.class);
        calibrationRecordRepository = mock(CalibrationRecordRepository.class);
        maintenanceDueEventRepository = mock(MaintenanceDueEventRepository.class);
        userRepository = mock(UserRepository.class);
        scopeAccessService = mock(ScopeAccessService.class);
        legacyStockProjectionService = mock(LegacyStockProjectionService.class);
        when(legacyStockProjectionService.currentAll()).thenReturn(java.util.Map.of());

        dashboardService = new DashboardService(
                repairRequestRepository,
                defectRepository,
                pprTaskRepository,
                workOrderRepository,
                equipmentRepository,
                departmentRepository,
                warehouseStockRepository,
                warehouseRepository,
                sparePartRepository,
                stockMovementRepository,
                downtimeEventRepository,
                reliabilityMetricRepository,
                contractorRepository,
                contractorWorkRepository,
                reservationRepository,
                actualCostRepository,
                conditionReadingRepository,
                userCertificationRepository,
                calibrationRecordRepository,
                maintenanceDueEventRepository,
                userRepository,
                scopeAccessService,
                legacyStockProjectionService
        );
        analyticsService = new AnalyticsService(
                repairRequestRepository,
                defectRepository,
                workOrderRepository,
                pprTaskRepository,
                downtimeEventRepository,
                reliabilityMetricRepository,
                equipmentRepository,
                departmentRepository,
                actualCostRepository,
                scopeAccessService
        );
        stubDashboardEmptyData();
    }

    @Test
    void nonAdminDepartmentDashboardDefaultsToCurrentDepartment() {
        UUID departmentA = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(departmentA);

        dashboardService.overview(null);

        verify(repairRequestRepository).search(null, departmentA, null);
        verify(workOrderRepository).search(null, departmentA, null);
    }

    @Test
    void nonAdminRequestingAnotherDepartmentIsClampedToCurrentDepartment() {
        UUID departmentA = UUID.randomUUID();
        UUID departmentB = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(departmentA);

        dashboardService.overview(departmentB);

        verify(repairRequestRepository).search(null, departmentA, null);
        verify(workOrderRepository).search(null, departmentA, null);
    }

    @Test
    void nonAdminWithoutDepartmentCannotReceiveGlobalDashboardData() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(null);

        assertThatThrownBy(() -> dashboardService.overview(null))
                .isInstanceOf(AccessDeniedException.class);

        verify(repairRequestRepository, never()).search(any(), any(), any());
    }

    @Test
    void systemAdminCanRequestGlobalDashboardData() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);

        dashboardService.overview(null);

        verify(repairRequestRepository).search(null, null, null);
        verify(workOrderRepository).search(null, null, null);
    }

    @Test
    void wildcardCanRequestSpecificDepartmentDashboardData() {
        UUID departmentB = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);

        dashboardService.overview(departmentB);

        verify(repairRequestRepository).search(null, departmentB, null);
        verify(workOrderRepository).search(null, departmentB, null);
    }

    @Test
    void dashboardIncludesMaintenanceDueEventCounts() {
        UUID departmentId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(maintenanceDueEventRepository.countByDueStatusAndDepartment(MaintenanceDueStatus.UPCOMING, departmentId)).thenReturn(2L);
        when(maintenanceDueEventRepository.countByDueStatusAndDepartment(MaintenanceDueStatus.DUE, departmentId)).thenReturn(3L);
        when(maintenanceDueEventRepository.countByDueStatusAndDepartment(MaintenanceDueStatus.OVERDUE, departmentId)).thenReturn(4L);
        when(maintenanceDueEventRepository.countByDueStatusAndDepartment(MaintenanceDueStatus.BLOCKED, departmentId)).thenReturn(5L);
        when(maintenanceDueEventRepository.countByStatusAndDepartment(MaintenanceDueEventStatus.AWAITING_APPROVAL, departmentId)).thenReturn(6L);

        var overview = dashboardService.overview(departmentId);

        assertThat(overview.maintenanceDueCounts().upcoming()).isEqualTo(2);
        assertThat(overview.maintenanceDueCounts().due()).isEqualTo(3);
        assertThat(overview.maintenanceDueCounts().overdue()).isEqualTo(4);
        assertThat(overview.maintenanceDueCounts().blocked()).isEqualTo(5);
        assertThat(overview.maintenanceDueCounts().awaitingApproval()).isEqualTo(6);
    }

    @Test
    void dashboardOpenRequestsCountsOnlyOpenRepairRequestsInScopedDepartment() {
        UUID departmentId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repairRequestRepository.search(null, departmentId, null)).thenReturn(List.of(
                repairRequest(RequestStatus.OPEN),
                repairRequest(RequestStatus.OPEN),
                repairRequest(RequestStatus.IN_PROGRESS)
        ));

        var overview = dashboardService.overview(departmentId);

        assertThat(overview.counters().openRequests()).isEqualTo(2);
        verify(repairRequestRepository).search(null, departmentId, null);
    }

    @Test
    void dashboardThisMonthCountersUseCalendarMonthInsteadOfRollingThirtyDays() {
        UUID departmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Instant currentMonth = LocalDate.now()
                .withDayOfMonth(1)
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();
        Instant previousMonth = LocalDate.now()
                .withDayOfMonth(1)
                .minusDays(1)
                .atTime(12, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(warehouseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(warehouse(warehouseId, departmentId)));
        when(workOrderRepository.search(null, departmentId, null)).thenReturn(List.of(
                closedWorkOrder(currentMonth, departmentId),
                closedWorkOrder(previousMonth, departmentId)
        ));
        when(stockMovementRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                stockIssue(warehouseId, currentMonth, 3),
                stockIssue(warehouseId, previousMonth, 7)
        ));

        var overview = dashboardService.overview(departmentId);

        assertThat(overview.counters().repairsThisMonth()).isEqualTo(1);
        assertThat(overview.counters().materialIssuedThisMonth()).isEqualTo(3);
    }

    @Test
    void adminCanAccessAnyEquipmentAnalytics() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, null);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        authenticateSystemAdmin();

        EquipmentAnalyticsResponse response = analyticsServiceWithRealScope().equipmentAnalytics(equipmentId);

        assertThat(response.equipmentId()).isEqualTo(equipmentId.toString());
        verify(repairRequestRepository).search(null, null, equipmentId);
    }

    @Test
    void analyticsReadUserCanAccessOwnDepartmentEquipmentAnalytics() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentA = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, departmentA);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        authenticateDepartmentUser(departmentA);

        EquipmentAnalyticsResponse response = analyticsServiceWithRealScope().equipmentAnalytics(equipmentId);

        assertThat(response.equipmentId()).isEqualTo(equipmentId.toString());
        verify(repairRequestRepository).search(null, null, equipmentId);
    }

    @Test
    void analyticsReadUserCannotAccessOtherDepartmentEquipmentAnalytics() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentA = UUID.randomUUID();
        UUID departmentB = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, departmentB);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        authenticateDepartmentUser(departmentA);

        assertThatThrownBy(() -> analyticsServiceWithRealScope().equipmentAnalytics(equipmentId))
                .isInstanceOf(AccessDeniedException.class);

        verify(repairRequestRepository, never()).search(any(), any(), eq(equipmentId));
    }

    @Test
    void fakeEquipmentIdReturns404BeforeScopeCheck() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analyticsService.equipmentAnalytics(equipmentId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Equipment not found");

        verify(scopeAccessService, never()).assertCanAccessDepartment(any());
        verify(scopeAccessService, never()).isScopeAdmin();
    }

    @Test
    void equipmentAnalyticsDeniesOutOfScopeEquipment() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentB = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, departmentB);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        org.mockito.Mockito.doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentB);

        assertThatThrownBy(() -> analyticsService.equipmentAnalytics(equipmentId))
                .isInstanceOf(AccessDeniedException.class);

        verify(repairRequestRepository, never()).search(any(), any(), eq(equipmentId));
    }

    @Test
    void equipmentAnalyticsAllowsCurrentDepartmentEquipment() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentA = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, departmentA);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        authenticateDepartmentUser(departmentA);

        EquipmentAnalyticsResponse response = analyticsServiceWithRealScope().equipmentAnalytics(equipmentId);

        assertThat(response.equipmentId()).isEqualTo(equipmentId.toString());
        verify(repairRequestRepository).search(null, null, equipmentId);
    }

    @Test
    void missingEquipmentAnalyticsReturns404BeforeScopeCheck() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analyticsService.equipmentAnalytics(equipmentId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Equipment not found");

        verify(scopeAccessService, never()).assertCanAccessDepartment(any());
    }

    @Test
    void rcaEquipmentAllowsOutsideEquipmentByResponsibleDepartment() {
        UUID equipmentId = UUID.randomUUID();
        UUID responsibleDepartmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, null);
        equipment.setResponsibleDepartmentId(responsibleDepartmentId);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(defectRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of());

        analyticsService.rcaEquipment(equipmentId);

        verify(scopeAccessService).assertCanAccessEquipmentScope(responsibleDepartmentId, null);
    }

    @Test
    void reliabilityListIsFilteredToCurrentDepartment() {
        UUID departmentA = UUID.randomUUID();
        UUID departmentB = UUID.randomUUID();
        UUID equipmentA = UUID.randomUUID();
        UUID equipmentB = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(departmentA);
        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                equipment(equipmentA, departmentA),
                equipment(equipmentB, departmentB)
        ));
        ReliabilityMetric metricA = ReliabilityMetric.builder().equipmentId(equipmentA).build();
        ReliabilityMetric metricB = ReliabilityMetric.builder().equipmentId(equipmentB).build();
        when(reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(metricA, metricB));

        List<ReliabilityMetric> result = analyticsService.reliabilityList();

        assertThat(result).containsExactly(metricA);
    }

    private void stubDashboardEmptyData() {
        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(departmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(warehouseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(sparePartRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(userRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(repairRequestRepository.search(any(), any(), any())).thenReturn(List.of());
        when(pprTaskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(workOrderRepository.search(any(), any(), any())).thenReturn(List.of());
        when(reservationRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(any())).thenReturn(List.of());
        when(warehouseStockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(stockMovementRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(actualCostRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(any())).thenReturn(List.of());
        when(contractorWorkRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(conditionReadingRepository.countBySeveritiesAndDepartment(any(), any())).thenReturn(0L);
        when(userCertificationRepository.findAllByExpiresAtBeforeAndIsDeletedFalse(any())).thenReturn(List.of());
        when(calibrationRecordRepository.findAllByNextDueAtBeforeAndIsDeletedFalse(any())).thenReturn(List.of());
        when(reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(downtimeEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(contractorRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
    }

    private Equipment equipment(UUID id, UUID departmentId) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("EQ-1");
        equipment.setName("Pump");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(departmentId);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }

    private RepairRequest repairRequest(RequestStatus status) {
        RepairRequest request = new RepairRequest();
        request.setId(UUID.randomUUID());
        request.setNumber("RR-" + UUID.randomUUID());
        request.setTitle("Pump issue");
        request.setDescription("Pump issue");
        request.setDepartmentId(UUID.randomUUID());
        request.setEquipmentId(UUID.randomUUID());
        request.setReporterId(UUID.randomUUID());
        request.setStatus(status);
        request.setCreatedAt(Instant.parse("2026-06-10T00:00:00Z"));
        request.setUpdatedAt(Instant.parse("2026-06-10T01:00:00Z"));
        request.setDetectedAt(Instant.parse("2026-06-10T00:00:00Z"));
        return request;
    }

    private WorkOrder closedWorkOrder(Instant completedAt, UUID departmentId) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(UUID.randomUUID());
        workOrder.setDepartmentId(departmentId);
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setNumber("WO-" + UUID.randomUUID());
        workOrder.setTitle("Repair");
        workOrder.setStatus(WorkOrderStatus.CLOSED);
        workOrder.setCompletedAt(completedAt);
        return workOrder;
    }

    private StockMovement stockIssue(UUID warehouseId, Instant occurredAt, double quantity) {
        StockMovement movement = new StockMovement();
        movement.setId(UUID.randomUUID());
        movement.setWarehouseId(warehouseId);
        movement.setSparePartId(UUID.randomUUID());
        movement.setType(StockMovementType.ISSUE);
        movement.setOccurredAt(occurredAt);
        movement.setQuantity(quantity);
        return movement;
    }

    private Warehouse warehouse(UUID id, UUID departmentId) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setCode("WH-1");
        warehouse.setName("Warehouse");
        warehouse.setDepartmentId(departmentId);
        return warehouse;
    }

    private AnalyticsService analyticsServiceWithRealScope() {
        return new AnalyticsService(
                repairRequestRepository,
                defectRepository,
                workOrderRepository,
                pprTaskRepository,
                downtimeEventRepository,
                reliabilityMetricRepository,
                equipmentRepository,
                departmentRepository,
                actualCostRepository,
                new ScopeAccessService(mock(com.toir.repository.users.EmployeeRepository.class))
        );
    }

    private void authenticateDepartmentUser(UUID departmentId) {
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID().toString(),
                "department-user",
                "department-user@example.com",
                "Department User",
                departmentId.toString(),
                "WORKSHOP_HEAD",
                List.of(PermissionConstants.ANALYTICS_READ)
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(new SimpleGrantedAuthority(PermissionConstants.ANALYTICS_READ))
                )
        );
    }

    private void authenticateSystemAdmin() {
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID().toString(),
                "system-admin",
                "system-admin@example.com",
                "System Admin",
                UUID.randomUUID().toString(),
                "SYSTEM_ADMIN",
                List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(new SimpleGrantedAuthority("SYSTEM_ADMIN"))
                )
        );
    }
}
