package com.toir.service.maintanance;

import com.toir.dto.workorder.WorkOrderSparePartRequirementDto;
import com.toir.dto.workorder.WorkOrderSparePartRequirementRequest;
import com.toir.entity.SparePart;
import com.toir.entity.PprTask;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.entity.maintenance.MaintenanceRegulationSparePartRequirement;
import com.toir.entity.maintenance.MaintenanceTemplateSparePartRequirement;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.enums.WorkOrderSparePartRequirementSourceType;
import com.toir.enums.WorkOrderSparePartRequirementStatus;
import com.toir.exception.RestException;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.repository.maintenance.MaintenanceRegulationSparePartRequirementRepository;
import com.toir.repository.maintenance.MaintenanceTemplateSparePartRequirementRepository;
import com.toir.repository.maintenance.WorkOrderSparePartRequirementRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.repository.repair.RepairCampaignMaterialRequirementRepository;
import com.toir.repository.repair.RepairCampaignWorkItemRepository;
import com.toir.repository.ReservationRepository;
import com.toir.enums.ReservationStatus;
import com.toir.security.ScopeAccessService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final RepairMaterialUsageRepository repairMaterialUsageRepository;
    private final SparePartRepository sparePartRepository;
    private final ScopeAccessService scopeAccessService;
    private final RepairCampaignMaterialRequirementRepository campaignMaterialRequirementRepository;
    private final RepairCampaignWorkItemRepository campaignWorkItemRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    public void syncFromCampaignWorkItem(UUID workOrderId, UUID campaignId, UUID workItemId) {
        if (workOrderId == null || campaignId == null || workItemId == null) {
            return;
        }
        WorkOrder workOrder = workOrderOrThrow(workOrderId);
        if(!Objects.equals(workOrder.getRepairCampaignId(),campaignId))
            throw RestException.conflict("CAMPAIGN_WORK_ORDER_MISMATCH");
        campaignWorkItemRepository.findByIdAndCampaignIdAndIsDeletedFalse(workItemId,campaignId)
                .orElseThrow(()->RestException.badRequest("CAMPAIGN_WORK_ITEM_MISMATCH"));
        for (var campaignRequirement : campaignMaterialRequirementRepository
                .findAllByRepairCampaignIdAndWorkItemIdAndIsDeletedFalseOrderBySparePartIdAsc(campaignId, workItemId)) {
            if (repository.findByWorkOrderIdAndCampaignRequirementIdAndIsDeletedFalse(
                    workOrderId, campaignRequirement.getId()).isPresent()) {
                continue;
            }
            SparePart sparePart = sparePartOrThrow(campaignRequirement.getSparePartId());
            WorkOrderSparePartRequirement requirement = new WorkOrderSparePartRequirement();
            requirement.setWorkOrder(workOrder);
            requirement.setSourceType(WorkOrderSparePartRequirementSourceType.REPAIR_CAMPAIGN_WORK_ITEM);
            requirement.setCampaignRequirementId(campaignRequirement.getId());
            requirement.setSparePart(sparePart);
            requirement.setRequiredQty(campaignRequirement.getRequiredQuantity());
            requirement.setUnit(sparePart.getUnit());
            requirement.setCriticality(campaignRequirement.isCritical() ? "CRITICAL" : null);
            requirement.setStatus(WorkOrderSparePartRequirementStatus.PLANNED);
            repository.save(requirement);
        }
    }

    @Transactional(readOnly = true)
    public List<WorkOrderSparePartRequirementDto> findByWorkOrder(UUID workOrderId) {
        WorkOrder workOrder = workOrderOrThrow(workOrderId);
        assertCanAccessWorkOrder(workOrder);
        List<WorkOrderSparePartRequirement> requirements = repository.findActiveByWorkOrderId(workOrderId);
        if (requirements.isEmpty()) {
            return List.of();
        }

        // Requirement larga bog'liq faktik materiallarni olamiz
        List<UUID> requirementIds = requirements.stream()
                .map(WorkOrderSparePartRequirement::getId)
                .toList();
        Map<UUID, RepairMaterialUsage> usageByRequirementId = repairMaterialUsageRepository
                .findAllByRequirementIdInAndIsDeletedFalse(requirementIds)
                .stream()
                .collect(Collectors.toMap(
                        RepairMaterialUsage::getRequirementId,
                        Function.identity(),
                        (a, b) -> a // bitta requirement uchun eng birinchisini olamiz
                ));

        // Almashtirish bo'lgan spare part lar uchun nom va kodni yuklaymiz
        List<UUID> replacedSparePartIds = usageByRequirementId.values().stream()
                .map(RepairMaterialUsage::getReplacedSparePartId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<UUID, SparePart> replacedSparePartById = replacedSparePartIds.isEmpty()
                ? Map.of()
                : sparePartRepository.findAllByIdInAndIsDeletedFalse(replacedSparePartIds)
                .stream()
                .collect(Collectors.toMap(SparePart::getId, Function.identity()));

        return requirements.stream()
                .map(req -> {
                    RepairMaterialUsage usage = usageByRequirementId.get(req.getId());
                    if (usage == null) {
                        return WorkOrderSparePartRequirementDto.from(req);
                    }
                    UUID issuedSparePartId = usage.getSparePartId();
                    SparePart replacedSparePart = usage.getReplacedSparePartId() != null
                            ? replacedSparePartById.get(usage.getReplacedSparePartId())
                            : null;
                    return WorkOrderSparePartRequirementDto.withUsage(
                            req,
                            usage.getQuantity(),
                            issuedSparePartId,
                            req.getSparePart() != null ? req.getSparePart().getCode() : null,
                            req.getSparePart() != null ? req.getSparePart().getName() : null
                    );
                })
                .toList();
    }

    @Transactional
    public WorkOrderSparePartRequirementDto createManual(UUID workOrderId,
                                                         WorkOrderSparePartRequirementRequest request) {
        WorkOrder workOrder = workOrderOrThrow(workOrderId);
        assertCanMutateWorkOrder(workOrder);
        SparePart sparePart = sparePartOrThrow(request.sparePartId());
        if (request.requiredQty() == null || request.requiredQty().signum() <= 0) {
            throw RestException.badRequest("requiredQty must be positive");
        }

        WorkOrderSparePartRequirement requirement = new WorkOrderSparePartRequirement();
        requirement.setWorkOrder(workOrder);
        requirement.setSourceType(WorkOrderSparePartRequirementSourceType.MANUAL);
        requirement.setSparePart(sparePart);
        requirement.setRequiredQty(request.requiredQty());
        requirement.setUnit(request.unit());
        requirement.setCriticality(request.criticality());
        requirement.setNotes(request.notes());
        requirement.setStatus(WorkOrderSparePartRequirementStatus.PLANNED);
        return WorkOrderSparePartRequirementDto.from(repository.save(requirement));
    }

    @Transactional
    public WorkOrderSparePartRequirementDto updateManual(UUID workOrderId,
                                                         UUID requirementId,
                                                         WorkOrderSparePartRequirementRequest request) {
        WorkOrder workOrder = workOrderOrThrow(workOrderId);
        assertCanMutateWorkOrder(workOrder);
        WorkOrderSparePartRequirement requirement = requirementOrThrow(workOrderId, requirementId);
        assertManualRequirement(requirement);
        SparePart sparePart = sparePartOrThrow(request.sparePartId());
        if (request.requiredQty() == null || request.requiredQty().signum() <= 0) {
            throw RestException.badRequest("requiredQty must be positive");
        }
        assertReservationFactsAllowUpdate(requirement,request.sparePartId(),request.requiredQty());

        requirement.setSparePart(sparePart);
        requirement.setRequiredQty(request.requiredQty());
        requirement.setUnit(request.unit());
        requirement.setCriticality(request.criticality());
        requirement.setNotes(request.notes());
        return WorkOrderSparePartRequirementDto.from(repository.save(requirement));
    }

    @Transactional
    public void deleteManual(UUID workOrderId, UUID requirementId) {
        WorkOrder workOrder = workOrderOrThrow(workOrderId);
        assertCanMutateWorkOrder(workOrder);
        WorkOrderSparePartRequirement requirement = requirementOrThrow(workOrderId, requirementId);
        assertManualRequirement(requirement);
        if(!activeReservations(requirementId).isEmpty())throw RestException.conflict("RESERVATION_REQUIREMENT_IN_USE");
        requirement.setDeleted(true);
        repository.save(requirement);
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

    private void assertCanMutateWorkOrder(WorkOrder workOrder) {
        assertCanAccessWorkOrder(workOrder);
    }

    private WorkOrderSparePartRequirement requirementOrThrow(UUID workOrderId, UUID requirementId) {
        return repository.findByIdAndWorkOrderIdAndIsDeletedFalseForUpdate(requirementId, workOrderId)
                .orElseThrow(() -> RestException.notFound(
                        "Work order spare part requirement not found: " + requirementId));
    }

    private List<com.toir.entity.Reservation> activeReservations(UUID requirementId){
        return reservationRepository.findAllByRequirementIdAndStatusAndIsDeletedFalse(requirementId,ReservationStatus.ACTIVE);
    }

    private void assertReservationFactsAllowUpdate(WorkOrderSparePartRequirement requirement,UUID sparePartId,java.math.BigDecimal quantity){
        List<com.toir.entity.Reservation> facts=activeReservations(requirement.getId());
        if(facts.isEmpty())return;
        UUID currentSpare=requirement.getSparePartId()!=null?requirement.getSparePartId():requirement.getSparePart()==null?null:requirement.getSparePart().getId();
        java.math.BigDecimal reserved=facts.stream().map(com.toir.entity.Reservation::getQuantity).filter(Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
        if(!Objects.equals(currentSpare,sparePartId)||quantity.compareTo(reserved)<0)
            throw RestException.conflict("RESERVATION_REQUIREMENT_IN_USE");
    }

    private SparePart sparePartOrThrow(UUID sparePartId) {
        return sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)
                .orElseThrow(() -> RestException.notFound("Spare part not found: " + sparePartId));
    }

    private void assertManualRequirement(WorkOrderSparePartRequirement requirement) {
        if (requirement.getSourceType() != WorkOrderSparePartRequirementSourceType.MANUAL) {
            throw RestException.conflict("Only manually added spare part requirements can be changed");
        }
    }
}
