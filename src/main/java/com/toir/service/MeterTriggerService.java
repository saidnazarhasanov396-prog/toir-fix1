package com.toir.service;
import com.toir.entity.EquipmentMeter;
import com.toir.repository.EquipmentMeterRepository;

import com.toir.dto.meter.MeterTriggerMatch;

import com.toir.entity.MaintenanceRegulation;
import com.toir.repository.MaintenanceRegulationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeterTriggerService {

    private final EquipmentMeterRepository meterRepository;
    private final MaintenanceRegulationRepository regulationRepository;



    public List<MeterTriggerMatch> dueTriggers(UUID equipmentId) {
        List<EquipmentMeter> meters = meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId);
        List<MaintenanceRegulation> regs = com.toir.util.UpdatedAtSorter.descending(regulationRepository.findAllByIsDeletedFalse()).stream()
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

}
