package com.toir.repository.projection;

import java.util.UUID;

public interface WorkOrderCountProjection {
    UUID getWorkOrderId();

    long getCount();
}
