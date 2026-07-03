package com.toir.service;

import com.toir.dto.sparepart.SparePartDto;
import com.toir.dto.sparepart.SparePartDetailDto;
import com.toir.dto.sparepart.SparePartLocationDto;
import com.toir.dto.sparepart.SparePartRecentMovementDto;
import com.toir.dto.mxik.MxikRefDto;
import com.toir.entity.Department;
import com.toir.entity.Location;
import com.toir.dto.sparepart.SparePartRequest;
import com.toir.entity.InventoryTransaction;
import com.toir.entity.Mxik;
import com.toir.entity.SparePart;
import com.toir.entity.SparePartType;
import com.toir.entity.StockMovement;
import com.toir.entity.UnitOfMeasurement;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.InventoryItemKind;
import com.toir.enums.InventoryTransactionType;
import com.toir.enums.SparePartUnit;
import com.toir.exception.RestException;
import com.toir.repository.InventoryTransactionRepository;
import com.toir.repository.LocationRepository;
import com.toir.repository.MxikRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.SparePartTypeRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.UnitOfMeasurementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.warehouse.LegacyStockProjectionService;
import com.toir.service.warehouse.WmsStockSnapshot;
import com.toir.util.AuditBuilderService;
import com.toir.util.CodeGenerationUtils;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class SparePartService {

    private final SparePartRepository repository;
    private final SparePartTypeRepository typeRepository;
    private final CounteragentService counteragentService;
    private final MxikRepository mxikRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final WarehouseStockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WarehouseRepository warehouseRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final UnitOfMeasurementRepository unitOfMeasurementRepository;
    private final UnitOfMeasurementService unitOfMeasurementService;
    private final ScopeAccessService scopeAccessService;
    private final AuditBuilderService auditBuilderService;
    private final LegacyStockProjectionService legacyStockProjectionService;
    private final WarehouseStockPolicyService warehouseStockPolicyService;

    private static final List<String> NUMERIC_SORT_FIELDS = List.of(
            "minStock",
            "currentStock",
            "reservedStock",
            "availableStock",
            "warehouseCount",
            "leadTimeDays",
            "lastPurchasePrice",
            "averageCost",
            "lastPurchaseCost",
            "inventoryValue"
    );

    @Transactional(readOnly = true)
    public Page<SparePartDto> findAll(Integer pageSize, Integer page, String itemType, String search, UUID warehouseId) {
        return findAll(pageSize, page, itemType, (UUID) null, null, null, search, warehouseId, null, "asc");
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
        return findAll(pageSize, page, itemType, null, type, null, search, warehouseId, null, "asc");
    }

    @Transactional(readOnly = true)
    public Page<SparePartDto> findAll(
            Integer pageSize,
            Integer page,
            String itemType,
            UUID typeId,
            String search,
            UUID warehouseId
    ) {
        return findAll(pageSize, page, itemType, typeId, null, null, search, warehouseId, null, "asc");
    }

    @Transactional(readOnly = true)
    public Page<SparePartDto> findAll(
            Integer pageSize,
            Integer page,
            String itemType,
            UUID typeId,
            String type,
            String search,
            UUID warehouseId
    ) {
        return findAll(pageSize, page, itemType, typeId, type, null, search, warehouseId, null, "asc");
    }

    @Transactional(readOnly = true)
    public Page<SparePartDto> findAll(
            Integer pageSize,
            Integer page,
            String itemType,
            UUID typeId,
            String type,
            String search,
            UUID warehouseId,
            String sortBy,
            String sortDir
    ) {
        return findAll(pageSize, page, itemType, typeId, type, null, search, warehouseId, sortBy, sortDir);
    }

    @Transactional(readOnly = true)
    public Page<SparePartDto> findAll(
            Integer pageSize,
            Integer page,
            String itemType,
            UUID typeId,
            String type,
            String unit,
            String search,
            UUID warehouseId
    ) {
        return findAll(pageSize, page, itemType, typeId, type, unit, search, warehouseId, null, "asc");
    }

    @Transactional(readOnly = true)
    public Page<SparePartDto> findAll(
            Integer pageSize,
            Integer page,
            String itemType,
            UUID typeId,
            String type,
            String unit,
            String search,
            UUID warehouseId,
            String sortBy,
            String sortDir
    ) {
        return findAll(pageSize, page, itemType, typeId, type, unit, search, warehouseId, null, sortBy, sortDir);
    }

    @Transactional(readOnly = true)
    public Page<SparePartDto> findAll(
            Integer pageSize,
            Integer page,
            String itemType,
            UUID typeId,
            String type,
            String unit,
            String search,
            UUID warehouseId,
            UUID mxikId,
            String sortBy,
            String sortDir
    ) {
        int safePage = Math.max(page != null ? page : 0, 0);
        int safePageSize = Math.max(pageSize != null ? pageSize : 20, 1);
        boolean numericSort = isNumericSort(sortBy);
        Pageable pageable = numericSort ? Pageable.unpaged() : PaginationUtils.pageRequest(safePage, safePageSize);
        InventoryItemKind inventoryItemKind = mapItemType(itemType);
        UUID sparePartTypeId = resolveTypeFilter(typeId, type);
        UUID unitId = resolveUnitFilter(unit);
        String searchPattern = toSearchPattern(search);

        List<UUID> scopedWarehouseIds = null;
        Page<SparePart> parts;
        if (warehouseId != null) {
            assertCanAccessWarehouseId(warehouseId);
            parts = mxikId == null
                    ? repository.findAllByFilterAndWarehouseId(
                            inventoryItemKind,
                            sparePartTypeId,
                            unitId,
                            searchPattern,
                            warehouseId,
                            pageable
                    )
                    : repository.findAllByFilterAndWarehouseIdWithMxik(
                            inventoryItemKind,
                            sparePartTypeId,
                            unitId,
                            mxikId,
                            searchPattern,
                            warehouseId,
                            pageable
                    );
        } else if (scopeAccessService.isScopeAdmin()) {
            parts = mxikId == null
                    ? repository.findAllByFilter(
                            inventoryItemKind,
                            sparePartTypeId,
                            unitId,
                            searchPattern,
                            pageable
                    )
                    : repository.findAllByFilterWithMxik(
                            inventoryItemKind,
                            sparePartTypeId,
                            unitId,
                            mxikId,
                            searchPattern,
                            pageable
                    );
        } else {
            scopedWarehouseIds = accessibleWarehouseIds();
            if (scopedWarehouseIds.isEmpty()) {
                return Page.empty(pageable);
            }
            parts = mxikId == null
                    ? repository.findAllByFilterAndWarehouseIds(
                            inventoryItemKind,
                            sparePartTypeId,
                            unitId,
                            searchPattern,
                            scopedWarehouseIds,
                            pageable
                    )
                    : repository.findAllByFilterAndWarehouseIdsWithMxik(
                            inventoryItemKind,
                            sparePartTypeId,
                            unitId,
                            mxikId,
                            searchPattern,
                            scopedWarehouseIds,
                            pageable
                    );
        }

        if (parts.isEmpty()) {
            return parts.map(SparePartDto::from);
        }
        Page<SparePartDto> enrichedParts = enrichPartPage(parts, warehouseId, scopedWarehouseIds);
        if (!numericSort) {
            return enrichedParts;
        }
        List<SparePartDto> sorted = enrichedParts.getContent().stream()
                .sorted(sparePartComparator(sortBy, sortDir))
                .toList();
        return PaginationUtils.page(sorted, safePage, safePageSize);
    }

    private Page<SparePartDto> enrichPartPage(Page<SparePart> parts, UUID warehouseId, List<UUID> scopedWarehouseIds) {
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
        var stockSnapshots = legacyStockProjectionService.currentAll();
        Map<UUID, Mxik> mxikById = mxikMap(parts.getContent());

        return parts
                .map(part -> {
                    List<WarehouseStock> stocks = stocksByPart.getOrDefault(part.getId(), List.of());
                    double currentStock = stocks.stream().mapToDouble(stock -> snapshot(stock, stockSnapshots).qtyOnHand().doubleValue()).sum();
                    double reservedStock = stocks.stream().mapToDouble(stock -> snapshot(stock, stockSnapshots).qtyReserved().doubleValue()).sum();
                    double availableStock = stocks.stream().mapToDouble(stock -> snapshot(stock, stockSnapshots).availableQty().doubleValue()).sum();
                    double nonAvailableStock = stocks.stream().mapToDouble(stock -> snapshot(stock, stockSnapshots).nonAvailableQty().doubleValue()).sum();
                    return enrichWarehousePolicies(enrichCounteragent(SparePartDto.from(
                            part,
                            currentStock,
                            reservedStock,
                            availableStock,
                            nonAvailableStock,
                            stocks.size(),
                            unitRefFor(part.getUnit(), unitRefsByToken),
                            MxikRefDto.from(mxikById.get(part.getMxikId()))
                    )));
                });
    }

    private boolean isNumericSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return false;
        }
        return NUMERIC_SORT_FIELDS.contains(sortBy.trim());
    }

    private Comparator<SparePartDto> sparePartComparator(String sortBy, String sortDir) {
        Comparator<SparePartDto> comparator = switch (sortBy.trim()) {
            case "minStock" -> Comparator.comparingDouble(SparePartDto::minStock);
            case "currentStock" -> Comparator.comparingDouble(SparePartDto::currentStock);
            case "reservedStock" -> Comparator.comparingDouble(SparePartDto::reservedStock);
            case "availableStock" -> Comparator.comparingDouble(SparePartDto::availableStock);
            case "warehouseCount" -> Comparator.comparingInt(SparePartDto::warehouseCount);
            case "leadTimeDays" -> Comparator.comparing(
                    SparePartDto::leadTimeDays,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "lastPurchasePrice" -> Comparator.comparing(
                    SparePartDto::lastPurchasePrice,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "averageCost" -> Comparator.comparing(
                    SparePartDto::averageCost,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "lastPurchaseCost" -> Comparator.comparing(
                    SparePartDto::lastPurchaseCost,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "inventoryValue" -> Comparator.comparing(
                    SparePartDto::inventoryValue,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            default -> throw RestException.badRequest("Unsupported spare part sort: " + sortBy);
        };
        return "desc".equalsIgnoreCase(sortDir) ? comparator.reversed() : comparator;
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

    private UUID resolveTypeFilter(UUID typeId, String type) {
        if (typeId != null) {
            typeRepository.findByIdAndActiveTrue(typeId)
                    .orElseThrow(() -> RestException.badRequest("Invalid typeId: " + typeId));
            return typeId;
        }
        if (type == null) {
            return null;
        }
        String normalized = type.trim();
        if (normalized.isEmpty() || normalized.equalsIgnoreCase("ALL")) {
            return null;
        }
        String code = normalizeLegacyTypeCode(normalized);
        return typeRepository.findByCodeIgnoreCaseAndActiveTrue(code)
                .map(SparePartType::getId)
                .orElseThrow(() -> RestException.badRequest("Invalid type: " + type));
    }

    @Transactional(readOnly = true)
    public SparePartDto findById(UUID id) {
        SparePart part = getOrThrow(id);
        List<WarehouseStock> stocks = stockRepository.findAllBySparePartIdAndIsDeletedFalse(id);
        var stockSnapshots = legacyStockProjectionService.currentForSparePart(id);
        double currentStock = stocks.stream().mapToDouble(stock -> snapshot(stock, stockSnapshots).qtyOnHand().doubleValue()).sum();
        double reservedStock = stocks.stream().mapToDouble(stock -> snapshot(stock, stockSnapshots).qtyReserved().doubleValue()).sum();
        double availableStock = stocks.stream().mapToDouble(stock -> snapshot(stock, stockSnapshots).availableQty().doubleValue()).sum();
        double nonAvailableStock = stocks.stream().mapToDouble(stock -> snapshot(stock, stockSnapshots).nonAvailableQty().doubleValue()).sum();
        return enrichWarehousePolicies(enrichCounteragent(SparePartDto.from(part, currentStock, reservedStock,
                availableStock, nonAvailableStock, stocks.size(), unitRefFor(part.getUnit()),
                MxikRefDto.from(mxik(part.getMxikId()).orElse(null)))));
    }

    @Transactional(readOnly = true)
    public List<SparePartLocationDto> findLocations(UUID id) {
        SparePart part = getOrThrow(id);
        ScopedStockContext context = scopedStocksForPart(id);
        return locationDtos(part, context);
    }

    @Transactional(readOnly = true)
    public SparePartDetailDto findDetail(UUID id) {
        SparePart part = getOrThrow(id);
        ScopedStockContext context = scopedStocksForPart(id);
        List<SparePartLocationDto> locations = locationDtos(part, context);
        List<SparePartRecentMovementDto> movements = recentMovements(part, context.warehouseById());
        SparePartType type = part.getType();

        double totalQuantity = locations.stream().mapToDouble(SparePartLocationDto::quantity).sum();
        double totalReservedQty = locations.stream().mapToDouble(SparePartLocationDto::reservedQty).sum();
        double totalAvailableQty = locations.stream().mapToDouble(SparePartLocationDto::availableQty).sum();
        double totalNonAvailableQty = context.stocks().stream()
                .mapToDouble(stock -> snapshot(stock, context.snapshots()).nonAvailableQty().doubleValue())
                .sum();
        InventoryTransactionSummary transactionSummary = transactionSummaryFor(part.getId(), totalQuantity);

        return new SparePartDetailDto(
                part.getId(),
                part.getCode(),
                part.getName(),
                part.getSku(),
                type == null ? null : type.getId(),
                type == null ? part.getLegacyType() : type.getCode(),
                type == null ? null : type.getName(),
                part.getUnit(),
                part.getSpecification(),
                part.getManufacturer(),
                part.getMxikId(),
                MxikRefDto.from(mxik(part.getMxikId()).orElse(null)),
                totalQuantity,
                totalReservedQty,
                totalAvailableQty,
                totalNonAvailableQty,
                transactionSummary.totalReceivedQuantity(),
                transactionSummary.totalIssuedQuantity(),
                transactionSummary.currentQuantity(),
                transactionSummary.lastReceiptDate(),
                transactionSummary.lastIssueDate(),
                transactionSummary.lastReceiptDocument(),
                transactionSummary.lastIssueDocument(),
                locations,
                movements
        );
    }

    @Transactional
    public SparePartDto create(SparePartRequest request) {
        CodeGenerationUtils.rejectClientProvidedCode(request.code());
        validateMxik(request.mxikId());
        SparePart entity = new SparePart();
        entity.setCode(nextCode());
        apply(entity, request);
        SparePart saved = repository.save(entity);
        var warehousePolicies = warehouseStockPolicyService.replaceForSparePart(saved.getId(), request.warehousePolicies());

        auditBuilderService.log(
                "spare_part",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.SPARE_PART,
                "Запасная часть создана",
                null,
                saved
        );

        return enrichCounteragent(SparePartDto.from(saved, 0, 0, 0, unitRefFor(saved.getUnit()),
                MxikRefDto.from(mxik(request.mxikId()).orElse(null))).withWarehousePolicies(warehousePolicies));
    }

    @Transactional
    public SparePartDto update(UUID id, SparePartRequest request) {
        CodeGenerationUtils.rejectClientProvidedCode(request.code());
        validateMxik(request.mxikId());
        SparePart entity = getOrThrow(id);
        apply(entity, request);

        SparePart saved = repository.save(entity);
        var warehousePolicies = warehouseStockPolicyService.replaceForSparePart(saved.getId(), request.warehousePolicies());

        auditBuilderService.log(
                "spare_part",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.SPARE_PART,
                "Запасная часть обновлена",
                entity,
                saved
        );
        return enrichCounteragent(SparePartDto.from(saved, 0, 0, 0, unitRefFor(saved.getUnit()),
                MxikRefDto.from(mxik(request.mxikId()).orElse(null))).withWarehousePolicies(warehousePolicies));
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
        SparePartType type = resolveTypeForRequest(request);
        entity.setType(type);
        entity.setLegacyType(type.getCode());
        entity.setMxikId(request.mxikId());
        entity.setUnit(resolveUnit(request.unit(), type));
        entity.setSpecification(request.specification());
        entity.setManufacturer(request.manufacturer());
        entity.setMinStock(request.minStock());
        validatePreferredCounteragent(request.preferredCounteragentId());
        entity.setPreferredCounteragentId(request.preferredCounteragentId());
        entity.setLeadTimeDays(request.leadTimeDays());
        entity.setLastPurchasePrice(request.lastPurchasePrice());
        entity.setAverageCost(request.averageCost());
        entity.setLastPurchaseCost(request.lastPurchaseCost());
        if (request.criticality() != null) {
            entity.setCriticality(request.criticality());
        }
    }

    private void validateMxik(UUID mxikId) {
        if (mxikId == null) {
            return;
        }
        mxikRepository.findByIdAndIsDeletedFalse(mxikId)
                .orElseThrow(() -> RestException.notFound("MXIK not found: " + mxikId));
    }

    private Optional<Mxik> mxik(UUID mxikId) {
        if (mxikId == null) {
            return Optional.empty();
        }
        return mxikRepository.findByIdAndIsDeletedFalse(mxikId);
    }

    private Map<UUID, Mxik> mxikMap(List<SparePart> parts) {
        List<UUID> mxikIds = parts.stream()
                .map(SparePart::getMxikId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (mxikIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return mxikRepository.findAllByIdInAndIsDeletedFalse(mxikIds)
                .stream()
                .collect(Collectors.toMap(Mxik::getId, Function.identity(), (left, right) -> left));
    }

    private SparePartDto enrichCounteragent(SparePartDto dto) {
        if (dto == null || dto.preferredCounteragentId() == null) {
            return dto;
        }
        try {
            return dto.withPreferredCounteragentName(counteragentService.load(dto.preferredCounteragentId()).getName());
        } catch (RestException ignored) {
            return dto;
        }
    }

    private SparePartDto enrichWarehousePolicies(SparePartDto dto) {
        if (dto == null || dto.id() == null) {
            return dto;
        }
        return dto.withWarehousePolicies(warehouseStockPolicyService.findBySparePart(dto.id()));
    }

    private UUID resolveUnitFilter(String unit) {
        if (unit == null || unit.isBlank()) {
            return null;
        }
        String token = unit.trim();
        Optional<UUID> parsedId = parseUuid(token);
        if (parsedId.isPresent()) {
            return unitOfMeasurementRepository.existsByIdAndIsDeletedFalse(parsedId.get())
                    ? parsedId.get()
                    : null;
        }
        return unitOfMeasurementRepository.findByTokenIgnoreCase(token).stream()
                .findFirst()
                .map(UnitOfMeasurement::getId)
                .orElse(null);
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private void validatePreferredCounteragent(UUID counteragentId) {
        if (counteragentId == null) {
            return;
        }
        counteragentService.loadActive(counteragentId, "spare parts");
    }

    private String resolveUnit(String rawUnit, SparePartType type) {
        String token = rawUnit == null ? null : rawUnit.trim();
        if (token == null || token.isBlank()) {
            if (type != null && type.getDefaultUnit() != null && !type.getDefaultUnit().isBlank()) {
                return type.getDefaultUnit();
            }
            return defaultUnit(type == null ? null : type.getCode()).name();
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

    private SparePartType resolveTypeForRequest(SparePartRequest request) {
        if (request.typeId() != null) {
            return typeRepository.findByIdAndActiveTrue(request.typeId())
                    .orElseThrow(() -> RestException.badRequest("Invalid typeId: " + request.typeId()));
        }
        String code = request.type() != null ? legacyCodeFromEnum(request.type()) : "OTHER";
        return typeRepository.findByCodeIgnoreCaseAndActiveTrue(code)
                .orElseThrow(() -> RestException.badRequest("Invalid type: " + code));
    }

    private String legacyCodeFromEnum(com.toir.enums.SparePartType type) {
        return switch (type) {
            case ELECTRICAL -> "ELECTRICAL_PART";
            case MECHANICAL -> "MECHANICAL_PART";
            default -> type.name();
        };
    }

    private String normalizeLegacyTypeCode(String type) {
        String code = type.trim().replaceAll("[^A-Za-z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "").toUpperCase();
        return switch (code) {
            case "ELECTRICAL" -> "ELECTRICAL_PART";
            case "MECHANICAL" -> "MECHANICAL_PART";
            default -> code;
        };
    }

    private SparePartUnit defaultUnit(String typeCode) {
        return switch (typeCode != null ? typeCode : "OTHER") {
            case "OIL", "CHEMICAL" -> SparePartUnit.LITER;
            case "GREASE", "RAW_MATERIAL", "METAL" -> SparePartUnit.KG;
            case "BELT", "CABLE" -> SparePartUnit.METER;
            case "FILTER", "BEARING", "ELECTRICAL_PART", "MECHANICAL_PART", "ELECTRICAL", "MECHANICAL",
                 "FASTENER", "CONSUMABLE", "OTHER" -> SparePartUnit.PCS;
            default -> SparePartUnit.PCS;
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

    private ScopedStockContext scopedStocksForPart(UUID sparePartId) {
        List<WarehouseStock> stocks = stockRepository.findAllBySparePartIdAndIsDeletedFalse(sparePartId);
        if (stocks.isEmpty()) {
            return new ScopedStockContext(List.of(), Map.of(), Map.of());
        }

        List<UUID> warehouseIds = stocks.stream()
                .map(WarehouseStock::getWarehouseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, Warehouse> warehouseById = warehouseIds.isEmpty()
                ? Map.of()
                : warehouseRepository.findAllByIdInAndIsDeletedFalse(warehouseIds).stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));

        List<WarehouseStock> scopedStocks = stocks.stream()
                .filter(stock -> canAccessWarehouse(warehouseById.get(stock.getWarehouseId())))
                .toList();
        return new ScopedStockContext(scopedStocks, warehouseById, legacyStockProjectionService.currentForSparePart(sparePartId));
    }

    private List<SparePartLocationDto> locationDtos(SparePart part, ScopedStockContext context) {
        if (context.stocks().isEmpty()) {
            return List.of();
        }
        List<Warehouse> scopedWarehouses = context.stocks().stream()
                .map(stock -> context.warehouseById().get(stock.getWarehouseId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, String> departmentNames = departmentNames(scopedWarehouses);
        Map<UUID, String> locationNames = locationNames(scopedWarehouses);

        return context.stocks().stream()
                .map(stock -> {
                    WmsStockSnapshot snapshot = snapshot(stock, context.snapshots());
                    Warehouse warehouse = context.warehouseById().get(stock.getWarehouseId());
                    UUID departmentId = warehouse == null ? null : warehouse.getDepartmentId();
                    UUID locationId = warehouse == null ? null : warehouse.getLocationId();
                    return new SparePartLocationDto(
                            stock.getWarehouseId(),
                            warehouse == null ? null : warehouse.getName(),
                            departmentId,
                            departmentId == null ? null : departmentNames.get(departmentId),
                            locationId,
                            locationId == null ? null : locationNames.get(locationId),
                            stock.getBinLocation(),
                            snapshot.qtyOnHand().doubleValue(),
                            snapshot.qtyReserved().doubleValue(),
                            snapshot.availableQty().doubleValue(),
                            part.getUnit(),
                            stock.getMinQty(),
                            stock.getMaxQty(),
                            stock.getReorderPoint()
                    );
                })
                .toList();
    }

    private Map<UUID, String> departmentNames(List<Warehouse> warehouses) {
        List<UUID> ids = warehouses.stream()
                .map(Warehouse::getDepartmentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return departmentRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
    }

    private Map<UUID, String> locationNames(List<Warehouse> warehouses) {
        List<UUID> ids = warehouses.stream()
                .map(Warehouse::getLocationId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return locationRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(Location::getId, Location::getName));
    }

    private List<SparePartRecentMovementDto> recentMovements(SparePart part, Map<UUID, Warehouse> stockWarehouseById) {
        List<StockMovement> movements = stockMovementRepository.findAllBySparePartIdAndIsDeletedFalseOrderByOccurredAtDesc(part.getId());
        if (movements.isEmpty()) {
            return List.of();
        }
        Map<UUID, Warehouse> warehouseById = new HashMap<>(stockWarehouseById);
        List<UUID> missingWarehouseIds = movements.stream()
                .map(StockMovement::getWarehouseId)
                .filter(Objects::nonNull)
                .filter(id -> !warehouseById.containsKey(id))
                .distinct()
                .toList();
        if (!missingWarehouseIds.isEmpty()) {
            warehouseRepository.findAllByIdInAndIsDeletedFalse(missingWarehouseIds)
                    .forEach(warehouse -> warehouseById.put(warehouse.getId(), warehouse));
        }

        List<StockMovement> scopedMovements = movements.stream()
                .filter(movement -> canAccessWarehouse(warehouseById.get(movement.getWarehouseId())))
                .limit(10)
                .toList();
        if (scopedMovements.isEmpty()) {
            return List.of();
        }

        Map<UUID, WorkOrder> workOrderById = workOrdersById(scopedMovements);
        Map<UUID, String> departmentNameById = recentMovementDepartmentNames(scopedMovements, warehouseById, workOrderById);

        return scopedMovements.stream()
                .map(movement -> {
                    Warehouse warehouse = warehouseById.get(movement.getWarehouseId());
                    WorkOrder workOrder = movement.getWorkOrderId() == null ? null : workOrderById.get(movement.getWorkOrderId());
                    UUID departmentId = recentMovementDepartmentId(movement, workOrder, warehouse);
                    return toRecentMovementDto(
                            part,
                            movement,
                            warehouse,
                            workOrder,
                            departmentId,
                            departmentId == null ? null : departmentNameById.get(departmentId)
                    );
                })
                .toList();
    }

    private Map<UUID, WorkOrder> workOrdersById(List<StockMovement> movements) {
        List<UUID> ids = movements.stream()
                .map(StockMovement::getWorkOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return workOrderRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(WorkOrder::getId, Function.identity()));
    }

    private Map<UUID, String> recentMovementDepartmentNames(
            List<StockMovement> movements,
            Map<UUID, Warehouse> warehouseById,
            Map<UUID, WorkOrder> workOrderById
    ) {
        List<UUID> ids = movements.stream()
                .map(movement -> {
                    Warehouse warehouse = warehouseById.get(movement.getWarehouseId());
                    WorkOrder workOrder = movement.getWorkOrderId() == null ? null : workOrderById.get(movement.getWorkOrderId());
                    return recentMovementDepartmentId(movement, workOrder, warehouse);
                })
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return departmentRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
    }

    private UUID recentMovementDepartmentId(StockMovement movement, WorkOrder workOrder, Warehouse warehouse) {
        if (movement.getDepartmentId() != null) {
            return movement.getDepartmentId();
        }
        if (workOrder != null && workOrder.getDepartmentId() != null) {
            return workOrder.getDepartmentId();
        }
        return warehouse == null ? null : warehouse.getDepartmentId();
    }

    private SparePartRecentMovementDto toRecentMovementDto(
            SparePart part,
            StockMovement movement,
            Warehouse warehouse,
            WorkOrder workOrder,
            UUID departmentId,
            String departmentName
    ) {
        BigDecimal unitPrice = movement.getUnitPrice() != null
                ? movement.getUnitPrice()
                : movement.getUnitCost() == null ? null : BigDecimal.valueOf(movement.getUnitCost());
        BigDecimal totalAmount = movement.getTotalAmount() != null
                ? movement.getTotalAmount()
                : unitPrice == null ? null : unitPrice.multiply(BigDecimal.valueOf(movement.getQuantity()));
        return new SparePartRecentMovementDto(
                movement.getId(),
                movement.getType(),
                movement.getQuantity(),
                firstNonBlank(movement.getUnit(), part.getUnit()),
                unitPrice,
                totalAmount,
                movement.getMovementDate() != null
                        ? movement.getMovementDate()
                        : movement.getOccurredAt() == null ? null : movement.getOccurredAt().atZone(ZoneId.systemDefault()).toLocalDate(),
                warehouse == null ? null : warehouse.getName(),
                movement.getDocumentNumber(),
                movement.getWorkOrderId(),
                workOrder == null ? null : workOrder.getNumber(),
                workOrder == null ? null : workOrder.getTitle(),
                departmentId,
                departmentName
        );
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private InventoryTransactionSummary transactionSummaryFor(UUID sparePartId, double totalQuantity) {
        List<InventoryTransaction> transactions = inventoryTransactionRepository
                .findAllBySparePartIdAndTypeInOrderByTransactionDateDescCreatedAtDesc(
                        sparePartId,
                        List.of(InventoryTransactionType.RECEIPT, InventoryTransactionType.ISSUE)
                );
        if (transactions == null || transactions.isEmpty()) {
            return new InventoryTransactionSummary(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(totalQuantity),
                    null,
                    null,
                    null,
                    null
            );
        }

        BigDecimal totalReceived = BigDecimal.ZERO;
        BigDecimal totalIssued = BigDecimal.ZERO;
        LocalDate lastReceiptDate = null;
        LocalDate lastIssueDate = null;
        String lastReceiptDocument = null;
        String lastIssueDocument = null;

        for (InventoryTransaction transaction : transactions) {
            BigDecimal quantity = transaction.getQuantity() == null ? BigDecimal.ZERO : transaction.getQuantity();
            if (transaction.getType() == InventoryTransactionType.RECEIPT) {
                totalReceived = totalReceived.add(quantity);
                if (lastReceiptDate == null) {
                    lastReceiptDate = transaction.getTransactionDate();
                    lastReceiptDocument = transaction.getDocumentNumber();
                }
            } else if (transaction.getType() == InventoryTransactionType.ISSUE) {
                totalIssued = totalIssued.add(quantity);
                if (lastIssueDate == null) {
                    lastIssueDate = transaction.getTransactionDate();
                    lastIssueDocument = transaction.getDocumentNumber();
                }
            }
        }

        return new InventoryTransactionSummary(
                totalReceived,
                totalIssued,
                BigDecimal.valueOf(totalQuantity),
                lastReceiptDate,
                lastIssueDate,
                lastReceiptDocument,
                lastIssueDocument
        );
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

    private record ScopedStockContext(
            List<WarehouseStock> stocks,
            Map<UUID, Warehouse> warehouseById,
            Map<LegacyStockProjectionService.StockKey, WmsStockSnapshot> snapshots
    ) {
    }

    private WmsStockSnapshot snapshot(
            WarehouseStock stock,
            Map<LegacyStockProjectionService.StockKey, WmsStockSnapshot> snapshots) {
        return legacyStockProjectionService.snapshot(
                snapshots, stock.getWarehouseId(), stock.getSparePartId());
    }

    private record InventoryTransactionSummary(
            BigDecimal totalReceivedQuantity,
            BigDecimal totalIssuedQuantity,
            BigDecimal currentQuantity,
            LocalDate lastReceiptDate,
            LocalDate lastIssueDate,
            String lastReceiptDocument,
            String lastIssueDocument
    ) {
    }
}
