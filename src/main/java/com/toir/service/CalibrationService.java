package com.toir.service;
import com.toir.entity.CalibrationRecord;
import com.toir.entity.Equipment;
import com.toir.repository.CalibrationRecordRepository;

import com.toir.dto.calibration.CalibrationRecordDto;
import com.toir.dto.calibration.CalibrationRecordRequest;
import com.toir.exception.RestException;
import com.toir.repository.EquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CalibrationService {

    private final CalibrationRecordRepository repo;
    private final EquipmentRepository equipmentRepository;

    public CalibrationService(CalibrationRecordRepository repo, EquipmentRepository equipmentRepository) {
        this.repo = repo;
        this.equipmentRepository = equipmentRepository;
    }

    @Transactional(readOnly = true)
    public List<CalibrationRecordDto> findForEquipment(UUID equipmentId) {
        return repo.findAllByEquipmentIdOrderByPerformedAtDesc(equipmentId).stream()
                .map(CalibrationRecordDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CalibrationRecordDto> findAll() {
        return repo.findAll().stream().map(CalibrationRecordDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CalibrationRecordDto> findDueWithin(int days) {
        LocalDate cutoff = LocalDate.now().plusDays(days);
        return repo.findAllByNextDueAtBefore(cutoff).stream()
                .map(CalibrationRecordDto::from).toList();
    }

    public CalibrationRecordDto create(CalibrationRecordRequest r) {
        if (!equipmentRepository.existsById(r.equipmentId())) {
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
        return CalibrationRecordDto.from(repo.save(c));
    }

    public void delete(UUID id) {
        CalibrationRecord c = repo.findById(id)
                .orElseThrow(() -> RestException.notFound("Calibration record not found: " + id));
        repo.delete(c);
    }
}
