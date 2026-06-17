package com.toir.service.maintanance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceCompletionAnchor;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MeterType;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceCompletionAnchorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceMeterBaselineService {

    static final String INITIAL_METER_BASELINE_SOURCE = "INITIAL_METER_BASELINE";

    private final EquipmentRepository equipmentRepository;
    private final EquipmentMeterRepository equipmentMeterRepository;
    private final MaintenanceCompletionAnchorRepository anchorRepository;
    private final EquipmentMaintenanceEffectiveRuleResolver effectiveRuleResolver;
    private final ObjectMapper objectMapper;
    private Clock clock = Clock.systemUTC();

    @Transactional
    public void seedForRegulation(MaintenanceRegulation regulation) {
        if (!isMeterTriggered(regulation)) {
            return;
        }
        List<Equipment> equipment = safeList(
                equipmentRepository.findAllForMaintenanceRegulations(regulation.getEquipmentTypeId()));
        for (Equipment item : equipment) {
            if (item == null || item.getId() == null) {
                continue;
            }
            effectiveRuleResolver.resolveApplicable(item.getId()).stream()
                    .filter(EquipmentMaintenanceEffectiveRule::hasMeterTrigger)
                    .filter(rule -> Objects.equals(rule.regulationId(), regulation.getId()))
                    .filter(rule -> rule.triggerMeterType() == regulation.getTriggerMeterType())
                    .forEach(this::seedForEffectiveRule);
        }
    }

    @Transactional
    public void seedForEquipmentRule(EquipmentMaintenanceRule rule) {
        if (!isMeterTriggered(rule)) {
            return;
        }
        effectiveRuleResolver.resolveApplicable(rule.getEquipmentId()).stream()
                .filter(EquipmentMaintenanceEffectiveRule::hasMeterTrigger)
                .filter(effectiveRule -> Objects.equals(effectiveRule.equipmentMaintenanceRuleId(), rule.getId()))
                .forEach(this::seedForEffectiveRule);
    }

    private void seedForEffectiveRule(EquipmentMaintenanceEffectiveRule rule) {
        if (rule.equipmentId() == null || !rule.hasMeterTrigger()) {
            return;
        }
        if (anchorRepository.findLatestAnchor(
                rule.equipmentId(),
                rule.regulationId(),
                rule.equipmentMaintenanceRuleId()).isPresent()) {
            return;
        }
        EquipmentMeter meter = activeMeter(rule.equipmentId(), rule.triggerMeterType());
        if (meter == null) {
            return;
        }

        Instant now = clock.instant();
        MaintenanceCompletionAnchor anchor = new MaintenanceCompletionAnchor();
        anchor.setEquipmentId(rule.equipmentId());
        anchor.setRegulationId(rule.regulationId());
        anchor.setEquipmentMaintenanceRuleId(rule.equipmentMaintenanceRuleId());
        anchor.setPerformedAt(now);
        anchor.setRecalculationPolicy(rule.recalculationPolicy() == null
                ? MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION
                : rule.recalculationPolicy());
        anchor.setMeterSnapshots(toMeterSnapshotsJson(List.of(MeterBaselineSnapshot.from(meter, now))));
        anchor.setSource(INITIAL_METER_BASELINE_SOURCE);
        anchor.setNote("Initial meter baseline captured when maintenance rule became effective");
        anchorRepository.save(anchor);
    }

    private EquipmentMeter activeMeter(UUID equipmentId, MeterType meterType) {
        return safeList(equipmentMeterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId))
                .stream()
                .filter(meter -> meter.getMeterType() == meterType)
                .findFirst()
                .orElse(null);
    }

    private String toMeterSnapshotsJson(List<MeterBaselineSnapshot> snapshots) {
        try {
            return objectMapper.writeValueAsString(snapshots);
        } catch (JsonProcessingException ex) {
            throw RestException.badRequest("Invalid meter baseline snapshot");
        }
    }

    private boolean isMeterTriggered(MaintenanceRegulation regulation) {
        return regulation != null
                && regulation.isActive()
                && regulation.getTriggerMeterType() != null
                && regulation.getTriggerMeterInterval() != null
                && regulation.getTriggerMeterInterval() > 0;
    }

    private boolean isMeterTriggered(EquipmentMaintenanceRule rule) {
        return rule != null
                && rule.isActive()
                && rule.getEquipmentId() != null
                && rule.getTriggerMeterType() != null
                && rule.getTriggerMeterInterval() != null
                && rule.getTriggerMeterInterval() > 0;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record MeterBaselineSnapshot(
            UUID meterId,
            MeterType meterType,
            Double value,
            String readAt
    ) {
        static MeterBaselineSnapshot from(EquipmentMeter meter, Instant fallbackReadAt) {
            Instant readAt = meter.getLastReadAt() == null ? fallbackReadAt : meter.getLastReadAt();
            return new MeterBaselineSnapshot(
                    meter.getId(),
                    meter.getMeterType(),
                    meter.getCurrentValue(),
                    readAt.toString()
            );
        }
    }
}
