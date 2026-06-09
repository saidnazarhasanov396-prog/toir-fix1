package com.toir.service;

import com.toir.dto.equipmentcost.EquipmentCostDrilldownResponse;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.ActualCostStatus;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentCostDrilldownServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;
    @Mock
    ActualCostRepository actualCostRepository;
    @Mock
    WorkOrderRepository workOrderRepository;
    @Mock
    RepairRequestRepository repairRequestRepository;
    @Mock
    ContractorWorkRepository contractorWorkRepository;
    @Mock
    CostCategoryRepository costCategoryRepository;
    @Mock
    FinanceScopeService financeScopeService;

    @InjectMocks
    EquipmentCostDrilldownService service;

    @Test
    void returnsGroupedTotalsForLaborMaterialAndContractorCosts() {
        UUID equipmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID contractorWorkId = UUID.randomUUID();
        UUID laborCategoryId = UUID.randomUUID();
        UUID materialCategoryId = UUID.randomUUID();
        UUID contractorCategoryId = UUID.randomUUID();

        WorkOrder workOrder = workOrder(workOrderId, equipmentId, "WO-001", "Pump repair");
        ContractorWork contractorWork = contractorWork(contractorWorkId, workOrderId);
        ActualCost labor = cost(UUID.randomUUID(), 120, ActualCostStatus.APPROVED,
                ActualCostSourceType.LABOR_ENTRY, UUID.randomUUID(), workOrderId, null, null,
                laborCategoryId, "2026-06-01T10:00:00Z");
        ActualCost material = cost(UUID.randomUUID(), 80, ActualCostStatus.APPROVED,
                ActualCostSourceType.MATERIAL_ISSUE, UUID.randomUUID(), workOrderId, null, null,
                materialCategoryId, "2026-06-02T10:00:00Z");
        ActualCost contractor = cost(UUID.randomUUID(), 300, ActualCostStatus.APPROVED,
                ActualCostSourceType.CONTRACTOR_WORK, contractorWorkId, null, null, contractorWorkId,
                contractorCategoryId, "2026-06-03T10:00:00Z");

        stubCosts(equipmentId, List.of(labor, material, contractor), List.of(workOrder), List.of(), List.of(contractorWork),
                List.of(category(laborCategoryId, "LABOR", "Labor"),
                        category(materialCategoryId, "MAT", "Materials"),
                        category(contractorCategoryId, "CTR", "Contractor")));

        EquipmentCostDrilldownResponse result = service.getDrilldown(equipmentId, null, null, null, null);

        assertThat(result.totalAmount()).isEqualTo(500);
        assertThat(result.approvedAmount()).isEqualTo(500);
        assertThat(result.pendingAmount()).isZero();
        assertThat(result.rejectedAmount()).isZero();
        assertThat(result.byCategory()).extracting(EquipmentCostDrilldownResponse.Bucket::key)
                .containsExactlyInAnyOrder(laborCategoryId.toString(), materialCategoryId.toString(), contractorCategoryId.toString());
        assertThat(result.bySourceType()).extracting(EquipmentCostDrilldownResponse.Bucket::key)
                .containsExactlyInAnyOrder("LABOR_ENTRY", "MATERIAL_ISSUE", "CONTRACTOR_WORK");
        assertThat(result.byMonth()).singleElement().satisfies(month -> {
            assertThat(month.key()).isEqualTo("2026-06");
            assertThat(month.approvedAmount()).isEqualTo(500);
        });
        assertThat(result.rows()).hasSize(3);
        assertThat(result.rows()).anySatisfy(row -> {
            assertThat(row.sourceType()).isEqualTo(ActualCostSourceType.CONTRACTOR_WORK);
            assertThat(row.contractorWorkId()).isEqualTo(contractorWorkId);
            assertThat(row.workOrderId()).isEqualTo(workOrderId);
            assertThat(row.sourceDisplay()).contains("Contractor work");
        });
    }

    @Test
    void keepsPendingAndRejectedVisibleButOutsideApprovedTotals() {
        UUID equipmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        ActualCost approved = cost(UUID.randomUUID(), 100, ActualCostStatus.APPROVED,
                ActualCostSourceType.WORK_ORDER, workOrderId, workOrderId, null, null, categoryId, "2026-05-01T00:00:00Z");
        ActualCost pending = cost(UUID.randomUUID(), 40, ActualCostStatus.PENDING,
                ActualCostSourceType.WORK_ORDER, workOrderId, workOrderId, null, null, categoryId, "2026-05-02T00:00:00Z");
        ActualCost rejected = cost(UUID.randomUUID(), 25, ActualCostStatus.REJECTED,
                ActualCostSourceType.WORK_ORDER, workOrderId, workOrderId, null, null, categoryId, "2026-05-03T00:00:00Z");

        stubCosts(equipmentId, List.of(approved, pending, rejected), List.of(workOrder(workOrderId, equipmentId, "WO-002", "Service")),
                List.of(), List.of(), List.of(category(categoryId, "SRV", "Service")));

        EquipmentCostDrilldownResponse result = service.getDrilldown(equipmentId, null, null, null, null);

        assertThat(result.totalAmount()).isEqualTo(165);
        assertThat(result.approvedAmount()).isEqualTo(100);
        assertThat(result.pendingAmount()).isEqualTo(40);
        assertThat(result.rejectedAmount()).isEqualTo(25);
        assertThat(result.byCategory()).singleElement().satisfies(bucket -> {
            assertThat(bucket.totalAmount()).isEqualTo(165);
            assertThat(bucket.approvedAmount()).isEqualTo(100);
            assertThat(bucket.pendingAmount()).isEqualTo(40);
            assertThat(bucket.rejectedAmount()).isEqualTo(25);
        });
        assertThat(result.rows()).extracting(EquipmentCostDrilldownResponse.Row::status)
                .containsExactlyInAnyOrder(ActualCostStatus.APPROVED, ActualCostStatus.PENDING, ActualCostStatus.REJECTED);
    }

    @Test
    void excludesCostsLinkedToOtherEquipment() {
        UUID equipmentId = UUID.randomUUID();
        UUID otherEquipmentId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID includedWorkOrderId = UUID.randomUUID();
        UUID excludedWorkOrderId = UUID.randomUUID();
        ActualCost included = cost(UUID.randomUUID(), 90, ActualCostStatus.APPROVED,
                ActualCostSourceType.WORK_ORDER, includedWorkOrderId, includedWorkOrderId, null, null, categoryId, "2026-04-01T00:00:00Z");
        ActualCost excluded = cost(UUID.randomUUID(), 900, ActualCostStatus.APPROVED,
                ActualCostSourceType.WORK_ORDER, excludedWorkOrderId, excludedWorkOrderId, null, null, categoryId, "2026-04-01T00:00:00Z");

        stubCosts(equipmentId, List.of(included, excluded),
                List.of(workOrder(includedWorkOrderId, equipmentId, "WO-IN", "Included"),
                        workOrder(excludedWorkOrderId, otherEquipmentId, "WO-OUT", "Excluded")),
                List.of(), List.of(), List.of(category(categoryId, "CAT", "Category")));

        EquipmentCostDrilldownResponse result = service.getDrilldown(equipmentId, null, null, null, null);

        assertThat(result.approvedAmount()).isEqualTo(90);
        assertThat(result.rows()).singleElement().satisfies(row -> assertThat(row.actualCostId()).isEqualTo(included.getId()));
    }

    @Test
    void filtersByDateRangeCategoryAndSourceType() {
        UUID equipmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID includedCategoryId = UUID.randomUUID();
        UUID excludedCategoryId = UUID.randomUUID();
        ActualCost included = cost(UUID.randomUUID(), 70, ActualCostStatus.APPROVED,
                ActualCostSourceType.WORK_ORDER, workOrderId, workOrderId, null, null,
                includedCategoryId, "2026-03-15T09:00:00Z");
        ActualCost outsideDate = cost(UUID.randomUUID(), 20, ActualCostStatus.APPROVED,
                ActualCostSourceType.WORK_ORDER, workOrderId, workOrderId, null, null,
                includedCategoryId, "2026-04-01T09:00:00Z");
        ActualCost outsideCategory = cost(UUID.randomUUID(), 30, ActualCostStatus.APPROVED,
                ActualCostSourceType.WORK_ORDER, workOrderId, workOrderId, null, null,
                excludedCategoryId, "2026-03-16T09:00:00Z");
        ActualCost outsideSource = cost(UUID.randomUUID(), 40, ActualCostStatus.APPROVED,
                ActualCostSourceType.LABOR_ENTRY, UUID.randomUUID(), workOrderId, null, null,
                includedCategoryId, "2026-03-17T09:00:00Z");

        stubCosts(equipmentId, List.of(included, outsideDate, outsideCategory, outsideSource),
                List.of(workOrder(workOrderId, equipmentId, "WO-003", "Filtered")),
                List.of(), List.of(),
                List.of(category(includedCategoryId, "INC", "Included")));

        EquipmentCostDrilldownResponse result = service.getDrilldown(
                equipmentId,
                LocalDate.parse("2026-03-01"),
                LocalDate.parse("2026-03-31"),
                includedCategoryId,
                ActualCostSourceType.WORK_ORDER
        );

        assertThat(result.rows()).singleElement().satisfies(row -> assertThat(row.actualCostId()).isEqualTo(included.getId()));
        assertThat(result.totalAmount()).isEqualTo(70);
    }

    @Test
    void rejectsInvalidDateRange() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.existsByIdAndIsDeletedFalse(equipmentId)).thenReturn(true);

        assertThatThrownBy(() -> service.getDrilldown(
                equipmentId,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-03-31"),
                null,
                null
        ))
                .hasMessage("from must be on or before to");
    }

    private void stubCosts(UUID equipmentId,
                           List<ActualCost> costs,
                           List<WorkOrder> workOrders,
                           List<RepairRequest> repairRequests,
                           List<ContractorWork> contractorWorks,
                           List<CostCategory> categories) {
        when(equipmentRepository.existsByIdAndIsDeletedFalse(equipmentId)).thenReturn(true);
        when(actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(costs);
        when(financeScopeService.filterActualCosts(costs)).thenReturn(costs);
        if (!workOrders.isEmpty()) {
            when(workOrderRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(workOrders);
        }
        if (!repairRequests.isEmpty()) {
            when(repairRequestRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(repairRequests);
        }
        if (!contractorWorks.isEmpty()) {
            when(contractorWorkRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(contractorWorks);
        }
        if (!categories.isEmpty()) {
            when(costCategoryRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(categories);
        }
    }

    private ActualCost cost(UUID id,
                            double amount,
                            ActualCostStatus status,
                            ActualCostSourceType sourceType,
                            UUID sourceId,
                            UUID workOrderId,
                            UUID repairRequestId,
                            UUID contractorWorkId,
                            UUID categoryId,
                            String costDate) {
        ActualCost cost = new ActualCost();
        cost.setId(id);
        cost.setAmount(amount);
        cost.setStatus(status);
        cost.setSourceType(sourceType);
        cost.setSourceId(sourceId);
        cost.setWorkOrderId(workOrderId);
        cost.setRepairRequestId(repairRequestId);
        cost.setContractorWorkId(contractorWorkId);
        cost.setCostCategoryId(categoryId);
        cost.setCostDate(Instant.parse(costDate));
        cost.setCreatedAt(Instant.parse(costDate));
        if (status == ActualCostStatus.APPROVED) {
            cost.setReviewedAt(Instant.parse(costDate).plusSeconds(3600));
        }
        return cost;
    }

    private WorkOrder workOrder(UUID id, UUID equipmentId, String number, String title) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setEquipmentId(equipmentId);
        workOrder.setNumber(number);
        workOrder.setTitle(title);
        return workOrder;
    }

    private ContractorWork contractorWork(UUID id, UUID workOrderId) {
        ContractorWork work = new ContractorWork();
        work.setId(id);
        work.setWorkOrderId(workOrderId);
        work.setDescription("Contractor work");
        return work;
    }

    private CostCategory category(UUID id, String code, String name) {
        CostCategory category = new CostCategory();
        category.setId(id);
        category.setCode(code);
        category.setName(name);
        return category;
    }
}
