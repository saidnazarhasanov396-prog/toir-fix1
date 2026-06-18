package com.toir.service;

import com.toir.entity.projects.ProcurementRequest;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.enums.ProcurementRequestType;
import org.springframework.security.access.AccessDeniedException;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseEquipmentItemRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.warehouse.ToirStockService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcurementRequestSearchTest {

    @Mock ProcurementRequestRepository repository;
    @Mock SparePartRepository sparePartRepository;
    @Mock EquipmentTypeRepository equipmentTypeRepository;
    @Mock EquipmentRepository equipmentRepository;
    @Mock WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;
    @Mock DefectRepository defectRepository;
    @Mock PprTaskRepository pprTaskRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock WarehouseStockRepository stockRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock AuditBuilderService auditBuilderService;
    @Mock WarehouseRepository warehouseRepository;
    @Mock ScopeAccessService scopeAccessService;
    @Mock LowStockRecommendationService lowStockRecommendationService;
    @Mock ActualCostRepository actualCostRepository;
    @Mock CostCategoryRepository costCategoryRepository;
    @Mock ToirStockService toirStockService;

    ProcurementRequestService service;

    @BeforeEach
    void setUp() {
        service = new ProcurementRequestService(
                repository,
                sparePartRepository,
                equipmentTypeRepository,
                equipmentRepository,
                warehouseEquipmentItemRepository,
                defectRepository,
                pprTaskRepository,
                departmentRepository,
                stockRepository,
                stockMovementRepository,
                auditBuilderService,
                warehouseRepository,
                scopeAccessService,
                lowStockRecommendationService,
                actualCostRepository,
                costCategoryRepository,
                toirStockService
        );
    }

    @Test
    void adminSearchPassesSearchTermToRepository() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.search("bearing", null, null, null, null, null, null, null))
                .thenReturn(List.of(procurement("Bearing order", "PR-001")));

        var result = service.findAll(null, null, "bearing");

        assertThat(result).hasSize(1);
        verify(repository).search("bearing", null, null, null, null, null, null, null);
    }

    @Test
    void adminSearchWithStatusFilter() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.search(isNull(), eq("DRAFT"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of());

        service.findAll(ProcurementRequestStatus.DRAFT, null, null);

        verify(repository).search(null, "DRAFT", null, null, null, null, null, null);
    }

    @Test
    void adminSearchWithDepartmentFilter() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID deptId = UUID.randomUUID();
        when(repository.search(isNull(), isNull(), eq(deptId), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of());

        service.findAll(null, deptId, null);

        verify(repository).search(null, null, deptId, null, null, null, null, null);
    }

    @Test
    void adminSearchWithTypeAndSourceTraceFilters() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID defectId = UUID.randomUUID();
        UUID pprTaskId = UUID.randomUUID();
        when(repository.search(
                isNull(),
                isNull(),
                isNull(),
                eq("EQUIPMENT"),
                eq(defectId),
                eq(pprTaskId),
                isNull(),
                isNull()
        )).thenReturn(List.of());

        service.findAll(null, null, null, ProcurementRequestType.EQUIPMENT, defectId, pprTaskId);

        verify(repository).search(null, null, null, "EQUIPMENT", defectId, pprTaskId, null, null);
    }

    @Test
    void adminSearchWithMinAmountFilter() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.search(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(100.0), isNull()))
                .thenReturn(List.of());

        service.findAll(null, null, null, null, null, null, 100.0, null);

        verify(repository).search(null, null, null, null, null, null, 100.0, null);
    }

    @Test
    void adminSearchWithMaxAmountFilter() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.search(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(500.0)))
                .thenReturn(List.of());

        service.findAll(null, null, null, null, null, null, null, 500.0);

        verify(repository).search(null, null, null, null, null, null, null, 500.0);
    }

    @Test
    void adminSearchWithAmountRange() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.search(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(100.0), eq(500.0)))
                .thenReturn(List.of());

        service.findAll(null, null, null, null, null, null, 100.0, 500.0);

        verify(repository).search(null, null, null, null, null, null, 100.0, 500.0);
    }

    @Test
    void adminBlankSearchNormalizesToNull() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.search(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of());

        service.findAll(null, null, "   ");

        verify(repository).search(null, null, null, null, null, null, null, null);
    }

    @Test
    void scopedUserWithNoDepartmentAndNoStatusThrowsForbidden() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);

        assertThatThrownBy(() -> service.findAll(null, null, "pump"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void scopedUserWithDepartmentCanSearch() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        UUID deptId = UUID.randomUUID();
        when(scopeAccessService.enforceDepartmentScope(deptId)).thenReturn(deptId);
        when(repository.search(eq("pump"), isNull(), eq(deptId), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of());

        service.findAll(null, deptId, "pump");

        verify(repository).search("pump", null, deptId, null, null, null, null, null);
    }

    @Test
    void scopedUserWithStatusCanSearch() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(repository.search(isNull(), eq("APPROVED"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of());

        service.findAll(ProcurementRequestStatus.APPROVED, null, null);

        verify(repository).search(null, "APPROVED", null, null, null, null, null, null);
    }

    @Test
    void adminSearchBothMinAndMaxAtSameValue() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.search(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(250.0), eq(250.0)))
                .thenReturn(List.of());

        service.findAll(null, null, null, null, null, null, 250.0, 250.0);

        verify(repository).search(null, null, null, null, null, null, 250.0, 250.0);
    }

    private ProcurementRequest procurement(String title, String number) {
        ProcurementRequest pr = new ProcurementRequest();
        pr.setId(UUID.randomUUID());
        pr.setTitle(title);
        pr.setNumber(number);
        pr.setStatus(ProcurementRequestStatus.DRAFT);
        return pr;
    }
}
