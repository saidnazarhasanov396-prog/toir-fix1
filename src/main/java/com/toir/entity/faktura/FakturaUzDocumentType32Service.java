package com.toir.entity.faktura;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "faktura_uz_doc32_services")
public class FakturaUzDocumentType32Service extends BaseEntity {
    @Column(name = "document_unique_id", nullable = false)
    private String documentUniqueId;
    @Column(name = "price_per_item")
    private BigDecimal pricePerItem;
    private BigDecimal price;
    private BigDecimal summa;
    @Column(name = "delivery_cost")
    private BigDecimal deliveryCost;
    @Column(name = "vat_rate")
    private Double vatRate;
    @Column(name = "vat_amount")
    private BigDecimal vatAmount;
    @Column(name = "vat_rate_display")
    private String vatRateDisplay;
    @Column(name = "vat_amount_display")
    private String vatAmountDisplay;
    @Column(name = "delivery_cost_with_vat")
    private BigDecimal deliveryCostWithVat;
    @Column(name = "delivery_cost_with_vat_display")
    private String deliveryCostWithVatDisplay;
    @Column(name = "tax_rate")
    private String taxRate;
    @Column(name = "tax_amount")
    private String taxAmount;
    @Column(name = "delivery_cost_with_taxes")
    private BigDecimal deliveryCostWithTaxes;
    @Column(name = "delivery_cost_with_taxes_display")
    private String deliveryCostWithTaxesDisplay;
    @Column(name = "catalog_code")
    private String catalogCode;
    @Column(name = "catalog_name")
    private String catalogName;
    @Column(name = "catalog_package_names")
    private String catalogPackageNames;
    @Column(name = "catalog_title", columnDefinition = "text")
    private String catalogTitle;
    private String barcode;
    private String number;
    @Column(columnDefinition = "text")
    private String title;
    private String measurement;
    @Column(name = "measurement_code")
    private String measurementCode;
    private Double quantity;
}
