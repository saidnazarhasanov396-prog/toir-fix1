package com.toir.service;

import com.toir.dto.repaircampaign.RepairCampaignDto;
import com.toir.dto.repaircampaign.RepairCampaignGenerateWorkOrdersRequest;
import com.toir.dto.repaircampaign.RepairCampaignRequest;
import com.toir.dto.repaircampaign.RepairCampaignStageDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.Department;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.repair.RepairCampaign;
import com.toir.entity.repair.RepairCampaignStage;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.BudgetStatus;
import com.toir.enums.RepairCampaignScopeType;
import com.toir.enums.RepairCampaignStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.maintenance.RepairAcceptanceRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.repair.RepairCampaignDepartmentRepository;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.repository.repair.RepairCampaignStageRepository;
import com.toir.service.repair.RepairCampaignService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairCampaignServiceTest {

    @Mock
    private RepairCampaignRepository repository;

    @Mock
    private RepairCampaignStageRepository stageRepository;

    @Mock
    private RepairCampaignDepartmentRepository campaignDepartmentRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private ContractorWorkRepository contractorWorkRepository;

    @Mock
    private ActualCostRepository actualCostRepository;

    @Mock
    private MaintenanceBudgetRepository maintenanceBudgetRepository;

    @Mock
    private BudgetLineRepository budgetLineRepository;

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private RepairAcceptanceRepository repairAcceptanceRepository;

    @Mock
    private WorkOrderService workOrderService;

    @Mock
    private AuditBuilderService auditBuilderService;

    @InjectMocks
    private RepairCampaignService service;

    @Test
    void generateWorkOrdersRejectsDraftCampaign() {
        UUID campaignId = UUID.randomUUID();
        RepairCampaign campaign = campaign(campaignId, null);
        when(repository.findByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service.generateWorkOrders(campaignId, generateRequest(), "generation-1"))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).containsIgnoringCase("approved");
                });

        verify(workOrderService, never()).create(any());
    }

    @Test
    void generateWorkOrdersUsesDeterministicPerEquipmentGenerationKey() throws Exception {
        UUID campaignId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        RepairCampaign campaign = campaign(campaignId, null);
        campaign.setStatus(RepairCampaignStatus.APPROVED);
        campaign.setScopeType(RepairCampaignScopeType.EQUIPMENT_TYPE);
        campaign.setEquipmentTypeId(UUID.randomUUID());
        RepairCampaignStage stage = new RepairCampaignStage();
        stage.setId(stageId);
        stage.setCampaign(campaign);
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setCode("P-1");
        equipment.setName("Pump");
        equipment.setDepartmentId(UUID.randomUUID());
        when(repository.findByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));
        when(stageRepository.findByIdAndIsDeletedFalse(stageId)).thenReturn(Optional.of(stage));
        when(equipmentRepository.findAllForMaintenanceRegulations(campaign.getEquipmentTypeId()))
                .thenReturn(List.of(equipment));

        service.generateWorkOrders(campaignId, generateRequest(stageId), "generation-1");

        ArgumentCaptor<WorkOrderRequest> requestCaptor = ArgumentCaptor.forClass(WorkOrderRequest.class);
        verify(workOrderService).create(requestCaptor.capture());
        Object generationKey = WorkOrderRequest.class.getMethod("generationKey")
                .invoke(requestCaptor.getValue());
        assertThat(generationKey).isEqualTo("RC:" + campaignId + ":" + stageId + ":" + equipmentId);
    }

    @Test
    void generateWorkOrdersReturnsExistingCanonicalOrderOnReplay() {
        GenerationFixture fixture = generationFixture();
        String key = "RC:" + fixture.campaignId() + ":" + fixture.stageId() + ":" + fixture.equipmentId();
        WorkOrder existing = new WorkOrder();
        existing.setId(UUID.randomUUID());
        existing.setGenerationKey(key);
        com.toir.dto.workorder.WorkOrderDto existingDto = workOrderDto(existing.getId());
        when(workOrderRepository.findByGenerationKeyAndIsDeletedFalse(key)).thenReturn(Optional.of(existing));
        when(workOrderService.findById(existing.getId())).thenReturn(existingDto);

        List<com.toir.dto.workorder.WorkOrderDto> result = service.generateWorkOrders(
                fixture.campaignId(), generateRequest(fixture.stageId()), "generation-1");

        assertThat(result).hasSize(1);
        InOrder order = inOrder(workOrderRepository, workOrderService);
        order.verify(workOrderRepository).lockGenerationKey(key);
        order.verify(workOrderRepository).findByGenerationKeyAndIsDeletedFalse(key);
        order.verify(workOrderService).findById(existing.getId());
        verify(workOrderService, never()).create(any());
    }

    @Test
    void generateWorkOrdersLocksBeforeCanonicalLookupAndCreate() {
        GenerationFixture fixture = generationFixture();
        String key = "RC:" + fixture.campaignId() + ":" + fixture.stageId() + ":" + fixture.equipmentId();
        when(workOrderService.create(any())).thenReturn(workOrderDto(UUID.randomUUID()));

        service.generateWorkOrders(fixture.campaignId(), generateRequest(fixture.stageId()), "generation-1");

        InOrder order = inOrder(workOrderRepository, workOrderService);
        order.verify(workOrderRepository).lockGenerationKey(key);
        order.verify(workOrderRepository).findByGenerationKeyAndIsDeletedFalse(key);
        order.verify(workOrderService).create(any());
    }

    @Test
    void generateWorkOrdersProcessesEquipmentInDeterministicUuidOrder() {
        GenerationFixture fixture = generationFixture();
        Equipment first = fixture.equipment();
        first.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        Equipment second = new Equipment();
        second.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        second.setCode("P-2");
        second.setName("Pump 2");
        second.setDepartmentId(UUID.randomUUID());
        when(equipmentRepository.findAllForMaintenanceRegulations(fixture.campaign().getEquipmentTypeId()))
                .thenReturn(List.of(second, first));
        when(workOrderService.create(any())).thenReturn(workOrderDto(UUID.randomUUID()));

        service.generateWorkOrders(fixture.campaignId(), generateRequest(fixture.stageId()), "generation-1");

        String prefix = "RC:" + fixture.campaignId() + ":" + fixture.stageId() + ":";
        InOrder order = inOrder(workOrderRepository);
        order.verify(workOrderRepository).lockGenerationKey(prefix + first.getId());
        order.verify(workOrderRepository).findByGenerationKeyAndIsDeletedFalse(prefix + first.getId());
        order.verify(workOrderRepository).lockGenerationKey(prefix + second.getId());
        order.verify(workOrderRepository).findByGenerationKeyAndIsDeletedFalse(prefix + second.getId());
    }

    @Test
    void generateWorkOrdersDoesNotReturnPartialSuccessWhenSecondCreateFails() {
        GenerationFixture fixture = generationFixture();
        Equipment second = new Equipment();
        second.setId(UUID.randomUUID());
        second.setCode("P-2");
        second.setName("Pump 2");
        second.setDepartmentId(UUID.randomUUID());
        when(equipmentRepository.findAllForMaintenanceRegulations(fixture.campaign().getEquipmentTypeId()))
                .thenReturn(List.of(fixture.equipment(), second));
        when(workOrderService.create(any()))
                .thenReturn(workOrderDto(UUID.randomUUID()))
                .thenThrow(new IllegalStateException("second create failed"));

        assertThatThrownBy(() -> service.generateWorkOrders(
                fixture.campaignId(), generateRequest(fixture.stageId()), "generation-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("second create failed");

        verify(workOrderService, org.mockito.Mockito.times(2)).create(any());
    }

    @Test
    void generateWorkOrdersRejectsBlankIdempotencyKey() {
        UUID campaignId = UUID.randomUUID();

        assertThatThrownBy(() -> service.generateWorkOrders(campaignId, generateRequest(UUID.randomUUID()), "  "))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Idempotency-Key");
                });

        verify(repository, never()).findByIdAndIsDeletedFalse(any());
        verify(workOrderService, never()).create(any());
    }

    private RepairCampaignGenerateWorkOrdersRequest generateRequest() {
        return generateRequest(UUID.randomUUID());
    }

    private RepairCampaignGenerateWorkOrdersRequest generateRequest(UUID stageId) {
        return new RepairCampaignGenerateWorkOrdersRequest(
                stageId,
                null,
                List.of(),
                null,
                null,
                null,
                null
        );
    }

    private GenerationFixture generationFixture() {
        UUID campaignId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        RepairCampaign campaign = campaign(campaignId, null);
        campaign.setStatus(RepairCampaignStatus.APPROVED);
        campaign.setScopeType(RepairCampaignScopeType.EQUIPMENT_TYPE);
        campaign.setEquipmentTypeId(UUID.randomUUID());
        RepairCampaignStage stage = new RepairCampaignStage();
        stage.setId(stageId);
        stage.setCampaign(campaign);
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setCode("P-1");
        equipment.setName("Pump");
        equipment.setDepartmentId(UUID.randomUUID());
        when(repository.findByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));
        when(stageRepository.findByIdAndIsDeletedFalse(stageId)).thenReturn(Optional.of(stage));
        when(equipmentRepository.findAllForMaintenanceRegulations(campaign.getEquipmentTypeId()))
                .thenReturn(List.of(equipment));
        return new GenerationFixture(campaignId, stageId, equipmentId, campaign, equipment);
    }

    private com.toir.dto.workorder.WorkOrderDto workOrderDto(UUID id) {
        return new com.toir.dto.workorder.WorkOrderDto(
                id, "WO-1", "Generated", UUID.randomUUID(), UUID.randomUUID(), null, null,
                null, null, null, null, null, null, WorkOrderStatus.DRAFT,
                com.toir.enums.WorkOrderType.OVERHAUL, com.toir.enums.WorkType.REPAIR,
                com.toir.enums.PriorityLevel.MEDIUM, null, null, null, null, null, null, null,
                null, null, null, null, null, List.of(), null, null, 0, 0
        );
    }

    private record GenerationFixture(
            UUID campaignId,
            UUID stageId,
            UUID equipmentId,
            RepairCampaign campaign,
            Equipment equipment
    ) {
    }

    @Test
    void findAllFilteredAppliesFiltersCorrectly() {
        UUID departmentId = UUID.randomUUID();
        Department dept = new Department();
        dept.setName("Maintenance");

        RepairCampaign c1 = new RepairCampaign();
        c1.setStatus(RepairCampaignStatus.DRAFT);
        c1.setDepartmentId(departmentId);
        c1.setStartDate(LocalDate.of(2026, 1, 1));
        c1.setEndDate(LocalDate.of(2026, 12, 31));
        c1.setStages(List.of());

        when(repository.findAllFiltered(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "DRAFT",
                "%annual%"
        )).thenReturn(List.of(c1));
        List<RepairCampaignDto> results = service.findAllFiltered(
                "annual",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                RepairCampaignStatus.DRAFT
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).startDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(results.get(0).endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(results.get(0).status()).isEqualTo(RepairCampaignStatus.DRAFT);
    }

    @Test
    void createRejectsClientProvidedCode() {
        RepairCampaignRequest request = new RepairCampaignRequest(
                "RC-CLIENT",
                "Annual Repair",
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1),
                1000,
                null,
                null,
                List.of(),
                null,
                null
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("code is generated by backend");
                });

        verify(repository, never()).save(any());
    }

    @Test
    void createEquipmentTypeCampaignRequiresEquipmentTypeId() {
        RepairCampaignRequest request = new RepairCampaignRequest(
                null,
                "Pump type overhaul",
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1),
                1000,
                RepairCampaignScopeType.EQUIPMENT_TYPE,
                null,
                List.of(),
                null,
                null
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("equipmentTypeId");
                });

        verify(repository, never()).save(any());
    }

    @Test
    void createCrossDepartmentCampaignRequiresParticipants() {
        RepairCampaignRequest request = new RepairCampaignRequest(
                null,
                "Shutdown overhaul",
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1),
                1000,
                RepairCampaignScopeType.CROSS_DEPARTMENT,
                null,
                List.of(),
                null,
                null
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("participant department");
                });

        verify(repository, never()).save(any());
    }

    @Test
    void createStoresLinkedMaintenanceBudgetWhenCompatible() {
        UUID budgetId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        MaintenanceBudget budget = budget(budgetId, 2026, departmentId, BudgetStatus.DRAFT, 10_000, 0);
        when(maintenanceBudgetRepository.findByIdAndIsDeletedFalse(budgetId)).thenReturn(Optional.of(budget));
        when(repository.maxSequenceByCodePrefix(anyString())).thenReturn(0L);
        when(repository.existsByCodeAndIsDeletedFalse(anyString())).thenReturn(false);
        when(repository.save(any(RepairCampaign.class))).thenAnswer(invocation -> {
            RepairCampaign campaign = invocation.getArgument(0);
            campaign.setId(UUID.randomUUID());
            return campaign;
        });

        RepairCampaignDto result = service.create(new RepairCampaignRequest(
                null,
                "Annual Repair",
                departmentId,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1),
                1000,
                RepairCampaignScopeType.DEPARTMENT,
                null,
                List.of(),
                null,
                null,
                budgetId
        ));

        assertThat(result.maintenanceBudgetId()).isEqualTo(budgetId);
        assertThat(result.budgetPlanned()).isEqualTo(10_000);
        assertThat(result.budgetRemaining()).isEqualTo(10_000);
    }

    @Test
    void createRejectsMaintenanceBudgetFromDifferentDepartment() {
        UUID budgetId = UUID.randomUUID();
        MaintenanceBudget budget = budget(budgetId, 2026, UUID.randomUUID(), BudgetStatus.DRAFT, 10_000, 0);
        when(maintenanceBudgetRepository.findByIdAndIsDeletedFalse(budgetId)).thenReturn(Optional.of(budget));

        assertThatThrownBy(() -> service.create(new RepairCampaignRequest(
                null,
                "Annual Repair",
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1),
                1000,
                RepairCampaignScopeType.DEPARTMENT,
                null,
                List.of(),
                null,
                null,
                budgetId
        )))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("department");
                });

        verify(repository, never()).save(any());
    }

    @Test
    void addStageLinksBudgetLineFromCampaignBudget() {
        UUID campaignId = UUID.randomUUID();
        UUID budgetId = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        RepairCampaign campaign = campaign(campaignId, budgetId);
        BudgetLine line = budgetLine(budgetLineId, budget(budgetId, 2026, UUID.randomUUID(), BudgetStatus.APPROVED, 5000, 1200));
        when(repository.findByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)).thenReturn(Optional.of(line));
        when(stageRepository.save(any(RepairCampaignStage.class))).thenAnswer(invocation -> {
            RepairCampaignStage stage = invocation.getArgument(0);
            stage.setId(UUID.randomUUID());
            return stage;
        });
        when(repository.save(any(RepairCampaign.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RepairCampaignStageDto result = service.addStage(campaignId, new RepairCampaignStageDto(
                null,
                1,
                "Preparation",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 10),
                700,
                0,
                RepairCampaignStatus.DRAFT,
                null,
                0,
                0,
                0,
                0,
                budgetLineId,
                0,
                0,
                0
        ));

        assertThat(result.budgetLineId()).isEqualTo(budgetLineId);
        assertThat(result.budgetLinePlanned()).isEqualTo(5000);
        assertThat(result.budgetLineRemaining()).isEqualTo(3800);
    }

    @Test
    void addStageRejectsBudgetLineFromAnotherMaintenanceBudget() {
        UUID campaignId = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        RepairCampaign campaign = campaign(campaignId, UUID.randomUUID());
        BudgetLine line = budgetLine(budgetLineId, budget(UUID.randomUUID(), 2026, UUID.randomUUID(), BudgetStatus.APPROVED, 5000, 0));
        when(repository.findByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));
        when(budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)).thenReturn(Optional.of(line));

        assertThatThrownBy(() -> service.addStage(campaignId, new RepairCampaignStageDto(
                null,
                1,
                "Preparation",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 10),
                700,
                0,
                RepairCampaignStatus.DRAFT,
                null,
                0,
                0,
                0,
                0,
                budgetLineId,
                0,
                0,
                0
        )))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("maintenance budget");
                });

        verify(stageRepository, never()).save(any());
    }

    @Test
    void attachWorkOrderInheritsStageBudgetLine() {
        UUID campaignId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID budgetId = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        RepairCampaign campaign = campaign(campaignId, budgetId);
        RepairCampaignStage stage = new RepairCampaignStage();
        stage.setId(stageId);
        stage.setCampaign(campaign);
        stage.setStartDate(LocalDate.of(2026, 1, 1));
        stage.setEndDate(LocalDate.of(2026, 1, 31));
        stage.setBudgetLineId(budgetLineId);
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setType(com.toir.enums.WorkOrderType.OVERHAUL);

        when(stageRepository.findByIdAndIsDeletedFalse(stageId)).thenReturn(Optional.of(stage));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workOrderService.findById(workOrderId)).thenReturn(null);

        service.attachWorkOrder(campaignId, stageId, workOrderId);

        assertThat(workOrder.getRepairCampaignId()).isEqualTo(campaignId);
        assertThat(workOrder.getRepairCampaignStageId()).isEqualTo(stageId);
        assertThat(workOrder.getBudgetLineId()).isEqualTo(budgetLineId);
    }

    @Test
    void completeStageRejectsActiveLinkedWorkOrders() {
        UUID stageId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        RepairCampaign campaign = new RepairCampaign();
        campaign.setId(campaignId);
        campaign.setStatus(RepairCampaignStatus.IN_PROGRESS);

        RepairCampaignStage stage = new RepairCampaignStage();
        stage.setId(stageId);
        stage.setCampaign(campaign);

        WorkOrder activeOrder = new WorkOrder();
        activeOrder.setId(UUID.randomUUID());
        activeOrder.setStatus(WorkOrderStatus.IN_PROGRESS);

        when(stageRepository.findByIdAndIsDeletedFalse(stageId)).thenReturn(Optional.of(stage));
        when(workOrderRepository.findAllByRepairCampaignStageIdAndIsDeletedFalseOrderByUpdatedAtDesc(stageId))
                .thenReturn(List.of(activeOrder));

        assertThatThrownBy(() -> service.completeStage(campaignId, stageId))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("active work order");
                });
    }

    @Test
    void closeBlocksApprovedOrPendingUnallocatedActualCosts() {
        UUID campaignId = UUID.randomUUID();
        RepairCampaign campaign = campaign(campaignId, UUID.randomUUID());
        campaign.setStatus(RepairCampaignStatus.COMPLETED);
        WorkOrder closedOrder = new WorkOrder();
        closedOrder.setId(UUID.randomUUID());
        closedOrder.setStatus(WorkOrderStatus.CLOSED);
        ActualCost approvedUnallocated = new ActualCost();
        approvedUnallocated.setId(UUID.randomUUID());
        approvedUnallocated.setWorkOrderId(closedOrder.getId());
        approvedUnallocated.setStatus(ActualCostStatus.APPROVED);
        approvedUnallocated.setCostCategoryId(UUID.randomUUID());
        approvedUnallocated.setAmount(100);

        when(repository.findByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));
        when(workOrderRepository.findAllByRepairCampaignIdAndIsDeletedFalseOrderByUpdatedAtDesc(campaignId))
                .thenReturn(List.of(closedOrder));
        when(contractorWorkRepository.findAllByWorkOrderIdInAndIsDeletedFalse(List.of(closedOrder.getId())))
                .thenReturn(List.of());
        when(actualCostRepository.findAllByWorkOrderIdInAndIsDeletedFalseOrderByUpdatedAtDesc(List.of(closedOrder.getId())))
                .thenReturn(List.of(approvedUnallocated));

        assertThatThrownBy(() -> service.close(campaignId))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("not allocated");
                });

        verify(repository, never()).save(campaign);
    }

    private RepairCampaign campaign(UUID id, UUID maintenanceBudgetId) {
        RepairCampaign campaign = new RepairCampaign();
        campaign.setId(id);
        campaign.setStatus(RepairCampaignStatus.DRAFT);
        campaign.setMaintenanceBudgetId(maintenanceBudgetId);
        campaign.setStartDate(LocalDate.of(2026, 1, 1));
        campaign.setEndDate(LocalDate.of(2026, 12, 31));
        campaign.setStages(new java.util.ArrayList<>());
        return campaign;
    }

    private MaintenanceBudget budget(
            UUID id,
            int year,
            UUID departmentId,
            BudgetStatus status,
            double totalPlanned,
            double totalActual
    ) {
        MaintenanceBudget budget = new MaintenanceBudget();
        budget.setId(id);
        budget.setYear(year);
        budget.setDepartmentId(departmentId);
        budget.setStatus(status);
        budget.setTotalPlanned(totalPlanned);
        budget.setTotalActual(totalActual);
        return budget;
    }

    private BudgetLine budgetLine(UUID id, MaintenanceBudget budget) {
        BudgetLine line = new BudgetLine();
        line.setId(id);
        line.setBudget(budget);
        line.setCostCategoryId(UUID.randomUUID());
        line.setPlannedAmount(budget.getTotalPlanned());
        line.setActualAmount(budget.getTotalActual());
        return line;
    }
}
