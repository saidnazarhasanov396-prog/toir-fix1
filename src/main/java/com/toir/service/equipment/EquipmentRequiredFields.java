package com.toir.service.equipment;

import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentLocationType;
import com.toir.exception.RestException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class EquipmentRequiredFields {

    private EquipmentRequiredFields() {
    }

    public static void validate(Equipment equipment) {
        List<String> missing = new ArrayList<>();
        if (equipment.getCriticalityClassId() == null) missing.add("criticalityClassId");
        if (!hasLocation(equipment)) missing.add("locationId");
        if (equipment.getCommissionedAt() == null) missing.add("commissionedAt");
        if (equipment.getResponsibleId() == null) missing.add("responsibleId");
        if (!missing.isEmpty()) {
            throw RestException.badRequest(
                    "EQUIPMENT_REQUIRED_FIELDS_MISSING: " + String.join(",", missing)
            );
        }
    }

    public static boolean hasLocation(Equipment equipment) {
        if (equipment.getLocationId() != null) return true;
        if (equipment.getCurrentLocationType() == EquipmentLocationType.DEPARTMENT) {
            return equipment.getDepartmentId() != null;
        }
        if (equipment.getCurrentLocationType() == EquipmentLocationType.WAREHOUSE) {
            return equipment.getCurrentWarehouseId() != null;
        }
        if (equipment.getCurrentLocationType() == EquipmentLocationType.OUTSIDE_FACILITY) {
            return StringUtils.hasText(equipment.getOutsideDestination());
        }
        return equipment.getDepartmentId() != null || equipment.getCurrentWarehouseId() != null;
    }
}
