package com.toir.service.warehouse;

import com.toir.dto.warehouse.WarehouseBinDto;
import com.toir.dto.warehouse.WarehouseBinRequest;
import com.toir.dto.warehouse.WarehouseBinStatusRequest;
import com.toir.dto.warehouse.WarehouseStockBalanceDto;
import com.toir.entity.warehouse.WarehouseBin;
import com.toir.enums.WarehouseQualityZoneType;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseBinRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseBinService {

    private final WarehouseBinRepository binRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseStockBalanceRepository balanceRepository;
    @SuppressWarnings("unused")
    private final ScopeAccessService scopeAccessService;

    @Transactional(readOnly = true)
    public Page<WarehouseBinDto> list(UUID warehouseId,
                                      String search,
                                      String zone,
                                      String aisle,
                                      String rack,
                                      String shelfLevel,
                                      String binType,
                                      WarehouseQualityZoneType qualityZoneType,
                                      String temperatureZone,
                                      String hazardClass,
                                      Boolean active,
                                      Boolean blocked,
                                      Boolean frozen,
                                      Integer binLevel,
                                      int page,
                                      int size) {
        assertWarehouseExists(warehouseId);
        return binRepository.search(
                warehouseId,
                trimToNull(search),
                trimToNull(zone),
                trimToNull(aisle),
                trimToNull(rack),
                trimToNull(shelfLevel),
                trimToNull(binType),
                qualityZoneType,
                trimToNull(temperatureZone),
                trimToNull(hazardClass),
                active,
                blocked,
                frozen,
                binLevel,
                PaginationUtils.pageRequest(page, size)
        ).map(WarehouseBinDto::from);
    }

    @Transactional(readOnly = true)
    public WarehouseBinDto get(UUID warehouseId, UUID binId) {
        assertWarehouseExists(warehouseId);
        return WarehouseBinDto.from(loadOwnedBin(warehouseId, binId));
    }

    @Transactional
    public WarehouseBinDto create(UUID warehouseId, WarehouseBinRequest request) {
        assertWarehouseExists(warehouseId);
        String code = normalizeRequiredCode(request.code());
        assertCodeAvailable(warehouseId, code, null);
        String barcode = normalizeBarcode(request.barcode());
        assertBarcodeAvailable(barcode, null);

        WarehouseBin bin = new WarehouseBin();
        bin.setWarehouseId(warehouseId);
        apply(bin, request, code, barcode, true);
        return WarehouseBinDto.from(binRepository.save(bin));
    }

    @Transactional
    public WarehouseBinDto update(UUID warehouseId, UUID binId, WarehouseBinRequest request) {
        assertWarehouseExists(warehouseId);
        WarehouseBin bin = loadOwnedBin(warehouseId, binId);
        String code = normalizeRequiredCode(request.code());
        assertCodeAvailable(warehouseId, code, bin.getCode());
        String barcode = normalizeBarcode(request.barcode());
        assertBarcodeAvailable(barcode, bin.getBarcode());

        boolean requestedActive = request.active() == null ? bin.isActive() : request.active();
        if (!requestedActive && bin.isActive() && hasStock(warehouseId, binId)) {
            throw RestException.badRequest("Cannot deactivate bin with stock");
        }
        apply(bin, request, code, barcode, false);
        return WarehouseBinDto.from(binRepository.save(bin));
    }

    @Transactional
    public WarehouseBinDto block(UUID warehouseId, UUID binId, WarehouseBinStatusRequest request) {
        assertWarehouseExists(warehouseId);
        WarehouseBin bin = loadOwnedActiveBin(warehouseId, binId);
        bin.setBlocked(true);
        bin.setBlockReason(trimToNull(request == null ? null : request.reason()));
        bin.setBlockedAt(Instant.now());
        return WarehouseBinDto.from(binRepository.save(bin));
    }

    @Transactional
    public WarehouseBinDto unblock(UUID warehouseId, UUID binId) {
        assertWarehouseExists(warehouseId);
        WarehouseBin bin = loadOwnedActiveBin(warehouseId, binId);
        bin.setBlocked(false);
        bin.setBlockReason(null);
        bin.setBlockedAt(null);
        return WarehouseBinDto.from(binRepository.save(bin));
    }

    @Transactional
    public WarehouseBinDto freeze(UUID warehouseId, UUID binId, WarehouseBinStatusRequest request) {
        assertWarehouseExists(warehouseId);
        WarehouseBin bin = loadOwnedActiveBin(warehouseId, binId);
        bin.setFrozen(true);
        return WarehouseBinDto.from(binRepository.save(bin));
    }

    @Transactional
    public WarehouseBinDto unfreeze(UUID warehouseId, UUID binId) {
        assertWarehouseExists(warehouseId);
        WarehouseBin bin = loadOwnedActiveBin(warehouseId, binId);
        bin.setFrozen(false);
        return WarehouseBinDto.from(binRepository.save(bin));
    }

    @Transactional(readOnly = true)
    public List<WarehouseStockBalanceDto> stockBalances(UUID warehouseId, UUID binId) {
        assertWarehouseExists(warehouseId);
        loadOwnedBin(warehouseId, binId);
        return balanceRepository.findAllByWarehouseIdAndBinIdAndIsDeletedFalse(warehouseId, binId)
                .stream()
                .map(WarehouseStockBalanceDto::from)
                .toList();
    }

    private void apply(WarehouseBin bin,
                       WarehouseBinRequest request,
                       String code,
                       String barcode,
                       boolean create) {
        bin.setCode(code);
        bin.setZone(trimToNull(request.zone()));
        bin.setAisle(trimToNull(request.aisle()));
        bin.setRack(trimToNull(request.rack()));
        bin.setShelfLevel(trimToNull(request.shelfLevel()));
        bin.setBinType(trimToNull(request.binType()));
        bin.setMaxWeightKg(request.maxWeightKg());
        bin.setMaxVolumeM3(request.maxVolumeM3());
        bin.setQualityZoneType(request.qualityZoneType() == null ? WarehouseQualityZoneType.STORAGE : request.qualityZoneType());
        bin.setTemperatureZone(trimToNull(request.temperatureZone()));
        bin.setHazardClass(trimToNull(request.hazardClass()));
        bin.setAllowMixedSpareParts(request.allowMixedSpareParts() == null || request.allowMixedSpareParts());
        bin.setAllowMixedLots(request.allowMixedLots() == null || request.allowMixedLots());
        bin.setBarcode(barcode);
        bin.setQrPayload(trimToNull(request.qrPayload()));
        if (create) {
            bin.setActive(request.active() == null || request.active());
        } else if (request.active() != null) {
            bin.setActive(request.active());
        }
        bin.setTravelSequence(request.travelSequence());
        bin.setBinLevel(request.binLevel());
    }

    private WarehouseBin loadOwnedActiveBin(UUID warehouseId, UUID binId) {
        WarehouseBin bin = loadOwnedBin(warehouseId, binId);
        if (!bin.isActive()) {
            throw RestException.badRequest("Cannot modify inactive bin");
        }
        return bin;
    }

    private WarehouseBin loadOwnedBin(UUID warehouseId, UUID binId) {
        WarehouseBin bin = binRepository.findByIdAndIsDeletedFalse(binId)
                .orElseThrow(() -> RestException.notFound("Warehouse bin not found: " + binId));
        if (!Objects.equals(bin.getWarehouseId(), warehouseId)) {
            throw RestException.badRequest("Bin does not belong to warehouse");
        }
        return bin;
    }

    private void assertWarehouseExists(UUID warehouseId) {
        if (warehouseId == null || !warehouseRepository.existsByIdAndIsDeletedFalse(warehouseId)) {
            throw RestException.notFound("Warehouse not found: " + warehouseId);
        }
    }

    private void assertCodeAvailable(UUID warehouseId, String code, String currentCode) {
        if (currentCode != null && currentCode.equalsIgnoreCase(code)) {
            return;
        }
        if (binRepository.existsByWarehouseIdAndCodeIgnoreCaseAndIsDeletedFalse(warehouseId, code)) {
            throw RestException.badRequest("Warehouse bin code already exists");
        }
    }

    private void assertBarcodeAvailable(String barcode, String currentBarcode) {
        if (barcode == null) {
            return;
        }
        if (currentBarcode != null && currentBarcode.equalsIgnoreCase(barcode)) {
            return;
        }
        if (binRepository.existsByBarcodeIgnoreCaseAndIsDeletedFalse(barcode)) {
            throw RestException.badRequest("Warehouse bin barcode already exists");
        }
    }

    private boolean hasStock(UUID warehouseId, UUID binId) {
        return balanceRepository.existsByWarehouseIdAndBinIdAndQtyOnHandGreaterThanAndIsDeletedFalse(
                warehouseId,
                binId,
                BigDecimal.ZERO
        );
    }

    private String normalizeRequiredCode(String code) {
        String normalized = trimToNull(code);
        if (normalized == null) {
            throw RestException.badRequest("code is required");
        }
        return normalized;
    }

    private String normalizeBarcode(String barcode) {
        String normalized = trimToNull(barcode);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
