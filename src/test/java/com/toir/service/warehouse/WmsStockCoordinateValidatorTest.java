package com.toir.service.warehouse;

import com.toir.entity.warehouse.WarehouseBin;
import com.toir.enums.WarehouseQualityZoneType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseBinRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WmsStockCoordinateValidatorTest {

    @Mock
    WarehouseBinRepository binRepository;

    WmsStockCoordinateValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WmsStockCoordinateValidator(binRepository);
    }

    @Test
    void receiveIntoBlockedBinIsRejected() {
        UUID warehouseId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        WarehouseBin bin = bin(warehouseId, binId);
        bin.setBlocked(true);
        when(binRepository.findByIdAndIsDeletedFalse(binId)).thenReturn(Optional.of(bin));

        assertThatThrownBy(() -> validator.assertCanReceiveOrMoveInto(
                warehouseId,
                binId,
                WarehouseStockStatus.AVAILABLE
        )).isInstanceOf(RestException.class)
                .hasMessageContaining("blocked bin");
    }

    @Test
    void readFromForeignWarehouseBinIsRejected() {
        UUID warehouseId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        WarehouseBin bin = bin(UUID.randomUUID(), binId);
        when(binRepository.findByIdAndIsDeletedFalse(binId)).thenReturn(Optional.of(bin));

        assertThatThrownBy(() -> validator.assertCanReadFrom(warehouseId, binId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Bin does not belong to warehouse");
    }


    @Test
    void putawayIntoReceivingZoneIsRejected() {
        UUID warehouseId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        WarehouseBin bin = bin(warehouseId, binId);
        bin.setQualityZoneType(WarehouseQualityZoneType.RECEIVING);
        when(binRepository.findByIdAndIsDeletedFalse(binId)).thenReturn(Optional.of(bin));

        assertThatThrownBy(() -> validator.assertCanPutawayInto(
                warehouseId,
                binId,
                WarehouseStockStatus.AVAILABLE
        )).isInstanceOf(RestException.class)
                .hasMessageContaining("RECEIVING");
    }

    @Test
    void putawayIntoStorageZoneIsAllowed() {
        UUID warehouseId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        WarehouseBin bin = bin(warehouseId, binId);
        bin.setQualityZoneType(WarehouseQualityZoneType.STORAGE);
        when(binRepository.findByIdAndIsDeletedFalse(binId)).thenReturn(Optional.of(bin));

        assertThatCode(() -> validator.assertCanPutawayInto(
                warehouseId,
                binId,
                WarehouseStockStatus.AVAILABLE
        )).doesNotThrowAnyException();
    }

    private WarehouseBin bin(UUID warehouseId, UUID binId) {
        WarehouseBin bin = new WarehouseBin();
        bin.setId(binId);
        bin.setWarehouseId(warehouseId);
        bin.setCode("A-01");
        bin.setActive(true);
        return bin;
    }
}
