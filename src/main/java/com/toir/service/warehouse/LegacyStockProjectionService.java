package com.toir.service.warehouse;

import com.toir.entity.warehouse.WarehouseStock;
import com.toir.entity.warehouse.WarehouseStockBalance;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.repository.WarehouseStockPolicyRepository;
import com.toir.repository.WarehouseStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LegacyStockProjectionService {

    private final WarehouseStockBalanceRepository balanceRepository;
    private final WarehouseStockRepository legacyRepository;
    private final WarehouseStockPolicyRepository policyRepository;

    @Transactional(readOnly = true)
    public WmsStockSnapshot current(UUID warehouseId, UUID sparePartId) {
        BigDecimal onHand = BigDecimal.ZERO;
        BigDecimal reserved = BigDecimal.ZERO;
        for (WarehouseStockBalance balance :
                balanceRepository.findAllByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId)) {
            onHand = onHand.add(zero(balance.getQtyOnHand()));
            reserved = reserved.add(zero(balance.getQtyReserved()));
        }
        return new WmsStockSnapshot(warehouseId, sparePartId, onHand, reserved);
    }

    @Transactional(readOnly = true)
    public Map<StockKey, WmsStockSnapshot> currentAll() {
        return aggregate(balanceRepository.findAllByIsDeletedFalse());
    }

    @Transactional(readOnly = true)
    public Map<StockKey, WmsStockSnapshot> currentForWarehouse(UUID warehouseId) {
        return aggregate(balanceRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId));
    }

    @Transactional(readOnly = true)
    public Map<StockKey, WmsStockSnapshot> currentForSparePart(UUID sparePartId) {
        return aggregate(balanceRepository.findAllBySparePartIdAndIsDeletedFalse(sparePartId));
    }

    public WmsStockSnapshot snapshot(Map<StockKey, WmsStockSnapshot> snapshots,
                                     UUID warehouseId,
                                     UUID sparePartId) {
        return snapshots.getOrDefault(
                new StockKey(warehouseId, sparePartId),
                new WmsStockSnapshot(warehouseId, sparePartId, BigDecimal.ZERO, BigDecimal.ZERO)
        );
    }

    public BigDecimal totalOnHand(Collection<WmsStockSnapshot> snapshots) {
        return snapshots.stream()
                .map(WmsStockSnapshot::qtyOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalAvailable(Collection<WmsStockSnapshot> snapshots) {
        return snapshots.stream()
                .map(WmsStockSnapshot::availableQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public WarehouseStock sync(UUID warehouseId, UUID sparePartId) {
        ensurePolicy(warehouseId, sparePartId, null);
        return legacyRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId)
                .orElseThrow();
    }

    @Transactional
    public WarehouseStock syncWithMinQty(UUID warehouseId, UUID sparePartId, double minQty) {
        ensurePolicy(warehouseId, sparePartId, minQty);
        return legacyRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId)
                .orElseThrow();
    }

    private Map<StockKey, WmsStockSnapshot> aggregate(List<WarehouseStockBalance> balances) {
        Map<StockKey, WmsStockSnapshot> result = new LinkedHashMap<>();
        for (WarehouseStockBalance balance : balances) {
            StockKey key = new StockKey(balance.getWarehouseId(), balance.getSparePartId());
            WmsStockSnapshot current = result.getOrDefault(key, new WmsStockSnapshot(
                    balance.getWarehouseId(), balance.getSparePartId(), BigDecimal.ZERO, BigDecimal.ZERO));
            result.put(key, new WmsStockSnapshot(
                    key.warehouseId(),
                    key.sparePartId(),
                    current.qtyOnHand().add(zero(balance.getQtyOnHand())),
                    current.qtyReserved().add(zero(balance.getQtyReserved()))
            ));
        }
        return result;
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void ensurePolicy(UUID warehouseId, UUID sparePartId, Double minQty) {
        var policy = policyRepository
                .findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId)
                .orElseGet(() -> {
                    var created = new com.toir.entity.warehouse.WarehouseStockPolicy();
                    created.setWarehouseId(warehouseId);
                    created.setSparePartId(sparePartId);
                    return created;
                });
        if (minQty != null) {
            policy.setMinQty(minQty);
        }
        policyRepository.save(policy);
    }

    public record StockKey(UUID warehouseId, UUID sparePartId) {
    }
}
