package com.toir.service.repair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.repairrequest.RepairRequestCostKind;
import com.toir.dto.repairrequest.RepairRequestCostsSummaryDto;
import com.toir.entity.LaborEntry;
import com.toir.entity.SparePart;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.users.User;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.RequestStatus;
import com.toir.repository.AuditLogRepository;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.LaborEntryRepository;
import com.toir.repository.MeterReadingRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.repository.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairRequestInsightsServiceTest {

    @Mock ActualCostRepository actualCostRepository;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock CostCategoryRepository costCategoryRepository;
    @Mock LaborEntryRepository laborEntryRepository;
    @Mock RepairMaterialUsageRepository materialUsageRepository;
    @Mock SparePartRepository sparePartRepository;
    @Mock ContractorWorkRepository contractorWorkRepository;
    @Mock DefectRepository defectRepository;
    @Mock EquipmentMeterRepository equipmentMeterRepository;
    @Mock MeterReadingRepository meterReadingRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock UserRepository userRepository;

    private RepairRequestInsightsService service;

    @BeforeEach
    void setUp() {
        service = new RepairRequestInsightsService(
                actualCostRepository,
                workOrderRepository,
                costCategoryRepository,
                laborEntryRepository,
                materialUsageRepository,
                sparePartRepository,
                contractorWorkRepository,
                defectRepository,
                equipmentMeterRepository,
                meterReadingRepository,
                auditLogRepository,
                userRepository,
                new ObjectMapper()
        );
    }

    @Test
    void costsSummaryAggregatesMappedCostsAndExcludesRejectedAmountsFromTotals() {
        UUID requestId = UUID.randomUUID();
        RepairRequest request = repairRequest(requestId, RequestStatus.COMPLETED);
        ActualCost labor = cost(
                ActualCostSourceType.LABOR_ENTRY,
                ActualCostStatus.APPROVED,
                100_000,
                "Labor entry",
                Instant.parse("2026-07-08T09:00:00Z")
        );
        ActualCost material = cost(
                ActualCostSourceType.MATERIAL_ISSUE,
                ActualCostStatus.PENDING,
                50_000,
                "Bearing issue",
                Instant.parse("2026-07-09T09:00:00Z")
        );
        ActualCost rejected = cost(
                ActualCostSourceType.CONTRACTOR_WORK,
                ActualCostStatus.REJECTED,
                70_000,
                "Rejected contractor invoice",
                Instant.parse("2026-07-07T09:00:00Z")
        );
        when(actualCostRepository.findAllForRepairRequest(requestId))
                .thenReturn(List.of(labor, material, rejected));

        RepairRequestCostsSummaryDto result = service.getCostsSummary(request);

        assertThat(result.requestId()).isEqualTo(requestId);
        assertThat(result.currency()).isEqualTo("UZS");
        assertThat(result.laborCost()).isEqualTo(100_000);
        assertThat(result.materialCost()).isEqualTo(50_000);
        assertThat(result.contractorCost()).isZero();
        assertThat(result.totalCost()).isEqualTo(150_000);
        assertThat(result.rows()).extracting(row -> row.kind())
                .containsExactly(
                        RepairRequestCostKind.MATERIAL,
                        RepairRequestCostKind.LABOR,
                        RepairRequestCostKind.CONTRACTOR
                );
        assertThat(result.rows()).extracting(row -> row.status())
                .containsExactly(
                        ActualCostStatus.PENDING,
                        ActualCostStatus.APPROVED,
                        ActualCostStatus.REJECTED
                );
    }

    @Test
    void costsSummaryUsesCostCategoryForManualRepairRequestCosts() {
        UUID requestId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        RepairRequest request = repairRequest(requestId, RequestStatus.COMPLETED);
        ActualCost manual = cost(
                ActualCostSourceType.REPAIR_REQUEST,
                ActualCostStatus.APPROVED,
                25_000,
                "Manual material adjustment",
                Instant.parse("2026-07-08T09:00:00Z")
        );
        manual.setCostCategoryId(categoryId);
        CostCategory category = new CostCategory();
        category.setId(categoryId);
        category.setCode("MATERIALS");
        category.setName("Materials");
        when(actualCostRepository.findAllForRepairRequest(requestId)).thenReturn(List.of(manual));
        when(costCategoryRepository.findAllByIdInAndIsDeletedFalse(List.of(categoryId)))
                .thenReturn(List.of(category));

        RepairRequestCostsSummaryDto result = service.getCostsSummary(request);

        assertThat(result.rows()).singleElement()
                .extracting(row -> row.kind())
                .isEqualTo(RepairRequestCostKind.MATERIAL);
        assertThat(result.materialCost()).isEqualTo(25_000);
    }

    @Test
    void costsSummaryBuildsHumanReadableLaborAndMaterialLabelsInBatches() {
        UUID requestId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID laborEntryId = UUID.randomUUID();
        UUID materialUsageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        RepairRequest request = repairRequest(requestId, RequestStatus.COMPLETED);

        ActualCost laborCost = cost(
                ActualCostSourceType.LABOR_ENTRY,
                ActualCostStatus.APPROVED,
                100_000,
                null,
                Instant.parse("2026-07-08T09:00:00Z")
        );
        laborCost.setSourceId(laborEntryId);
        laborCost.setWorkOrderId(workOrderId);
        ActualCost materialCost = cost(
                ActualCostSourceType.MATERIAL_ISSUE,
                ActualCostStatus.APPROVED,
                350_000,
                null,
                Instant.parse("2026-07-07T09:00:00Z")
        );
        materialCost.setSourceId(materialUsageId);

        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setNumber("WO-2026-000123");
        LaborEntry laborEntry = new LaborEntry();
        laborEntry.setId(laborEntryId);
        laborEntry.setUserId(userId);
        laborEntry.setHours(4);
        laborEntry.setRate(25_000D);
        User user = new User();
        user.setId(userId);
        user.setFullName("Ivanov I.");
        RepairMaterialUsage materialUsage = new RepairMaterialUsage();
        materialUsage.setId(materialUsageId);
        materialUsage.setSparePartId(sparePartId);
        materialUsage.setQuantity(2);
        SparePart sparePart = new SparePart();
        sparePart.setId(sparePartId);
        sparePart.setName("Bearing 6205");

        when(actualCostRepository.findAllForRepairRequest(requestId))
                .thenReturn(List.of(laborCost, materialCost));
        when(workOrderRepository.findAllByIdInAndIsDeletedFalse(List.of(workOrderId)))
                .thenReturn(List.of(workOrder));
        when(laborEntryRepository.findAllByIdInAndIsDeletedFalse(List.of(laborEntryId)))
                .thenReturn(List.of(laborEntry));
        when(materialUsageRepository.findAllByIdInAndIsDeletedFalse(List.of(materialUsageId)))
                .thenReturn(List.of(materialUsage));
        when(userRepository.findAllByIdInAndIsDeletedFalse(List.of(userId))).thenReturn(List.of(user));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(List.of(sparePartId)))
                .thenReturn(List.of(sparePart));

        RepairRequestCostsSummaryDto result = service.getCostsSummary(request);

        assertThat(result.rows()).extracting(row -> row.sourceLabel())
                .containsExactly("Ivanov I. — 4h @ 25000", "Bearing 6205 × 2");
        assertThat(result.rows().getFirst().workOrderNumber()).isEqualTo("WO-2026-000123");
    }

    private RepairRequest repairRequest(UUID id, RequestStatus status) {
        RepairRequest request = new RepairRequest();
        request.setId(id);
        request.setEquipmentId(UUID.randomUUID());
        request.setStatus(status);
        return request;
    }

    private ActualCost cost(ActualCostSourceType sourceType,
                            ActualCostStatus status,
                            double amount,
                            String notes,
                            Instant costDate) {
        ActualCost cost = new ActualCost();
        cost.setId(UUID.randomUUID());
        cost.setSourceType(sourceType);
        cost.setSourceId(UUID.randomUUID());
        cost.setStatus(status);
        cost.setAmount(amount);
        cost.setNotes(notes);
        cost.setCostDate(costDate);
        return cost;
    }
}
