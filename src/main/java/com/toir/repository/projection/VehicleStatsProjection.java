package com.toir.repository.projection;

public interface VehicleStatsProjection {
    Long getTotal();
    Long getActive();
    Long getInRepair();
    Long getOutOfService();
}