package com.toir.service.maintanance;

import com.toir.entity.PprTask;
import com.toir.entity.SparePart;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.MaintenanceOperation;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.entity.maintenance.MaintenanceRegulationSparePartRequirement;
import com.toir.entity.maintenance.MaintenanceTemplate;
import com.toir.entity.maintenance.MaintenanceTemplateSparePartRequirement;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.enums.WorkOrderSparePartRequirementSourceType;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.repository.maintenance.MaintenanceRegulationSparePartRequirementRepository;
import com.toir.repository.maintenance.MaintenanceTemplateSparePartRequirementRepository;
import com.toir.repository.maintenance.WorkOrderSparePartRequirementRepository;
import com.toir.security.ScopeAccessService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.toir.dto.workorder.WorkOrderSparePartRequirementDto;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.enums.WorkOrderSparePartRequirementStatus;
import com.toir.repository.SparePartRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.repository.repair.RepairCampaignMaterialRequirementRepository;
import com.toir.entity.repair.RepairCampaignMaterialRequirement;

@ExtendWith(MockitoExtension.class)
class WorkOrderSparePartRequirementServiceTest {



    @Mock
    private RepairMaterialUsageRepository repairMaterialUsageRepository;
    @Mock
    private SparePartRepository sparePartRepository;

    @Mock
    private WorkOrderSparePartRequirementRepository repository;
    @Mock
    private WorkOrderRepository workOrderRepository;
    @Mock
    private MaintenanceTemplateSparePartRequirementRepository templateRequirementRepository;
    @Mock
    private MaintenanceRegulationSparePartRequirementRepository regulationRequirementRepository;
    @Mock
    private MaintenanceDueEventRepository maintenanceDueEventRepository;
    @Mock
    private MaintenanceRegulationRepository maintenanceRegulationRepository;
    @Mock
    private EquipmentMaintenanceRuleRepository equipmentMaintenanceRuleRepository;
    @Mock
    private PprTaskRepository pprTaskRepository;
    @Mock
    private ScopeAccessService scopeAccessService;
    @Mock
    private RepairCampaignMaterialRequirementRepository campaignMaterialRequirementRepository;

    @InjectMocks
    private WorkOrderSparePartRequirementService service;

