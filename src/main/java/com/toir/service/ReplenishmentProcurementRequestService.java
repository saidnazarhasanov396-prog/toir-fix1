package com.toir.service;

import com.toir.dto.procurement.ProcurementRequestDto;
import com.toir.dto.warehouse.InventoryReplenishmentRecommendationDto;
import com.toir.dto.warehouse.ReplenishmentProcurementItemRequest;
import com.toir.dto.warehouse.ReplenishmentProcurementRequest;
import com.toir.entity.SparePart;
import com.toir.entity.equipment.ProcurementRequestLine;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.entity.warehouse.Warehouse;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.PriorityLevel;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.enums.ProcurementRequestType;
import com.toir.exception.RestException;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReplenishmentProcurementRequestService {

    private final ProcurementRequestRepository procurementRequestRepository;
    private final SparePartRepository sparePartRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryReplenishmentRecommendationService recommendationService;
    private final ScopeAccessService scopeAccessService;
    private final AuditBuilderService auditBuilderService;

    @Transactional
    public List<ProcurementRequestDto> createProcurementRequests(ReplenishmentProcurementRequest request) {
        ReplenishmentProcurementRequest effectiveRequest = request == null
                ? new ReplenishmentProcurementRequest(null, null, null, null, null, List.of())
                : request;
        if (effectiveRequest.items() == null || effectiveRequest.items().isEmpty()) {
            throw RestException.badRequest("At least one replenishment item is required");
        }

        Map<RecommendationKey, InventoryReplenishmentRecommendationDto> recommendations = recommendationService
                .recommendationRows(
                        effectiveRequest.days(),
                        effectiveRequest.from(),
                        effectiveRequest.to(),
                        effectiveRequest.warehouseId(),
                        effectiveRequest.onlyDeficit()
                )
                .stream()
                .collect(Collectors.toMap(
                        item -> new RecommendationKey(item.sparePartId(), item.warehouseId()),
                        item -> item,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        Map<UUID, ProcurementAssembly> byWarehouse = new LinkedHashMap<>();
        Set<String> allocatedNumbers = new HashSet<>();
        for (ReplenishmentProcurementItemRequest item : effectiveRequest.items()) {
            if (item == null || item.sparePartId() == null) {
                throw RestException.badRequest("sparePartId is required for replenishment procurement item");
            }
            InventoryReplenishmentRecommendationDto recommendation = recommendations.get(
                    new RecommendationKey(item.sparePartId(), item.warehouseId())
            );
            if (recommendation == null) {
                throw RestException.badRequest("Replenishment recommendation not found for sparePartId="
                        + item.sparePartId() + ", warehouseId=" + item.warehouseId());
            }

            UUID targetWarehouseId = resolveTargetWarehouseId(item, recommendation);
            Warehouse warehouse = loadWarehouse(targetWarehouseId);
            assertCanCreateForWarehouse(warehouse);
            if (procurementRequestRepository.existsActiveAutoForWarehouseAndSparePart(targetWarehouseId, item.sparePartId())) {
                throw RestException.conflict("Active AUTO procurement request already exists for sparePartId="
                        + item.sparePartId() + ", warehouseId=" + targetWarehouseId);
            }

            SparePart sparePart = sparePartRepository.findByIdAndIsDeletedFalse(item.sparePartId())
                    .orElseThrow(() -> RestException.notFound("Spare part not found: " + item.sparePartId()));
            double quantity = requestedQuantity(item, recommendation);

            ProcurementAssembly assembly = byWarehouse.computeIfAbsent(targetWarehouseId,
                    ignored -> newAssembly(warehouse, allocatedNumbers));
            assembly.sparePartsById().put(sparePart.getId(), sparePart);
            assembly.request().setPriority(higherPriority(assembly.request().getPriority(), priority(recommendation)));
            assembly.request().setRequiredBy(earliest(assembly.request().getRequiredBy(), requiredBy(recommendation)));
            assembly.request().getLines().add(line(assembly.request(), sparePart, quantity, recommendation));
        }

        return byWarehouse.values().stream()
                .map(this::save)
                .toList();
    }

    private ProcurementAssembly newAssembly(Warehouse warehouse, Set<String> allocatedNumbers) {
        ProcurementRequest request = new ProcurementRequest();
        request.setNumber(nextNumber(allocatedNumbers));
        request.setTitle("Replenishment request - " + firstNonBlank(warehouse.getName(), warehouse.getId().toString()));
        request.setDescription("Generated from replenishment recommendations");
        request.setWarehouseId(warehouse.getId());
        request.setRequestedBy(scopeAccessService.currentUserIdOrNull());
        request.setPriority(PriorityLevel.MEDIUM);
        request.setType(ProcurementRequestType.SPARE_PART);
        request.setStatus(ProcurementRequestStatus.DRAFT);
        request.setSource("AUTO");
        return new ProcurementAssembly(request, warehouse, new HashMap<>());
    }

    private ProcurementRequestDto save(ProcurementAssembly assembly) {
        ProcurementRequest request = assembly.request();
        if (request.getRequiredBy() == null) {
            request.setRequiredBy(LocalDate.now(ZoneOffset.UTC).plusDays(14));
        }
        request.setTotalEstimatedCost(request.getLines().stream()
                .mapToDouble(ProcurementRequestLine::getEstimatedCost)
                .sum());
        ProcurementRequest saved = procurementRequestRepository.save(request);
        auditBuilderService.log(
                "procurement_request",
                saved.getId() == null ? saved.getNumber() : saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку создана",
                null,
                saved
        );
        return ProcurementRequestDto.from(saved, null, assembly.warehouse().getName(), assembly.sparePartsById());
    }

    private ProcurementRequestLine line(ProcurementRequest request,
                                        SparePart sparePart,
                                        double quantity,
                                        InventoryReplenishmentRecommendationDto recommendation) {
        ProcurementRequestLine line = new ProcurementRequestLine();
        line.setRequest(request);
        line.setSparePartId(sparePart.getId());
        line.setQuantity(quantity);
        line.setReceivedQuantity(0);
        line.setRemainingQuantity(quantity);
        line.setUnit(firstNonBlank(sparePart.getUnit(), "PCS"));
        line.setUnitPrice(null);
        line.setEstimatedCost(0.0);
        line.setNotes(notes(recommendation, quantity));
        return line;
    }

    private String notes(InventoryReplenishmentRecommendationDto recommendation, double quantity) {
        return String.format(Locale.ROOT,
                "Generated from replenishment recommendation: reason=%s, available=%s, min=%s, reorderPoint=%s, shortage=%s, suggested=%s, sourceCount=%d",
                recommendation.reason(),
                recommendation.availableStock(),
                recommendation.minStock(),
                recommendation.reorderPoint(),
                recommendation.totalShortageQty(),
                quantity,
                recommendation.sourceCount());
    }

    private UUID resolveTargetWarehouseId(ReplenishmentProcurementItemRequest item,
                                          InventoryReplenishmentRecommendationDto recommendation) {
        if (recommendation.warehouseId() != null) {
            if (item.targetWarehouseId() != null && !item.targetWarehouseId().equals(recommendation.warehouseId())) {
                throw RestException.badRequest("targetWarehouseId must match recommendation warehouseId");
            }
            return recommendation.warehouseId();
        }
        if (item.targetWarehouseId() == null) {
            throw RestException.badRequest("targetWarehouseId is required for enterprise-level replenishment recommendation");
        }
        return item.targetWarehouseId();
    }

    private double requestedQuantity(ReplenishmentProcurementItemRequest item,
                                     InventoryReplenishmentRecommendationDto recommendation) {
        double quantity = item.quantityOverride() == null
                ? recommendation.suggestedOrderQty()
                : item.quantityOverride();
        if (quantity <= 0) {
            throw RestException.badRequest("Replenishment procurement quantity must be greater than 0");
        }
        return quantity;
    }

    private Warehouse loadWarehouse(UUID warehouseId) {
        return warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                .orElseThrow(() -> RestException.notFound("Warehouse not found: " + warehouseId));
    }

    private void assertCanCreateForWarehouse(Warehouse warehouse) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (warehouse.getDepartmentId() != null && scopeAccessService.canAccessDepartment(warehouse.getDepartmentId())) {
            return;
        }
        if (warehouse.getResponsibleId() != null && scopeAccessService.canAccessEmployee(warehouse.getResponsibleId())) {
            return;
        }
        throw new AccessDeniedException("Access denied by procurement scope");
    }

    private PriorityLevel priority(InventoryReplenishmentRecommendationDto recommendation) {
        if (recommendation.severity() == NotificationSeverity.CRITICAL) {
            return PriorityLevel.CRITICAL;
        }
        if (recommendation.severity() == NotificationSeverity.WARNING) {
            return PriorityLevel.HIGH;
        }
        return PriorityLevel.MEDIUM;
    }

    private PriorityLevel higherPriority(PriorityLevel current, PriorityLevel incoming) {
        if (current == null) {
            return incoming;
        }
        if (incoming == null) {
            return current;
        }
        return incoming.ordinal() > current.ordinal() ? incoming : current;
    }

    private LocalDate requiredBy(InventoryReplenishmentRecommendationDto recommendation) {
        if (recommendation.firstDueAt() != null) {
            return recommendation.firstDueAt().atZone(ZoneOffset.UTC).toLocalDate();
        }
        if (recommendation.expectedDeliveryDate() != null) {
            return recommendation.expectedDeliveryDate();
        }
        return LocalDate.now(ZoneOffset.UTC).plusDays(14);
    }

    private LocalDate earliest(LocalDate current, LocalDate incoming) {
        if (current == null) {
            return incoming;
        }
        if (incoming == null) {
            return current;
        }
        return incoming.isBefore(current) ? incoming : current;
    }

    private String nextNumber(Set<String> allocatedNumbers) {
        String base = "PR-" + LocalDate.now(ZoneOffset.UTC).getYear() + "-";
        long count = procurementRequestRepository.countByIsDeletedFalse() + 1;
        String number;
        do {
            number = base + String.format("%05d", count);
            count++;
        } while (procurementRequestRepository.existsByNumberAndIsDeletedFalse(number)
                || !allocatedNumbers.add(number));
        return number;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private record RecommendationKey(UUID sparePartId, UUID warehouseId) {
    }

    private record ProcurementAssembly(ProcurementRequest request,
                                       Warehouse warehouse,
                                       Map<UUID, SparePart> sparePartsById) {
    }
}
