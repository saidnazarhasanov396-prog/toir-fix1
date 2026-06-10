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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderSparePartRequirementServiceTest {

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

    @InjectMocks
    private WorkOrderSparePartRequirementService service;

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
        assertThat(saved.getRequiredQty()).isEqualTo(templateRequirement.getQuantity());
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
        assertThat(saved.getRequiredQty()).isEqualTo(regulationRequirement.getQuantity());
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
    void templateRequirementsDoNotCreateRepairMaterialUsageOrStockMovementDependencies() {
        assertThat(WorkOrderSparePartRequirementService.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getName().contains("RepairMaterialUsage"))
                .noneMatch(field -> field.getType().getName().contains("StockMovement"))
                .noneMatch(field -> field.getType().getName().contains("WarehouseStock"));
    }

    private WorkOrder workOrder(UUID id) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setDepartmentId(UUID.randomUUID());
        return workOrder;
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
}
