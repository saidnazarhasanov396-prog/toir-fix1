package com.toir.service.warehouse;

import com.toir.entity.warehouse.WarehouseBin;
import com.toir.enums.WarehouseQualityZoneType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseBinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WmsStockCoordinateValidator {

    private final WarehouseBinRepository binRepository;

    @Transactional(readOnly = true)
    public void assertCanReceiveOrMoveInto(UUID warehouseId, UUID binId, WarehouseStockStatus status) {
        if (binId == null) {
            return;
        }
        WarehouseBin bin = loadBin(binId);
        assertSameWarehouse(warehouseId, bin);
        assertUsableDestination(bin);
    }

    @Transactional(readOnly = true)
    public void assertCanPutawayInto(UUID warehouseId, UUID binId, WarehouseStockStatus status) {
        if (binId == null) {
            throw RestException.badRequest("toBinId is required for putaway");
        }
        WarehouseBin bin = loadBin(binId);
        assertSameWarehouse(warehouseId, bin);
        assertUsableDestination(bin);
        if (bin.getQualityZoneType() == WarehouseQualityZoneType.RECEIVING) {
            throw RestException.badRequest("PUTAWAY destination bin cannot be in RECEIVING zone");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanReadFromReceiving(UUID warehouseId, UUID binId) {
        if (binId == null) {
            throw RestException.badRequest("fromBinId is required for putaway");
        }
        assertCanReadFrom(warehouseId, binId);
    }

    @Transactional(readOnly = true)
    public void assertCanReadFrom(UUID warehouseId, UUID binId) {
        if (binId == null) {
            return;
        }
        WarehouseBin bin = loadBin(binId);
        assertSameWarehouse(warehouseId, bin);
        if (!bin.isActive()) {
            throw RestException.badRequest("Cannot read stock from inactive bin");
        }
    }

    private void assertUsableDestination(WarehouseBin bin) {
        if (!bin.isActive()) {
            throw RestException.badRequest("Cannot use inactive bin");
        }
        if (bin.isBlocked()) {
            throw RestException.badRequest("Cannot receive or move stock into blocked bin");
        }
        if (bin.isFrozen()) {
            throw RestException.badRequest("Cannot receive or move stock into frozen bin");
        }
    }

    private WarehouseBin loadBin(UUID binId) {
        return binRepository.findByIdAndIsDeletedFalse(binId)
                .orElseThrow(() -> RestException.notFound("Warehouse bin not found: " + binId));
    }

    private void assertSameWarehouse(UUID warehouseId, WarehouseBin bin) {
        if (!Objects.equals(bin.getWarehouseId(), warehouseId)) {
            throw RestException.badRequest("Bin does not belong to warehouse");
        }
    }
}
