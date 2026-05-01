package com.toir.service;
import com.toir.entity.RepairMaterialUsage;
import com.toir.repository.RepairMaterialUsageRepository;

import com.toir.exception.RestException;
import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.entity.WarehouseStock;
import com.toir.repository.WarehouseStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RepairMaterialUsageService {

    private final RepairMaterialUsageRepository repository;
    private final WarehouseStockRepository stockRepository;


    @Transactional(readOnly = true)
    public List<RepairMaterialUsageDto> findByWorkOrder(UUID workOrderId) {
        return com.toir.util.UpdatedAtSorter.descending(repository.findAllByWorkOrderIdAndIsDeletedFalse(workOrderId)).stream().map(RepairMaterialUsageDto::from).toList();
    }

    public RepairMaterialUsageDto register(UUID workOrderId, RepairMaterialUsageDto r) {
        WarehouseStock stock = stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(r.warehouseId(), r.sparePartId())
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
