package com.toir.service;
import com.toir.entity.CertificationType;
import com.toir.entity.users.UserCertification;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.CertificationTypeRepository;
import com.toir.repository.users.UserCertificationRepository;

import com.toir.dto.certification.CertificationTypeDto;
import com.toir.dto.certification.UserCertificationDto;
import com.toir.dto.certification.UserCertificationRequest;
import com.toir.exception.RestException;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CertificationService {

    private final CertificationTypeRepository typeRepo;
    private final UserCertificationRepository certRepo;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;

    // types
    @Transactional(readOnly = true)
    public List<CertificationTypeDto> findTypes(String code, String name,String search) {
        return typeRepo.findAllByIsDeletedFalse(code,name,search).stream().map(CertificationTypeDto::from).toList();
    }

    public CertificationTypeDto createType(CertificationTypeDto r) {
        if (typeRepo.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("Certification type code already exists: " + r.code());
        }
        CertificationType t = new CertificationType();
        applyType(t, r);
        CertificationType saved = typeRepo.save(t);
        auditType(AuditAction.CREATE, saved.getId(), null, saved);
        return CertificationTypeDto.from(saved);
    }

    public CertificationTypeDto updateType(UUID id, CertificationTypeDto r) {
        CertificationType t = typeRepo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Certification type not found: " + id));
        String oldJson = auditSerializationService.toJson(t);
        applyType(t, r);
        auditType(AuditAction.UPDATE, t.getId(), oldJson, t);
        return CertificationTypeDto.from(t);
    }

    public void deleteType(UUID id) {
        var entity = typeRepo.findByIdAndIsDeletedFalse(id).orElseThrow();
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        CertificationType saved = typeRepo.save(entity);
        auditType(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    // user certifications
    @Transactional(readOnly = true)
    public List<UserCertificationDto> findForUser(UUID userId) {
        return certRepo.findAllByUserIdAndIsDeletedFalse(userId).stream().map(UserCertificationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<UserCertificationDto> findExpiring(int withinDays) {
        LocalDate cutoff = LocalDate.now().plusDays(withinDays);
        return certRepo.findAllByExpiresAtBeforeAndIsDeletedFalse(cutoff).stream()
                .filter(c -> "ACTIVE".equals(c.getStatus()) || "EXPIRED".equals(c.getStatus()))
                .map(UserCertificationDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserCertificationDto> findAll(String search) {
        return certRepo.findUserCertifications(search).stream().map(UserCertificationDto::from).toList();
    }

    public UserCertificationDto issue(UserCertificationRequest r) {
        CertificationType type = typeRepo.findByCodeAndIsDeletedFalse(r.typeCode());
        if (type == null) {
            throw RestException.notFound("Certification type not found: " + r.typeCode());
        }
        UserCertification c = new UserCertification();
        c.setUserId(r.userId());
        c.setTypeCode(r.typeCode());
        c.setCertificateNumber(r.certificateNumber());
        c.setIssuedBy(r.issuedBy());
        c.setIssuedAt(r.issuedAt());
        if (r.expiresAt() != null) {
            c.setExpiresAt(r.expiresAt());
        } else if (type.getValidityMonths() != null) {
            c.setExpiresAt(r.issuedAt().plusMonths(type.getValidityMonths()));
        }
        c.setGradeOrLevel(r.gradeOrLevel());
        c.setDocumentFileId(r.documentFileId());
        c.setNotes(r.notes());
        c.setStatus("ACTIVE");
        UserCertification saved = certRepo.save(c);
        auditCertification(AuditAction.CREATE, saved.getId(), null, saved);
        return UserCertificationDto.from(saved);
    }

    public UserCertificationDto suspend(UUID id, String reason) {
        UserCertification c = load(id);
        String oldJson = auditSerializationService.toJson(c);
        c.setStatus("SUSPENDED");
        c.setNotes(reason);
        auditCertification(AuditAction.UPDATE, c.getId(), oldJson, c);
        return UserCertificationDto.from(c);
    }

    public UserCertificationDto revoke(UUID id, String reason) {
        UserCertification c = load(id);
        String oldJson = auditSerializationService.toJson(c);
        c.setStatus("REVOKED");
        c.setNotes(reason);
        auditCertification(AuditAction.UPDATE, c.getId(), oldJson, c);
        return UserCertificationDto.from(c);
    }

    /** Идемпотентный пересчёт статусов: ACTIVE → EXPIRED для просроченных. */
    public int markExpired() {
        LocalDate today = LocalDate.now();
        int count = 0;
        for (UserCertification c : certRepo.findAllByStatusAndIsDeletedFalse("ACTIVE")) {
            if (c.getExpiresAt() != null && c.getExpiresAt().isBefore(today)) {
                String oldJson = auditSerializationService.toJson(c);
                c.setStatus("EXPIRED");
                auditCertification(AuditAction.UPDATE, c.getId(), oldJson, c);
                count++;
            }
        }
        return count;
    }

    private UserCertification load(UUID id) {
        return certRepo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("User certification not found: " + id));
    }

    private void applyType(CertificationType t, CertificationTypeDto r) {
        t.setCode(r.code());
        t.setName(r.name());
        t.setNameEn(r.nameEn());
        t.setNameUz(r.nameUz());
        t.setValidityMonths(r.validityMonths());
        if (r.category() != null) t.setCategory(r.category());
        t.setDescription(r.description());
    }

    private void auditType(AuditAction action, UUID id, String oldJson, CertificationType current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "certification_type",
                id != null ? id.toString() : null,
                action,
                AuditModule.CERTIFICATION_TYPE,
                auditTypeMessage(action),
                oldJson,
                newJson
        );
    }

    private void auditCertification(AuditAction action, UUID id, String oldJson, UserCertification current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "user_certification",
                id != null ? id.toString() : null,
                action,
                AuditModule.USER_CERTIFICATION,
                auditCertificationMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditTypeMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Тип сертификации создан";
            case UPDATE -> "Тип сертификации обновлен";
            case DELETE -> "Тип сертификации удален";
            default -> "Действие выполнено над типом сертификации";
        };
    }

    private String auditCertificationMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Сертификация пользователя создана";
            case UPDATE -> "Сертификация пользователя обновлена";
            case DELETE -> "Сертификация пользователя удалена";
            default -> "Действие выполнено над сертификацией пользователя";
        };
    }

    public UserCertificationDto findOne(UUID id) {
        return certRepo.findAllByUserIdAndIsDeletedFalse(id).stream().findFirst().map(UserCertificationDto::from)
                .orElseThrow(() -> RestException.notFound("User certification not found: " + id));
    }
}
