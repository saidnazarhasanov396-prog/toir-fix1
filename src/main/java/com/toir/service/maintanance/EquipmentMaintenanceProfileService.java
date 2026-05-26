package com.toir.service.maintanance;

import com.toir.dto.equipmentmaintenance.EquipmentMaintenanceProfileDto;
import com.toir.dto.equipmentmaintenance.EquipmentMaintenanceRuleDto;
import com.toir.dto.equipmentmaintenance.EquipmentMaintenanceRuleRequest;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationDto;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.repository.maintenance.MaintenanceTemplateRepository;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EquipmentMaintenanceProfileService {

    private static final int MAX_CODE_GENERATION_ATTEMPTS = 50;

    private final EquipmentRepository equipmentRepository;
    private final MaintenanceRegulationRepository regulationRepository;
    private final EquipmentMaintenanceRuleRepository ruleRepository;
    private final MaintenanceTemplateRepository templateRepository;

    @Transactional(readOnly = true)
    public EquipmentMaintenanceProfileDto getProfile(UUID equipmentId) {
        Equipment equipment = equipmentOrThrow(equipmentId);
        List<MaintenanceRegulationDto> inheritedRules = regulationRepository
                .findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(equipment.getEquipmentTypeId())
                .stream()
                .map(MaintenanceRegulationDto::from)
                .toList();
        List<EquipmentMaintenanceRuleDto> individualRules = ruleRepository
                .findAllByEquipmentIdAndIsDeletedFalse(equipmentId)
                .stream()
                .map(EquipmentMaintenanceRuleDto::from)
                .toList();
        return new EquipmentMaintenanceProfileDto(
                equipment.getId(),
                equipment.getEquipmentTypeId(),
                inheritedRules,
                individualRules
        );
    }

    @Transactional
    public EquipmentMaintenanceRuleDto createRule(UUID equipmentId, EquipmentMaintenanceRuleRequest request) {
        Equipment equipment = equipmentOrThrow(equipmentId);
        validateReferences(equipment, request);

        EquipmentMaintenanceRule rule = new EquipmentMaintenanceRule();
        rule.setEquipmentId(equipment.getId());
        rule.setCode(generateCode());
        apply(rule, request);
        return EquipmentMaintenanceRuleDto.from(ruleRepository.save(rule));
    }

    @Transactional
    public EquipmentMaintenanceRuleDto updateRule(UUID equipmentId, UUID ruleId, EquipmentMaintenanceRuleRequest request) {
        Equipment equipment = equipmentOrThrow(equipmentId);
        validateReferences(equipment, request);
        EquipmentMaintenanceRule rule = ruleRepository.findByIdAndIsDeletedFalse(ruleId)
                .orElseThrow(() -> RestException.notFound("Equipment maintenance rule not found: " + ruleId));
        if (!rule.getEquipmentId().equals(equipmentId)) {
            throw RestException.badRequest("Equipment maintenance rule belongs to another equipment");
        }
        apply(rule, request);
        return EquipmentMaintenanceRuleDto.from(ruleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(UUID equipmentId, UUID ruleId) {
        equipmentOrThrow(equipmentId);
        EquipmentMaintenanceRule rule = ruleRepository.findByIdAndIsDeletedFalse(ruleId)
                .orElseThrow(() -> RestException.notFound("Equipment maintenance rule not found: " + ruleId));
        if (!rule.getEquipmentId().equals(equipmentId)) {
            throw RestException.badRequest("Equipment maintenance rule belongs to another equipment");
        }
        rule.setDeleted(true);
        ruleRepository.save(rule);
    }

    private Equipment equipmentOrThrow(UUID equipmentId) {
        return equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
    }

    private void validateReferences(Equipment equipment, EquipmentMaintenanceRuleRequest request) {
        if (request.baseRegulationId() != null) {
            var base = regulationRepository.findByIdAndIsDeletedFalse(request.baseRegulationId())
                    .orElseThrow(() -> RestException.notFound("Base maintenance regulation not found: " + request.baseRegulationId()));
            if (!base.getEquipmentTypeId().equals(equipment.getEquipmentTypeId())) {
                throw RestException.badRequest("Base regulation belongs to another equipment type");
            }
        }
        if (request.templateId() != null && !templateRepository.existsByIdAndIsDeletedFalse(request.templateId())) {
            throw RestException.notFound("Maintenance template not found: " + request.templateId());
        }
    }

    private void apply(EquipmentMaintenanceRule rule, EquipmentMaintenanceRuleRequest request) {
        rule.setBaseRegulationId(request.baseRegulationId());
        rule.setTemplateId(request.templateId());
        rule.setName(request.name().trim());
        rule.setDescription(blankToNull(request.description()));
        rule.setMaintenanceKind(request.maintenanceKind());
        rule.setNormativeLaborHours(request.normativeLaborHours());
        rule.setActive(request.active() == null || request.active());
        rule.setPeriodicityUnit(request.periodicityUnit());
        rule.setPeriodicityValue(request.periodicityValue());
        rule.setToleranceDays(request.toleranceDays());
        rule.setRequiresShutdown(request.requiresShutdown());
        rule.setTriggerMeterType(request.triggerMeterType());
        rule.setTriggerMeterInterval(request.triggerMeterInterval());
    }

    private String generateCode() {
        int year = Year.now().getValue();
        String prefix = "EMR-" + year + "-";
        long sequence = ruleRepository.maxSequenceByCodePrefix(prefix) + 1;
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = "%s%04d".formatted(prefix, sequence + attempt);
            if (ruleRepository.existsByCodeAndIsDeletedFalse(code)) {
                continue;
            }
            return code;
        }
        throw new DataIntegrityViolationException("Could not generate unique equipment maintenance rule code");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
