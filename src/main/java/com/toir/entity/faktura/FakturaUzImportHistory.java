package com.toir.entity.faktura;

import com.toir.entity.BaseEntity;
import com.toir.enums.FakturaUzDocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "faktura_uz_import_history")
public class FakturaUzImportHistory extends BaseEntity {
    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private FakturaUzDocumentType documentType;
    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;
    @Column(columnDefinition = "text")
    private String description;
    @Column(name = "import_request_date_from")
    private LocalDate importRequestDateFrom;
    @Column(name = "import_request_date_to")
    private LocalDate importRequestDateTo;
    @Column(name = "total_data_count_in_request")
    private Long totalDataCountInRequest;
    @Column(name = "total_saved_data_count")
    private Long totalSavedDataCount;
    @Column(name = "total_updated_data_count")
    private Long totalUpdatedDataCount;
    @Column(name = "total_failed_data_count")
    private Long totalFailedDataCount;
}
