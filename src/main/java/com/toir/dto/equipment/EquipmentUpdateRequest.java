package com.toir.dto.equipment;

import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueRequest;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EquipmentUpdateRequest(
        String code,
        String name,
        String inventoryNumber,
        String technicalNumber,
        String serialNumber,
        String model,
        UUID equipmentTypeId,
        UUID departmentId,
        UUID locationId,
        UUID parentId,
        UUID criticalityClassId,
        UUID responsibleId,
        String manufacturer,
        EquipmentStatus status,
        EquipmentCategory category,
        LocalDate commissionedAt,
        LocalDate warrantyUntil,
        String description,
        @Positive Long averageOperatingLifeHours,
        List<EquipmentAttributeValueRequest> attributes
) {
        public EquipmentUpdateRequest(
                String code,
                String name,
                String inventoryNumber,
                String technicalNumber,
                String serialNumber,
                String model,
                UUID equipmentTypeId,
                UUID departmentId,
                UUID locationId,
                UUID parentId,
                UUID criticalityClassId,
                UUID responsibleId,
                String manufacturer,
                EquipmentStatus status,
                EquipmentCategory category,
                LocalDate commissionedAt,
                LocalDate warrantyUntil,
                String description,
                List<EquipmentAttributeValueRequest> attributes
        ) {
                this(code, name, inventoryNumber, technicalNumber, serialNumber, model, equipmentTypeId,
                        departmentId, locationId, parentId, criticalityClassId, responsibleId, manufacturer,
                        status, category, commissionedAt, warrantyUntil, description, null, attributes);
        }

        public EquipmentUpdateRequest(
                String code,
                String name,
                String inventoryNumber,
                String technicalNumber,
                String serialNumber,
                String model,
                UUID equipmentTypeId,
                UUID departmentId,
                UUID locationId,
                UUID parentId,
                UUID criticalityClassId,
                UUID responsibleId,
                String manufacturer,
                EquipmentStatus status,
                EquipmentCategory category,
                LocalDate commissionedAt,
                LocalDate warrantyUntil,
                String description,
                Long averageOperatingLifeHours
        ) {
                this(code, name, inventoryNumber, technicalNumber, serialNumber, model, equipmentTypeId,
                        departmentId, locationId, parentId, criticalityClassId, responsibleId, manufacturer,
                        status, category, commissionedAt, warrantyUntil, description, averageOperatingLifeHours, null);
        }

        public EquipmentUpdateRequest(
                String code,
                String name,
                String inventoryNumber,
                String technicalNumber,
                String serialNumber,
                String model,
                UUID equipmentTypeId,
                UUID departmentId,
                UUID locationId,
                UUID parentId,
                UUID criticalityClassId,
                UUID responsibleId,
                String manufacturer,
                EquipmentStatus status,
                EquipmentCategory category,
                LocalDate commissionedAt,
                LocalDate warrantyUntil,
                String description
        ) {
                this(code, name, inventoryNumber, technicalNumber, serialNumber, model, equipmentTypeId,
                        departmentId, locationId, parentId, criticalityClassId, responsibleId, manufacturer,
                        status, category, commissionedAt, warrantyUntil, description, null, null);
        }
}
