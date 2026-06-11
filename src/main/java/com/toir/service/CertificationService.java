package com.toir.service;

import com.toir.dto.certification.CertificationTypeDto;
import com.toir.dto.certification.UserCertificationDto;
import com.toir.dto.certification.UserCertificationRequest;
import com.toir.entity.CertificationType;
import com.toir.entity.users.UserCertification;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.CertificationTypeRepository;
import com.toir.repository.users.UserCertificationRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.CodeGenerationUtils;
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

    // types
    @Transactional(readOnly = true)
    public List<CertificationTypeDto> findTypes(String code, String name,String search) {
        return typeRepo.findAllByIsDeletedFalse(code,name,search).stream().map(CertificationTypeDto::from).toList();
    }

    @Transactional
    public CertificationTypeDto createType(CertificationTypeDto r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        CertificationType t = new CertificationType();
        t.setCode(nextTypeCode());
        applyType(t, r);
        CertificationType saved = typeRepo.save(t);

        auditBuilderService.log(
                "certification_type",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.CERTIFICATION_TYPE,
                "Тип сертификации создан",
                null,
                saved
        );
        return CertificationTypeDto.from(saved);
    }

    @Transactional
    public CertificationTypeDto updateType(UUID id, CertificationTypeDto r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        CertificationType t = typeRepo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Certification type not found: " + id));

        applyType(t, r);

        CertificationType save = typeRepo.save(t);

        auditBuilderService.log(
                "certification_type",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.CERTIFICATION_TYPE,
                "Тип сертификации обновлен",
                t,
                save
        );
        return CertificationTypeDto.from(save);
    }

    @Transactional
    public void deleteType(UUID id) {
        var entity = typeRepo.findByIdAndIsDeletedFalse(id).orElseThrow();
        entity.setDeleted(true);
        CertificationType saved = typeRepo.save(entity);

        auditBuilderService.log(
                "certification_type",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.CERTIFICATION_TYPE,
                "Тип сертификации удален",
                entity,
                null
        );
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

        auditBuilderService.log(
                "user_certification",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.USER_CERTIFICATION,
                "Сертификация пользователя создана",
                null,
                saved
        );

        return UserCertificationDto.from(saved);
    }

    @Transactional
    public UserCertificationDto suspend(UUID id, String reason) {
        UserCertification c = load(id);
        c.setStatus("SUSPENDED");
        c.setNotes(reason);

        UserCertification save = certRepo.save(c);

        auditBuilderService.log(
                "user_certification",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.USER_CERTIFICATION,
                "Сертификация пользователя обновлена",
                c,
                save
        );
        return UserCertificationDto.from(c);
    }

    public UserCertificationDto revoke(UUID id, String reason) {
        UserCertification c = load(id);

        c.setStatus("REVOKED");
        c.setNotes(reason);

        UserCertification save = certRepo.save(c);

        auditBuilderService.log(
                "user_certification",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.USER_CERTIFICATION,
                "Сертификация пользователя обновлена",
                c,
                save
        );
        return UserCertificationDto.from(c);
    }

    /** Идемпотентный пересчёт статусов: ACTIVE → EXPIRED для просроченных. */
    public int markExpired() {
        LocalDate today = LocalDate.now();
        int count = 0;
        for (UserCertification c : certRepo.findAllByStatusAndIsDeletedFalse("ACTIVE")) {
            if (c.getExpiresAt() != null && c.getExpiresAt().isBefore(today)) {
                c.setStatus("EXPIRED");
                UserCertification save = certRepo.save(c);

                auditBuilderService.log(
                        "user_certification",
                        save.getId().toString(),
                        AuditAction.UPDATE,
                        AuditModule.USER_CERTIFICATION,
                        "Сертификация пользователя обновлена",
                        c,
                        save
                );
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
        t.setName(r.name());
        t.setNameEn(r.nameEn());
        t.setNameUz(r.nameUz());
        t.setValidityMonths(r.validityMonths());
        if (r.category() != null) t.setCategory(r.category());
        t.setDescription(r.description());
    }

    private String nextTypeCode() {
        String prefix = "CERT-" + java.time.Year.now().getValue() + "-";
        return CodeGenerationUtils.nextYearSequenceCode(
                "CERT",
                () -> typeRepo.maxSequenceByCodePrefix(prefix),
                typeRepo::existsByCodeAndIsDeletedFalse
        );
    }

    public UserCertificationDto findOne(UUID id) {
        return certRepo.findAllByUserIdAndIsDeletedFalse(id).stream().findFirst().map(UserCertificationDto::from)
                .orElseThrow(() -> RestException.notFound("User certification not found: " + id));
    }
}
