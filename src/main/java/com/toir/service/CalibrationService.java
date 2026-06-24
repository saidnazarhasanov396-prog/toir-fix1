package com.toir.service;

import com.toir.dto.calibration.CalibrationRecordDto;
import com.toir.dto.calibration.CalibrationRecordRequest;
import com.toir.entity.equipment.CalibrationRecord;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.CalibrationRecordRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CalibrationService {

    private static final Pattern CERTIFICATE_ALLOWED_PATTERN = Pattern.compile("^[A-Za-z0-9/_-]+$");
    private static final Pattern CERTIFICATE_HAS_LETTER_PATTERN = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern CERTIFICATE_HAS_DIGIT_PATTERN = Pattern.compile(".*\\d.*");

    private final CalibrationRecordRepository repo;
    private final EquipmentRepository equipmentRepository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<CalibrationRecordDto> findForEquipment(UUID equipmentId) {
        ensureEquipmentExists(equipmentId);
        return repo.findAllByEquipmentIdAndIsDeletedFalseOrderByPerformedAtDesc(equipmentId).stream()
                .map(CalibrationRecordDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CalibrationRecordDto> findAll(String search) {
        return repo.findAll(search).stream().map(CalibrationRecordDto::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<CalibrationRecordDto> search(UUID equipmentId, String search, int page, int size, Sort sort) {
        if (equipmentId != null) {
            ensureEquipmentExists(equipmentId);
        }
        return repo.findAll(
                        calibrationSpecification(equipmentId, search),
                        PaginationUtils.pageRequest(page, size, sort == null ? Sort.by(Sort.Direction.DESC, "updatedAt") : sort)
                )
                .map(CalibrationRecordDto::from);
    }

    private Specification<CalibrationRecord> calibrationSpecification(UUID equipmentId, String search) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (equipmentId != null) {
                predicates.add(cb.equal(root.get("equipmentId"), equipmentId));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("certificateNumber"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("performedBy"), "")), pattern)
                ));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public List<CalibrationRecordDto> findDueWithin(int days) {
        LocalDate cutoff = LocalDate.now().plusDays(days);
        return repo.findAllByNextDueAtBeforeAndIsDeletedFalse(cutoff).stream()
                .map(CalibrationRecordDto::from).toList();
    }

    @Transactional
    public CalibrationRecordDto create(CalibrationRecordRequest r) {
        String certificateNumber = normalizeAndValidateCertificateNumber(r.certificateNumber());
        ensureEquipmentExists(r.equipmentId());
        CalibrationRecord c = new CalibrationRecord();
        c.setEquipmentId(r.equipmentId());
        c.setCertificateNumber(certificateNumber);
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

        auditBuilderService.log(
                "calibration_record",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.CALIBRATION_RECORD,
                "Запись калибровки создана",
                null,
                saved
        );


        return CalibrationRecordDto.from(saved);
    }

    @Transactional
    public CalibrationRecordDto update(UUID id, CalibrationRecordRequest r) {
        CalibrationRecord c = repo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Calibration record not found: " + id));
        String certificateNumber = normalizeAndValidateCertificateNumber(r.certificateNumber());
        ensureEquipmentExists(r.equipmentId());
        c.setEquipmentId(r.equipmentId());
        c.setCertificateNumber(certificateNumber);
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

        auditBuilderService.log(
                "calibration_record",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.CALIBRATION_RECORD,
                "Запись калибровки удалена",
                c,
                saved
        );
        return CalibrationRecordDto.from(c);
    }

    @Transactional
    public void delete(UUID id) {
        CalibrationRecord c = repo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Calibration record not found: " + id));
        c.setDeleted(true);
        CalibrationRecord saved = repo.save(c);

        auditBuilderService.log(
                "calibration_record",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.CALIBRATION_RECORD,
                "Запись калибровки удалена",
                c,
                null
        );
    }

    public CalibrationRecordDto findById(UUID id) {
        return repo.findById(id)
                .filter(c -> !c.isDeleted())
                .map(CalibrationRecordDto::from)
                .orElseThrow(() -> RestException.notFound("Calibration record not found: " + id));
    }

    private void ensureEquipmentExists(UUID equipmentId) {
        if (!equipmentRepository.existsByIdAndIsDeletedFalse(equipmentId)) {
            throw RestException.notFound("Equipment not found: " + equipmentId);
        }
    }

    private String normalizeAndValidateCertificateNumber(String certificateNumber) {
        if (certificateNumber == null || certificateNumber.isBlank()) {
            throw RestException.badRequest("certificateNumber is required");
        }
        String normalized = certificateNumber.trim();
        if (normalized.length() < 3 || normalized.length() > 64) {
            throw RestException.badRequest("certificateNumber length must be between 3 and 64");
        }
        if (!CERTIFICATE_ALLOWED_PATTERN.matcher(normalized).matches()) {
            throw RestException.badRequest("certificateNumber contains invalid characters");
        }
        if (!CERTIFICATE_HAS_LETTER_PATTERN.matcher(normalized).matches()
                || !CERTIFICATE_HAS_DIGIT_PATTERN.matcher(normalized).matches()) {
            throw RestException.badRequest("certificateNumber must contain at least one letter and one digit");
        }
        return normalized;
    }
}
