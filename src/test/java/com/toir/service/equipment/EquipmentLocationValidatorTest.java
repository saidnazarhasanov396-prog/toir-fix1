package com.toir.service.equipment;

import com.toir.dto.equipment.EquipmentLocationRequest;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentOutsideReason;
import com.toir.enums.PlacementTargetType;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.exception.RestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquipmentLocationValidatorTest {

    private final EquipmentLocationValidator validator = new EquipmentLocationValidator();

    @Test
    void resolveCreateLocationInfersDepartmentFromOldFlatFields() {
        UUID departmentId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        EquipmentLocationRequest resolved = validator.resolveCreateLocation(
                departmentId,
                null,
                locationId,
                null
        );

        assertThat(resolved.locationType()).isEqualTo(EquipmentLocationType.DEPARTMENT);
        assertThat(resolved.departmentId()).isEqualTo(departmentId);
        assertThat(resolved.locationId()).isEqualTo(locationId);
    }

    @Test
    void validateOutsideRejectsOtherWithoutNote() {
        EquipmentLocationRequest request = new EquipmentLocationRequest(
                EquipmentLocationType.OUTSIDE_FACILITY,
                null,
                null,
                null,
                UUID.randomUUID(),
                EquipmentOutsideReason.OTHER,
                null,
                null,
                LocalDate.now(),
                null,
                null,
                " ",
                null
        );

        assertThatThrownBy(() -> validator.validateAndNormalize(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("outsideReasonNote is required when outsideReason is OTHER");
    }

    @Test
    void validateOutsideRejectsExpectedReturnBeforeStartedDate() {
        LocalDate started = LocalDate.of(2026, 6, 10);
        EquipmentLocationRequest request = new EquipmentLocationRequest(
                EquipmentLocationType.OUTSIDE_FACILITY,
                null,
                null,
                null,
                UUID.randomUUID(),
                EquipmentOutsideReason.SERVICE,
                null,
                null,
                started,
                started.minusDays(1),
                "Servis markazi",
                null,
                null
        );

        assertThatThrownBy(() -> validator.validateAndNormalize(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("outsideExpectedReturnDate must not be before outsideStartedDate");
    }

    @Test
    void resolvePlacementLocationKeepsOldWarehousePayloadCompatible() {
        UUID warehouseId = UUID.randomUUID();

        EquipmentLocationRequest resolved = validator.resolvePlacementLocation(
                PlacementTargetType.WAREHOUSE,
                null,
                warehouseId,
                WarehouseEquipmentStatus.OUT_OF_SERVICE,
                null
        );

        assertThat(resolved.locationType()).isEqualTo(EquipmentLocationType.WAREHOUSE);
        assertThat(resolved.warehouseId()).isEqualTo(warehouseId);
        assertThat(resolved.warehouseStatus()).isEqualTo(WarehouseEquipmentStatus.OUT_OF_SERVICE);
    }
}
