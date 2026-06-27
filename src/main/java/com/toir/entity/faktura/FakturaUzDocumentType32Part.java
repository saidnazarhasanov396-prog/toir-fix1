package com.toir.entity.faktura;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "faktura_uz_doc32_parts")
public class FakturaUzDocumentType32Part extends BaseEntity {
    @Column(name = "document_unique_id", nullable = false)
    private String documentUniqueId;
    private String number;
    @Column(columnDefinition = "text")
    private String title;
    @Column(columnDefinition = "text")
    private String body;
}