    @Test
    void syncFromCampaignWorkItemIsReplaySafeAndPreservesExactQuantity() {
        UUID workOrderId=UUID.randomUUID(),campaignId=UUID.randomUUID(),itemId=UUID.randomUUID(),campaignRequirementId=UUID.randomUUID(),sparePartId=UUID.randomUUID();
        WorkOrder workOrder=workOrder(workOrderId);RepairCampaignMaterialRequirement campaignRequirement=new RepairCampaignMaterialRequirement();campaignRequirement.setId(campaignRequirementId);campaignRequirement.setRepairCampaignId(campaignId);campaignRequirement.setWorkItemId(itemId);campaignRequirement.setSparePartId(sparePartId);campaignRequirement.setRequiredQuantity(new java.math.BigDecimal("2.3456"));campaignRequirement.setCritical(true);
        SparePart sparePart=new SparePart();sparePart.setId(sparePartId);
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(campaignMaterialRequirementRepository.findAllByRepairCampaignIdAndWorkItemIdAndIsDeletedFalseOrderBySparePartIdAsc(campaignId,itemId)).thenReturn(List.of(campaignRequirement));
        when(repository.findByWorkOrderIdAndCampaignRequirementIdAndIsDeletedFalse(workOrderId,campaignRequirementId)).thenReturn(Optional.empty());
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        service.syncFromCampaignWorkItem(workOrderId,campaignId,itemId);
        ArgumentCaptor<WorkOrderSparePartRequirement> saved=ArgumentCaptor.forClass(WorkOrderSparePartRequirement.class);verify(repository).save(saved.capture());assertThat(saved.getValue().getSourceType()).isEqualTo(WorkOrderSparePartRequirementSourceType.REPAIR_CAMPAIGN_WORK_ITEM);assertThat(saved.getValue().getCampaignRequirementId()).isEqualTo(campaignRequirementId);assertThat(saved.getValue().getRequiredQty()).isEqualByComparingTo("2.3456");
        when(repository.findByWorkOrderIdAndCampaignRequirementIdAndIsDeletedFalse(workOrderId,campaignRequirementId)).thenReturn(Optional.of(saved.getValue()));
        service.syncFromCampaignWorkItem(workOrderId,campaignId,itemId);verify(repository,org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void createWorkOrderFromDueEventAddsTemplateSparePartRequirements() {
        UUID workOrderId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId);
        UUID dueEventId = UUID.randomUUID();
        workOrder.setMaintenanceDueEventId(dueEventId);
        MaintenanceDueEvent dueEvent = new MaintenanceDueEvent();
        dueEvent.setTemplateId(templateId);
        MaintenanceTemplateSparePartRequirement templateRequirement = templateRequirement(templateId);

        when(maintenanceDueEventRepository.findByIdAndIsDeletedFalse(dueEventId)).thenReturn(Optional.of(dueEvent));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(templateRequirementRepository.findActiveByTemplateId(templateId)).thenReturn(List.of(templateRequirement));
        when(repository.findByWorkOrderIdAndSourceTypeAndSourceRequirementIdAndIsDeletedFalse(
                workOrderId,
                WorkOrderSparePartRequirementSourceType.TEMPLATE_REQUIRED_SPARE_PART,
                templateRequirement.getId()
        )).thenReturn(Optional.empty());

        service.syncFromWorkOrderContext(workOrder);

        ArgumentCaptor<WorkOrderSparePartRequirement> captor =
                ArgumentCaptor.forClass(WorkOrderSparePartRequirement.class);
        verify(repository).save(captor.capture());
        WorkOrderSparePartRequirement saved = captor.getValue();
        assertThat(saved.getWorkOrder()).isEqualTo(workOrder);
        assertThat(saved.getSourceRequirement()).isEqualTo(templateRequirement);
        assertThat(saved.getTemplate()).isEqualTo(templateRequirement.getTemplate());
        assertThat(saved.getOperation()).isEqualTo(templateRequirement.getOperation());
        assertThat(saved.getSparePart()).isEqualTo(templateRequirement.getSparePart());
        assertThat(saved.getRequiredQty()).isEqualByComparingTo(java.math.BigDecimal.valueOf(templateRequirement.getQuantity()));
        assertThat(saved.getUnit()).isEqualTo(templateRequirement.getUnit());
        assertThat(saved.getCriticality()).isEqualTo(templateRequirement.getCriticality());
        assertThat(saved.getNotes()).isEqualTo(templateRequirement.getNotes());
    }

    @Test
    void createWorkOrderWithoutTemplateDoesNotAddRequirements() {
        WorkOrder workOrder = workOrder(UUID.randomUUID());

        service.syncFromWorkOrderContext(workOrder);

        verify(templateRequirementRepository, never()).findActiveByTemplateId(any());
        verify(regulationRequirementRepository, never()).findActiveByRegulationId(any());
        verify(repository, never()).save(any());
    }

    @Test
    void dueEventWithoutTemplateAddsRegulationSparePartRequirements() {
        UUID workOrderId = UUID.randomUUID();
        UUID dueEventId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId);
        workOrder.setMaintenanceDueEventId(dueEventId);
        MaintenanceDueEvent dueEvent = new MaintenanceDueEvent();
        dueEvent.setRegulationId(regulationId);
        MaintenanceRegulationSparePartRequirement regulationRequirement =
                regulationRequirement(regulationId);

        when(maintenanceDueEventRepository.findByIdAndIsDeletedFalse(dueEventId)).thenReturn(Optional.of(dueEvent));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(regulationRequirementRepository.findActiveByRegulationId(regulationId))
                .thenReturn(List.of(regulationRequirement));
        when(repository.findByWorkOrderIdAndSourceTypeAndRegulationRequirementIdAndIsDeletedFalse(
                workOrderId,
                WorkOrderSparePartRequirementSourceType.REGULATION_REQUIRED_SPARE_PART,
                regulationRequirement.getId()
        )).thenReturn(Optional.empty());

        service.syncFromWorkOrderContext(workOrder);

        ArgumentCaptor<WorkOrderSparePartRequirement> captor =
                ArgumentCaptor.forClass(WorkOrderSparePartRequirement.class);
        verify(repository).save(captor.capture());
        WorkOrderSparePartRequirement saved = captor.getValue();
        assertThat(saved.getWorkOrder()).isEqualTo(workOrder);
        assertThat(saved.getSourceType())
                .isEqualTo(WorkOrderSparePartRequirementSourceType.REGULATION_REQUIRED_SPARE_PART);
        assertThat(saved.getRegulationRequirement()).isEqualTo(regulationRequirement);
        assertThat(saved.getSparePart()).isEqualTo(regulationRequirement.getSparePart());
        assertThat(saved.getRequiredQty()).isEqualByComparingTo(java.math.BigDecimal.valueOf(regulationRequirement.getQuantity()));
        assertThat(saved.getUnit()).isEqualTo(regulationRequirement.getUnit());
        assertThat(saved.getCriticality()).isEqualTo(regulationRequirement.getCriticality());
        assertThat(saved.getNotes()).isEqualTo(regulationRequirement.getNotes());
    }

