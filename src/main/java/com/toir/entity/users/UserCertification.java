package com.toir.entity.users;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Сертификат/аттестация сотрудника. По ТЗ §4.2.17 — учёт допусков к работам
 * (сварка, высота, электробезопасность и пр.) с отслеживанием срока действия.
 */
@Entity
@Table(name = "user_certifications",
        indexes = {
                @Index(name = "ix_user_cert_user_type", columnList = "user_id,type_code"),
                @Index(name = "ix_user_cert_expires", columnList = "expires_at")
        })
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserCertification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "type_code", nullable = false)
    private String typeCode;

    @Column(name = "certificate_number")
    private String certificateNumber;

    @Column(name = "issued_by")
    private String issuedBy;

    @Column(name = "issued_at", nullable = false)
    private LocalDate issuedAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "grade_or_level")
    private String gradeOrLevel;

    /** ACTIVE / EXPIRED / SUSPENDED / REVOKED. */
    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "document_file_id")
    private UUID documentFileId;

    @Column(columnDefinition = "text")
    private String notes;

}
