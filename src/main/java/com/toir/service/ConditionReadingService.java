package com.toir.service;
import com.toir.enums.ConditionParameter;
import com.toir.entity.ConditionReading;
import com.toir.repository.ConditionReadingRepository;

import com.toir.exception.RestException;
import com.toir.dto.conditionreading.ConditionReadingDto;
import com.toir.dto.conditionreading.ConditionReadingRequest;
import com.toir.entity.Defect;
import com.toir.repository.DefectRepository;
import com.toir.enums.DefectStatus;
import com.toir.entity.Equipment;
import com.toir.repository.EquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ConditionReadingService {

    private final ConditionReadingRepository repo;
    private final EquipmentRepository equipmentRepository;
    private final DefectRepository defectRepository;
    private final WebhookService webhookService;

    public ConditionReadingService(ConditionReadingRepository repo,
                                   EquipmentRepository equipmentRepository,
                                   DefectRepository defectRepository,
                                   WebhookService webhookService) {
        this.repo = repo;
        this.equipmentRepository = equipmentRepository;
        this.defectRepository = defectRepository;
        this.webhookService = webhookService;
    }

    @Transactional(readOnly = true)
    public List<ConditionReadingDto> findForEquipment(UUID equipmentId, ConditionParameter parameter) {
        List<ConditionReading> list = parameter != null
                ? repo.findAllByEquipmentIdAndParameterOrderByRecordedAtDesc(equipmentId, parameter)
                : repo.findAllByEquipmentIdOrderByRecordedAtDesc(equipmentId);
        return list.stream().map(ConditionReadingDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ConditionReadingDto> findAlarms() {
        List<ConditionReading> warn = repo.findAllBySeverityOrderByRecordedAtDesc("WARN");
        List<ConditionReading> alarm = repo.findAllBySeverityOrderByRecordedAtDesc("ALARM");
        return java.util.stream.Stream.concat(alarm.stream(), warn.stream())
                .map(ConditionReadingDto::from).toList();
    }

    public ConditionReadingDto record(UUID equipmentId, ConditionReadingRequest r, UUID userId) {
        Equipment eq = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        ConditionReading cr = new ConditionReading();
        cr.setEquipmentId(eq.getId());
        cr.setParameter(r.parameter());
        cr.setValue(r.value());
        cr.setUnit(r.unit());
        cr.setRecordedAt(r.recordedAt() != null ? r.recordedAt() : Instant.now());
        cr.setRecordedBy(userId);
        cr.setWarnHigh(r.warnHigh());
        cr.setAlarmHigh(r.alarmHigh());
        cr.setWarnLow(r.warnLow());
        cr.setAlarmLow(r.alarmLow());
        cr.setSeverity(computeSeverity(r.value(), r.warnHigh(), r.alarmHigh(), r.warnLow(), r.alarmLow()));
        cr.setNotes(r.notes());
        ConditionReading saved = repo.save(cr);
        if ("ALARM".equals(saved.getSeverity())) {
            autoCreateDefect(eq.getId(), saved);
            webhookService.publish("CONDITION_ALARM", ConditionReadingDto.from(saved));
        } else if ("WARN".equals(saved.getSeverity())) {
            webhookService.publish("CONDITION_WARN", ConditionReadingDto.from(saved));
        }
        return ConditionReadingDto.from(saved);
    }

    private void autoCreateDefect(UUID equipmentId, ConditionReading cr) {
        Defect d = new Defect();
        d.setCode("AUTO-CM-" + Instant.now().toEpochMilli());
        d.setTitle("Auto: " + cr.getParameter() + " ALARM");
        d.setDescription("Превышение аварийного порога по параметру " + cr.getParameter()
                + ": value=" + cr.getValue() + " " + cr.getUnit()
                + (cr.getAlarmHigh() != null ? ", alarmHigh=" + cr.getAlarmHigh() : "")
                + (cr.getAlarmLow() != null ? ", alarmLow=" + cr.getAlarmLow() : ""));
        d.setEquipmentId(equipmentId);
        d.setCategory("CONDITION_MONITORING");
        d.setSeverity("CRITICAL");
        d.setStatus(DefectStatus.OPEN);
        defectRepository.save(d);
    }

    public void delete(UUID id) {
        ConditionReading cr = repo.findById(id)
                .orElseThrow(() -> RestException.notFound("Condition reading not found: " + id));
        repo.delete(cr);
    }

    private String computeSeverity(double value, Double warnHigh, Double alarmHigh, Double warnLow, Double alarmLow) {
        if (alarmHigh != null && value > alarmHigh) return "ALARM";
        if (alarmLow != null && value < alarmLow) return "ALARM";
        if (warnHigh != null && value > warnHigh) return "WARN";
        if (warnLow != null && value < warnLow) return "WARN";
        return "OK";
    }
}
