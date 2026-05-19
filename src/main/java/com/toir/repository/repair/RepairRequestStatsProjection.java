package com.toir.repository.repair;

public interface RepairRequestStatsProjection {
    Long getTotalRequests();
    Long getEmergency();
    Long getOpen();
    Long getWithWorkOrder();
}
