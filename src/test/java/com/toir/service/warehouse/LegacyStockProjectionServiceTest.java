package com.toir.service.warehouse;

import com.toir.entity.warehouse.WarehouseStock;
import com.toir.entity.warehouse.WarehouseStockBalance;
import com.toir.entity.warehouse.WarehouseStockPolicy;
import com.toir.enums.WarehouseStockStatus;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.repository.WarehouseStockPolicyRepository;
import com.toir.repository.WarehouseStockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyStockProjectionServiceTest {

    @Mock
    WarehouseStockBalanceRepository balanceRepository;

    @Mock
    WarehouseStockRepository legacyRepository;

    @Mock
    WarehouseStockPolicyRepository policyRepository;

    @Test
    void currentAggregatesAllWmsBalanceIdentities() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        when(balanceRepository.findAllByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(List.of(balance("6.25", "1.00"), balance("3.75", "0.50")));

        LegacyStockProjectionService service =
                new LegacyStockProjectionService(balanceRepository, legacyRepository, policyRepository);

        WmsStockSnapshot result = service.current(warehouseId, sparePartId);

        assertThat(result.qtyOnHand()).isEqualByComparingTo("10.00");
        assertThat(result.qtyReserved()).isEqualByComparingTo("1.50");
        assertThat(result.availableQty()).isEqualByComparingTo("8.50");
    }

    @Test
    void currentUsesOnlyAvailableStatusForUsableAvailability() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        when(balanceRepository.findAllByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(List.of(
                        balance("6.25", "1.00", WarehouseStockStatus.AVAILABLE),
                        balance("3.75", "0.00", WarehouseStockStatus.QUARANTINE),
                        balance("2.00", "0.00", WarehouseStockStatus.BLOCKED)
                ));

        LegacyStockProjectionService service =
                new LegacyStockProjectionService(balanceRepository, legacyRepository, policyRepository);

        WmsStockSnapshot result = service.current(warehouseId, sparePartId);

        assertThat(result.qtyOnHand()).isEqualByComparingTo("12.00");
        assertThat(result.nonAvailableQty()).isEqualByComparingTo("5.75");
        assertThat(result.usableAvailableQty()).isEqualByComparingTo("5.25");
        assertThat(result.availableQty()).isEqualByComparingTo("5.25");
    }

    @Test
    void syncCreatesMissingPolicyAndReturnsWmsBackedCompatibilityView() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock compatibilityRow = stock(warehouseId, sparePartId, 4, 1);

        when(policyRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.empty());
        when(policyRepository.save(any(WarehouseStockPolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(legacyRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(compatibilityRow));

        LegacyStockProjectionService service =
                new LegacyStockProjectionService(balanceRepository, legacyRepository, policyRepository);

        WarehouseStock result = service.sync(warehouseId, sparePartId);

        assertThat(result).isSameAs(compatibilityRow);
        verify(policyRepository).save(any(WarehouseStockPolicy.class));
    }

    @Test
    void syncWithMinQtyUpdatesPolicyWithoutWritingCompatibilityQuantity() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStockPolicy policy = new WarehouseStockPolicy();
        policy.setWarehouseId(warehouseId);
        policy.setSparePartId(sparePartId);
        WarehouseStock compatibilityRow = stock(warehouseId, sparePartId, 2, 0);

        when(policyRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(policy));
        when(policyRepository.save(policy)).thenReturn(policy);
        when(legacyRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(compatibilityRow));

        LegacyStockProjectionService service =
                new LegacyStockProjectionService(balanceRepository, legacyRepository, policyRepository);

        WarehouseStock result = service.syncWithMinQty(warehouseId, sparePartId, 7);

        assertThat(policy.getMinQty()).isEqualTo(7);
        assertThat(result).isSameAs(compatibilityRow);
        verify(policyRepository).save(policy);
    }

    private WarehouseStockBalance balance(String onHand, String reserved) {
        return balance(onHand, reserved, WarehouseStockStatus.AVAILABLE);
    }

    private WarehouseStockBalance balance(String onHand, String reserved, WarehouseStockStatus status) {
        WarehouseStockBalance balance = new WarehouseStockBalance();
        balance.setQtyOnHand(new BigDecimal(onHand));
        balance.setQtyReserved(new BigDecimal(reserved));
        balance.setStockStatus(status);
        return balance;
    }

    private WarehouseStock stock(UUID warehouseId, UUID sparePartId, double quantity, double reserved) {
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(quantity);
        stock.setReservedQty(reserved);
        return stock;
    }
}
