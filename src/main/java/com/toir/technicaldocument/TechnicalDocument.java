package com.toir.technicaldocument;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "technical_documents")
public class TechnicalDocument extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "file_id")
    private UUID fileId;

    @Column(nullable = false)
    private String title;

    private String revision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType type;

    @Column(name = "document_date")
    private LocalDate documentDate;

    @Column(name = "uploaded_by_id")
    private UUID uploadedById;

    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public UUID getFileId() { return fileId; }
    public void setFileId(UUID fileId) { this.fileId = fileId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getRevision() { return revision; }
    public void setRevision(String revision) { this.revision = revision; }
    public DocumentType getType() { return type; }
    public void setType(DocumentType type) { this.type = type; }
    public LocalDate getDocumentDate() { return documentDate; }
    public void setDocumentDate(LocalDate documentDate) { this.documentDate = documentDate; }
    public UUID getUploadedById() { return uploadedById; }
    public void setUploadedById(UUID uploadedById) { this.uploadedById = uploadedById; }
}
