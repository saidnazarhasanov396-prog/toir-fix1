package com.toir.service.warehouse;

import com.toir.enums.WarehouseStockStatus;
import com.toir.exception.RestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WmsStockCoordinateValidatorTest {

    WmsStockCoordinateValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WmsStockCoordinateValidator();
    }

    @Test
    void nullBinCoordinatesRemainAllowedForSurfaceWarehouseFlows() {
        UUID warehouseId = UUID.randomUUID();

        assertThatCode(() -> validator.assertCanReceiveOrMoveInto(
                warehouseId,
                null,
                WarehouseStockStatus.AVAILABLE
        )).doesNotThrowAnyException();
    }

    @Test
    void nonNullBinCoordinatesAreRejectedAfterBinModuleRemoval() {
        UUID warehouseId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();

        assertThatThrownBy(() -> validator.assertCanReadFrom(warehouseId, binId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Warehouse bins are no longer supported");
    }
}
