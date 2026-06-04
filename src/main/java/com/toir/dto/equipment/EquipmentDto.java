package com.toir.dto.equipment;

import com.toir.entity.FileAsset;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentOutsideReason;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.LifetimeStatus;
import com.toir.enums.PlacementType;
import com.toir.enums.WarehouseEquipmentStatus;

import java.time.Period;
import java.time.LocalDate;
import java.util.UUID;

public record EquipmentDto(
        UUID id,
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
        Ref department,
        Ref location,
        Ref equipmentType,
        Ref parent,
        PassportRef passport,
        PlacementRef placement,
        LocalDate operationStartDate,
        Integer expectedLifetimeMonths,
        Integer expectedLifetimeYears,
        String operatingDuration,
        LocalDate expectedEndDate,
        String remainingLifetime,
        LifetimeStatus lifetimeStatus,
        Boolean hasWarranty,
        UUID warrantyAttachmentId,
        WarrantyAttachmentRef warrantyAttachment
) {
    public EquipmentDto(
            UUID id,
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
            Ref department,
            Ref location,
            Ref equipmentType,
            Ref parent,
            PassportRef passport,
            PlacementRef placement
    ) {
        this(id, code, name, inventoryNumber, technicalNumber, serialNumber, model, equipmentTypeId,
                departmentId, locationId, parentId, criticalityClassId, responsibleId, manufacturer, status,
                category, commissionedAt, warrantyUntil, description, averageOperatingLifeHours, department,
                location, equipmentType, parent, passport, placement, null, null, null, null, null, null,
                LifetimeStatus.UNKNOWN, false, null, null);
    }

    public EquipmentDto(
            UUID id,
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
            Ref department,
            Ref location,
            Ref equipmentType,
            Ref parent,
            PassportRef passport,
            PlacementRef placement
    ) {
        this(id, code, name, inventoryNumber, technicalNumber, serialNumber, model, equipmentTypeId,
                departmentId, locationId, parentId, criticalityClassId, responsibleId, manufacturer,
                status, category, commissionedAt, warrantyUntil, description, null, department, location,
                equipmentType, parent, passport, placement);
    }

    public record Ref(UUID id, String code, String name) {}

    public record PassportRef(
            String passportNumber,
            Double powerKw,
            Double voltageV,
            Double pressureBar
    ) {}

    public record WarrantyAttachmentRef(
            UUID id,
            String originalName,
            String mimeType,
            long sizeBytes,
            String downloadUrl
    ) {}

    public record PlacementRef(
            PlacementType type,
            Ref department,
            Ref warehouse,
            WarehouseEquipmentStatus warehouseStatus,
            Ref location,
            UUID responsibleDepartmentId,
            EquipmentOutsideReason outsideReason,
            String outsideTakenBy,
            UUID outsideRecipientUserId,
            LocalDate outsideStartedDate,
            LocalDate outsideExpectedReturnDate,
            String outsideDestination,
            String outsideReasonNote,
            boolean overdue
    ) {
        public PlacementRef(
                PlacementType type,
                Ref department,
                Ref warehouse,
                WarehouseEquipmentStatus warehouseStatus,
                Ref location
        ) {
            this(type, department, warehouse, warehouseStatus, location, null, null, null, null, null, null, null, null, false);
        }
    }

    public static EquipmentDto from(Equipment e) {
        return from(e, null, null, null, null, null, null);
    }

    public static EquipmentDto from(Equipment e,
                                    Ref department,
                                    Ref location,
                                    Ref equipmentType,
                                    Ref parent,
                                    PassportRef passport) {
        return from(e, department, location, equipmentType, parent, passport, null);
    }

    public static EquipmentDto from(Equipment e,
                                    Ref department,
                                    Ref location,
                                    Ref equipmentType,
                                    Ref parent,
                                    PassportRef passport,
                                    PlacementRef placement) {
        return from(e, department, location, equipmentType, parent, passport, placement, null);
    }

    public static EquipmentDto from(Equipment e,
                                    Ref department,
                                    Ref location,
                                    Ref equipmentType,
                                    Ref parent,
                                    PassportRef passport,
                                    PlacementRef placement,
                                    FileAsset warrantyAttachment) {
        return new EquipmentDto(
                e.getId(), e.getCode(), e.getName(), e.getInventoryNumber(), e.getTechnicalNumber(),
                e.getSerialNumber(), e.getModel(), e.getEquipmentTypeId(), e.getDepartmentId(),
                e.getLocationId(), e.getParentId(), e.getCriticalityClassId(), e.getResponsibleId(),
                e.getManufacturer(), e.getStatus(), e.getCategory(),
                e.getCommissionedAt(), e.getWarrantyUntil(), e.getDescription(), e.getAverageOperatingLifeHours(),
                department, location, equipmentType, parent, passport, placement,
                e.getOperationStartDate(), e.getExpectedLifetimeMonths(), e.getExpectedLifetimeYears(),
                operatingDuration(e), expectedEndDate(e), remainingLifetime(e), lifetimeStatus(e),
                Boolean.TRUE.equals(e.getHasWarranty()), e.getWarrantyAttachmentId(), warrantyAttachmentRef(warrantyAttachment)
        );
    }

    private static WarrantyAttachmentRef warrantyAttachmentRef(FileAsset fileAsset) {
        if (fileAsset == null) {
            return null;
        }
        return new WarrantyAttachmentRef(
                fileAsset.getId(),
                fileAsset.getOriginalName(),
                fileAsset.getMimeType(),
                fileAsset.getSizeBytes(),
                "/api/v1/files/assets/" + fileAsset.getId() + "/download"
        );
    }

    private static LocalDate expectedEndDate(Equipment e) {
        LocalDate start = e.getOperationStartDate() != null ? e.getOperationStartDate() : e.getCommissionedAt();
        Integer months = effectiveLifetimeMonths(e);
        return start == null || months == null || months <= 0 ? null : start.plusMonths(months);
    }

    private static Integer effectiveLifetimeMonths(Equipment e) {
        if (e.getExpectedLifetimeMonths() != null && e.getExpectedLifetimeMonths() > 0) {
            return e.getExpectedLifetimeMonths();
        }
        if (e.getExpectedLifetimeYears() != null && e.getExpectedLifetimeYears() > 0) {
            return e.getExpectedLifetimeYears() * 12;
        }
        return null;
    }

    private static String operatingDuration(Equipment e) {
        LocalDate start = e.getOperationStartDate() != null ? e.getOperationStartDate() : e.getCommissionedAt();
        if (start == null) {
            return null;
        }
        Period period = Period.between(start, LocalDate.now());
        return "%d years %d months %d days".formatted(period.getYears(), period.getMonths(), period.getDays());
    }

    private static String remainingLifetime(Equipment e) {
        LocalDate end = expectedEndDate(e);
        if (end == null) {
            return null;
        }
        Period period = Period.between(LocalDate.now(), end);
        if (period.isNegative()) {
            Period overdue = Period.between(end, LocalDate.now());
            return "expired %d years %d months %d days ago".formatted(
                    overdue.getYears(), overdue.getMonths(), overdue.getDays());
        }
        return "%d years %d months %d days".formatted(period.getYears(), period.getMonths(), period.getDays());
    }

    private static LifetimeStatus lifetimeStatus(Equipment e) {
        LocalDate end = expectedEndDate(e);
        if (end == null) {
            return LifetimeStatus.UNKNOWN;
        }
        LocalDate today = LocalDate.now();
        if (end.isBefore(today)) {
            return LifetimeStatus.EXPIRED;
        }
        return !end.isAfter(today.plusMonths(3)) ? LifetimeStatus.EXPIRING_SOON : LifetimeStatus.NORMAL;
    }
}
