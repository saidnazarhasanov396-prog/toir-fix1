package com.toir.service.repair;

import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.util.AuditBuilderService;
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
    private final AuditBuilderService auditBuilderService;


    @Transactional(readOnly = true)
    public List<RepairMaterialUsageDto> findByWorkOrder(UUID workOrderId) {
        return repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId).stream().map(RepairMaterialUsageDto::from).toList();
    }

    @Transactional
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
        RepairMaterialUsage saved = repository.save(usage);

        auditBuilderService.log(
                "repair_material_usage",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.REPAIR_MATERIAL_USAGE,
                "Использование материала в ремонте создано",
                null,
                saved
        );

        return RepairMaterialUsageDto.from(saved);
    }
}
