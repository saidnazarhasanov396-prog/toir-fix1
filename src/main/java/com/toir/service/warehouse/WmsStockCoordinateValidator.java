package com.toir.service.warehouse;

import com.toir.enums.WarehouseStockStatus;
import com.toir.exception.RestException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class WmsStockCoordinateValidator {

    public void assertCanReceiveOrMoveInto(UUID warehouseId, UUID binId, WarehouseStockStatus status) {
        assertNoBin(binId);
    }

    public void assertCanPutawayInto(UUID warehouseId, UUID binId, WarehouseStockStatus status) {
        assertNoBin(binId);
    }

    public void assertCanReadFromReceiving(UUID warehouseId, UUID binId) {
        assertNoBin(binId);
    }

    public void assertCanReadFrom(UUID warehouseId, UUID binId) {
        assertNoBin(binId);
    }

    private void assertNoBin(UUID binId) {
        if (binId != null) {
            throw RestException.badRequest("Warehouse bins are no longer supported");
        }
    }
}
