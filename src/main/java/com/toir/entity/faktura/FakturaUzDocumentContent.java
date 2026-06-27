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
@Table(name = "faktura_uz_document_contents")
public class FakturaUzDocumentContent extends BaseEntity {
    @Column(name = "document_unique_id", nullable = false, unique = true)
    private String documentUniqueId;
    @Column(name = "roaming_uid")
    private String roamingUid;
    private Integer type;
    @Column(name = "raw_content_json", columnDefinition = "text")
    private String rawContentJson;
}
