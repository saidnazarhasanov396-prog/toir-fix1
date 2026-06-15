package com.toir.repository;

import java.time.Instant;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface StockMovementListRow {
    UUID getId();

    UUID getWarehouseId();

    String getWarehouseName();

    UUID getSparePartId();

    String getSparePartName();

    String getSparePartType();

    UUID getWorkOrderId();

    String getWorkOrderNumber();

    String getWorkOrderName();

    String getType();

    double getQuantity();

    String getUnit();

    Double getUnitCost();

    BigDecimal getUnitPrice();

    BigDecimal getTotalAmount();

    String getDocumentNumber();

    UUID getCreatedById();

    String getCreatedByFullName();

    UUID getResponsiblePersonId();

    String getResponsiblePersonName();

    UUID getTakenById();

    String getTakenByName();

    UUID getDepartmentId();

    String getSupplierName();

    LocalDate getMovementDate();

    Instant getOccurredAt();

    String getNotes();

    String getComment();

    long getFileCount();
}
