package com.toir.entity.warehouse;

import org.junit.jupiter.api.Test;

import com.toir.enums.WarehouseStockStatus;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WarehouseStockBalanceTest {

    @Test
    void availableQtySubtractsReservedFromOnHand() {
        WarehouseStockBalance balance = new WarehouseStockBalance();
        balance.setQtyOnHand(new BigDecimal("10.5000"));
        balance.setQtyReserved(new BigDecimal("3.2500"));

        assertThat(balance.getAvailableQty()).isEqualByComparingTo("7.2500");
    }

    @Test
    void availableQtyIsZeroWhenStockIsNotAvailable() {
        WarehouseStockBalance balance = new WarehouseStockBalance();
        balance.setStockStatus(WarehouseStockStatus.QUARANTINE);
        balance.setQtyOnHand(new BigDecimal("10.5000"));
        balance.setQtyReserved(BigDecimal.ZERO);

        assertThat(balance.getAvailableQty()).isEqualByComparingTo("0.0000");
    }

    @Test
    void prepareForSaveBuildsNormalizedIdentityKey() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        WarehouseStockBalance balance = new WarehouseStockBalance();
        balance.setWarehouseId(warehouseId);
        balance.setSparePartId(sparePartId);
        balance.setBinId(binId);
        balance.setLotNumber(" lot-a ");
        balance.setSerialNumber(" sn-7 ");

        balance.prepareForSave();

        assertThat(balance.getIdentityKey()).isEqualTo(
                warehouseId + "|" + sparePartId + "|" + binId + "|LOT-A|SN-7||AVAILABLE");
    }

    @Test
    void prepareForSaveRejectsReservedGreaterThanOnHand() {
        WarehouseStockBalance balance = new WarehouseStockBalance();
        balance.setQtyOnHand(new BigDecimal("2.0000"));
        balance.setQtyReserved(new BigDecimal("2.0001"));

        assertThatThrownBy(balance::prepareForSave)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("qtyReserved cannot exceed qtyOnHand");
    }
}
