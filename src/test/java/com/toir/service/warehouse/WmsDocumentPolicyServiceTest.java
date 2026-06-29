package com.toir.service.warehouse;

import com.toir.dto.wms.WmsDocumentGroupRequest;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WmsDocumentOperationType;
import com.toir.exception.RestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WmsDocumentPolicyServiceTest {

    WmsDocumentPolicyService service;

    @BeforeEach
    void setUp() {
        service = new WmsDocumentPolicyService();
    }

    @Test
    void strictReceiptRequiresInvoiceOrDeliveryNote() {
        assertThatThrownBy(() -> service.validateReceiptDocuments(
                WmsDocumentOperationType.PROCUREMENT_RECEIPT,
                List.of(),
                false,
                false,
                true
        )).isInstanceOf(RestException.class)
                .hasMessageContaining("INVOICE")
                .hasMessageContaining("DELIVERY_NOTE");

        assertThatCode(() -> service.validateReceiptDocuments(
                WmsDocumentOperationType.PROCUREMENT_RECEIPT,
                List.of(group("Invoice", " invoice ")),
                false,
                false,
                true
        )).doesNotThrowAnyException();
    }

    @Test
    void discrepancyReceiptRequiresDiscrepancyAct() {
        assertThatThrownBy(() -> service.validateReceiptDocuments(
                WmsDocumentOperationType.PROCUREMENT_RECEIPT,
                List.of(group("Delivery note", "DELIVERY_NOTE")),
                true,
                false,
                true
        )).isInstanceOf(RestException.class)
                .hasMessageContaining("DISCREPANCY_ACT");
    }

    @Test
    void warrantyReceiptRequiresWarrantyDocument() {
        assertThatThrownBy(() -> service.validateReceiptDocuments(
                WmsDocumentOperationType.PURCHASE_ORDER_RECEIPT,
                List.of(group("Delivery note", "DELIVERY_NOTE")),
                false,
                true,
                true
        )).isInstanceOf(RestException.class)
                .hasMessageContaining("WARRANTY");
    }

    @Test
    void workOrderIssueRequiresDocumentNumberIssuedByAndTakenBy() {
        assertThatThrownBy(() -> service.validateIssueDocuments(
                UUID.randomUUID(),
                " ",
                null,
                UUID.randomUUID(),
                List.of(),
                true
        )).isInstanceOf(RestException.class)
                .hasMessageContaining("document number");

        assertThatThrownBy(() -> service.validateIssueDocuments(
                UUID.randomUUID(),
                "ISS-1",
                UUID.randomUUID(),
                null,
                List.of(),
                true
        )).isInstanceOf(RestException.class)
                .hasMessageContaining("taken by");
    }

    @Test
    void damagedOrQuarantineReturnRequiresReasonStatusAndPhoto() {
        assertThatThrownBy(() -> service.validateReturnDocuments(
                " ",
                WarehouseStockStatus.DAMAGED,
                List.of(group("Photo", "PHOTO")),
                true
        )).isInstanceOf(RestException.class)
                .hasMessageContaining("reason");

        assertThatThrownBy(() -> service.validateReturnDocuments(
                "damaged",
                null,
                List.of(group("Photo", "PHOTO")),
                true
        )).isInstanceOf(RestException.class)
                .hasMessageContaining("target stock status");

        assertThatThrownBy(() -> service.validateReturnDocuments(
                "damaged",
                WarehouseStockStatus.QUARANTINE,
                List.of(group("Return act", "MATERIAL_RETURN_ACT")),
                true
        )).isInstanceOf(RestException.class)
                .hasMessageContaining("PHOTO");
    }

    @Test
    void inventoryCountWithVarianceRequiresVarianceAct() {
        assertThatThrownBy(() -> service.validateInventoryCountDocuments(
                true,
                List.of(group("Count sheet", "COUNT_SHEET")),
                true
        )).isInstanceOf(RestException.class)
                .hasMessageContaining("VARIANCE_ACT");
    }

    @Test
    void writeoffRequiresWriteoffActAndApprovalId() {
        assertThatThrownBy(() -> service.validateWriteoffDocuments(
                null,
                List.of(group("Writeoff act", "WRITEOFF_ACT")),
                true
        )).isInstanceOf(RestException.class)
                .hasMessageContaining("approval");

        assertThatThrownBy(() -> service.validateWriteoffDocuments(
                UUID.randomUUID(),
                List.of(group("Photo", "PHOTO")),
                true
        )).isInstanceOf(RestException.class)
                .hasMessageContaining("WRITEOFF_ACT");
    }

    @Test
    void nonStrictModeSkipsRequiredDocumentChecksButValidatesTypes() {
        assertThatCode(() -> service.validateReceiptDocuments(
                WmsDocumentOperationType.MANUAL_RECEIPT,
                List.of(),
                true,
                true,
                false
        )).doesNotThrowAnyException();

        assertThatThrownBy(() -> service.validateInventoryCountDocuments(
                false,
                List.of(group("Broken", "NOT_A_DOCUMENT")),
                false
        )).isInstanceOf(RestException.class)
                .hasMessageContaining("Unsupported document type");
    }

    private WmsDocumentGroupRequest group(String name, String type) {
        return new WmsDocumentGroupRequest(name, type, "DOC-1", LocalDate.of(2026, 6, 13), UUID.randomUUID());
    }
}
