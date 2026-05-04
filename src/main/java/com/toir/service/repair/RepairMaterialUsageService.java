package com.toir.service.repair;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.repair.RepairMaterialUsageRepository;

import com.toir.exception.RestException;
import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.repository.WarehouseStockRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
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
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<RepairMaterialUsageDto> findByWorkOrder(UUID workOrderId) {
        return repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId).stream().map(RepairMaterialUsageDto::from).toList();
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
        RepairMaterialUsage saved = repository.save(usage);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return RepairMaterialUsageDto.from(saved);
    }

    private void audit(AuditAction action, UUID id, String oldJson, RepairMaterialUsage current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "repair_material_usage",
                id != null ? id.toString() : null,
                action,
                AuditModule.REPAIR_MATERIAL_USAGE,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Использование материала в ремонте создано";
            case UPDATE -> "Использование материала в ремонте обновлено";
            case DELETE -> "Использование материала в ремонте удалено";
            default -> "Действие выполнено над использованием материала в ремонте";
        };
    }
}
