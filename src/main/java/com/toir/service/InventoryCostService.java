package com.toir.service;

import com.toir.entity.SparePart;
import com.toir.repository.SparePartRepository;
import com.toir.service.warehouse.LegacyStockProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class InventoryCostService {

    private final SparePartRepository sparePartRepository;
    private final LegacyStockProjectionService legacyStockProjectionService;

    @Transactional
    public void applyReceiptCost(SparePart sparePart, BigDecimal previousQuantity, BigDecimal receivedQuantity, BigDecimal unitCost) {
        if (sparePart == null || receivedQuantity == null || receivedQuantity.compareTo(BigDecimal.ZERO) <= 0 || unitCost == null) {
            return;
        }
        BigDecimal oldAverage = zero(sparePart.getAverageCost());
        BigDecimal oldValue = previousQuantity.max(BigDecimal.ZERO).multiply(oldAverage);
        BigDecimal newValue = receivedQuantity.multiply(unitCost);
        BigDecimal newQuantity = previousQuantity.max(BigDecimal.ZERO).add(receivedQuantity);
        if (newQuantity.compareTo(BigDecimal.ZERO) > 0) {
            sparePart.setAverageCost(oldValue.add(newValue).divide(newQuantity, 2, RoundingMode.HALF_UP));
        }
        sparePart.setLastPurchaseCost(unitCost);
        sparePart.setLastPurchasePrice(unitCost);
        refreshInventoryValue(sparePart);
    }

    @Transactional
    public void refreshInventoryValue(SparePart sparePart) {
        if (sparePart == null || sparePart.getId() == null) {
            return;
        }
        BigDecimal available = availableQuantity(sparePart);
        sparePart.setInventoryValue(available.multiply(zero(sparePart.getAverageCost())).setScale(2, RoundingMode.HALF_UP));
        sparePartRepository.save(sparePart);
    }

    public BigDecimal availableQuantity(SparePart sparePart) {
        if (sparePart == null || sparePart.getId() == null) {
            return BigDecimal.ZERO;
        }
        return legacyStockProjectionService.totalAvailable(
                legacyStockProjectionService.currentForSparePart(sparePart.getId()).values()
        ).max(BigDecimal.ZERO);
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
