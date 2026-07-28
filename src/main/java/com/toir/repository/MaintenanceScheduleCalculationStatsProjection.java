package com.toir.repository;

public interface MaintenanceScheduleCalculationStatsProjection {

    long getTotal();

    long getSaved();

    long getPendingApproval();

    long getApproved();
}
