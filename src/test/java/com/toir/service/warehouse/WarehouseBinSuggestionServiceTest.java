package com.toir.service.warehouse;

import com.toir.entity.warehouse.WarehouseBin;
import com.toir.enums.WarehouseQualityZoneType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.repository.WarehouseBinRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseBinSuggestionServiceTest {

    @Mock
    WarehouseBinRepository binRepository;

    WarehouseBinSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseBinSuggestionService(binRepository);
    }

    @Test
    void suggestPutawayBinChoosesFirstUsableStorageBin() {
        UUID warehouseId = UUID.randomUUID();
        UUID stagingBinId = UUID.randomUUID();
        WarehouseBin staging = bin(stagingBinId, "RECV", WarehouseQualityZoneType.RECEIVING, 1, true, false, false);
        WarehouseBin blocked = bin(UUID.randomUUID(), "S-01", WarehouseQualityZoneType.STORAGE, 2, true, true, false);
        WarehouseBin quarantine = bin(UUID.randomUUID(), "Q-01", WarehouseQualityZoneType.QUARANTINE, 3, true, false, false);
        WarehouseBin storage = bin(UUID.randomUUID(), "S-02", WarehouseQualityZoneType.STORAGE, 4, true, false, false);
        when(binRepository.findAllByWarehouseIdAndIsDeletedFalseOrderByTravelSequenceAscCodeAsc(warehouseId))
                .thenReturn(List.of(staging, blocked, quarantine, storage));

        var result = service.suggestPutawayBin(warehouseId, stagingBinId, WarehouseStockStatus.AVAILABLE);

        assertThat(result).contains(storage.getId());
    }

    @Test
    void suggestPutawayBinUsesQuarantineZoneForQuarantineStock() {
        UUID warehouseId = UUID.randomUUID();
        UUID stagingBinId = UUID.randomUUID();
        WarehouseBin storage = bin(UUID.randomUUID(), "S-01", WarehouseQualityZoneType.STORAGE, 1, true, false, false);
        WarehouseBin quarantine = bin(UUID.randomUUID(), "Q-01", WarehouseQualityZoneType.QUARANTINE, 2, true, false, false);
        when(binRepository.findAllByWarehouseIdAndIsDeletedFalseOrderByTravelSequenceAscCodeAsc(warehouseId))
                .thenReturn(List.of(storage, quarantine));

        var result = service.suggestPutawayBin(warehouseId, stagingBinId, WarehouseStockStatus.QUARANTINE);

        assertThat(result).contains(quarantine.getId());
    }

    private WarehouseBin bin(UUID id,
                             String code,
                             WarehouseQualityZoneType qualityZoneType,
                             Integer travelSequence,
                             boolean active,
                             boolean blocked,
                             boolean frozen) {
        WarehouseBin bin = new WarehouseBin();
        bin.setId(id);
        bin.setCode(code);
        bin.setQualityZoneType(qualityZoneType);
        bin.setTravelSequence(travelSequence);
        bin.setActive(active);
        bin.setBlocked(blocked);
        bin.setFrozen(frozen);
        return bin;
    }
}
