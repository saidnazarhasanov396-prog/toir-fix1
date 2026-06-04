package com.toir.service.maintanance;

import com.toir.dto.maintenanceplanning.MaintenanceDueCalculationDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationImpactDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationPreviewDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.AutomationAction;
import com.toir.enums.ApprovalResultAction;
import com.toir.enums.DuplicatePolicy;
import com.toir.enums.MaintenanceInitialSchedulePolicy;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaintenanceImpactService {

    private final EquipmentRepository equipmentRepository;
    private final MaintenanceRegulationRepository regulationRepository;
    private final MaintenanceDueCalculationService dueCalculationService;
    private final MaintenanceRegulationApplicabilityService applicabilityService;

    @Transactional(readOnly = true)
    public MaintenanceRegulationPreviewDto preview(MaintenanceRegulationRequest request) {
        MaintenanceRegulation probe = new MaintenanceRegulation();
        probe.setEquipmentTypeId(request.equipmentTypeId());
        probe.setTemplateId(request.templateId());
        probe.setMaintenanceKind(request.maintenanceKind());
        probe.setNormativeLaborHours(request.normativeLaborHours());
        probe.setActive(request.active() == null || request.active());
        probe.setPeriodicityUnit(request.periodicityUnit());
        probe.setPeriodicityValue(request.periodicityValue());
        probe.setToleranceDays(request.toleranceDays());
        probe.setRequiresShutdown(request.requiresShutdown());
        probe.setTriggerMeterType(request.triggerMeterType());
        probe.setTriggerMeterInterval(request.triggerMeterInterval());
        probe.setTriggerPolicy(request.triggerPolicy());
        probe.setRecalculationPolicy(request.recalculationPolicy());
        probe.setInitialSchedulePolicy(request.initialSchedulePolicy() == null
                ? MaintenanceInitialSchedulePolicy.FROM_OPERATION_START
                : request.initialSchedulePolicy());
        probe.setAutomationAction(request.automationAction() == null
                ? AutomationAction.REQUIRE_APPROVAL
                : request.automationAction());
        probe.setApprovalResultAction(request.approvalResultAction() == null
                ? ApprovalResultAction.CREATE_TASK
                : request.approvalResultAction());
        probe.setDuplicatePolicy(request.duplicatePolicy() == null
                ? DuplicatePolicy.ONE_ITEM_PER_CYCLE
                : request.duplicatePolicy());
        return build(probe, request.attributeConditions());
    }

    @Transactional(readOnly = true)
    public MaintenanceRegulationImpactDto impact(UUID regulationId) {
        MaintenanceRegulation regulation = regulationRepository.findByIdAndIsDeletedFalse(regulationId)
                .orElseThrow(() -> RestException.notFound("Maintenance regulation not found: " + regulationId));
        MaintenanceRegulationPreviewDto preview = build(regulation, null);
        return new MaintenanceRegulationImpactDto(
                regulationId,
                preview.affectedEquipment(),
                preview.matchedCount(),
                preview.unmatchedCount(),
                preview.blockedCount(),
                preview.missingMetersCount(),
                preview.automationSummary(),
                preview.duplicatePolicy(),
                preview.items()
        );
    }

    private MaintenanceRegulationPreviewDto build(MaintenanceRegulation regulation,
                                                  List<com.toir.dto.maintenanceregulation.MaintenanceRegulationAttributeConditionRequest> requestConditions) {
        List<Equipment> equipment = equipmentRepository.findAllForMaintenanceRegulations(regulation.getEquipmentTypeId());
        List<MaintenanceRegulationPreviewDto.Item> items = equipment.stream()
                .map(item -> item(regulation, requestConditions, item))
                .toList();
        long matched = items.stream().filter(MaintenanceRegulationPreviewDto.Item::matched).count();
        long blocked = items.stream().filter(MaintenanceRegulationPreviewDto.Item::blocked).count();
        long missingMeters = items.stream().filter(MaintenanceRegulationPreviewDto.Item::missingMeter).count();
        return new MaintenanceRegulationPreviewDto(
                equipment.size(),
                matched,
                Math.max(0, equipment.size() - matched),
                blocked,
                missingMeters,
                automationSummary(regulation),
                regulation.getDuplicatePolicy(),
                items
        );
    }

    private MaintenanceRegulationPreviewDto.Item item(MaintenanceRegulation regulation,
                                                      List<com.toir.dto.maintenanceregulation.MaintenanceRegulationAttributeConditionRequest> requestConditions,
                                                      Equipment equipment) {
        MaintenanceDueCalculationDto due = dueCalculationService.calculate(equipment.getId(), regulation);
        MaintenanceRegulationApplicabilityService.ApplicabilityResult applicability =
                applicabilityService.evaluate(equipment, regulation, requestConditions, due);
        return new MaintenanceRegulationPreviewDto.Item(
                equipment.getId(),
                equipment.getCode(),
                equipment.getName(),
                applicability.matched(),
                applicability.blocked(),
                applicability.missingMeter(),
                applicability.reason(),
                applicability.dueStatus()
        );
    }

    private String automationSummary(MaintenanceRegulation regulation) {
        return "Action: %s, approval result: %s, duplicates: %s".formatted(
                regulation.getAutomationAction(),
                regulation.getApprovalResultAction(),
                regulation.getDuplicatePolicy()
        );
    }
}
