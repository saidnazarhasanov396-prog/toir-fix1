package com.toir.dto.equipment;

import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueRequest;
import com.toir.dto.equipmentmanualattribute.EquipmentManualAttributeRequest;
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
        LocalDate arrivalDate,
        LocalDate warrantyUntil,
        Boolean hasWarranty,
        UUID warrantyAttachmentId,
        LocalDate warrantyStartDate,
        LocalDate warrantyEndDate,
        String description,
        @Positive Long averageOperatingLifeHours,
        LocalDate operationStartDate,
        @Positive Integer expectedLifetimeMonths,
        @Positive Integer expectedLifetimeYears,
        List<EquipmentAttributeValueRequest> attributes,
        List<EquipmentManualAttributeRequest> manualAttributes,
        EquipmentLocationRequest location
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
                LocalDate arrivalDate,
                LocalDate warrantyUntil,
                Boolean hasWarranty,
                UUID warrantyAttachmentId,
                String description,
                @Positive Long averageOperatingLifeHours,
                LocalDate operationStartDate,
                @Positive Integer expectedLifetimeMonths,
                @Positive Integer expectedLifetimeYears,
                List<EquipmentAttributeValueRequest> attributes,
                List<EquipmentManualAttributeRequest> manualAttributes,
                EquipmentLocationRequest location
        ) {
                this(code, name, inventoryNumber, technicalNumber, serialNumber, model, equipmentTypeId,
                        departmentId, locationId, parentId, criticalityClassId, responsibleId, manufacturer,
                        status, category, commissionedAt, arrivalDate, warrantyUntil, hasWarranty,
                        warrantyAttachmentId, null, null, description, averageOperatingLifeHours, operationStartDate,
                        expectedLifetimeMonths, expectedLifetimeYears, attributes, manualAttributes, location);
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
                Long averageOperatingLifeHours,
                List<EquipmentAttributeValueRequest> attributes,
                List<EquipmentManualAttributeRequest> manualAttributes
        ) {
                this(code, name, inventoryNumber, technicalNumber, serialNumber, model, equipmentTypeId,
                        departmentId, locationId, parentId, criticalityClassId, responsibleId, manufacturer,
                        status, category, commissionedAt, null, warrantyUntil, null, null, null, null, description, averageOperatingLifeHours,
                        null, null, null, attributes, manualAttributes, null);
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
                List<EquipmentAttributeValueRequest> attributes
        ) {
                this(code, name, inventoryNumber, technicalNumber, serialNumber, model, equipmentTypeId,
                        departmentId, locationId, parentId, criticalityClassId, responsibleId, manufacturer,
                        status, category, commissionedAt, null, warrantyUntil, null, null, null, null, description, null, null, null, null,
                        attributes, null, null);
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
                        status, category, commissionedAt, null, warrantyUntil, null, null, null, null, description, averageOperatingLifeHours,
                        null, null, null, null, null, null);
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
                        status, category, commissionedAt, null, warrantyUntil, null, null, null, null, description, null, null, null, null,
                        null, null, null);
        }
}
