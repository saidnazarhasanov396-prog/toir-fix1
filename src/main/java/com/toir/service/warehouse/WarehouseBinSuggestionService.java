package com.toir.service.warehouse;

import com.toir.entity.warehouse.WarehouseBin;
import com.toir.enums.WarehouseQualityZoneType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.repository.WarehouseBinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseBinSuggestionService {

    private final WarehouseBinRepository binRepository;

    public Optional<UUID> suggestPutawayBin(UUID warehouseId, UUID stagingBinId, WarehouseStockStatus stockStatus) {
        if (warehouseId == null) {
            return Optional.empty();
        }
        WarehouseQualityZoneType targetZone = targetZone(stockStatus);
        return binRepository.findAllByWarehouseIdAndIsDeletedFalseOrderByTravelSequenceAscCodeAsc(warehouseId)
                .stream()
                .filter(WarehouseBin::isActive)
                .filter(bin -> !bin.isBlocked())
                .filter(bin -> !bin.isFrozen())
                .filter(bin -> !Objects.equals(bin.getId(), stagingBinId))
                .filter(bin -> bin.getQualityZoneType() == targetZone)
                .min(Comparator
                        .comparing(WarehouseBin::getTravelSequence, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(WarehouseBin::getBinLevel, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(WarehouseBin::getCode, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(WarehouseBin::getId);
    }

    private WarehouseQualityZoneType targetZone(WarehouseStockStatus status) {
        if (status == WarehouseStockStatus.QUARANTINE) {
            return WarehouseQualityZoneType.QUARANTINE;
        }
        if (status == WarehouseStockStatus.DAMAGED) {
            return WarehouseQualityZoneType.DAMAGED;
        }
        if (status == WarehouseStockStatus.EXPIRED || status == WarehouseStockStatus.WRITEOFF_PENDING) {
            return WarehouseQualityZoneType.SCRAP;
        }
        return WarehouseQualityZoneType.STORAGE;
    }
}
