package com.toir.controller;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.repository.WarehouseStockRepository;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/warehouses/reorder")
@Tag(name = "warehouse-reorder")
public class WarehouseReorderController {

    private final WarehouseStockRepository stockRepository;

    public WarehouseReorderController(WarehouseStockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public record ReorderSuggestion(
            UUID stockId,
            UUID warehouseId,
            UUID sparePartId,
            double quantity,
            double available,
            Double minQty,
            Double reorderPoint,
            Double reorderQty,
            double shortfall,
            String urgency
    ) {}

    @GetMapping("/suggestions")
    public ResponseEntity<Page<ReorderSuggestion>> suggestions(@RequestParam(required = false) UUID warehouseId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        List<WarehouseStock> stocks = warehouseId != null
                ? stockRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId)
                : stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();

        List<ReorderSuggestion> result = new ArrayList<>();
        for (WarehouseStock s : stocks) {
            double available = s.getAvailable();
            Double rp = s.getReorderPoint();
            double minQty = s.getMinQty();
            double trigger = rp != null ? rp : minQty;
            if (trigger <= 0) continue;
            if (available > trigger) continue;

            double shortfall;
            String urgency;
            if (available <= minQty) {
                shortfall = Math.max((s.getReorderQty() != null ? s.getReorderQty() : minQty * 2) - available, 0);
                urgency = "CRITICAL";
            } else {
                shortfall = trigger - available;
                urgency = "WARNING";
            }

            result.add(new ReorderSuggestion(
                    s.getId(), s.getWarehouseId(), s.getSparePartId(),
                    s.getQuantity(), available, minQty, rp, s.getReorderQty(),
                    shortfall, urgency));
        }
        return ResponseEntity.ok(PaginationUtils.page(result, page, size));
    }
}
