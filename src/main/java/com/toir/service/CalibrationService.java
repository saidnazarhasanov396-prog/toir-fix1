package com.toir.service;
import com.toir.entity.equipment.CalibrationRecord;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.CalibrationRecordRepository;

import com.toir.dto.calibration.CalibrationRecordDto;
import com.toir.dto.calibration.CalibrationRecordRequest;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static java.util.Locale.filter;

@Service
@Transactional
@RequiredArgsConstructor
public class CalibrationService {

    private final CalibrationRecordRepository repo;
    private final EquipmentRepository equipmentRepository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;

    @Transactional(readOnly = true)
    public List<CalibrationRecordDto> findForEquipment(UUID equipmentId) {
        return repo.findAllByEquipmentIdAndIsDeletedFalseOrderByPerformedAtDesc(equipmentId).stream()
                .map(CalibrationRecordDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CalibrationRecordDto> findAll(String search) {
        return repo.findAll(search).stream().map(CalibrationRecordDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CalibrationRecordDto> findDueWithin(int days) {
        LocalDate cutoff = LocalDate.now().plusDays(days);
        return repo.findAllByNextDueAtBeforeAndIsDeletedFalse(cutoff).stream()
                .map(CalibrationRecordDto::from).toList();
    }

    public CalibrationRecordDto create(CalibrationRecordRequest r) {
        if (!equipmentRepository.existsByIdAndIsDeletedFalse(r.equipmentId())) {
            throw RestException.notFound("Equipment not found: " + r.equipmentId());
        }
        CalibrationRecord c = new CalibrationRecord();
        c.setEquipmentId(r.equipmentId());
        c.setCertificateNumber(r.certificateNumber());
        c.setPerformedBy(r.performedBy());
        c.setPerformedAt(r.performedAt());
        c.setNextDueAt(r.nextDueAt());
        if (r.result() != null) c.setResult(r.result());
        c.setTolerance(r.tolerance());
        c.setMeasuredError(r.measuredError());
        c.setUnit(r.unit());
        c.setDocumentFileId(r.documentFileId());
        c.setNotes(r.notes());
        CalibrationRecord saved = repo.save(c);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return CalibrationRecordDto.from(saved);
    }

    public CalibrationRecordDto update(UUID id, CalibrationRecordRequest r) {
        CalibrationRecord c = repo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Calibration record not found: " + id));
        String oldJson = auditSerializationService.toJson(c);
        if (!equipmentRepository.existsByIdAndIsDeletedFalse(r.equipmentId())) {
            throw RestException.notFound("Equipment not found: " + r.equipmentId());
        }
        c.setEquipmentId(r.equipmentId());
        c.setCertificateNumber(r.certificateNumber());
        c.setPerformedBy(r.performedBy());
        c.setPerformedAt(r.performedAt());
        c.setNextDueAt(r.nextDueAt());
        if (r.result() != null) c.setResult(r.result());
        c.setTolerance(r.tolerance());
        c.setMeasuredError(r.measuredError());
        c.setUnit(r.unit());
        c.setDocumentFileId(r.documentFileId());
        c.setNotes(r.notes());
        audit(AuditAction.UPDATE, c.getId(), oldJson, c);
        return CalibrationRecordDto.from(c);
    }

    public void delete(UUID id) {
        CalibrationRecord c = repo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Calibration record not found: " + id));
        String oldJson = auditSerializationService.toJson(c);
        c.setDeleted(true);
        CalibrationRecord saved = repo.save(c);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    public CalibrationRecordDto findById(UUID id) {
        return repo.findById(id)
                .filter(c -> !c.isDeleted())
                .map(CalibrationRecordDto::from)
                .orElseThrow(() -> RestException.notFound("Calibration record not found: " + id));
    }

    private void audit(AuditAction action, UUID id, String oldJson, CalibrationRecord current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "calibration_record",
                id != null ? id.toString() : null,
                action,
                AuditModule.CALIBRATION_RECORD,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Запись калибровки создана";
            case UPDATE -> "Запись калибровки обновлена";
            case DELETE -> "Запись калибровки удалена";
            default -> "Действие выполнено над записью калибровки";
        };
    }
}
