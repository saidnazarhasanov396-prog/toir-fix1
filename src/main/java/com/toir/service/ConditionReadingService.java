package com.toir.service;

import com.toir.dto.conditionreading.ConditionReadingDto;
import com.toir.dto.conditionreading.ConditionReadingRequest;
import com.toir.entity.ConditionReading;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.ConditionParameter;
import com.toir.enums.DefectStatus;
import com.toir.exception.RestException;
import com.toir.repository.ConditionReadingRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ConditionReadingService {

    private final ConditionReadingRepository repo;
    private final EquipmentRepository equipmentRepository;
    private final DefectRepository defectRepository;
    private final WebhookService webhookService;
    private final AuditBuilderService auditBuilderService;



    @Transactional(readOnly = true)
    public List<ConditionReadingDto> findForEquipment(UUID equipmentId, ConditionParameter parameter) {
        List<ConditionReading> list = parameter != null
                ? repo.findAllByEquipmentIdAndParameterAndIsDeletedFalseOrderByRecordedAtDesc(equipmentId, parameter)
                : repo.findAllByEquipmentIdAndIsDeletedFalseOrderByRecordedAtDesc(equipmentId);
        return list.stream().map(ConditionReadingDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ConditionReadingDto> findAlarms() {
        List<ConditionReading> warn = repo.findAllBySeverityAndIsDeletedFalseOrderByRecordedAtDesc("WARN");
        List<ConditionReading> alarm = repo.findAllBySeverityAndIsDeletedFalseOrderByRecordedAtDesc("ALARM");
        return java.util.stream.Stream.concat(alarm.stream(), warn.stream()).toList().stream()
                .map(ConditionReadingDto::from).toList();
    }

    @Transactional
    public ConditionReadingDto record(UUID equipmentId, ConditionReadingRequest r, UUID userId) {
        Equipment eq = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
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

        auditBuilderService.log(
                "condition_reading",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.CONDITION_READING,
                "Показание состояния создано",
                null,
                saved
        );

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

    @Transactional
    public void delete(UUID id) {
        ConditionReading cr = repo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Condition reading not found: " + id));
        cr.setDeleted(true);
        ConditionReading saved = repo.save(cr);

        auditBuilderService.log(
                "condition_reading",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.CONDITION_READING,
                "Показание состояния удалено",
                cr,
                saved
        );

    }

    private String computeSeverity(double value, Double warnHigh, Double alarmHigh, Double warnLow, Double alarmLow) {
        if (alarmHigh != null && value > alarmHigh) return "ALARM";
        if (alarmLow != null && value < alarmLow) return "ALARM";
        if (warnHigh != null && value > warnHigh) return "WARN";
        if (warnLow != null && value < warnLow) return "WARN";
        return "OK";
    }
}
