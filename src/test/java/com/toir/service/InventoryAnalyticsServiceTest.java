package com.toir.service;

import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.enums.InventoryMovementClass;
import com.toir.enums.StockMovementType;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.security.ScopeAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryAnalyticsServiceTest {

    @Mock
    SparePartRepository sparePartRepository;
    @Mock
    WarehouseStockRepository stockRepository;
    @Mock
    StockMovementRepository movementRepository;
    @Mock
    WarehouseRepository warehouseRepository;
    @Mock
    ScopeAccessService scopeAccessService;
    @Mock
    InventoryCostService inventoryCostService;

    InventoryAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new InventoryAnalyticsService(
                sparePartRepository,
                stockRepository,
                movementRepository,
                warehouseRepository,
                scopeAccessService,
                inventoryCostService
        );
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
    }

    @Test
    void abcClassificationKeepsTopValueItemInAClass() {
        SparePart expensive = part("EXP", BigDecimal.valueOf(100));
        SparePart cheap = part("CHP", BigDecimal.TEN);
        when(sparePartRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(expensive, cheap));
        when(movementRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                issue(expensive.getId(), 100, LocalDate.now().minusMonths(1)),
                issue(cheap.getId(), 1, LocalDate.now().minusMonths(1))
        ));

        var result = service.abcAnalysis();

        assertThat(result.getFirst().sparePartId()).isEqualTo(expensive.getId());
        assertThat(result.getFirst().classification()).isEqualTo("A");
    }

    @Test
    void deadStockIncludesPartsWithoutRecentIssues() {
        SparePart part = part("DEAD", BigDecimal.ONE);
        when(sparePartRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(part));
        when(movementRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());

        var result = service.deadStock();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().movementClass()).isEqualTo(InventoryMovementClass.DEAD);
    }

    private SparePart part(String code, BigDecimal averageCost) {
        SparePart part = new SparePart();
        part.setId(UUID.randomUUID());
        part.setCode(code);
        part.setName(code);
        part.setAverageCost(averageCost);
        part.setMinStock(0);
        return part;
    }

    private StockMovement issue(UUID sparePartId, double quantity, LocalDate date) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(UUID.randomUUID());
        movement.setSparePartId(sparePartId);
        movement.setType(StockMovementType.ISSUE);
        movement.setQuantity(quantity);
        movement.setMovementDate(date);
        return movement;
    }
}
