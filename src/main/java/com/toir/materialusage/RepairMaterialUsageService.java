package com.toir.materialusage;

import com.toir.common.exception.RestException;
import com.toir.materialusage.dto.RepairMaterialUsageDto;
import com.toir.warehouse.WarehouseStock;
import com.toir.warehouse.WarehouseStockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RepairMaterialUsageService {

    private final RepairMaterialUsageRepository repository;
    private final WarehouseStockRepository stockRepository;

    public RepairMaterialUsageService(RepairMaterialUsageRepository repository,
                                      WarehouseStockRepository stockRepository) {
        this.repository = repository;
        this.stockRepository = stockRepository;
    }

    @Transactional(readOnly = true)
    public List<RepairMaterialUsageDto> findByWorkOrder(UUID workOrderId) {
        return repository.findAllByWorkOrderId(workOrderId).stream().map(RepairMaterialUsageDto::from).toList();
    }

    public RepairMaterialUsageDto register(UUID workOrderId, RepairMaterialUsageDto r) {
        WarehouseStock stock = stockRepository.findByWarehouseIdAndSparePartId(r.warehouseId(), r.sparePartId())
                .orElseThrow(() -> RestException.notFound("No stock found for spare part in this warehouse"));
        if (stock.getQuantity() < r.quantity()) {
            throw RestException.badRequest("Cannot write off more than available: available="
                    + stock.getQuantity() + ", requested=" + r.quantity());
        }
        stock.setQuantity(stock.getQuantity() - r.quantity());

        RepairMaterialUsage usage = new RepairMaterialUsage();
        usage.setWorkOrderId(workOrderId);
        usage.setWarehouseId(r.warehouseId());
        usage.setSparePartId(r.sparePartId());
        usage.setQuantity(r.quantity());
        usage.setUnitCost(r.unitCost());
        return RepairMaterialUsageDto.from(repository.save(usage));
    }
}