    @Test
    void syncDoesNotDuplicateRegulationRequirements() {
        UUID workOrderId = UUID.randomUUID();
        UUID dueEventId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId);
        workOrder.setMaintenanceDueEventId(dueEventId);
        MaintenanceDueEvent dueEvent = new MaintenanceDueEvent();
        dueEvent.setRegulationId(regulationId);
        MaintenanceRegulationSparePartRequirement regulationRequirement =
                regulationRequirement(regulationId);

        when(maintenanceDueEventRepository.findByIdAndIsDeletedFalse(dueEventId)).thenReturn(Optional.of(dueEvent));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(regulationRequirementRepository.findActiveByRegulationId(regulationId))
                .thenReturn(List.of(regulationRequirement));
        when(repository.findByWorkOrderIdAndSourceTypeAndRegulationRequirementIdAndIsDeletedFalse(
                workOrderId,
                WorkOrderSparePartRequirementSourceType.REGULATION_REQUIRED_SPARE_PART,
                regulationRequirement.getId()
        )).thenReturn(Optional.of(new WorkOrderSparePartRequirement()));

        service.syncFromWorkOrderContext(workOrder);

        verify(repository, never()).save(any());
    }

    @Test
    void createWorkOrderWithNoTemplateRequirementsDoesNotAddRequirements() {
        UUID workOrderId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId);

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(templateRequirementRepository.findActiveByTemplateId(templateId)).thenReturn(List.of());

        service.syncFromTemplate(workOrderId, templateId);

        verify(repository, never()).save(any());
    }

    @Test
    void syncDoesNotDuplicateTemplateRequirements() {
        UUID workOrderId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId);
        MaintenanceTemplateSparePartRequirement templateRequirement = templateRequirement(templateId);

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(templateRequirementRepository.findActiveByTemplateId(templateId)).thenReturn(List.of(templateRequirement));
        when(repository.findByWorkOrderIdAndSourceTypeAndSourceRequirementIdAndIsDeletedFalse(
                workOrderId,
                WorkOrderSparePartRequirementSourceType.TEMPLATE_REQUIRED_SPARE_PART,
                templateRequirement.getId()
        )).thenReturn(Optional.of(new WorkOrderSparePartRequirement()));

        service.syncFromTemplate(workOrderId, templateId);

        verify(repository, never()).save(any());
    }

    @Test
    void syncDoesNotDeleteManualRequirements() {
        UUID workOrderId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId);

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(templateRequirementRepository.findActiveByTemplateId(templateId)).thenReturn(List.of());

        service.syncFromTemplate(workOrderId, templateId);

        verify(repository, never()).delete(any());
        verify(repository, never()).save(any());
    }

    @Test
    void pprTaskWorkOrderResolveTemplateThroughEquipmentRule() {
        UUID workOrderId = UUID.randomUUID();
        UUID pprTaskId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId);
        workOrder.setPprTaskId(pprTaskId);
        PprTask task = new PprTask();
        task.setEquipmentMaintenanceRuleId(ruleId);
        EquipmentMaintenanceRule rule = new EquipmentMaintenanceRule();
        rule.setTemplateId(templateId);

        when(pprTaskRepository.findByIdAndIsDeletedFalse(pprTaskId)).thenReturn(Optional.of(task));
        when(equipmentMaintenanceRuleRepository.findByIdAndIsDeletedFalse(ruleId)).thenReturn(Optional.of(rule));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(templateRequirementRepository.findActiveByTemplateId(templateId)).thenReturn(List.of());

        service.syncFromWorkOrderContext(workOrder);

        verify(templateRequirementRepository).findActiveByTemplateId(templateId);
    }

    @Test
    void dueEventWithoutTemplateFallsBackToRegulationTemplate() {
        UUID workOrderId = UUID.randomUUID();
        UUID dueEventId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId);
        workOrder.setMaintenanceDueEventId(dueEventId);
        MaintenanceDueEvent dueEvent = new MaintenanceDueEvent();
        dueEvent.setRegulationId(regulationId);
        MaintenanceRegulation regulation = new MaintenanceRegulation();
        regulation.setTemplateId(templateId);

        when(maintenanceDueEventRepository.findByIdAndIsDeletedFalse(dueEventId)).thenReturn(Optional.of(dueEvent));
        when(maintenanceRegulationRepository.findByIdAndIsDeletedFalse(regulationId)).thenReturn(Optional.of(regulation));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(templateRequirementRepository.findActiveByTemplateId(templateId)).thenReturn(List.of());

        service.syncFromWorkOrderContext(workOrder);

        verify(templateRequirementRepository).findActiveByTemplateId(templateId);
    }

    @Test
    void templateRequirementsDoNotCreateStockMovementOrWarehouseStockDependencies() {
        assertThat(WorkOrderSparePartRequirementService.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getName().contains("StockMovement"))
                .noneMatch(field -> field.getType().getName().contains("WarehouseStock"));
    }

    private MaintenanceTemplateSparePartRequirement templateRequirement(UUID templateId) {
        UUID operationId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        MaintenanceTemplate template = new MaintenanceTemplate();
        template.setId(templateId);
        MaintenanceOperation operation = new MaintenanceOperation();
        operation.setId(operationId);
        operation.setTemplate(template);
        operation.setName("Inspect bearings");
        SparePart sparePart = new SparePart();
        sparePart.setId(sparePartId);
        sparePart.setCode("BRG-001");
        sparePart.setName("Bearing");
        sparePart.setUnit("pcs");

        MaintenanceTemplateSparePartRequirement requirement = new MaintenanceTemplateSparePartRequirement();
        requirement.setId(UUID.randomUUID());
        requirement.setTemplate(template);
        requirement.setTemplateId(templateId);
        requirement.setOperation(operation);
        requirement.setOperationId(operationId);
        requirement.setSparePart(sparePart);
        requirement.setSparePartId(sparePartId);
        requirement.setQuantity(3.5);
        requirement.setUnit("pcs");
        requirement.setCriticality("CRITICAL");
        requirement.setNotes("keep ready");
        requirement.setActive(true);
        return requirement;
    }

    private MaintenanceRegulationSparePartRequirement regulationRequirement(UUID regulationId) {
        UUID sparePartId = UUID.randomUUID();
        SparePart sparePart = new SparePart();
        sparePart.setId(sparePartId);
        sparePart.setCode("BRG-002");
        sparePart.setName("Bearing kit");
        sparePart.setUnit("pcs");

        MaintenanceRegulation regulation = new MaintenanceRegulation();
        regulation.setId(regulationId);

        MaintenanceRegulationSparePartRequirement requirement =
                new MaintenanceRegulationSparePartRequirement();
        requirement.setId(UUID.randomUUID());
        requirement.setRegulation(regulation);
        requirement.setRegulationId(regulationId);
        requirement.setSparePart(sparePart);
        requirement.setSparePartId(sparePartId);
        requirement.setQuantity(4.0);
        requirement.setUnit("pcs");
        requirement.setCriticality("NORMAL");
        requirement.setNotes("planned stock");
        requirement.setActive(true);
        return requirement;
    }

    @Test
    void findByWorkOrder_withLinkedUsage_populatesIssuedQty() {
        UUID workOrderId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        WorkOrder workOrder = workOrder(workOrderId);
        WorkOrderSparePartRequirement req = requirement(requirementId, workOrderId, sparePartId);

        RepairMaterialUsage usage = new RepairMaterialUsage();
        usage.setId(UUID.randomUUID());
        usage.setRequirementId(requirementId);
        usage.setSparePartId(sparePartId);      // bir xil — almashtirish yo'q
        usage.setQuantity(3.0);

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findActiveByWorkOrderId(workOrderId))
                .thenReturn(List.of(req));
        when(repairMaterialUsageRepository.findAllByRequirementIdInAndIsDeletedFalse(List.of(requirementId)))
                .thenReturn(List.of(usage));

        List<WorkOrderSparePartRequirementDto> result = service.findByWorkOrder(workOrderId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).issuedQty()).isEqualTo(3.0);
        assertThat(result.get(0).issuedSparePartId()).isEqualTo(sparePartId);
        assertThat(result.get(0).isReplacement()).isFalse();
    }

    @Test
    void findByWorkOrder_withReplacement_marksIsReplacementTrue() {
        UUID workOrderId  = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        UUID plannedSparePartId = UUID.randomUUID();
        UUID actualSparePartId  = UUID.randomUUID(); // boshqa spare part

        WorkOrder workOrder = workOrder(workOrderId);
        WorkOrderSparePartRequirement req = requirement(requirementId, workOrderId, plannedSparePartId);

        RepairMaterialUsage usage = new RepairMaterialUsage();
        usage.setId(UUID.randomUUID());
        usage.setRequirementId(requirementId);
        usage.setSparePartId(actualSparePartId);           // faktda ishlatilgan
        usage.setReplacedSparePartId(plannedSparePartId);  // almashtirish
        usage.setQuantity(2.0);

        SparePart replacedSparePart = new SparePart();
        replacedSparePart.setId(plannedSparePartId);
        replacedSparePart.setName("Original Part");
        replacedSparePart.setCode("OP-001");

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findActiveByWorkOrderId(workOrderId))
                .thenReturn(List.of(req));
        when(repairMaterialUsageRepository.findAllByRequirementIdInAndIsDeletedFalse(List.of(requirementId)))
                .thenReturn(List.of(usage));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(List.of(plannedSparePartId)))
                .thenReturn(List.of(replacedSparePart));

        List<WorkOrderSparePartRequirementDto> result = service.findByWorkOrder(workOrderId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).issuedQty()).isEqualTo(2.0);
        assertThat(result.get(0).issuedSparePartId()).isEqualTo(actualSparePartId);
        assertThat(result.get(0).isReplacement()).isTrue();
    }

    @Test
    void findByWorkOrder_noLinkedUsage_returnsRequirementWithNullUsageFields() {
        UUID workOrderId  = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        UUID sparePartId  = UUID.randomUUID();

        WorkOrder workOrder = workOrder(workOrderId);
        WorkOrderSparePartRequirement req = requirement(requirementId, workOrderId, sparePartId);

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findActiveByWorkOrderId(workOrderId))
                .thenReturn(List.of(req));
        when(repairMaterialUsageRepository.findAllByRequirementIdInAndIsDeletedFalse(List.of(requirementId)))
                .thenReturn(List.of()); // usage yo'q

        List<WorkOrderSparePartRequirementDto> result = service.findByWorkOrder(workOrderId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).issuedQty()).isNull();
        assertThat(result.get(0).issuedSparePartId()).isNull();
        assertThat(result.get(0).isReplacement()).isFalse();
    }

    @Test
    void findByWorkOrder_multipleRequirements_eachLinkedToOwnUsage() {
        UUID workOrderId   = UUID.randomUUID();
        UUID requirementId1 = UUID.randomUUID();
        UUID requirementId2 = UUID.randomUUID();
        UUID sparePartId1  = UUID.randomUUID();
        UUID sparePartId2  = UUID.randomUUID();

        WorkOrder workOrder = workOrder(workOrderId);
        WorkOrderSparePartRequirement req1 = requirement(requirementId1, workOrderId, sparePartId1);
        WorkOrderSparePartRequirement req2 = requirement(requirementId2, workOrderId, sparePartId2);

        RepairMaterialUsage usage1 = new RepairMaterialUsage();
        usage1.setRequirementId(requirementId1);
        usage1.setSparePartId(sparePartId1);
        usage1.setQuantity(1.0);

        // req2 uchun usage yo'q

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findActiveByWorkOrderId(workOrderId))
                .thenReturn(List.of(req1, req2));
        when(repairMaterialUsageRepository.findAllByRequirementIdInAndIsDeletedFalse(
                List.of(requirementId1, requirementId2)))
                .thenReturn(List.of(usage1));

        List<WorkOrderSparePartRequirementDto> result = service.findByWorkOrder(workOrderId);

        assertThat(result).hasSize(2);
        WorkOrderSparePartRequirementDto dto1 = result.stream()
                .filter(d -> d.id().equals(requirementId1)).findFirst().orElseThrow();
        WorkOrderSparePartRequirementDto dto2 = result.stream()
                .filter(d -> d.id().equals(requirementId2)).findFirst().orElseThrow();

        assertThat(dto1.issuedQty()).isEqualTo(1.0);
        assertThat(dto2.issuedQty()).isNull();
    }

    @Test
    void findByWorkOrder_noRequirements_returnsEmptyList() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId)));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findActiveByWorkOrderId(workOrderId))
                .thenReturn(List.of());

        List<WorkOrderSparePartRequirementDto> result = service.findByWorkOrder(workOrderId);

        assertThat(result).isEmpty();
        verifyNoInteractions(repairMaterialUsageRepository);
    }

    @Test
    void createManual_savesManualRequirement() {
        UUID workOrderId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId);
        SparePart sparePart = new SparePart();
        sparePart.setId(sparePartId);
        sparePart.setCode("SP-1");
        sparePart.setName("Bearing");

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(repository.save(any(WorkOrderSparePartRequirement.class))).thenAnswer(invocation -> {
            WorkOrderSparePartRequirement saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setSparePart(sparePart);
            return saved;
        });

        var result = service.createManual(
                workOrderId,
                new com.toir.dto.workorder.WorkOrderSparePartRequirementRequest(sparePartId, java.math.BigDecimal.valueOf(3.0), "pcs", "HIGH", "manual")
        );

        assertThat(result.sourceType()).isEqualTo(WorkOrderSparePartRequirementSourceType.MANUAL);
        assertThat(result.requiredQty()).isEqualByComparingTo("3.0");
        assertThat(result.sparePartId()).isEqualTo(sparePartId);
    }

    @Test
    void updateManual_rejectsTemplateRequirement() {
        UUID workOrderId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId);
        WorkOrderSparePartRequirement requirement = requirement(requirementId, workOrderId, UUID.randomUUID());
        requirement.setSourceType(WorkOrderSparePartRequirementSourceType.TEMPLATE_REQUIRED_SPARE_PART);

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findByIdAndWorkOrderIdAndIsDeletedFalse(requirementId, workOrderId))
                .thenReturn(Optional.of(requirement));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.updateManual(
                workOrderId,
                requirementId,
                new com.toir.dto.workorder.WorkOrderSparePartRequirementRequest(UUID.randomUUID(), java.math.BigDecimal.valueOf(1.0), "pcs", null, null)
        )).isInstanceOf(com.toir.exception.RestException.class);
    }

    // ─── Helpers ───────────────────────────────────────────

    private WorkOrder workOrder(UUID id) {
        WorkOrder wo = new WorkOrder();
        wo.setId(id);
        wo.setDepartmentId(UUID.randomUUID());
        return wo;
    }

    private WorkOrderSparePartRequirement requirement(UUID id, UUID workOrderId, UUID sparePartId) {
        WorkOrderSparePartRequirement req = new WorkOrderSparePartRequirement();
        req.setId(id);
        req.setWorkOrderId(workOrderId);
        req.setSparePartId(sparePartId);
        req.setRequiredQty(java.math.BigDecimal.valueOf(2.0));
        req.setStatus(WorkOrderSparePartRequirementStatus.PLANNED);
        req.setSourceType(WorkOrderSparePartRequirementSourceType.MANUAL);
        return req;
    }
}
