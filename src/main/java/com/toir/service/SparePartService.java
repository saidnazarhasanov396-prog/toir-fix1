package com.toir.service;

import com.toir.dto.sparepart.SparePartDto;
import com.toir.dto.sparepart.SparePartRequest;
import com.toir.entity.SparePart;
import com.toir.entity.UnitOfMeasurement;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.InventoryItemKind;
import com.toir.enums.SparePartType;
import com.toir.enums.SparePartUnit;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.UnitOfMeasurementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import com.toir.util.CodeGenerationUtils;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class SparePartService {

    private final SparePartRepository repository;
    private final WarehouseStockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;
    private final UnitOfMeasurementRepository unitOfMeasurementRepository;
    private final UnitOfMeasurementService unitOfMeasurementService;
    private final ScopeAccessService scopeAccessService;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public Page<SparePartDto> findAll(Integer pageSize, Integer page, String itemType, String search, UUID warehouseId) {
        return findAll(pageSize, page, itemType, null, search, warehouseId);
    }

    @Transactional(readOnly = true)
    public Page<SparePartDto> findAll(
            Integer pageSize,
            Integer page,
            String itemType,
            String type,
            String search,
            UUID warehouseId
    ) {
        int safePage = Math.max(page != null ? page : 0, 0);
        int safePageSize = Math.max(pageSize != null ? pageSize : 20, 1);
        Pageable pageable = PaginationUtils.pageRequest(safePage, safePageSize);
        InventoryItemKind inventoryItemKind = mapItemType(itemType);
        SparePartType sparePartType = mapSparePartType(type);
        String searchPattern = toSearchPattern(search);

        List<UUID> scopedWarehouseIds = null;
        Page<SparePart> parts;
        if (warehouseId != null) {
            assertCanAccessWarehouseId(warehouseId);
            parts = repository.findAllByFilterAndWarehouseId(
                    inventoryItemKind,
                    sparePartType,
                    searchPattern,
                    warehouseId,
                    pageable
            );
        } else if (scopeAccessService.isScopeAdmin()) {
            parts = repository.findAllByFilter(
                    inventoryItemKind,
                    sparePartType,
                    searchPattern,
                    pageable
            );
        } else {
            scopedWarehouseIds = accessibleWarehouseIds();
            if (scopedWarehouseIds.isEmpty()) {
                return Page.empty(pageable);
            }
            parts = repository.findAllByFilterAndWarehouseIds(
                    inventoryItemKind,
                    sparePartType,
                    searchPattern,
                    scopedWarehouseIds,
                    pageable
            );
        }

        if (parts.isEmpty()) {
            return parts.map(SparePartDto::from);
        }
        Map<String, SparePartDto.UnitRef> unitRefsByToken = unitRefsByToken(parts.getContent());

        List<UUID> sparePartIds = parts.getContent().stream().map(SparePart::getId).toList();
        List<WarehouseStock> scopedStocks;
        if (warehouseId != null) {
            scopedStocks = stockRepository.findAllBySparePartIdInAndWarehouseIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                    sparePartIds,
                    warehouseId
            );
        } else if (scopeAccessService.isScopeAdmin()) {
            scopedStocks = stockRepository.findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(sparePartIds);
        } else {
            scopedStocks = stockRepository.findAllBySparePartIdInAndWarehouseIdInAndIsDeletedFalseOrderByUpdatedAtDesc(
                    sparePartIds,
                    scopedWarehouseIds
            );
        }

        Map<UUID, List<WarehouseStock>> stocksByPart = scopedStocks
                .stream()
                .filter(s -> s.getSparePartId() != null)
                .collect(Collectors.groupingBy(WarehouseStock::getSparePartId));

        return parts
                .map(part -> {
                    List<WarehouseStock> stocks = stocksByPart.getOrDefault(part.getId(), List.of());
                    double currentStock = stocks.stream().mapToDouble(WarehouseStock::getQuantity).sum();
                    double reservedStock = stocks.stream().mapToDouble(WarehouseStock::getReservedQty).sum();
                    return SparePartDto.from(
                            part,
                            currentStock,
                            reservedStock,
                            stocks.size(),
                            unitRefFor(part.getUnit(), unitRefsByToken)
                    );
                });
    }

    private InventoryItemKind mapItemType(String itemType) {
        if (itemType == null) {
            return null;
        }
        String normalized = itemType.trim();
        if (normalized.isEmpty() || normalized.equalsIgnoreCase("ALL")) {
            return null;
        }
        return switch (normalized.toUpperCase()) {
            case "SPARE_PART", "SPARE_PARTS" -> InventoryItemKind.SPARE_PART;
            case "MATERIAL", "MATERIALS" -> InventoryItemKind.MATERIAL;
            case "CONSUMABLE", "CONSUMABLES" -> InventoryItemKind.CONSUMABLE;
            default -> throw RestException.badRequest("Invalid itemType: " + itemType);
        };
    }

    private SparePartType mapSparePartType(String type) {
        if (type == null) {
            return null;
        }
        String normalized = type.trim();
        if (normalized.isEmpty() || normalized.equalsIgnoreCase("ALL")) {
            return null;
        }
        try {
            return SparePartType.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw RestException.badRequest("Invalid type: " + type);
        }
    }

    @Transactional(readOnly = true)
    public SparePartDto findById(UUID id) {
        SparePart part = getOrThrow(id);
        List<WarehouseStock> stocks = stockRepository.findAllBySparePartIdAndIsDeletedFalse(id);
        double currentStock = stocks.stream().mapToDouble(WarehouseStock::getQuantity).sum();
        double reservedStock = stocks.stream().mapToDouble(WarehouseStock::getReservedQty).sum();
        return SparePartDto.from(part, currentStock, reservedStock, stocks.size(), unitRefFor(part.getUnit()));
    }

    @Transactional
    public SparePartDto create(SparePartRequest request) {
        CodeGenerationUtils.rejectClientProvidedCode(request.code());
        SparePart entity = new SparePart();
        entity.setCode(nextCode());
        apply(entity, request);
        SparePart saved = repository.save(entity);

        auditBuilderService.log(
                "spare_part",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.SPARE_PART,
                "Запасная часть создана",
                null,
                saved
        );

        return SparePartDto.from(saved, 0, 0, 0, unitRefFor(saved.getUnit()));
    }

    @Transactional
    public SparePartDto update(UUID id, SparePartRequest request) {
        CodeGenerationUtils.rejectClientProvidedCode(request.code());
        SparePart entity = getOrThrow(id);
        apply(entity, request);

        SparePart saved = repository.save(entity);

        auditBuilderService.log(
                "spare_part",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.SPARE_PART,
                "Запасная часть обновлена",
                entity,
                saved
        );
        return SparePartDto.from(saved, 0, 0, 0, unitRefFor(saved.getUnit()));
    }

    @Transactional
    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        SparePart saved = repository.save(entity);

        auditBuilderService.log(
                "spare_part",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.SPARE_PART,
                "Запасная часть удалена",
                saved,
                null
        );
    }

    SparePart getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Spare part not found: " + id));
    }

    private void apply(SparePart entity, SparePartRequest request) {
        entity.setName(request.name());
        entity.setSku(request.sku());
        if (request.kind() != null) entity.setKind(request.kind());
        SparePartType type = request.type() != null ? request.type() : SparePartType.OTHER;
        entity.setType(type);
        entity.setUnit(resolveUnit(request.unit(), type));
        entity.setSpecification(request.specification());
        entity.setManufacturer(request.manufacturer());
        entity.setMinStock(request.minStock());
    }

    private String resolveUnit(String rawUnit, SparePartType type) {
        String token = rawUnit == null ? null : rawUnit.trim();
        if (token == null || token.isBlank()) {
            return defaultUnit(type).name();
        }
        try {
            String normalized = unitOfMeasurementService.normalizeOptionalUnitOrNull(token);
            if (normalized != null && !normalized.isBlank()) {
                return normalized;
            }
        } catch (RestException ignored) {
            // Spare part units remain flexible; the UoM dictionary is a normalization aid, not a hard policy.
        }
        return token;
    }

    private SparePartUnit defaultUnit(SparePartType type) {
        return switch (type != null ? type : SparePartType.OTHER) {
            case OIL, CHEMICAL -> SparePartUnit.LITER;
            case GREASE, RAW_MATERIAL -> SparePartUnit.KG;
            case BELT -> SparePartUnit.METER;
            case FILTER, BEARING, ELECTRICAL, MECHANICAL, FASTENER, CONSUMABLE, OTHER -> SparePartUnit.PCS;
        };
    }

    private String nextCode() {
        String prefix = "SP-" + java.time.Year.now().getValue() + "-";
        return CodeGenerationUtils.nextYearSequenceCode(
                "SP",
                () -> repository.maxSequenceByCodePrefix(prefix),
                repository::existsByCodeAndIsDeletedFalse
        );
    }

    public String toSearchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim().toLowerCase() + "%";
    }

    private List<UUID> accessibleWarehouseIds() {
        return warehouseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(this::canAccessWarehouse)
                .map(Warehouse::getId)
                .toList();
    }

    private void assertCanAccessWarehouseId(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                .orElseThrow(() -> RestException.notFound("Warehouse not found: " + warehouseId));
        if (!canAccessWarehouse(warehouse)) {
            throw new AccessDeniedException("Access denied by warehouse scope");
        }
    }

    private boolean canAccessWarehouse(Warehouse warehouse) {
        if (warehouse == null) {
            return false;
        }
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        return (warehouse.getDepartmentId() != null && scopeAccessService.canAccessDepartment(warehouse.getDepartmentId()))
                || (warehouse.getResponsibleId() != null && scopeAccessService.canAccessEmployee(warehouse.getResponsibleId()));
    }

    private SparePartDto.UnitRef unitRefFor(String unit) {
        if (unit == null || unit.isBlank()) {
            return SparePartDto.unitRef(unit);
        }
        return unitRefFor(unit, unitRefsByToken(List.of(unit)));
    }

    private SparePartDto.UnitRef unitRefFor(String unit, Map<String, SparePartDto.UnitRef> unitRefsByToken) {
        String token = normalizeToken(unit);
        if (token == null) {
            return SparePartDto.unitRef(unit);
        }
        return unitRefsByToken.getOrDefault(token, SparePartDto.unitRef(unit));
    }

    private Map<String, SparePartDto.UnitRef> unitRefsByToken(List<?> unitSources) {
        List<String> tokens = unitSources.stream()
                .map(source -> source instanceof SparePart sparePart ? sparePart.getUnit() : source)
                .map(value -> value == null ? null : value.toString())
                .map(this::normalizeToken)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (tokens.isEmpty()) {
            return Map.of();
        }
        return unitOfMeasurementRepository.findAllByTokenIgnoreCaseIn(tokens).stream()
                .flatMap(unit -> unitTokenEntries(unit).entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left));
    }

    private Map<String, SparePartDto.UnitRef> unitTokenEntries(UnitOfMeasurement unit) {
        SparePartDto.UnitRef ref = new SparePartDto.UnitRef(unit.getCode(), unit.getName());
        return Stream.of(unit.getCode(), unit.getName(), unit.getNameEn(), unit.getNameUz())
                .map(this::normalizeToken)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Function.identity(), ignored -> ref, (left, right) -> left));
    }

    private String normalizeToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return token.trim().toLowerCase();
    }
}
