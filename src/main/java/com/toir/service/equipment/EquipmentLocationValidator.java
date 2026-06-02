package com.toir.service.equipment;

import com.toir.dto.equipment.EquipmentLocationRequest;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentOutsideReason;
import com.toir.enums.PlacementTargetType;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.exception.RestException;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Component
public class EquipmentLocationValidator {

    private static final Set<EquipmentOutsideReason> OUTSIDE_DESTINATION_REQUIRED = EnumSet.of(
            EquipmentOutsideReason.SERVICE,
            EquipmentOutsideReason.RENTED_OUT,
            EquipmentOutsideReason.EXTERNAL_ORGANIZATION,
            EquipmentOutsideReason.INSTALLATION,
            EquipmentOutsideReason.CALIBRATION,
            EquipmentOutsideReason.INSPECTION,
            EquipmentOutsideReason.BUSINESS_TRIP
    );

    public EquipmentLocationRequest resolveCreateLocation(
            UUID departmentId,
            UUID warehouseId,
            UUID locationId,
            EquipmentLocationRequest location
    ) {
        if (location != null) {
            return validateAndNormalize(location);
        }
        if (departmentId != null && warehouseId != null) {
            throw RestException.badRequest("departmentId and warehouseId cannot both be provided");
        }
        if (departmentId != null) {
            return validateAndNormalize(new EquipmentLocationRequest(
                    EquipmentLocationType.DEPARTMENT,
                    departmentId,
                    null,
                    locationId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
        }
        if (warehouseId != null) {
            return validateAndNormalize(new EquipmentLocationRequest(
                    EquipmentLocationType.WAREHOUSE,
                    null,
                    warehouseId,
                    locationId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
        }
        throw RestException.badRequest("departmentId or warehouseId is required");
    }

    public EquipmentLocationRequest resolvePlacementLocation(
            PlacementTargetType targetType,
            UUID departmentId,
            UUID warehouseId,
            WarehouseEquipmentStatus warehouseStatus,
            EquipmentLocationRequest targetLocation
    ) {
        if (targetLocation != null) {
            return validateAndNormalize(targetLocation);
        }
        if (targetType == null) {
            throw RestException.badRequest("targetType is required when targetLocation is not provided");
        }
        if (departmentId != null && warehouseId != null) {
            throw RestException.badRequest("warehouseId and departmentId cannot both be provided");
        }
        if (targetType == PlacementTargetType.DEPARTMENT && warehouseStatus != null) {
            throw RestException.badRequest("warehouseStatus must be null when targetType is DEPARTMENT");
        }
        if (targetType == PlacementTargetType.WAREHOUSE
                && warehouseStatus != null
                && warehouseStatus != WarehouseEquipmentStatus.AVAILABLE
                && warehouseStatus != WarehouseEquipmentStatus.OUT_OF_SERVICE) {
            throw RestException.badRequest("warehouseStatus for WAREHOUSE target must be AVAILABLE or OUT_OF_SERVICE");
        }
        EquipmentLocationType locationType = switch (targetType) {
            case DEPARTMENT -> EquipmentLocationType.DEPARTMENT;
            case WAREHOUSE -> EquipmentLocationType.WAREHOUSE;
            case OUTSIDE_FACILITY -> EquipmentLocationType.OUTSIDE_FACILITY;
        };
        return validateAndNormalize(new EquipmentLocationRequest(
                locationType,
                departmentId,
                warehouseId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                warehouseStatus
        ));
    }

    public EquipmentLocationRequest validateAndNormalize(EquipmentLocationRequest request) {
        if (request == null) {
            throw RestException.badRequest("location is required");
        }
        if (request.locationType() == null) {
            throw RestException.badRequest("locationType is required");
        }

        EquipmentLocationRequest normalized = normalize(request);
        return switch (normalized.locationType()) {
            case DEPARTMENT -> validateDepartment(normalized);
            case WAREHOUSE -> validateWarehouse(normalized);
            case OUTSIDE_FACILITY -> validateOutside(normalized);
        };
    }

    private EquipmentLocationRequest validateDepartment(EquipmentLocationRequest request) {
        if (request.departmentId() == null) {
            throw RestException.badRequest("departmentId is required when locationType is DEPARTMENT");
        }
        if (request.warehouseId() != null) {
            throw RestException.badRequest("warehouseId must be null when locationType is DEPARTMENT");
        }
        rejectOutsideFields(request, "DEPARTMENT");
        return request;
    }

    private EquipmentLocationRequest validateWarehouse(EquipmentLocationRequest request) {
        if (request.warehouseId() == null) {
            throw RestException.badRequest("warehouseId is required when locationType is WAREHOUSE");
        }
        if (request.departmentId() != null) {
            throw RestException.badRequest("departmentId must be null when locationType is WAREHOUSE");
        }
        rejectOutsideFields(request, "WAREHOUSE");
        return request;
    }

    private EquipmentLocationRequest validateOutside(EquipmentLocationRequest request) {
        if (request.departmentId() != null || request.warehouseId() != null) {
            throw RestException.badRequest("departmentId and warehouseId must be null when locationType is OUTSIDE_FACILITY");
        }
        if (request.outsideReason() == null) {
            throw RestException.badRequest("outsideReason is required when locationType is OUTSIDE_FACILITY");
        }
        if (request.outsideReason() == EquipmentOutsideReason.OTHER && request.outsideReasonNote() == null) {
            throw RestException.badRequest("outsideReasonNote is required when outsideReason is OTHER");
        }
        if (OUTSIDE_DESTINATION_REQUIRED.contains(request.outsideReason()) && request.outsideDestination() == null) {
            throw RestException.badRequest("outsideDestination is required for outsideReason " + request.outsideReason());
        }
        if (request.outsideStartedDate() != null
                && request.outsideExpectedReturnDate() != null
                && request.outsideExpectedReturnDate().isBefore(request.outsideStartedDate())) {
            throw RestException.badRequest("outsideExpectedReturnDate must not be before outsideStartedDate");
        }
        if (request.outsideTakenBy() != null && request.outsideRecipientUserId() != null) {
            throw RestException.badRequest("outsideTakenBy and outsideRecipientUserId cannot both be provided");
        }
        return request;
    }

    private void rejectOutsideFields(EquipmentLocationRequest request, String locationType) {
        if (request.outsideReason() != null
                || request.outsideTakenBy() != null
                || request.outsideRecipientUserId() != null
                || request.outsideStartedDate() != null
                || request.outsideExpectedReturnDate() != null
                || request.outsideDestination() != null
                || request.outsideReasonNote() != null) {
            throw RestException.badRequest("outside fields must be null when locationType is " + locationType);
        }
    }

    private EquipmentLocationRequest normalize(EquipmentLocationRequest request) {
        return new EquipmentLocationRequest(
                request.locationType(),
                request.departmentId(),
                request.warehouseId(),
                request.locationId(),
                request.responsibleDepartmentId(),
                request.outsideReason(),
                normalizeBlank(request.outsideTakenBy()),
                request.outsideRecipientUserId(),
                request.outsideStartedDate(),
                request.outsideExpectedReturnDate(),
                normalizeBlank(request.outsideDestination()),
                normalizeBlank(request.outsideReasonNote()),
                request.warehouseStatus()
        );
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
