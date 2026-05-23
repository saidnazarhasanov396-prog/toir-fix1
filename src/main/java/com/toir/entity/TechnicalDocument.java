package com.toir.entity;
import com.toir.enums.DocumentType;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "technical_documents")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TechnicalDocument extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "equipment_node_id")
    private UUID equipmentNodeId;

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

}
