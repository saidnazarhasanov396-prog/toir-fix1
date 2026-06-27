package com.toir.entity.faktura;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "faktura_uz_documents")
public class FakturaUzDocument extends BaseEntity {
    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;
    @Column(name = "unique_id", nullable = false, unique = true)
    private String uniqueId;
    @Column(name = "roaming_uid")
    private String roamingUid;
    private Integer type;
    private String title;
    @Column(name = "file_name")
    private String fileName;
    @Column(name = "total_price")
    private BigDecimal totalPrice;
    private String contract;
    @Column(name = "created_date_time")
    private Long createdDateTime;
    @Column(name = "updated_date_time")
    private Long updatedDateTime;
    @Column(name = "is_new")
    private Boolean isNew;
    private Integer status;
    @Column(name = "organization_inn")
    private String organizationInn;
    @Column(name = "contractor_inn")
    private String contractorInn;
    @Column(name = "contractor_name")
    private String contractorName;
    @Column(name = "owner_inn")
    private String ownerInn;
    @Column(name = "owner_name")
    private String ownerName;
    @Column(name = "contractor_member_inn")
    private String contractorMemberInn;
    @Column(name = "contractor_member_name")
    private String contractorMemberName;

    public LocalDateTime getCreatedDateTimeAsLocalDateTime() {
        return createdDateTime == null ? null : Instant.ofEpochMilli(createdDateTime)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
