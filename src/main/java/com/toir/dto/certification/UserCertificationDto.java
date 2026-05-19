package com.toir.dto.certification;

import com.toir.entity.users.UserCertification;

import java.time.LocalDate;
import java.util.UUID;

public record UserCertificationDto(
        UUID id,
        UUID userId,
        String typeCode,
        String certificateNumber,
        String issuedBy,
        LocalDate issuedAt,
        LocalDate expiresAt,
        String gradeOrLevel,
        String status,
        UUID documentFileId,
        String notes,
        Integer daysUntilExpiry
) {
    public static UserCertificationDto from(UserCertification c) {
        Integer days = null;
        if (c.getExpiresAt() != null) {
            days = (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), c.getExpiresAt());
        }
        return new UserCertificationDto(
                c.getId(), c.getUserId(), c.getTypeCode(), c.getCertificateNumber(),
                c.getIssuedBy(), c.getIssuedAt(), c.getExpiresAt(), c.getGradeOrLevel(),
                c.getStatus(), c.getDocumentFileId(), c.getNotes(), days
        );
    }
}
