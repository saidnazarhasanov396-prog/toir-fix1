package com.toir.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface IssuedToWorkRowProjection {
    UUID getMovementId();

    LocalDate getMovementDate();

    UUID getSparePartId();

    String getSparePartCode();

    String getSparePartName();

    BigDecimal getQuantity();

    String getUnit();

    UUID getWarehouseId();

    String getWarehouseName();

    UUID getWorkOrderId();

    String getWorkOrderNumber();

    String getWorkOrderTitle();

    String getWorkOrderStatus();

    UUID getIssuedById();

    String getIssuedByName();

    UUID getResponsiblePersonId();

    String getResponsiblePersonName();

    String getDocumentNumber();

    String getSourceDocumentNo();

    String getSourceType();

    UUID getSourceId();
}
