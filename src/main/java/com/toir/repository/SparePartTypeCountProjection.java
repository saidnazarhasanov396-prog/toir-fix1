package com.toir.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface SparePartTypeCountProjection {
    UUID getTypeId();

    long getSparePartCount();

    BigDecimal getSparePartStockCount();
}
