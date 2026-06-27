package com.toir.entity.faktura;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "faktura_uz_doc_type32")
public class FakturaUzDocumentType32Content extends BaseEntity {
    @Column(name = "document_unique_id", nullable = false, unique = true)
    private String documentUniqueId;
    @Column(name = "roaming_uid")
    private String roamingUid;
    @Column(name = "has_vat")
    private String hasVat;
    @Column(name = "owner_inn")
    private String ownerInn;
    @Column(name = "owner_name")
    private String ownerName;
    @Column(name = "owner_account")
    private String ownerAccount;
    @Column(name = "owner_mfo")
    private String ownerMfo;
    @Column(name = "owner_bank")
    private String ownerBank;
    @Column(name = "owner_address")
    private String ownerAddress;
    @Column(name = "owner_phone")
    private String ownerPhone;
    @Column(name = "client_inn")
    private String clientInn;
    @Column(name = "client_name")
    private String clientName;
    @Column(name = "client_account")
    private String clientAccount;
    @Column(name = "client_mfo")
    private String clientMfo;
    @Column(name = "client_bank")
    private String clientBank;
    @Column(name = "client_address")
    private String clientAddress;
    @Column(name = "client_phone")
    private String clientPhone;
    @Column(name = "contract_name")
    private String contractName;
    @Column(name = "contractor_inn")
    private String contractorInn;
    @Column(name = "contract_number")
    private String contractNumber;
    @Column(name = "contract_date")
    private LocalDate contractDate;
    @Column(name = "contract_expire_date")
    private LocalDate contractExpireDate;
    @Column(name = "contract_place")
    private String contractPlace;
    @Column(name = "invoice_services_delivery_cost_total")
    private BigDecimal invoiceServicesDeliveryCostTotal;
    @Column(name = "invoice_services_vat_amount_total")
    private BigDecimal invoiceServicesVatAmountTotal;
    @Column(name = "invoice_services_total_price")
    private BigDecimal invoiceServicesTotalPrice;
    @Column(name = "invoice_services_total_price_in_words", columnDefinition = "text")
    private String invoiceServicesTotalPriceInWords;
    @Column(name = "is_new_identity")
    private Boolean isNewIdentity;
}
