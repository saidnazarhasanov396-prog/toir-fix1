package com.toir.service.repair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.repairrequest.RepairRequestCostKind;
import com.toir.dto.repairrequest.RepairRequestCloseReadinessDto;
import com.toir.dto.repairrequest.RepairRequestCloseReadinessItemDto;
import com.toir.dto.repairrequest.RepairRequestCostsSummaryDto;
import com.toir.dto.repairrequest.RepairRequestTimelineEventDto;
import com.toir.dto.repairrequest.RepairRequestTimelineEventType;
import com.toir.entity.AuditLog;
import com.toir.entity.LaborEntry;
import com.toir.entity.SparePart;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.equipment.MeterReading;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.users.User;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.AuditAction;
import com.toir.enums.CloseReadinessGroupStatus;
import com.toir.enums.DefectStatus;
import com.toir.enums.RequestStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WarrantyHandling;
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
import static org.mockito.Mockito.verifyNoInteractions;
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
        materialUsage.setQuantity(java.math.BigDecimal.valueOf(2));
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

    @Test
    void costsSummaryReturnsZerosWithoutRunningEmptyBatchQueries() {
        RepairRequest request = repairRequest(UUID.randomUUID(), RequestStatus.OPEN);
        when(actualCostRepository.findAllForRepairRequest(request.getId())).thenReturn(List.of());

        RepairRequestCostsSummaryDto result = service.getCostsSummary(request);

        assertThat(result.totalCost()).isZero();
        assertThat(result.rows()).isEmpty();
        verifyNoInteractions(
                workOrderRepository,
                costCategoryRepository,
                laborEntryRepository,
                materialUsageRepository,
                sparePartRepository,
                contractorWorkRepository,
                userRepository
        );
    }

    @Test
    void closeReadinessBlocksOpenRecordsAndReportsNonBlockingWarnings() {
        RepairRequest request = repairRequest(UUID.randomUUID(), RequestStatus.IN_PROGRESS);
        request.setWarrantyActiveAtCreation(true);
        request.setTargetCompletionAt(Instant.now().minusSeconds(60));
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(UUID.randomUUID());
        workOrder.setStatus(WorkOrderStatus.IN_PROGRESS);
        Defect defect = new Defect();
        defect.setId(UUID.randomUUID());
        defect.setStatus(DefectStatus.OPEN);
        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(UUID.randomUUID());
        meter.setEquipmentId(request.getEquipmentId());
        meter.setActive(true);

        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(request.getId()))
                .thenReturn(List.of(workOrder));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(request.getId()))
                .thenReturn(List.of(defect));
        when(equipmentMeterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(request.getEquipmentId()))
                .thenReturn(List.of(meter));
        when(meterReadingRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByReadAtDesc(request.getId()))
                .thenReturn(List.of());

        RepairRequestCloseReadinessDto result = service.getCloseReadiness(request);

        assertThat(result.ready()).isFalse();
        assertThat(result.isOverdue()).isTrue();
        assertThat(result.reactionOverdue()).isFalse();
        assertThat(result.blockers()).extracting(RepairRequestCloseReadinessItemDto::code)
                .containsExactly("REQUEST_NOT_COMPLETED", "OPEN_WORK_ORDERS", "OPEN_DEFECTS");
        assertThat(result.warnings()).extracting(RepairRequestCloseReadinessItemDto::code)
                .containsExactly("WARRANTY_DECISION_PENDING", "METER_READINGS_MISSING", "TARGET_COMPLETION_OVERDUE");
        assertThat(result.groups())
                .containsEntry("workOrders", CloseReadinessGroupStatus.BLOCKED)
                .containsEntry("defects", CloseReadinessGroupStatus.BLOCKED)
                .containsEntry("warranty", CloseReadinessGroupStatus.WARNING)
                .containsEntry("meterReadings", CloseReadinessGroupStatus.WARNING)
                .containsEntry("sla", CloseReadinessGroupStatus.WARNING);
    }

    @Test
    void closeReadinessRequiresAtLeastOneLinkedWorkOrder() {
        RepairRequest request = repairRequest(UUID.randomUUID(), RequestStatus.COMPLETED);
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(request.getId()))
                .thenReturn(List.of());
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(request.getId()))
                .thenReturn(List.of());
        when(equipmentMeterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(request.getEquipmentId()))
                .thenReturn(List.of());
        when(meterReadingRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByReadAtDesc(request.getId()))
                .thenReturn(List.of());

        RepairRequestCloseReadinessDto result = service.getCloseReadiness(request);

        assertThat(result.ready()).isFalse();
        assertThat(result.blockers()).extracting(RepairRequestCloseReadinessItemDto::code)
                .containsExactly("NO_LINKED_WORK_ORDERS");
    }

    @Test
    void closeReadinessIsReadyWhenBlockingRulesPassAndCompletionSuppressesOverdueWarning() {
        RepairRequest request = repairRequest(UUID.randomUUID(), RequestStatus.COMPLETED);
        request.setWarrantyActiveAtCreation(true);
        request.setWarrantyHandling(WarrantyHandling.NO_WARRANTY_ISSUE);
        request.setTargetCompletionAt(Instant.parse("2026-07-01T09:00:00Z"));
        request.setActualCompletionAt(Instant.parse("2026-07-02T09:00:00Z"));
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(UUID.randomUUID());
        workOrder.setStatus(WorkOrderStatus.COMPLETED);
        Defect defect = new Defect();
        defect.setId(UUID.randomUUID());
        defect.setStatus(DefectStatus.RESOLVED);
        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(UUID.randomUUID());
        MeterReading reading = new MeterReading();
        reading.setId(UUID.randomUUID());
        reading.setMeterId(meter.getId());

        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(request.getId()))
                .thenReturn(List.of(workOrder));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(request.getId()))
                .thenReturn(List.of(defect));
        when(equipmentMeterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(request.getEquipmentId()))
                .thenReturn(List.of(meter));
        when(meterReadingRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByReadAtDesc(request.getId()))
                .thenReturn(List.of(reading));

        RepairRequestCloseReadinessDto result = service.getCloseReadiness(request);

        assertThat(result.ready()).isTrue();
        assertThat(result.isOverdue()).isFalse();
        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.groups().values()).containsOnly(CloseReadinessGroupStatus.READY);
    }

    @Test
    void timelineCuratesAuditEventsAndResolvesActorNamesOldestFirst() {
        RepairRequest request = repairRequest(UUID.randomUUID(), RequestStatus.CLOSED);
        request.setCreatedAt(Instant.parse("2026-07-01T09:00:00Z"));
        UUID actorId = UUID.randomUUID();
        AuditLog assigned = audit(
                "Заявка назначена исполнителю",
                AuditAction.UPDATE,
                "{\"status\":\"ASSIGNED\"}",
                Instant.parse("2026-07-01T10:00:00Z"),
                actorId
        );
        AuditLog closed = audit(
                "Закрыта заявка",
                AuditAction.CLOSE,
                "{\"status\":\"CLOSED\"}",
                Instant.parse("2026-07-02T10:00:00Z"),
                actorId
        );
        User actor = new User();
        actor.setId(actorId);
        actor.setFullName("Ivanov I.");

        when(auditLogRepository.findRepairRequestTimelineAudits(request.getId().toString()))
                .thenReturn(List.of(assigned, closed));
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(request.getId()))
                .thenReturn(List.of());
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(request.getId()))
                .thenReturn(List.of());
        when(meterReadingRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByReadAtDesc(request.getId()))
                .thenReturn(List.of());
        when(userRepository.findAllByIdInAndIsDeletedFalse(List.of(actorId))).thenReturn(List.of(actor));

        List<RepairRequestTimelineEventDto> result = service.getTimeline(request);

        assertThat(result).extracting(RepairRequestTimelineEventDto::type)
                .containsExactly(
                        RepairRequestTimelineEventType.CREATED,
                        RepairRequestTimelineEventType.ASSIGNED,
                        RepairRequestTimelineEventType.CLOSED
                );
        assertThat(result.get(1).actorName()).isEqualTo("Ivanov I.");
        assertThat(result.get(1).fromStatus()).isEqualTo("OPEN");
        assertThat(result.get(1).toStatus()).isEqualTo("ASSIGNED");
        assertThat(result.get(2).fromStatus()).isEqualTo("ASSIGNED");
        assertThat(result.get(2).toStatus()).isEqualTo("CLOSED");
    }

    @Test
    void timelineAddsLinkedRecordsAndEnrichedMeterReadings() {
        RepairRequest request = repairRequest(UUID.randomUUID(), RequestStatus.IN_PROGRESS);
        request.setCreatedAt(Instant.parse("2026-07-01T08:00:00Z"));
        Defect defect = new Defect();
        defect.setId(UUID.randomUUID());
        defect.setCode("DEF-2026-0001");
        defect.setTitle("Bearing noise");
        defect.setCreatedAt(Instant.parse("2026-07-01T09:00:00Z"));
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(UUID.randomUUID());
        workOrder.setNumber("WO-2026-000123");
        workOrder.setTitle("Replace bearing");
        workOrder.setCreatedAt(Instant.parse("2026-07-01T10:00:00Z"));
        UUID actorId = UUID.randomUUID();
        UUID meterId = UUID.randomUUID();
        MeterReading reading = new MeterReading();
        reading.setId(UUID.randomUUID());
        reading.setMeterId(meterId);
        reading.setValue(123.5);
        reading.setReadAt(Instant.parse("2026-07-01T11:00:00Z"));
        reading.setRecordedByUserId(actorId);
        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(meterId);
        meter.setName("Operating hours");
        meter.setUnit("h");
        User actor = new User();
        actor.setId(actorId);
        actor.setFullName("Petrov P.");

        when(auditLogRepository.findRepairRequestTimelineAudits(request.getId().toString()))
                .thenReturn(List.of());
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(request.getId()))
                .thenReturn(List.of(defect));
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(request.getId()))
                .thenReturn(List.of(workOrder));
        when(meterReadingRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByReadAtDesc(request.getId()))
                .thenReturn(List.of(reading));
        when(equipmentMeterRepository.findAllByIdInAndIsDeletedFalse(List.of(meterId)))
                .thenReturn(List.of(meter));
        when(userRepository.findAllByIdInAndIsDeletedFalse(List.of(actorId))).thenReturn(List.of(actor));

        List<RepairRequestTimelineEventDto> result = service.getTimeline(request);

        assertThat(result).extracting(RepairRequestTimelineEventDto::type)
                .containsExactly(
                        RepairRequestTimelineEventType.CREATED,
                        RepairRequestTimelineEventType.DEFECT_LINKED,
                        RepairRequestTimelineEventType.WORK_ORDER_LINKED,
                        RepairRequestTimelineEventType.METER_READING
                );
        assertThat(result.get(1).targetType()).isEqualTo("DEFECT");
        assertThat(result.get(1).targetId()).isEqualTo(defect.getId());
        assertThat(result.get(2).targetType()).isEqualTo("WORK_ORDER");
        assertThat(result.get(2).targetId()).isEqualTo(workOrder.getId());
        assertThat(result.get(3).message()).isEqualTo("Operating hours: 123.5 h");
        assertThat(result.get(3).actorName()).isEqualTo("Petrov P.");
    }

    @Test
    void timelineClassifiesClarificationWarrantyAndRejectionEvenWithMalformedSnapshot() {
        RepairRequest request = repairRequest(UUID.randomUUID(), RequestStatus.REJECTED);
        request.setCreatedAt(Instant.parse("2026-07-01T08:00:00Z"));
        AuditLog clarification = audit(
                "Заявка требует уточнения",
                AuditAction.UPDATE,
                "{\"status\":\"NEEDS_CLARIFICATION\"}",
                Instant.parse("2026-07-01T09:00:00Z"),
                null
        );
        AuditLog warranty = audit(
                "Warranty decision recorded: CONTACT_SUPPLIER",
                AuditAction.UPDATE,
                "not-json",
                Instant.parse("2026-07-01T10:00:00Z"),
                null
        );
        AuditLog rejected = audit(
                "Заявка отклонена",
                AuditAction.CANCEL,
                "{\"status\":\"REJECTED\"}",
                Instant.parse("2026-07-01T11:00:00Z"),
                null
        );
        when(auditLogRepository.findRepairRequestTimelineAudits(request.getId().toString()))
                .thenReturn(List.of(clarification, warranty, rejected));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(request.getId()))
                .thenReturn(List.of());
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(request.getId()))
                .thenReturn(List.of());
        when(meterReadingRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByReadAtDesc(request.getId()))
                .thenReturn(List.of());

        List<RepairRequestTimelineEventDto> result = service.getTimeline(request);

        assertThat(result).extracting(RepairRequestTimelineEventDto::type)
                .containsExactly(
                        RepairRequestTimelineEventType.CREATED,
                        RepairRequestTimelineEventType.CLARIFICATION_REQUESTED,
                        RepairRequestTimelineEventType.WARRANTY_DECISION,
                        RepairRequestTimelineEventType.REJECTED
                );
        assertThat(result.getLast().fromStatus()).isEqualTo("NEEDS_CLARIFICATION");
        assertThat(result.getLast().toStatus()).isEqualTo("REJECTED");
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

    private AuditLog audit(String message,
                           AuditAction action,
                           String currentSnapshot,
                           Instant createdAt,
                           UUID userId) {
        AuditLog audit = new AuditLog();
        audit.setId(UUID.randomUUID());
        audit.setMessage(message);
        audit.setAction(action);
        audit.setCurrentSnapshot(currentSnapshot);
        audit.setCreatedAt(createdAt);
        audit.setUserId(userId);
        return audit;
    }
}
