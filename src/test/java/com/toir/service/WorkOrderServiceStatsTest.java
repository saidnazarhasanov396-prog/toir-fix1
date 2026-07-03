package com.toir.service;

import com.toir.dto.workorder.WorkOrderStatsResponse;
import com.toir.enums.WorkOrderStatus;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.WorkOrderStatsProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceStatsTest {

    @Mock
    WorkOrderRepository repository;

    // WorkOrderService has many other deps — mock them all
    @Mock com.toir.repository.equipment.EquipmentRepository equipmentRepository;
    @Mock com.toir.repository.department.DepartmentRepository departmentRepository;
    @Mock com.toir.util.AuditBuilderService auditBuilderService;
    @Mock com.toir.repository.PprPlanRepository pprPlanRepository;
    @Mock com.toir.repository.PprTaskRepository pprTaskRepository;
    @Mock com.toir.repository.repair.RepairRequestRepository repairRequestRepository;
    @Mock com.toir.repository.defects.DefectRepository defectRepository;
    @Mock com.toir.repository.WorkExecutionRepository workExecutionRepository;
    @Mock com.toir.repository.repair.RepairMaterialUsageRepository repairMaterialUsageRepository;
    @Mock com.toir.repository.WarehouseRepository warehouseRepository;
    @Mock com.toir.repository.WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;
    @Mock WarehouseEquipmentItemService warehouseEquipmentItemService;
    @Mock com.toir.service.repair.RepairMaterialUsageService repairMaterialUsageService;
    @Mock com.toir.service.maintanance.WorkOrderSparePartRequirementService workOrderSparePartRequirementService;

    @InjectMocks
    WorkOrderService service;

    @Test
    void getStatsWithNoFiltersReturnsMappedResponse() {
        WorkOrderStatsProjection projection = mockProjection(10L, 6L, 4L, 1L);
        when(repository.getWorkOrderStats(isNull(), eq(false), isNull(), isNull(), isNull()))
                .thenReturn(projection);

        WorkOrderStatsResponse stats = service.getStats(null, null, null, null);

        assertThat(stats.totalOrders()).isEqualTo(10);
        assertThat(stats.openOrders()).isEqualTo(6);
        assertThat(stats.completedOrders()).isEqualTo(4);
        assertThat(stats.overdueOrders()).isEqualTo(1);
        verify(repository).getWorkOrderStats(null, false, null, null, null);
    }

    @Test
    void getStatsWithStatusFilterPassesStatusName() {
        WorkOrderStatsProjection projection = mockProjection(3L, 3L, 0L, 0L);
        when(repository.getWorkOrderStats(eq("APPROVED"), eq(false), isNull(), isNull(), isNull()))
                .thenReturn(projection);

        service.getStats(WorkOrderStatus.APPROVED, null, null, null);

        verify(repository).getWorkOrderStats("APPROVED", false, null, null, null);
    }

    @Test
    void getStatsWithDepartmentAndEquipmentFilters() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        WorkOrderStatsProjection projection = mockProjection(2L, 1L, 1L, 0L);
        when(repository.getWorkOrderStats(isNull(), eq(false), eq(departmentId), eq(equipmentId), isNull()))
                .thenReturn(projection);

        service.getStats(null, departmentId, equipmentId, null);

        verify(repository).getWorkOrderStats(null, false, departmentId, equipmentId, null);
    }

    @Test
    void getStatsWithBlankSearchPassesNullToRepository() {
        WorkOrderStatsProjection projection = mockProjection(5L, 5L, 0L, 0L);
        when(repository.getWorkOrderStats(isNull(), eq(false), isNull(), isNull(), isNull()))
                .thenReturn(projection);

        service.getStats(null, null, null, "   ");

        verify(repository).getWorkOrderStats(null, false, null, null, null);
    }

    @Test
    void getStatsHandlesNullProjectionValuesGracefully() {
        WorkOrderStatsProjection projection = mockProjection(null, null, null, null);
        when(repository.getWorkOrderStats(isNull(), eq(false), isNull(), isNull(), isNull()))
                .thenReturn(projection);

        WorkOrderStatsResponse stats = service.getStats(null, null, null, null);

        assertThat(stats.totalOrders()).isZero();
        assertThat(stats.openOrders()).isZero();
        assertThat(stats.completedOrders()).isZero();
        assertThat(stats.overdueOrders()).isZero();
    }

    @Test
    void getStatsWithCompletedOrClosedScopePassesCompletedScopeFlag() {
        WorkOrderStatsProjection projection = mockProjection(2L, 0L, 2L, 0L);
        when(repository.getWorkOrderStats(isNull(), eq(true), isNull(), isNull(), isNull()))
                .thenReturn(projection);

        WorkOrderStatsResponse stats = service.getStats(null, "COMPLETED_OR_CLOSED", null, null, null);

        assertThat(stats.totalOrders()).isEqualTo(2);
        assertThat(stats.openOrders()).isZero();
        assertThat(stats.completedOrders()).isEqualTo(2);
        assertThat(stats.overdueOrders()).isZero();
        verify(repository).getWorkOrderStats(null, true, null, null, null);
    }

    @Test
    void getStatsWithUnsupportedStatusScopeThrowsBadRequest() {
        assertThatThrownBy(() -> service.getStats(null, "UNKNOWN", null, null, null))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Unsupported work order statusScope: UNKNOWN");

        verifyNoInteractions(repository);
    }

    private WorkOrderStatsProjection mockProjection(Long total, Long open, Long completed, Long overdue) {
        WorkOrderStatsProjection p = mock(WorkOrderStatsProjection.class);
        when(p.getTotalOrders()).thenReturn(total);
        when(p.getOpenOrders()).thenReturn(open);
        when(p.getCompletedOrders()).thenReturn(completed);
        when(p.getOverdueOrders()).thenReturn(overdue);
        return p;
    }
}
