package com.toir.repository;

import java.util.UUID;

public interface SparePartTypeCountProjection {
    UUID getTypeId();

    long getSparePartCount();
}
