package com.toir.repository.equipment;

public interface EquipmentStatsProjection {
    Long getTotalInRegistry();
    Long getActive();
    Long getInRepair();
    Long getReserved();
}
