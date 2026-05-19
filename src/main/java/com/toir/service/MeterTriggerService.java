package com.toir.service;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;

import com.toir.dto.meter.MeterTriggerMatch;

import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.exception.RestException;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeterTriggerService {

    private final EquipmentMeterRepository meterRepository;
    private final EquipmentRepository equipmentRepository;
    private final MaintenanceRegulationRepository regulationRepository;



    public List<MeterTriggerMatch> dueTriggers(UUID equipmentId) {
        if (equipmentId == null) {
            throw RestException.badRequest("equipmentId or equipmentSearch is required");
        }
        List<EquipmentMeter> meters = meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId);
        List<MaintenanceRegulation> regs = regulationRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(r -> r.isActive() && r.getTriggerMeterType() != null
                        && r.getTriggerMeterInterval() != null && r.getTriggerMeterInterval() > 0)
                .toList();
        List<MeterTriggerMatch> result = new ArrayList<>();
        for (MaintenanceRegulation reg : regs) {
            for (EquipmentMeter meter : meters) {
                if (meter.getMeterType() != reg.getTriggerMeterType()) continue;
                double interval = reg.getTriggerMeterInterval();
                double current = meter.getCurrentValue();
                double elapsedSinceAnchor = current % interval;
                double remaining = interval - elapsedSinceAnchor;
                boolean due = remaining <= (interval * 0.05);
                result.add(new MeterTriggerMatch(
                        reg.getId(), reg.getCode(), reg.getName(),
                        meter.getId(), meter.getMeterType(), current,
                        interval, remaining, due));
            }
        }
        return result;
    }

    public List<MeterTriggerMatch> dueTriggers(UUID equipmentId, String equipmentSearch) {
        if (equipmentId != null) {
            return dueTriggers(equipmentId);
        }
        if (equipmentSearch == null || equipmentSearch.isBlank()) {
            throw RestException.badRequest("equipmentId or equipmentSearch is required");
        }
        String pattern = "%" + equipmentSearch.trim().toLowerCase() + "%";
        List<UUID> equipmentIds = equipmentRepository.findIdsByBusinessSearch(pattern);
        if (equipmentIds.isEmpty()) {
            return List.of();
        }
        return dueTriggers(equipmentIds.get(0));
    }

}
