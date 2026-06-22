package com.toir.dto.equipment;

import com.toir.entity.FileAsset;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentOutsideReason;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.LifetimeStatus;
import com.toir.enums.MeterType;
import com.toir.enums.PlacementType;
import com.toir.enums.WarehouseEquipmentStatus;

import java.time.Period;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EquipmentDto(
        UUID id,
        String code,
        String name,
        String inventoryNumber,
        String technicalNumber,
        String serialNumber,
        String model,
        Integer producedYear,
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
        Long expectedLifetimeHours,
        String operatingDuration,
        LocalDate expectedEndDate,
        String remainingLifetime,
        LifetimeStatus lifetimeStatus,
        Boolean hasWarranty,
        UUID warrantyAttachmentId,
        LocalDate warrantyStartDate,
        LocalDate warrantyEndDate,
        WarrantyAttachmentRef warrantyAttachment,
        PassportCompletenessRef passportCompleteness,
        MeterType lifetimeCounterType,
        UUID lifetimeMeterId,
        Double lifetimeLimitValue,
        Double lifetimeBaselineValue,
        Double lifetimeWarningPercent,
        Double lifetimeCurrentValue,
        Double lifetimeTargetValue,
        Double lifetimeRemainingValue,
        Double lifetimeConsumedPercent,
        String lifetimeUnit,
        Double averageDailyUsage,
        ResponsibleRef responsible,
        Long daysOfResourceRemaining,
        Double forecastConsumedResource,
        Double forecastRemainingResource,
        Double forecastAvgUsagePerActiveDay,
        Long forecastRemainingActiveDays,
        LocalDate forecastCalculatedAt,
        boolean isCreatedAct
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
            LocalDate arrivalDate,
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
            Long expectedLifetimeHours,
            String operatingDuration,
            LocalDate expectedEndDate,
            String remainingLifetime,
            LifetimeStatus lifetimeStatus,
            Boolean hasWarranty,
            UUID warrantyAttachmentId,
            LocalDate warrantyStartDate,
            LocalDate warrantyEndDate,
            WarrantyAttachmentRef warrantyAttachment,
            PassportCompletenessRef passportCompleteness
    ) {
        this(id, code, name, inventoryNumber, technicalNumber, serialNumber, model, null, equipmentTypeId,
                departmentId, locationId, parentId, criticalityClassId, responsibleId, manufacturer, status,
                category, commissionedAt, arrivalDate, warrantyUntil, description, averageOperatingLifeHours,
                department, location, equipmentType, parent, passport, placement, operationStartDate,
                expectedLifetimeMonths, expectedLifetimeYears, expectedLifetimeHours, operatingDuration,
                expectedEndDate, remainingLifetime, lifetimeStatus, hasWarranty, warrantyAttachmentId,
                warrantyStartDate, warrantyEndDate, warrantyAttachment, passportCompleteness,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                false);
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
            LocalDate arrivalDate,
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
        this(id, code, name, inventoryNumber, technicalNumber, serialNumber, model, null, equipmentTypeId,
                departmentId, locationId, parentId, criticalityClassId, responsibleId, manufacturer, status,
                category, commissionedAt, arrivalDate, warrantyUntil, description, averageOperatingLifeHours,
                department, location, equipmentType, parent, passport, placement, operationStartDate,
                expectedLifetimeMonths, expectedLifetimeYears, null, operatingDuration, expectedEndDate, remainingLifetime,
                lifetimeStatus, hasWarranty, warrantyAttachmentId, null, null, warrantyAttachment, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                false);
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
            Long averageOperatingLifeHours,
            Ref department,
            Ref location,
            Ref equipmentType,
            Ref parent,
            PassportRef passport,
            PlacementRef placement
    ) {
        this(id, code, name, inventoryNumber, technicalNumber, serialNumber, model, null, equipmentTypeId,
                departmentId, locationId, parentId, criticalityClassId, responsibleId, manufacturer, status,
                category, commissionedAt, null, warrantyUntil, description, averageOperatingLifeHours, department,
                location, equipmentType, parent, passport, placement, null, null, null, null, null, null, null,
                LifetimeStatus.UNKNOWN, false, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                false);
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

    public record ResponsibleRef(
            UUID id,
            String personnelNumber,
            String fullName,
            String phone
    ) {}

    public record PassportRef(
            String passportNumber,
            Double powerKw,
            Double voltageV,
            Double pressureBar,
            String factoryNumber,
            String manufacturerSerial,
            java.util.List<com.toir.dto.equipmentpassport.ProductivityEntryDto> productivity,
            LocalDate installDate,
            LocalDate lastInspectionDate,
            String notes
    ) {}

    public record MissingPassportFieldRef(
            UUID definitionId,
            String key,
            String label,
            String groupName,
            int sortOrder,
            boolean critical
    ) {}

    public record PassportCompletenessRef(
            boolean complete,
            int requiredCount,
            int filledCount,
            int missingCriticalCount,
            int missingWarningCount,
            List<MissingPassportFieldRef> missingFields,
            String blockingReason,
            String fixAction,
            String fixLink
    ) {
        public PassportCompletenessRef {
            missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        }
    }

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

    public static EquipmentDto from(Equipment e, EquipmentMeter lifetimeMeter) {
        return from(e, null, null, null, null, null, null, null, null, lifetimeMeter, null);
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
        return from(e, department, location, equipmentType, parent, passport, placement, warrantyAttachment, null);
    }

    public static EquipmentDto from(Equipment e,
                                    Ref department,
                                    Ref location,
                                    Ref equipmentType,
                                    Ref parent,
                                    PassportRef passport,
                                    PlacementRef placement,
                                    FileAsset warrantyAttachment,
                                    PassportCompletenessRef passportCompleteness) {
        return from(e, department, location, equipmentType, parent, passport, placement, warrantyAttachment,
                passportCompleteness, null, null);
    }

    public static EquipmentDto from(Equipment e,
                                    Ref department,
                                    Ref location,
                                    Ref equipmentType,
                                    Ref parent,
                                    PassportRef passport,
                                    PlacementRef placement,
                                    FileAsset warrantyAttachment,
                                    PassportCompletenessRef passportCompleteness,
                                    EquipmentMeter lifetimeMeter,
                                    ResponsibleRef responsible) {
        return from(e, department, location, equipmentType, parent, passport, placement, warrantyAttachment,
                passportCompleteness, lifetimeMeter, responsible, false);
    }

    public static EquipmentDto from(Equipment e,
                                    Ref department,
                                    Ref location,
                                    Ref equipmentType,
                                    Ref parent,
                                    PassportRef passport,
                                    PlacementRef placement,
                                    FileAsset warrantyAttachment,
                                    PassportCompletenessRef passportCompleteness,
                                    EquipmentMeter lifetimeMeter,
                                    ResponsibleRef responsible,
                                    boolean isCreatedAct) {
        return new EquipmentDto(
                e.getId(), e.getCode(), e.getName(), e.getInventoryNumber(), e.getTechnicalNumber(),
                e.getSerialNumber(), e.getModel(), e.getProducedYear(), e.getEquipmentTypeId(), e.getDepartmentId(),
                e.getLocationId(), e.getParentId(), e.getCriticalityClassId(), e.getResponsibleId(),
                e.getManufacturer(), e.getStatus(), e.getCategory(),
                e.getCommissionedAt(), e.getArrivalDate(), e.getWarrantyUntil(), e.getDescription(), e.getAverageOperatingLifeHours(),
                department, location, equipmentType, parent, passport, placement,
                e.getOperationStartDate(), e.getExpectedLifetimeMonths(), e.getExpectedLifetimeYears(), e.getExpectedLifetimeHours(),
                operatingDuration(e), expectedEndDate(e), remainingLifetime(e), lifetimeStatus(e, lifetimeMeter),
                Boolean.TRUE.equals(e.getHasWarranty()), e.getWarrantyAttachmentId(),
                e.getWarrantyStartDate(), e.getWarrantyEndDate(), warrantyAttachmentRef(warrantyAttachment),
                passportCompleteness,
                lifetimeCounterType(e),
                lifetimeMeterId(e, lifetimeMeter),
                lifetimeLimitValue(e),
                lifetimeBaselineValue(e),
                lifetimeWarningPercent(e),
                lifetimeCurrentValue(lifetimeMeter),
                lifetimeTargetValue(e),
                lifetimeRemainingValue(e, lifetimeMeter),
                lifetimeConsumedPercent(e, lifetimeMeter),
                lifetimeUnit(e, lifetimeMeter),
                e.getAverageDailyUsage(),
                responsible,
                e.getDaysOfResourceRemaining(),
                e.getForecastConsumedResource(),
                e.getForecastRemainingResource(),
                e.getForecastAvgUsagePerActiveDay(),
                e.getForecastRemainingActiveDays(),
                e.getForecastCalculatedAt(),
                isCreatedAct
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
        return lifetimeStatus(e, null);
    }

    private static LifetimeStatus lifetimeStatus(Equipment e, EquipmentMeter lifetimeMeter) {
        return mostSevere(calendarLifetimeStatus(e), meterLifetimeStatus(e, lifetimeMeter));
    }

    private static LifetimeStatus calendarLifetimeStatus(Equipment e) {
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

    private static LifetimeStatus meterLifetimeStatus(Equipment e, EquipmentMeter lifetimeMeter) {
        Double remaining = lifetimeRemainingValue(e, lifetimeMeter);
        Double limit = lifetimeLimitValue(e);
        if (remaining == null || limit == null || limit <= 0) {
            return LifetimeStatus.UNKNOWN;
        }
        if (remaining <= 0) {
            return LifetimeStatus.EXPIRED;
        }
        double warningThreshold = Math.max(1.0, limit * lifetimeWarningPercent(e) / 100.0);
        return remaining <= warningThreshold ? LifetimeStatus.EXPIRING_SOON : LifetimeStatus.NORMAL;
    }

    private static LifetimeStatus mostSevere(LifetimeStatus calendar, LifetimeStatus meter) {
        if (calendar == LifetimeStatus.EXPIRED || meter == LifetimeStatus.EXPIRED) {
            return LifetimeStatus.EXPIRED;
        }
        if (calendar == LifetimeStatus.EXPIRING_SOON || meter == LifetimeStatus.EXPIRING_SOON) {
            return LifetimeStatus.EXPIRING_SOON;
        }
        if (calendar == LifetimeStatus.NORMAL || meter == LifetimeStatus.NORMAL) {
            return LifetimeStatus.NORMAL;
        }
        return LifetimeStatus.UNKNOWN;
    }

    private static MeterType lifetimeCounterType(Equipment e) {
        if (e.getLifetimeCounterType() != null) {
            return e.getLifetimeCounterType();
        }
        return e.getExpectedLifetimeHours() != null && e.getExpectedLifetimeHours() > 0
                ? MeterType.ENGINE_HOURS
                : null;
    }

    private static UUID lifetimeMeterId(Equipment e, EquipmentMeter lifetimeMeter) {
        if (e.getLifetimeMeterId() != null) {
            return e.getLifetimeMeterId();
        }
        return lifetimeMeter == null ? null : lifetimeMeter.getId();
    }

    private static Double lifetimeLimitValue(Equipment e) {
        if (e.getLifetimeLimitValue() != null && e.getLifetimeLimitValue() > 0) {
            return e.getLifetimeLimitValue();
        }
        return e.getExpectedLifetimeHours() != null && e.getExpectedLifetimeHours() > 0
                ? e.getExpectedLifetimeHours().doubleValue()
                : null;
    }

    private static Double lifetimeBaselineValue(Equipment e) {
        return e.getLifetimeBaselineValue() != null ? e.getLifetimeBaselineValue() : 0.0;
    }

    private static Double lifetimeWarningPercent(Equipment e) {
        return e.getLifetimeWarningPercent() != null && e.getLifetimeWarningPercent() > 0
                ? e.getLifetimeWarningPercent()
                : 10.0;
    }

    private static Double lifetimeCurrentValue(EquipmentMeter lifetimeMeter) {
        return lifetimeMeter == null ? null : lifetimeMeter.getCurrentValue();
    }

    private static Double lifetimeTargetValue(Equipment e) {
        Double limit = lifetimeLimitValue(e);
        return limit == null ? null : lifetimeBaselineValue(e) + limit;
    }

    private static Double lifetimeRemainingValue(Equipment e, EquipmentMeter lifetimeMeter) {
        Double target = lifetimeTargetValue(e);
        Double current = lifetimeCurrentValue(lifetimeMeter);
        return target == null || current == null ? null : target - current;
    }

    private static Double lifetimeConsumedPercent(Equipment e, EquipmentMeter lifetimeMeter) {
        Double limit = lifetimeLimitValue(e);
        Double current = lifetimeCurrentValue(lifetimeMeter);
        if (limit == null || limit <= 0 || current == null) {
            return null;
        }
        return ((current - lifetimeBaselineValue(e)) / limit) * 100.0;
    }

    private static String lifetimeUnit(Equipment e, EquipmentMeter lifetimeMeter) {
        if (lifetimeMeter != null && lifetimeMeter.getUnit() != null && !lifetimeMeter.getUnit().isBlank()) {
            return lifetimeMeter.getUnit();
        }
        MeterType type = lifetimeCounterType(e);
        if (type == null) {
            return null;
        }
        return switch (type) {
            case ENGINE_HOURS -> "h";
            case MILEAGE_KM -> "km";
            case CYCLES -> "cycle";
            case TONS_PRODUCED -> "t";
            case KWH_CONSUMED -> "kWh";
            case CUSTOM -> null;
        };
    }
}
