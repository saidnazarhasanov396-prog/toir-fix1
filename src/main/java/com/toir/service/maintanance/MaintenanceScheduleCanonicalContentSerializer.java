package com.toir.service.maintanance;

import com.toir.enums.MaintenanceScheduleContentHashVersion;

public interface MaintenanceScheduleCanonicalContentSerializer {

    MaintenanceScheduleContentHashVersion version();

    String serialize(MaintenanceScheduleCalculationContent content);
}
