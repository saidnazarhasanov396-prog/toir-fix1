package com.toir.certification;

import com.toir.certification.dto.CertificationTypeDto;
import com.toir.certification.dto.UserCertificationDto;
import com.toir.certification.dto.UserCertificationRequest;
import com.toir.common.exception.RestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CertificationService {

    private final CertificationTypeRepository typeRepo;
    private final UserCertificationRepository certRepo;

    public CertificationService(CertificationTypeRepository typeRepo, UserCertificationRepository certRepo) {
        this.typeRepo = typeRepo;
        this.certRepo = certRepo;
    }

    // types
    @Transactional(readOnly = true)
    public List<CertificationTypeDto> findTypes() {
        return typeRepo.findAll().stream().map(CertificationTypeDto::from).toList();
    }

    public CertificationTypeDto createType(CertificationTypeDto r) {
        if (typeRepo.existsByCode(r.code())) {
            throw RestException.conflict("Certification type code already exists: " + r.code());
        }
        CertificationType t = new CertificationType();
        applyType(t, r);
        return CertificationTypeDto.from(typeRepo.save(t));
    }

    public CertificationTypeDto updateType(UUID id, CertificationTypeDto r) {
        CertificationType t = typeRepo.findById(id)
                .orElseThrow(() -> RestException.notFound("Certification type not found: " + id));
        applyType(t, r);
        return CertificationTypeDto.from(t);
    }

    public void deleteType(UUID id) {
        typeRepo.deleteById(id);
    }

    // user certifications
    @Transactional(readOnly = true)
    public List<UserCertificationDto> findForUser(UUID userId) {
        return certRepo.findAllByUserId(userId).stream().map(UserCertificationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<UserCertificationDto> findExpiring(int withinDays) {
        LocalDate cutoff = LocalDate.now().plusDays(withinDays);
        return certRepo.findAllByExpiresAtBefore(cutoff).stream()
                .filter(c -> "ACTIVE".equals(c.getStatus()) || "EXPIRED".equals(c.getStatus()))
                .map(UserCertificationDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserCertificationDto> findAll() {
        return certRepo.findAll().stream().map(UserCertificationDto::from).toList();
    }

    public UserCertificationDto issue(UserCertificationRequest r) {
        CertificationType type = typeRepo.findByCode(r.typeCode());
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
        return UserCertificationDto.from(certRepo.save(c));
    }

    public UserCertificationDto suspend(UUID id, String reason) {
        UserCertification c = load(id);
        c.setStatus("SUSPENDED");
        c.setNotes(reason);
        return UserCertificationDto.from(c);
    }

    public UserCertificationDto revoke(UUID id, String reason) {
        UserCertification c = load(id);
        c.setStatus("REVOKED");
        c.setNotes(reason);
        return UserCertificationDto.from(c);
    }

    /** Идемпотентный пересчёт статусов: ACTIVE → EXPIRED для просроченных. */
    public int markExpired() {
        LocalDate today = LocalDate.now();
        int count = 0;
        for (UserCertification c : certRepo.findAllByStatus("ACTIVE")) {
            if (c.getExpiresAt() != null && c.getExpiresAt().isBefore(today)) {
                c.setStatus("EXPIRED");
                count++;
            }
        }
        return count;
    }

    private UserCertification load(UUID id) {
        return certRepo.findById(id)
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
}
