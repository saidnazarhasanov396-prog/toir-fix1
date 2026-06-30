package com.toir.service.warehouse;

import com.toir.dto.warehouse.WarehouseBinRequest;
import com.toir.dto.warehouse.WarehouseBinStatusRequest;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseBin;
import com.toir.entity.warehouse.WarehouseStockBalance;
import com.toir.enums.WarehouseQualityZoneType;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseBinRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.security.ScopeAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseBinServiceTest {

    @Mock
    WarehouseBinRepository binRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    WarehouseStockBalanceRepository balanceRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    WarehouseBinService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseBinService(binRepository, warehouseRepository, balanceRepository, scopeAccessService);
    }

    @Test
    void createTrimsCodeUppercasesBarcodeAndValidatesUniqueCodePerWarehouse() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.existsByIdAndIsDeletedFalse(warehouseId)).thenReturn(true);
        when(binRepository.existsByWarehouseIdAndCodeIgnoreCaseAndIsDeletedFalse(warehouseId, "A-01-02-03"))
                .thenReturn(false);
        when(binRepository.existsByBarcodeIgnoreCaseAndIsDeletedFalse("BIN-001")).thenReturn(false);
        when(binRepository.save(any(WarehouseBin.class))).thenAnswer(invocation -> {
            WarehouseBin bin = invocation.getArgument(0);
            bin.setId(UUID.randomUUID());
            return bin;
        });

        service.create(warehouseId, new WarehouseBinRequest(
                " A-01-02-03 ",
                "receiving",
                "A",
                "01",
                "02",
                "PALLET",
                new BigDecimal("100.00"),
                new BigDecimal("2.5"),
                WarehouseQualityZoneType.RECEIVING,
                "AMBIENT",
                null,
                false,
                true,
                " bin-001 ",
                "qr",
                true,
                10,
                2
        ));

        ArgumentCaptor<WarehouseBin> captor = ArgumentCaptor.forClass(WarehouseBin.class);
        verify(binRepository).save(captor.capture());
        WarehouseBin saved = captor.getValue();
        assertThat(saved.getWarehouseId()).isEqualTo(warehouseId);
        assertThat(saved.getCode()).isEqualTo("A-01-02-03");
        assertThat(saved.getBarcode()).isEqualTo("BIN-001");
        assertThat(saved.getQualityZoneType()).isEqualTo(WarehouseQualityZoneType.RECEIVING);
        assertThat(saved.isAllowMixedSpareParts()).isFalse();
        assertThat(saved.isAllowMixedLots()).isTrue();
    }

    @Test
    void listForwardsTrimmedFiltersAndPagination() {
        UUID warehouseId = UUID.randomUUID();
        WarehouseBin bin = bin(warehouseId, UUID.randomUUID());
        when(warehouseRepository.existsByIdAndIsDeletedFalse(warehouseId)).thenReturn(true);
        when(binRepository.search(
                eq(warehouseId),
                eq("A-01"),
                isNull(),
                eq("A"),
                eq("01"),
                eq("02"),
                eq("PALLET"),
                eq(WarehouseQualityZoneType.STORAGE),
                eq("AMBIENT"),
                eq("NONE"),
                eq(true),
                eq(false),
                eq(false),
                eq(2),
                eq(PageRequest.of(1, 5))
        )).thenReturn(new PageImpl<>(List.of(bin), PageRequest.of(1, 5), 6));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(List.of(warehouseId)))
                .thenReturn(List.of(warehouse(warehouseId, "Central Warehouse")));

        var result = service.list(
                warehouseId,
                " A-01 ",
                "   ",
                " A ",
                " 01 ",
                " 02 ",
                " PALLET ",
                WarehouseQualityZoneType.STORAGE,
                " AMBIENT ",
                " NONE ",
                true,
                false,
                false,
                2,
                1,
                5
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().id()).isEqualTo(bin.getId());
        assertThat(result.getContent().getFirst().warehouseName()).isEqualTo("Central Warehouse");
        assertThat(result.getTotalElements()).isEqualTo(6);
    }

    @Test
    void listAllowsMissingWarehouseIdAndReturnsWarehouseName() {
        UUID warehouseId = UUID.randomUUID();
        WarehouseBin bin = bin(warehouseId, UUID.randomUUID());
        when(binRepository.search(
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(PageRequest.of(0, 20))
        )).thenReturn(new PageImpl<>(List.of(bin), PageRequest.of(0, 20), 1));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(List.of(warehouseId)))
                .thenReturn(List.of(warehouse(warehouseId, "Central Warehouse")));

        var result = service.list(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                20
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().warehouseId()).isEqualTo(warehouseId);
        assertThat(result.getContent().getFirst().warehouseName()).isEqualTo("Central Warehouse");
        verify(warehouseRepository, never()).existsByIdAndIsDeletedFalse(null);
    }

    @Test
    void updateRejectsDeactivationWhenStockExistsInBin() {
        UUID warehouseId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        WarehouseBin bin = bin(warehouseId, binId);
        when(warehouseRepository.existsByIdAndIsDeletedFalse(warehouseId)).thenReturn(true);
        when(binRepository.findByIdAndIsDeletedFalse(binId)).thenReturn(Optional.of(bin));
        when(balanceRepository.existsByWarehouseIdAndBinIdAndQtyOnHandGreaterThanAndIsDeletedFalse(
                warehouseId,
                binId,
                BigDecimal.ZERO
        )).thenReturn(true);

        assertThatThrownBy(() -> service.update(warehouseId, binId, new WarehouseBinRequest(
                "A-01-02-03",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                WarehouseQualityZoneType.STORAGE,
                null,
                null,
                true,
                true,
                null,
                null,
                false,
                null,
                null
        ))).isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot deactivate bin with stock");
    }

    @Test
    void blockAndUnblockToggleBlockFields() {
        UUID warehouseId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        WarehouseBin bin = bin(warehouseId, binId);
        when(warehouseRepository.existsByIdAndIsDeletedFalse(warehouseId)).thenReturn(true);
        when(binRepository.findByIdAndIsDeletedFalse(binId)).thenReturn(Optional.of(bin));
        when(binRepository.save(any(WarehouseBin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var blocked = service.block(warehouseId, binId, new WarehouseBinStatusRequest("inventory count"));
        assertThat(blocked.blocked()).isTrue();
        assertThat(blocked.blockReason()).isEqualTo("inventory count");
        assertThat(blocked.blockedAt()).isNotNull();

        var unblocked = service.unblock(warehouseId, binId);
        assertThat(unblocked.blocked()).isFalse();
        assertThat(unblocked.blockReason()).isNull();
        assertThat(unblocked.blockedAt()).isNull();
    }

    @Test
    void stockBalancesReturnsBalancesScopedByBin() {
        UUID warehouseId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        WarehouseBin bin = bin(warehouseId, binId);
        WarehouseStockBalance balance = new WarehouseStockBalance();
        balance.setId(UUID.randomUUID());
        balance.setWarehouseId(warehouseId);
        balance.setSparePartId(UUID.randomUUID());
        balance.setBinId(binId);
        balance.setQtyOnHand(BigDecimal.TEN);
        balance.setQtyReserved(BigDecimal.ONE);
        balance.prepareForSave();

        when(warehouseRepository.existsByIdAndIsDeletedFalse(warehouseId)).thenReturn(true);
        when(binRepository.findByIdAndIsDeletedFalse(binId)).thenReturn(Optional.of(bin));
        when(balanceRepository.findAllByWarehouseIdAndBinIdAndIsDeletedFalse(warehouseId, binId))
                .thenReturn(List.of(balance));

        var result = service.stockBalances(warehouseId, binId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().binId()).isEqualTo(binId);
        assertThat(result.getFirst().availableQty()).isEqualByComparingTo("9");
    }

    private WarehouseBin bin(UUID warehouseId, UUID binId) {
        WarehouseBin bin = new WarehouseBin();
        bin.setId(binId);
        bin.setWarehouseId(warehouseId);
        bin.setCode("A-01-02-03");
        bin.setActive(true);
        return bin;
    }

    private Warehouse warehouse(UUID warehouseId, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setName(name);
        return warehouse;
    }
}
