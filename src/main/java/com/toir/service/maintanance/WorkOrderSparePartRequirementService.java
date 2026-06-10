package com.toir.service.maintanance;

import com.toir.dto.workorder.WorkOrderSparePartRequirementDto;
import com.toir.entity.PprTask;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.entity.maintenance.MaintenanceRegulationSparePartRequirement;
import com.toir.entity.maintenance.MaintenanceTemplateSparePartRequirement;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.enums.WorkOrderSparePartRequirementSourceType;
import com.toir.enums.WorkOrderSparePartRequirementStatus;
import com.toir.exception.RestException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkOrderSparePartRequirementService {

    private final WorkOrderSparePartRequirementRepository repository;
    private final WorkOrderRepository workOrderRepository;
    private final MaintenanceTemplateSparePartRequirementRepository templateRequirementRepository;
    private final MaintenanceRegulationSparePartRequirementRepository regulationRequirementRepository;
    private final MaintenanceDueEventRepository maintenanceDueEventRepository;
    private final MaintenanceRegulationRepository maintenanceRegulationRepository;
    private final EquipmentMaintenanceRuleRepository equipmentMaintenanceRuleRepository;
    private final PprTaskRepository pprTaskRepository;
    private final ScopeAccessService scopeAccessService;

    @Transactional(readOnly = true)
    public List<WorkOrderSparePartRequirementDto> findByWorkOrder(UUID workOrderId) {
        WorkOrder workOrder = workOrderOrThrow(workOrderId);
        assertCanAccessWorkOrder(workOrder);
        return repository.findActiveByWorkOrderId(workOrderId).stream()
                .map(WorkOrderSparePartRequirementDto::from)
                .toList();
    }

    @Transactional
    public void syncFromTemplate(UUID workOrderId, UUID templateId) {
        if (workOrderId == null || templateId == null) {
            return;
        }
        WorkOrder workOrder = workOrderOrThrow(workOrderId);
        List<MaintenanceTemplateSparePartRequirement> templateRequirements =
                templateRequirementRepository.findActiveByTemplateId(templateId);
        for (MaintenanceTemplateSparePartRequirement templateRequirement : templateRequirements) {
            UUID sourceRequirementId = templateRequirement.getId();
            if (sourceRequirementId == null || repository
                    .findByWorkOrderIdAndSourceTypeAndSourceRequirementIdAndIsDeletedFalse(
                            workOrderId,
                            WorkOrderSparePartRequirementSourceType.TEMPLATE_REQUIRED_SPARE_PART,
                            sourceRequirementId
                    )
                    .isPresent()) {
                continue;
            }
            repository.save(fromTemplateRequirement(workOrder, templateRequirement));
        }
    }

    @Transactional
    public void syncFromRegulation(UUID workOrderId, UUID regulationId) {
        if (workOrderId == null || regulationId == null) {
            return;
        }
        WorkOrder workOrder = workOrderOrThrow(workOrderId);
        List<MaintenanceRegulationSparePartRequirement> regulationRequirements =
                regulationRequirementRepository.findActiveByRegulationId(regulationId);
        for (MaintenanceRegulationSparePartRequirement regulationRequirement : regulationRequirements) {
            UUID regulationRequirementId = regulationRequirement.getId();
            if (regulationRequirementId == null || repository
                    .findByWorkOrderIdAndSourceTypeAndRegulationRequirementIdAndIsDeletedFalse(
                            workOrderId,
                            WorkOrderSparePartRequirementSourceType.REGULATION_REQUIRED_SPARE_PART,
                            regulationRequirementId
                    )
                    .isPresent()) {
                continue;
            }
            repository.save(fromRegulationRequirement(workOrder, regulationRequirement));
        }
    }

    @Transactional
    public void syncFromWorkOrderContext(WorkOrder workOrder) {
        if (workOrder == null || workOrder.getId() == null) {
            return;
        }
        resolveTemplateId(workOrder).ifPresent(templateId -> syncFromTemplate(workOrder.getId(), templateId));
        resolveRegulationId(workOrder).ifPresent(regulationId -> syncFromRegulation(workOrder.getId(), regulationId));
    }

    private WorkOrderSparePartRequirement fromTemplateRequirement(
            WorkOrder workOrder,
            MaintenanceTemplateSparePartRequirement templateRequirement
    ) {
        WorkOrderSparePartRequirement requirement = new WorkOrderSparePartRequirement();
        requirement.setWorkOrder(workOrder);
        requirement.setSourceType(WorkOrderSparePartRequirementSourceType.TEMPLATE_REQUIRED_SPARE_PART);
        requirement.setSourceRequirement(templateRequirement);
        requirement.setTemplate(templateRequirement.getTemplate());
        requirement.setOperation(templateRequirement.getOperation());
        requirement.setSparePart(templateRequirement.getSparePart());
        requirement.setRequiredQty(templateRequirement.getQuantity());
        requirement.setUnit(templateRequirement.getUnit());
        requirement.setCriticality(templateRequirement.getCriticality());
        requirement.setNotes(templateRequirement.getNotes());
        requirement.setStatus(WorkOrderSparePartRequirementStatus.PLANNED);
        return requirement;
    }

    private WorkOrderSparePartRequirement fromRegulationRequirement(
            WorkOrder workOrder,
            MaintenanceRegulationSparePartRequirement regulationRequirement
    ) {
        WorkOrderSparePartRequirement requirement = new WorkOrderSparePartRequirement();
        requirement.setWorkOrder(workOrder);
        requirement.setSourceType(WorkOrderSparePartRequirementSourceType.REGULATION_REQUIRED_SPARE_PART);
        requirement.setRegulationRequirement(regulationRequirement);
        requirement.setSparePart(regulationRequirement.getSparePart());
        requirement.setRequiredQty(regulationRequirement.getQuantity());
        requirement.setUnit(regulationRequirement.getUnit());
        requirement.setCriticality(regulationRequirement.getCriticality());
        requirement.setNotes(regulationRequirement.getNotes());
        requirement.setStatus(WorkOrderSparePartRequirementStatus.PLANNED);
        return requirement;
    }

    private Optional<UUID> resolveTemplateId(WorkOrder workOrder) {
        Optional<UUID> fromDueEvent = resolveTemplateIdFromDueEvent(workOrder.getMaintenanceDueEventId());
        if (fromDueEvent.isPresent()) {
            return fromDueEvent;
        }
        return resolveTemplateIdFromPprTask(workOrder.getPprTaskId());
    }

    private Optional<UUID> resolveTemplateIdFromDueEvent(UUID dueEventId) {
        if (dueEventId == null) {
            return Optional.empty();
        }
        return maintenanceDueEventRepository.findByIdAndIsDeletedFalse(dueEventId)
                .flatMap(this::resolveTemplateIdFromDueEvent);
    }

    private Optional<UUID> resolveTemplateIdFromDueEvent(MaintenanceDueEvent dueEvent) {
        if (dueEvent.getTemplateId() != null) {
            return Optional.of(dueEvent.getTemplateId());
        }
        Optional<UUID> fromRule = resolveTemplateIdFromEquipmentRule(dueEvent.getEquipmentMaintenanceRuleId());
        if (fromRule.isPresent()) {
            return fromRule;
        }
        return resolveTemplateIdFromRegulation(dueEvent.getRegulationId());
    }

    private Optional<UUID> resolveTemplateIdFromPprTask(UUID pprTaskId) {
        if (pprTaskId == null) {
            return Optional.empty();
        }
        return pprTaskRepository.findByIdAndIsDeletedFalse(pprTaskId)
                .flatMap(this::resolveTemplateIdFromPprTask);
    }

    private Optional<UUID> resolveTemplateIdFromPprTask(PprTask task) {
        Optional<UUID> fromDueEvent = resolveTemplateIdFromDueEvent(task.getMaintenanceDueEventId());
        if (fromDueEvent.isPresent()) {
            return fromDueEvent;
        }
        Optional<UUID> fromRule = resolveTemplateIdFromEquipmentRule(task.getEquipmentMaintenanceRuleId());
        if (fromRule.isPresent()) {
            return fromRule;
        }
        return resolveTemplateIdFromRegulation(task.getRegulationId());
    }

    private Optional<UUID> resolveTemplateIdFromEquipmentRule(UUID ruleId) {
        if (ruleId == null) {
            return Optional.empty();
        }
        return equipmentMaintenanceRuleRepository.findByIdAndIsDeletedFalse(ruleId)
                .map(EquipmentMaintenanceRule::getTemplateId)
                .filter(templateId -> templateId != null);
    }

    private Optional<UUID> resolveTemplateIdFromRegulation(UUID regulationId) {
        if (regulationId == null) {
            return Optional.empty();
        }
        return maintenanceRegulationRepository.findByIdAndIsDeletedFalse(regulationId)
                .map(MaintenanceRegulation::getTemplateId)
                .filter(templateId -> templateId != null);
    }

    private Optional<UUID> resolveRegulationId(WorkOrder workOrder) {
        Optional<UUID> fromDueEvent = resolveRegulationIdFromDueEvent(workOrder.getMaintenanceDueEventId());
        if (fromDueEvent.isPresent()) {
            return fromDueEvent;
        }
        return resolveRegulationIdFromPprTask(workOrder.getPprTaskId());
    }

    private Optional<UUID> resolveRegulationIdFromDueEvent(UUID dueEventId) {
        if (dueEventId == null) {
            return Optional.empty();
        }
        return maintenanceDueEventRepository.findByIdAndIsDeletedFalse(dueEventId)
                .map(MaintenanceDueEvent::getRegulationId)
                .filter(regulationId -> regulationId != null);
    }

    private Optional<UUID> resolveRegulationIdFromPprTask(UUID pprTaskId) {
        if (pprTaskId == null) {
            return Optional.empty();
        }
        return pprTaskRepository.findByIdAndIsDeletedFalse(pprTaskId)
                .map(PprTask::getRegulationId)
                .filter(regulationId -> regulationId != null);
    }

    private WorkOrder workOrderOrThrow(UUID workOrderId) {
        return workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + workOrderId));
    }

    private void assertCanAccessWorkOrder(WorkOrder workOrder) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (workOrder.getDepartmentId() == null || !scopeAccessService.canAccessDepartment(workOrder.getDepartmentId())) {
            throw new AccessDeniedException("Access denied by work order department scope");
        }
    }
}
