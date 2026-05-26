package com.toir.dto.equipment;

import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueRequest;
import com.toir.dto.equipmentmanualattribute.EquipmentManualAttributeRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EquipmentCreateRequest(
        String code,
        @NotBlank String name,
        @NotBlank String inventoryNumber,
        String technicalNumber,
        String serialNumber,
        String model,
        @NotNull UUID equipmentTypeId,
        UUID departmentId,
        UUID warehouseId,
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
        @NotNull @Positive Long averageOperatingLifeHours,
        List<EquipmentAttributeValueRequest> attributes,
        List<EquipmentManualAttributeRequest> manualAttributes
) {
        public EquipmentCreateRequest(
                String code,
                String name,
                String inventoryNumber,
                String technicalNumber,
                String serialNumber,
                String model,
                UUID equipmentTypeId,
                UUID departmentId,
                UUID warehouseId,
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
                Long averageOperatingLifeHours,
                List<EquipmentAttributeValueRequest> attributes
        ) {
                this(code, name, inventoryNumber, technicalNumber, serialNumber, model, equipmentTypeId,
                        departmentId, warehouseId, locationId, parentId, criticalityClassId, responsibleId,
                        manufacturer, status, category, commissionedAt, warrantyUntil, description,
                        averageOperatingLifeHours, attributes, null);
        }

        public EquipmentCreateRequest(
                String code,
                String name,
                String inventoryNumber,
                String technicalNumber,
                String serialNumber,
                String model,
                UUID equipmentTypeId,
                UUID departmentId,
                UUID warehouseId,
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
                        departmentId, warehouseId, locationId, parentId, criticalityClassId, responsibleId,
                        manufacturer, status, category, commissionedAt, warrantyUntil, description, null, attributes, null);
        }

        public EquipmentCreateRequest(
                String code,
                String name,
                String inventoryNumber,
                String technicalNumber,
                String serialNumber,
                String model,
                UUID equipmentTypeId,
                UUID departmentId,
                UUID warehouseId,
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
                        departmentId, warehouseId, locationId, parentId, criticalityClassId, responsibleId,
                        manufacturer, status, category, commissionedAt, warrantyUntil, description,
                        averageOperatingLifeHours, null, null);
        }

        public EquipmentCreateRequest(
                String code,
                String name,
                String inventoryNumber,
                String technicalNumber,
                String serialNumber,
                String model,
                UUID equipmentTypeId,
                UUID departmentId,
                UUID warehouseId,
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
                        departmentId, warehouseId, locationId, parentId, criticalityClassId, responsibleId,
                        manufacturer, status, category, commissionedAt, warrantyUntil, description, null, null, null);
        }
}
