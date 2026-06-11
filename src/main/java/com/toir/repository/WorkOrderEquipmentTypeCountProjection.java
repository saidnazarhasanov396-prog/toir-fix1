package com.toir.repository;

import java.util.UUID;

public interface WorkOrderEquipmentTypeCountProjection {
    UUID getEquipmentTypeId();

    String getEquipmentTypeName();

    long getWorkOrderCount();
}
