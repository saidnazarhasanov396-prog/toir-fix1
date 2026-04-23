package com.toir.entity;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

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

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public String getCertificateNumber() { return certificateNumber; }
    public void setCertificateNumber(String certificateNumber) { this.certificateNumber = certificateNumber; }
    public String getIssuedBy() { return issuedBy; }
    public void setIssuedBy(String issuedBy) { this.issuedBy = issuedBy; }
    public LocalDate getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDate issuedAt) { this.issuedAt = issuedAt; }
    public LocalDate getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDate expiresAt) { this.expiresAt = expiresAt; }
    public String getGradeOrLevel() { return gradeOrLevel; }
    public void setGradeOrLevel(String gradeOrLevel) { this.gradeOrLevel = gradeOrLevel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getDocumentFileId() { return documentFileId; }
    public void setDocumentFileId(UUID documentFileId) { this.documentFileId = documentFileId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
