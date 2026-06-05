package com.toir.repository;

import java.time.Instant;
import java.util.UUID;

public interface StockMovementListRow {
    UUID getId();

    UUID getWarehouseId();

    String getWarehouseName();

    UUID getSparePartId();

    String getSparePartName();

    UUID getWorkOrderId();

    String getWorkOrderNumber();

    String getWorkOrderName();

    String getType();

    double getQuantity();

    Double getUnitCost();

    String getDocumentNumber();

    UUID getCreatedById();

    String getCreatedByFullName();

    Instant getOccurredAt();

    String getNotes();
}
