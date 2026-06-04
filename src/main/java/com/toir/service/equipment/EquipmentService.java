package com.toir.service.equipment;

import com.toir.dto.equipment.*;
import com.toir.dto.file.PresignedUrlResponse;
import com.toir.dto.file.UploadFileResponse;
import com.toir.entity.FileAsset;
import com.toir.entity.UploadedFile;
import com.toir.repository.equipment.EquipmentStatsProjection;
import com.toir.dto.warehouse.WarehouseEquipmentAssignRequest;
import com.toir.entity.Department;
import com.toir.entity.DowntimeEvent;
import com.toir.entity.Location;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentLocationHistory;
import com.toir.entity.equipment.EquipmentPassport;
import com.toir.entity.equipment.EquipmentType;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.enums.AuditAction;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentOutsideReason;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.FileCategory;
import com.toir.enums.PlacementType;
import com.toir.enums.PlacementTargetType;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkType;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseEquipmentItemRepository;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.FileAssetRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.LocationRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentLocationHistoryRepository;
import com.toir.repository.equipment.EquipmentPassportRepository;
import com.toir.repository.equipment.EquipmentDocumentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.repository.UploadedFileRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.security.ScopeAccessService;
import com.toir.service.file_management.FileService;
import com.toir.service.WarehouseEquipmentItemService;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EquipmentService {

    private final EquipmentRepository repository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;
    private final EquipmentPassportRepository passportRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;
    private final EquipmentLocationHistoryRepository equipmentLocationHistoryRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final DefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final DowntimeEventRepository downtimeEventRepository;
    private final EquipmentAttributeService equipmentAttributeService;
    private final EquipmentManualAttributeService equipmentManualAttributeService;
    private final WarehouseEquipmentItemService warehouseEquipmentItemService;
    private final EquipmentStatusLifecycleService equipmentStatusLifecycleService;
    private final EquipmentLocationValidator equipmentLocationValidator;
    private final AuditBuilderService auditBuilderService;
    private final FileService fileService;
    private final FileAssetRepository fileAssetRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final EquipmentDocumentRepository equipmentDocumentRepository;
    private final UserRepository userRepository;
    private final ScopeAccessService scopeAccessService;
    private static final Set<WorkOrderStatus> FINAL_WORK_ORDER_STATUSES =
            EnumSet.of(WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED, WorkOrderStatus.CANCELLED);


    @Transactional(readOnly = true)
    public Page<EquipmentDto> search(UUID departmentId,
                                     UUID equipmentTypeId,
                                     EquipmentStatus status,
                                     EquipmentCategory category,
                                     UUID warehouseId,
                                     boolean availableForReplacement,
                                     String search,
                                     int page,
                                     int pageSize) {
        if (!availableForReplacement) {
            String searchPattern = null;
            if (search != null && !search.isBlank()) {
                searchPattern = "%" + search.trim().toLowerCase() + "%";
            }
            return enrich(repository.search(
                    departmentId,
                    equipmentTypeId,
                    status,
                    category,
                    searchPattern,
                    PaginationUtils.pageRequest(page, pageSize)
            ));
        }
        String searchPattern = null;
        if (search != null && !search.isBlank()) {
            searchPattern = "%" + search.trim().toLowerCase() + "%";
        }
        if (warehouseId == null) {
            throw RestException.badRequest("warehouseId is required when availableForReplacement is true");
        }
        warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                .orElseThrow(() -> RestException.notFound("Warehouse not found: " + warehouseId));
        Page<Equipment> items = repository.searchAvailableForReplacement(
                warehouseId,
                WarehouseEquipmentStatus.AVAILABLE,
                WorkType.REPLACEMENT,
                FINAL_WORK_ORDER_STATUSES,
                departmentId,
                equipmentTypeId,
                status,
                category,
                searchPattern,
                PaginationUtils.pageRequest(page, pageSize)
        );
        return enrich(items);
    }

    @Transactional(readOnly = true)
    public Page<EquipmentDto> search(UUID scopeDepartmentId,
                                     UUID departmentId,
                                     UUID equipmentTypeId,
                                     EquipmentStatus status,
                                     EquipmentCategory category,
                                     UUID warehouseId,
                                     EquipmentLocationType locationType,
                                     EquipmentOutsideReason outsideReason,
                                     boolean overdueOnly,
                                     boolean availableForReplacement,
                                     String search,
                                     int page,
                                     int pageSize) {
        String searchPattern = null;
        if (search != null && !search.isBlank()) {
            searchPattern = "%" + search.trim().toLowerCase() + "%";
        }
        Page<Equipment> items;
        if (availableForReplacement) {
            if (warehouseId == null) {
                throw RestException.badRequest("warehouseId is required when availableForReplacement is true");
            }
            warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                    .orElseThrow(() -> RestException.notFound("Warehouse not found: " + warehouseId));
            items = repository.searchAvailableForReplacement(
                    warehouseId,
                    WarehouseEquipmentStatus.AVAILABLE,
                    EquipmentLocationType.WAREHOUSE,
                    WorkType.REPLACEMENT,
                    FINAL_WORK_ORDER_STATUSES,
                    scopeDepartmentId,
                    equipmentTypeId,
                    status,
                    category,
                    searchPattern,
                    PaginationUtils.pageRequest(page, pageSize)
            );
        } else {
            items = repository.search(
                    scopeDepartmentId,
                    departmentId,
                    equipmentTypeId,
                    status,
                    category,
                    locationType,
                    warehouseId,
                    outsideReason,
                    overdueOnly,
                    LocalDate.now(),
                    searchPattern,
                    PaginationUtils.pageRequest(page, pageSize)
            );
        }
        return enrich(items);
    }

    @Transactional(readOnly = true)
    public EquipmentDto findById(UUID id) {
        return enrich(List.of(getOrThrow(id))).getFirst();
    }

    @Transactional(readOnly = true)
    public EquipmentDetailDto findDetailById(UUID id) {
        EquipmentDto equipment = findById(id);

        List<RepairRequest> repairRequestEntities = repairRequestRepository.search(null, null, id);
        if (repairRequestEntities == null) {
            repairRequestEntities = List.of();
        }
        List<EquipmentDetailDto.RepairRequestShortDto> repairRequests = repairRequestEntities.stream()
                .map(r -> new EquipmentDetailDto.RepairRequestShortDto(
                        r.getId(),
                        r.getNumber(),
                        r.getTitle(),
                        r.getStatus(),
                        r.getDetectedAt(),
                        r.getDescription()
                ))
                .toList();

        List<Defect> defectEntities = defectRepository.findAllByEquipmentIdAndIsDeletedFalse(id);
        if (defectEntities == null) {
            defectEntities = List.of();
        }
        List<EquipmentDetailDto.DefectShortDto> defects = defectEntities.stream()
                .map(d -> new EquipmentDetailDto.DefectShortDto(
                        d.getId(),
                        d.getCode(),
                        d.getTitle(),
                        d.getStatus(),
                        d.getDetectedAt(),
                        d.getDescription()
                ))
                .toList();

        List<WorkOrder> workOrderEntities = workOrderRepository.search(null, null, id);
        if (workOrderEntities == null) {
            workOrderEntities = List.of();
        }
        List<EquipmentDetailDto.WorkOrderShortDto> workOrders = workOrderEntities.stream()
                .map(w -> new EquipmentDetailDto.WorkOrderShortDto(
                        w.getId(),
                        w.getNumber(),
                        w.getTitle(),
                        w.getStatus(),
                        w.getStartedAt(),
                        w.getCompletedAt(),
                        w.getSummary()
                ))
                .toList();

        List<DowntimeEvent> downtimeEventEntities = downtimeEventRepository
                .findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(id);
        if (downtimeEventEntities == null) {
            downtimeEventEntities = List.of();
        }
        List<EquipmentDetailDto.DowntimeEventShortDto> downtimeEvents = downtimeEventEntities.stream()
                .map(d -> new EquipmentDetailDto.DowntimeEventShortDto(
                        d.getId(),
                        d.getStartAt(),
                        d.getEndAt(),
                        d.getDurationMinutes(),
                        d.getType(),
                        d.getDescription()
                ))
                .toList();

        return new EquipmentDetailDto(
                equipment,
                repairRequests,
                defects,
                workOrders,
                downtimeEvents,
                equipmentAttributeService == null ? List.of() : equipmentAttributeService.findValues(id),
                equipmentManualAttributeService == null ? List.of() : equipmentManualAttributeService.list(id),
                equipmentDocuments(id)
        );
    }

    @Transactional
    public List<EquipmentDocumentDto> attachDocuments(
            UUID equipmentId,
            List<MultipartFile> files,
            List<String> documentNames,
            String documentType,
            AuthenticatedUser user
    ) {
        UUID currentUserId = currentUserId(user);
        Equipment equipment = getOrThrow(equipmentId);
        if (files == null || files.isEmpty()) {
            throw RestException.badRequest("At least one equipment document file is required");
        }
        List<String> normalizedDocumentNames = normalizeDocumentNames(files, documentNames);
        String normalizedDocumentType = normalizeDocumentType(documentType);

        List<UUID> uploadedFileIds = new ArrayList<>();
        try {
            List<com.toir.entity.equipment.EquipmentDocument> documents = new ArrayList<>(files.size());
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                UploadFileResponse uploaded = fileService.upload(file, FileCategory.EQUIPMENT_DOCUMENT, currentUserId);
                uploadedFileIds.add(uploaded.id());
                UploadedFile uploadedFile = uploadedFileRepository.findByIdAndDeletedFalse(uploaded.id())
                        .orElseThrow(() -> RestException.notFound("Uploaded file not found: " + uploaded.id()));
                documents.add(com.toir.entity.equipment.EquipmentDocument.builder()
                        .equipment(equipment)
                        .file(uploadedFile)
                        .documentType(normalizedDocumentType)
                        .documentName(normalizedDocumentNames.get(i))
                        .build());
            }

            return equipmentDocumentRepository.saveAllAndFlush(documents).stream()
                    .map(document -> EquipmentDocumentDto.from(equipmentId, document))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (RuntimeException e) {
            cleanupUploadedFiles(uploadedFileIds, currentUserId);
            if (e instanceof RestException restException) {
                throw restException;
            }
            throw RestException.conflict("Could not attach equipment documents");
        }
    }

    @Transactional(readOnly = true)
    public List<EquipmentDocumentDto> getDocuments(UUID equipmentId, AuthenticatedUser user) {
        UUID currentUserId = currentUserId(user);
        getOrThrow(equipmentId);
        return equipmentDocumentRepository.findAllByEquipmentId(equipmentId)
                .stream()
                .map(document -> toDocumentDtoWithMetadata(equipmentId, document, currentUserId))
                .toList();
    }

    @Transactional(readOnly = true)
    public EquipmentDocumentDto getDocument(UUID equipmentId, UUID documentId, AuthenticatedUser user) {
        UUID currentUserId = currentUserId(user);
        getOrThrow(equipmentId);
        com.toir.entity.equipment.EquipmentDocument document = findEquipmentDocument(equipmentId, documentId);
        return toDocumentDtoWithMetadata(equipmentId, document, currentUserId);
    }

    @Transactional(readOnly = true)
    public PresignedUrlResponse getDocumentPresignedUrl(UUID equipmentId, UUID documentId, AuthenticatedUser user) {
        UUID currentUserId = currentUserId(user);
        getOrThrow(equipmentId);
        com.toir.entity.equipment.EquipmentDocument document = findEquipmentDocument(equipmentId, documentId);
        return fileService.getPresignedUrl(document.getFile().getId(), currentUserId);
    }

    @Transactional(readOnly = true)
    public Resource downloadDocument(UUID equipmentId, UUID documentId, AuthenticatedUser user) {
        UUID currentUserId = currentUserId(user);
        getOrThrow(equipmentId);
        com.toir.entity.equipment.EquipmentDocument document = findEquipmentDocument(equipmentId, documentId);
        return fileService.download(document.getFile().getId(), currentUserId);
    }

    @Transactional
    public void deleteDocument(UUID equipmentId, UUID documentId, AuthenticatedUser user) {
        UUID currentUserId = currentUserId(user);
        getOrThrow(equipmentId);
        com.toir.entity.equipment.EquipmentDocument document = findEquipmentDocument(equipmentId, documentId);
        equipmentDocumentRepository.delete(document);
        fileService.delete(document.getFile().getId(), currentUserId);
    }

    @Transactional(readOnly = true)
    public Page<EquipmentDto> findChildren(UUID parentId, int page, int pageSize) {
        getOrThrow(parentId);
        return enrich(repository.findAllByParentIdAndIsDeletedFalse(parentId, PaginationUtils.pageRequest(page, pageSize)));
    }

    @Transactional
    public EquipmentDto create(EquipmentCreateRequest request) {
        EquipmentLocationRequest location = equipmentLocationValidator.resolveCreateLocation(
                request.departmentId(),
                request.warehouseId(),
                request.locationId(),
                request.location()
        );
        validateClientProvidedCode(request.code());
        validateAverageOperatingLifeForCreate(request.averageOperatingLifeHours());
        assertCanAccessLocationBeforePersistence(location);
        validateLocationReferences(location);
        if (repository.existsByInventoryNumberAndIsDeletedFalse(request.inventoryNumber())) {
            throw RestException.conflict("Inventory number already exists: " + request.inventoryNumber());
        }
        validateParent(null, request.parentId());
        validateWarrantyDateRange(request.warrantyStartDate(), request.warrantyEndDate());
        validateWarrantyAttachment(request.hasWarranty(), request.warrantyAttachmentId());
        Equipment entity = new Equipment();
        entity.setCode(nextCode());
        apply(entity, request);
        applyLocation(entity, location);
        Equipment saved = repository.save(entity);
        if (equipmentAttributeService != null) {
            equipmentAttributeService.upsertValues(
                    saved,
                    request.attributes() == null ? List.of() : request.attributes()
            );
        }
        if (equipmentManualAttributeService != null && request.manualAttributes() != null && !request.manualAttributes().isEmpty()) {
            equipmentManualAttributeService.replaceAll(
                    saved.getId(),
                    new com.toir.dto.equipmentmanualattribute.BulkEquipmentManualAttributeRequest(request.manualAttributes())
            );
        }
        if (location.locationType() == EquipmentLocationType.WAREHOUSE) {
            warehouseEquipmentItemService.assign(
                    location.warehouseId(),
                    new WarehouseEquipmentAssignRequest(saved.getId(), resolveWarehousePlacementStatus(location.warehouseStatus()))
            );
        }
        writeLocationHistory(saved, null, snapshotLocation(saved), null);

        auditBuilderService.log(
                "equipment",
                saved.getId().toString(),
                com.toir.enums.AuditAction.CREATE,
                com.toir.enums.AuditModule.EQUIPMENT,
                "Equipment created with location: %s".formatted(saved.getCurrentLocationType()),
                null,
                saved
        );
        return enrich(List.of(saved)).getFirst();
    }

    @Transactional
    public EquipmentDto update(UUID id, EquipmentUpdateRequest request) {
        Equipment entity = getOrThrow(id);
        LocationSnapshot from = snapshotLocation(entity);
        EquipmentLocationRequest location = resolveUpdateLocation(request);
        validateClientProvidedCode(request.code());
        validateNoDirectStatusChange(entity, request.status());
        validateAverageOperatingLifeForUpdate(request.averageOperatingLifeHours());
        if (location != null) {
            assertCanAccessLocationBeforePersistence(location);
            validateLocationReferences(location);
        } else {
            validateDepartmentExists(request.departmentId());
        }

        boolean equipmentTypeChanged = isEquipmentTypeChanged(entity.getEquipmentTypeId(), request.equipmentTypeId());
        validateAttributesForTypeChange(equipmentTypeChanged, request.attributes());
        validateWarrantyDateRangeForUpdate(entity, request);
        validateWarrantyAttachmentForUpdate(request);

        applyForUpdate(entity, request);
        if (location != null) {
            syncWarehouseInventoryForUpdate(entity, location);
            applyLocation(entity, location);
        }
        validateParent(entity.getId(), entity.getParentId());

        Equipment saved = repository.save(entity);
        if (equipmentAttributeService != null && request.attributes() != null) {
            equipmentAttributeService.upsertValues(saved, request.attributes());
        }
        if (equipmentManualAttributeService != null && request.manualAttributes() != null && !request.manualAttributes().isEmpty()) {
            equipmentManualAttributeService.replaceAll(
                    saved.getId(),
                    new com.toir.dto.equipmentmanualattribute.BulkEquipmentManualAttributeRequest(request.manualAttributes())
            );
        }
        if (location != null) {
            LocationSnapshot to = snapshotLocation(saved);
            writeLocationHistory(saved, from, to, null);
            auditLocationChange(saved, from, to, to);
        }

        auditBuilderService.log(
                "equipment",
                saved.getId().toString(),
                AuditAction.UPDATE,
                com.toir.enums.AuditModule.EQUIPMENT,
                "Оборудование обновлено: код=%s, наименование=%s".formatted(saved.getCode(), saved.getName()),
                entity,
                saved);
        return enrich(List.of(saved)).getFirst();
    }

    @Transactional
    public EquipmentDto updatePlacement(UUID id, EquipmentPlacementRequest request) {
        Equipment equipment = getOrThrow(id);
        EquipmentLocationRequest target = resolvePlacementLocation(request);
        validateLocationReferences(target);
        assertCanAccessLocationBeforePersistence(target);
        LocationSnapshot from = snapshotLocation(equipment);

        if (target.locationType() == EquipmentLocationType.WAREHOUSE) {
            warehouseEquipmentItemService.transferEquipmentToWarehouse(
                    equipment.getId(),
                    target.warehouseId(),
                    resolveWarehousePlacementStatus(target.warehouseStatus())
            );
        } else if (target.locationType() == EquipmentLocationType.DEPARTMENT) {
            syncDepartmentPlacement(equipment, target.departmentId());
        } else {
            closeActiveWarehouseItemIfPresent(equipment);
        }

        applyLocation(equipment, target);
        Equipment saved = repository.save(equipment);
        LocationSnapshot to = snapshotLocation(saved);
        writeLocationHistory(saved, from, to, request.note());
        auditLocationChange(saved, from, to, to);
        return enrich(List.of(saved)).getFirst();
    }

    @Transactional
    public void delete(UUID id) {
        Equipment entity = getOrThrow(id);
        if (!repository.findAllByParentIdAndIsDeletedFalse(id).isEmpty()) {
            throw RestException.conflict("Equipment has child equipment");
        }

        entity.setDeleted(true);
        Equipment saved = repository.save(entity);

        auditBuilderService.log(
                "equipment",
                saved.getId().toString(),
                AuditAction.DELETE,
                com.toir.enums.AuditModule.EQUIPMENT,
                "Оборудование удалено: код=%s, наименование=%s".formatted(saved.getCode(), saved.getName()),
                saved,
                null);
    }

    Equipment getOrThrow(UUID id) {
        Optional<Equipment> equipment = repository.findByIdAndIsDeletedFalse(id);
        if (equipment == null) {
            throw RestException.notFound("Equipment not found: " + id);
        }
        return equipment
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + id));
    }

    public Page<EquipmentDto> enrich(Page<Equipment> items) {
        if (items.isEmpty()) return items.map(EquipmentDto::from);

        Map<UUID, EquipmentDto> enrichedById = enrich(items.getContent()).stream()
                .collect(Collectors.toMap(EquipmentDto::id, Function.identity(), (a, b) -> a));
        return items.map(e -> enrichedById.get(e.getId()));
    }

    private List<EquipmentDto> enrich(List<Equipment> items) {
        if (items.isEmpty()) return Collections.emptyList();

        Set<UUID> deptIds = collectIds(items, Equipment::getDepartmentId);
        Set<UUID> locIds = collectIds(items, Equipment::getLocationId);
        Set<UUID> typeIds = collectIds(items, Equipment::getEquipmentTypeId);
        Set<UUID> parentIds = collectIds(items, Equipment::getParentId);
        Set<UUID> currentWarehouseIds = collectIds(items, Equipment::getCurrentWarehouseId);
        Set<UUID> warrantyAttachmentIds = collectIds(items, Equipment::getWarrantyAttachmentId);
        Set<UUID> equipmentIds = items.stream().map(Equipment::getId).collect(Collectors.toSet());

        Map<UUID, Department> deptMap = byId(departmentRepository.findAllByIdInAndIsDeletedFalse(deptIds), Department::getId);
        Map<UUID, Location> locMap = byId(locationRepository.findAllByIdInAndIsDeletedFalse(locIds), Location::getId);
        Set<UUID> unresolvedLocIds = locIds.stream()
                .filter(id -> !locMap.containsKey(id))
                .collect(Collectors.toSet());
        Map<UUID, WarehouseEquipmentItem> activeWarehouseItemMap = byId(
                warehouseEquipmentItemRepository.findActiveByEquipmentIds(equipmentIds),
                WarehouseEquipmentItem::getEquipmentId
        );
        Set<UUID> warehouseIdsToLoad = new HashSet<>(unresolvedLocIds);
        warehouseIdsToLoad.addAll(currentWarehouseIds);
        warehouseIdsToLoad.addAll(
                activeWarehouseItemMap.values().stream()
                        .map(WarehouseEquipmentItem::getWarehouseId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
        );
        Map<UUID, Warehouse> warehouseMap = warehouseIdsToLoad.isEmpty()
                ? Collections.emptyMap()
                : byId(warehouseRepository.findAllByIdInAndIsDeletedFalse(warehouseIdsToLoad), Warehouse::getId);
        Map<UUID, EquipmentType> typeMap = byId(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(typeIds), EquipmentType::getId);
        Map<UUID, Equipment> parentMap = byId(repository.findAllByIdInAndIsDeletedFalse(parentIds), Equipment::getId);
        Map<UUID, EquipmentPassport> passportMap = passportRepository
                .findAllByEquipmentIdInAndIsDeletedFalse(equipmentIds).stream()
                .collect(Collectors.toMap(EquipmentPassport::getEquipmentId, Function.identity(), (a, b) -> a));
        Map<UUID, FileAsset> warrantyAttachmentMap = warrantyAttachmentIds.isEmpty()
                ? Collections.emptyMap()
                : byId(fileAssetRepository.findAllByIdInAndIsDeletedFalse(warrantyAttachmentIds), FileAsset::getId);

        return items.stream()
                .map(e -> {
                    EquipmentDto.Ref departmentRef = deptRef(deptMap.get(e.getDepartmentId()));
                    EquipmentDto.Ref locationRef = locRef(e.getLocationId(), locMap, warehouseMap);
                    WarehouseEquipmentItem activeWarehouseItem = activeWarehouseItemMap.get(e.getId());
                    EquipmentDto.Ref warehouseRef = warehouseRef(
                            resolvePlacementWarehouse(e, activeWarehouseItem, warehouseMap)
                    );
                    EquipmentDto.PlacementRef placement = placementRef(
                            e,
                            departmentRef,
                            warehouseRef,
                            activeWarehouseItem,
                            locationRef
                    );
                    return EquipmentDto.from(
                            e,
                            departmentRef,
                            locationRef,
                            typeRef(typeMap.get(e.getEquipmentTypeId())),
                            parentRef(parentMap.get(e.getParentId())),
                            passportRef(passportMap.get(e.getId())),
                            placement,
                            warrantyAttachmentMap.get(e.getWarrantyAttachmentId())
                    );
                })
                .toList();
    }

    private static Set<UUID> collectIds(List<Equipment> items, Function<Equipment, UUID> getter) {
        Set<UUID> ids = new HashSet<>();
        for (Equipment e : items) {
            UUID id = getter.apply(e);
            if (id != null) ids.add(id);
        }
        return ids;
    }

    private static <T> Map<UUID, T> byId(Collection<T> list, Function<T, UUID> getter) {
        return list.stream().collect(Collectors.toMap(getter, Function.identity(), (a, b) -> a));
    }

    private static EquipmentDto.Ref deptRef(Department d) {
        return d == null ? null : new EquipmentDto.Ref(d.getId(), d.getCode(), d.getName());
    }

    private static EquipmentDto.Ref locRef(Location l) {
        return l == null ? null : new EquipmentDto.Ref(l.getId(), l.getCode(), l.getName());
    }

    private static EquipmentDto.Ref warehouseRef(Warehouse warehouse) {
        return warehouse == null ? null : new EquipmentDto.Ref(warehouse.getId(), warehouse.getCode(), warehouse.getName());
    }

    private static EquipmentDto.Ref locRef(UUID locationId,
                                           Map<UUID, Location> locationMap,
                                           Map<UUID, Warehouse> warehouseMap) {
        if (locationId == null) {
            return null;
        }
        Location location = locationMap.get(locationId);
        if (location != null) {
            return locRef(location);
        }
        Warehouse warehouse = warehouseMap.get(locationId);
        return warehouse == null ? null : new EquipmentDto.Ref(warehouse.getId(), warehouse.getCode(), warehouse.getName());
    }

    private static EquipmentDto.Ref typeRef(EquipmentType t) {
        return t == null ? null : new EquipmentDto.Ref(t.getId(), t.getCode(), t.getName());
    }

    private static EquipmentDto.Ref parentRef(Equipment p) {
        return p == null ? null : new EquipmentDto.Ref(p.getId(), p.getCode(), p.getName());
    }

    private static EquipmentDto.PassportRef passportRef(EquipmentPassport p) {
        return p == null ? null : new EquipmentDto.PassportRef(
                p.getPassportNumber(), p.getPowerKw(), p.getVoltageV(), p.getPressureBar());
    }

    private static Warehouse resolvePlacementWarehouse(Equipment equipment,
                                                       WarehouseEquipmentItem activeWarehouseItem,
                                                       Map<UUID, Warehouse> warehouseMap) {
        if (equipment.getCurrentLocationType() == EquipmentLocationType.WAREHOUSE) {
            return warehouseMap.get(equipment.getCurrentWarehouseId());
        }
        return activeWarehouseItem == null ? null : warehouseMap.get(activeWarehouseItem.getWarehouseId());
    }

    private static EquipmentDto.PlacementRef placementRef(Equipment equipment,
                                                          EquipmentDto.Ref departmentRef,
                                                          EquipmentDto.Ref warehouseRef,
                                                          WarehouseEquipmentItem activeWarehouseItem,
                                                          EquipmentDto.Ref locationRef) {
        if (equipment.getCurrentLocationType() == EquipmentLocationType.DEPARTMENT) {
            return new EquipmentDto.PlacementRef(
                    PlacementType.DEPARTMENT,
                    departmentRef,
                    activeWarehouseItem == null ? null : warehouseRef,
                    activeWarehouseItem == null ? null : activeWarehouseItem.getStatus(),
                    locationRef,
                    equipment.getResponsibleDepartmentId(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false
            );
        }

        if (equipment.getCurrentLocationType() == EquipmentLocationType.WAREHOUSE) {
            return new EquipmentDto.PlacementRef(
                    PlacementType.WAREHOUSE,
                    null,
                    warehouseRef,
                    activeWarehouseItem == null ? null : activeWarehouseItem.getStatus(),
                    locationRef,
                    equipment.getResponsibleDepartmentId(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false
            );
        }

        if (equipment.getCurrentLocationType() == EquipmentLocationType.OUTSIDE_FACILITY) {
            LocalDate expectedReturnDate = equipment.getOutsideExpectedReturnDate();
            return new EquipmentDto.PlacementRef(
                    PlacementType.OUTSIDE_FACILITY,
                    null,
                    null,
                    null,
                    null,
                    equipment.getResponsibleDepartmentId(),
                    equipment.getOutsideReason(),
                    equipment.getOutsideTakenBy(),
                    equipment.getOutsideRecipientUserId(),
                    equipment.getOutsideStartedDate(),
                    expectedReturnDate,
                    equipment.getOutsideDestination(),
                    equipment.getOutsideReasonNote(),
                    expectedReturnDate != null && expectedReturnDate.isBefore(LocalDate.now())
            );
        }

        if (equipment.getDepartmentId() != null) {
            if (activeWarehouseItem == null) {
                return new EquipmentDto.PlacementRef(
                        PlacementType.DEPARTMENT,
                        departmentRef,
                        null,
                        null,
                        locationRef
                );
            }
            return new EquipmentDto.PlacementRef(
                    PlacementType.DEPARTMENT,
                    departmentRef,
                    warehouseRef,
                    activeWarehouseItem.getStatus(),
                    locationRef
            );
        }

        if (activeWarehouseItem != null) {
            EquipmentDto.Ref placementLocation = locationRef;
            if (warehouseRef != null && (
                    Objects.equals(equipment.getLocationId(), activeWarehouseItem.getWarehouseId())
                            || (locationRef != null && Objects.equals(locationRef.id(), activeWarehouseItem.getWarehouseId()))
            )) {
                placementLocation = warehouseRef;
            }
            return new EquipmentDto.PlacementRef(
                    PlacementType.WAREHOUSE,
                    null,
                    warehouseRef,
                    activeWarehouseItem.getStatus(),
                    placementLocation
            );
        }

        return new EquipmentDto.PlacementRef(
                PlacementType.UNKNOWN,
                null,
                null,
                null,
                locationRef
        );
    }

    private void apply(Equipment entity, EquipmentCreateRequest request) {
        entity.setName(request.name());
        entity.setInventoryNumber(request.inventoryNumber());
        entity.setTechnicalNumber(request.technicalNumber());
        entity.setSerialNumber(request.serialNumber());
        entity.setModel(request.model());
        entity.setEquipmentTypeId(request.equipmentTypeId());
        entity.setDepartmentId(request.departmentId());
        entity.setLocationId(request.locationId());
        entity.setParentId(request.parentId());
        entity.setCriticalityClassId(request.criticalityClassId());
        entity.setResponsibleId(request.responsibleId());
        entity.setManufacturer(request.manufacturer());
        if (request.status() != null) entity.setStatus(request.status());
        entity.setCategory(request.category() != null ? request.category() : EquipmentCategory.PRODUCTION_EQUIPMENT);
        entity.setCommissionedAt(request.commissionedAt());
        entity.setArrivalDate(request.arrivalDate());
        entity.setWarrantyUntil(request.warrantyUntil());
        entity.setHasWarranty(Boolean.TRUE.equals(request.hasWarranty()));
        entity.setWarrantyAttachmentId(Boolean.TRUE.equals(request.hasWarranty()) ? request.warrantyAttachmentId() : null);
        entity.setWarrantyStartDate(Boolean.TRUE.equals(request.hasWarranty()) ? request.warrantyStartDate() : null);
        entity.setWarrantyEndDate(Boolean.TRUE.equals(request.hasWarranty()) ? request.warrantyEndDate() : null);
        entity.setAverageOperatingLifeHours(request.averageOperatingLifeHours());
        entity.setOperationStartDate(request.operationStartDate());
        entity.setExpectedLifetimeMonths(request.expectedLifetimeMonths());
        entity.setExpectedLifetimeYears(request.expectedLifetimeYears());
        entity.setDescription(request.description());
    }

    private EquipmentLocationRequest resolveUpdateLocation(EquipmentUpdateRequest request) {
        if (request.location() != null) {
            return equipmentLocationValidator.validateAndNormalize(request.location());
        }
        if (request.departmentId() != null) {
            return equipmentLocationValidator.validateAndNormalize(new EquipmentLocationRequest(
                    EquipmentLocationType.DEPARTMENT,
                    request.departmentId(),
                    null,
                    request.locationId(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
        }
        return null;
    }

    private EquipmentLocationRequest resolvePlacementLocation(EquipmentPlacementRequest request) {
        if (request == null) {
            throw RestException.badRequest("Placement request is required");
        }
        if (request.targetLocation() != null) {
            return equipmentLocationValidator.validateAndNormalize(request.targetLocation());
        }
        if (request.targetType() == PlacementTargetType.OUTSIDE_FACILITY) {
            throw RestException.badRequest("targetLocation is required when targetType is OUTSIDE_FACILITY");
        }
        return equipmentLocationValidator.resolvePlacementLocation(
                request.targetType(),
                request.departmentId(),
                request.warehouseId(),
                request.warehouseStatus(),
                null
        );
    }

    private void syncDepartmentPlacement(Equipment equipment, UUID departmentId) {
        Optional<WarehouseEquipmentItem> activeItem = warehouseEquipmentItemRepository.findActiveByEquipmentId(equipment.getId());
        if (activeItem == null || activeItem.isEmpty()) {
            if (equipment.getCurrentLocationType() == EquipmentLocationType.WAREHOUSE || equipment.getCurrentWarehouseId() != null) {
                throw RestException.badRequest("Active warehouse assignment is required for DEPARTMENT target");
            }
            return;
        }
        WarehouseEquipmentItem activeWarehouseItem = activeItem.get();
        if (activeWarehouseItem.getStatus() == WarehouseEquipmentStatus.OUT_OF_SERVICE) {
            throw RestException.badRequest("OUT_OF_SERVICE equipment cannot be installed directly");
        }
        warehouseEquipmentItemService.updateStatus(
                activeWarehouseItem.getWarehouseId(),
                equipment.getId(),
                WarehouseEquipmentStatus.INSTALLED,
                departmentId
        );
    }

    private void markActiveWarehouseItemInstalledIfPresent(Equipment equipment, UUID departmentId) {
        Optional<WarehouseEquipmentItem> activeItem = warehouseEquipmentItemRepository.findActiveByEquipmentId(equipment.getId());
        if (activeItem == null) {
            return;
        }
        activeItem.ifPresent(item -> {
            if (item.getStatus() == WarehouseEquipmentStatus.OUT_OF_SERVICE) {
                throw RestException.badRequest("OUT_OF_SERVICE equipment cannot be installed directly");
            }
            warehouseEquipmentItemService.updateStatus(
                    item.getWarehouseId(),
                    equipment.getId(),
                    WarehouseEquipmentStatus.INSTALLED,
                    departmentId
            );
        });
    }

    private void closeActiveWarehouseItemIfPresent(Equipment equipment) {
        Optional<WarehouseEquipmentItem> activeItem = warehouseEquipmentItemRepository.findActiveByEquipmentId(equipment.getId());
        if (activeItem != null) {
            activeItem.ifPresent(item -> warehouseEquipmentItemService.remove(item.getWarehouseId(), equipment.getId()));
        }
    }

    private void syncWarehouseInventoryForUpdate(Equipment equipment, EquipmentLocationRequest location) {
        if (location.locationType() == EquipmentLocationType.WAREHOUSE) {
            warehouseEquipmentItemService.transferEquipmentToWarehouse(
                    equipment.getId(),
                    location.warehouseId(),
                    resolveWarehousePlacementStatus(location.warehouseStatus())
            );
        } else if (location.locationType() == EquipmentLocationType.DEPARTMENT) {
            markActiveWarehouseItemInstalledIfPresent(equipment, location.departmentId());
        } else {
            closeActiveWarehouseItemIfPresent(equipment);
        }
    }

    private void applyLocation(Equipment equipment, EquipmentLocationRequest location) {
        switch (location.locationType()) {
            case DEPARTMENT -> applyDepartmentLocation(equipment, location);
            case WAREHOUSE -> applyWarehouseLocation(equipment, location);
            case OUTSIDE_FACILITY -> applyOutsideLocation(equipment, location);
        }
    }

    private void applyDepartmentLocation(Equipment equipment, EquipmentLocationRequest location) {
        equipment.setCurrentLocationType(EquipmentLocationType.DEPARTMENT);
        equipment.setDepartmentId(location.departmentId());
        equipment.setLocationId(location.locationId());
        equipment.setCurrentWarehouseId(null);
        equipment.setResponsibleDepartmentId(
                location.responsibleDepartmentId() != null ? location.responsibleDepartmentId() : location.departmentId()
        );
        clearOutsideFields(equipment);
    }

    private void applyWarehouseLocation(Equipment equipment, EquipmentLocationRequest location) {
        UUID previousDepartmentId = equipment.getDepartmentId();
        Warehouse warehouse = warehouseRepository.findByIdAndIsDeletedFalse(location.warehouseId())
                .orElseThrow(() -> RestException.notFound("Warehouse not found: " + location.warehouseId()));
        equipment.setCurrentLocationType(EquipmentLocationType.WAREHOUSE);
        equipment.setDepartmentId(null);
        equipment.setCurrentWarehouseId(location.warehouseId());
        equipment.setResponsibleDepartmentId(resolveResponsibleDepartmentId(
                location.responsibleDepartmentId(),
                equipment.getResponsibleDepartmentId(),
                previousDepartmentId,
                warehouse.getDepartmentId()
        ));
        equipment.setLocationId(warehouse.getLocationId());
        clearOutsideFields(equipment);
    }

    private void applyOutsideLocation(Equipment equipment, EquipmentLocationRequest location) {
        UUID previousDepartmentId = equipment.getDepartmentId();
        equipment.setCurrentLocationType(EquipmentLocationType.OUTSIDE_FACILITY);
        equipment.setDepartmentId(null);
        equipment.setCurrentWarehouseId(null);
        equipment.setLocationId(null);
        equipment.setResponsibleDepartmentId(resolveResponsibleDepartmentId(
                location.responsibleDepartmentId(),
                equipment.getResponsibleDepartmentId(),
                previousDepartmentId,
                null
        ));
        equipment.setOutsideReason(location.outsideReason());
        equipment.setOutsideTakenBy(location.outsideTakenBy());
        equipment.setOutsideRecipientUserId(location.outsideRecipientUserId());
        equipment.setOutsideStartedDate(location.outsideStartedDate() != null ? location.outsideStartedDate() : LocalDate.now());
        equipment.setOutsideExpectedReturnDate(location.outsideExpectedReturnDate());
        equipment.setOutsideDestination(location.outsideDestination());
        equipment.setOutsideReasonNote(location.outsideReasonNote());
    }

    private void clearOutsideFields(Equipment equipment) {
        equipment.setOutsideReason(null);
        equipment.setOutsideTakenBy(null);
        equipment.setOutsideRecipientUserId(null);
        equipment.setOutsideStartedDate(null);
        equipment.setOutsideExpectedReturnDate(null);
        equipment.setOutsideDestination(null);
        equipment.setOutsideReasonNote(null);
    }

    private UUID resolveResponsibleDepartmentId(UUID requested,
                                                UUID existingResponsible,
                                                UUID previousDepartment,
                                                UUID warehouseDepartment) {
        UUID responsibleDepartmentId = requested != null
                ? requested
                : firstNonNull(existingResponsible, previousDepartment, warehouseDepartment, scopeAccessService.currentDepartmentIdOrNull());
        if (responsibleDepartmentId == null) {
            throw RestException.badRequest("responsibleDepartmentId is required for equipment location");
        }
        validateDepartmentExists(responsibleDepartmentId);
        assertDepartmentAccessIfAuthenticated(responsibleDepartmentId);
        return responsibleDepartmentId;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private void validateLocationReferences(EquipmentLocationRequest location) {
        if (location.locationType() == EquipmentLocationType.DEPARTMENT) {
            validateDepartmentExists(location.departmentId());
            assertDepartmentAccessIfAuthenticated(location.departmentId());
        } else if (location.locationType() == EquipmentLocationType.WAREHOUSE) {
            resolveWarehousePlacementStatus(location.warehouseStatus());
            validateWarehouseExists(location.warehouseId());
        } else if (location.outsideRecipientUserId() != null) {
            userRepository.findByIdAndIsDeletedFalse(location.outsideRecipientUserId())
                    .orElseThrow(() -> RestException.notFound("User not found: " + location.outsideRecipientUserId()));
        }
        validateDepartmentExists(location.responsibleDepartmentId());
        assertDepartmentAccessIfAuthenticated(location.responsibleDepartmentId());
    }

    private void assertCanAccessLocationBeforePersistence(EquipmentLocationRequest location) {
        if (location.locationType() == EquipmentLocationType.DEPARTMENT) {
            scopeAccessService.assertCanAccessEquipmentScope(
                    location.responsibleDepartmentId(),
                    location.departmentId()
            );
            return;
        }
        UUID responsibleDepartmentId = location.responsibleDepartmentId();
        if (location.locationType() == EquipmentLocationType.WAREHOUSE) {
            Optional<Warehouse> warehouseResult = warehouseRepository.findByIdAndIsDeletedFalse(location.warehouseId());
            if (warehouseResult == null) {
                throw RestException.notFound("Warehouse not found: " + location.warehouseId());
            }
            Warehouse warehouse = warehouseResult
                    .orElseThrow(() -> RestException.notFound("Warehouse not found: " + location.warehouseId()));
            responsibleDepartmentId = firstNonNull(
                    responsibleDepartmentId,
                    warehouse.getDepartmentId(),
                    scopeAccessService.currentDepartmentIdOrNull()
            );
        } else {
            responsibleDepartmentId = firstNonNull(
                    responsibleDepartmentId,
                    scopeAccessService.currentDepartmentIdOrNull()
            );
        }
        scopeAccessService.assertCanAccessEquipmentScope(responsibleDepartmentId, null);
    }

    private void assertDepartmentAccessIfAuthenticated(UUID departmentId) {
        Optional<AuthenticatedUser> currentUser = scopeAccessService.currentUser();
        if (departmentId != null && currentUser != null && currentUser.isPresent()) {
            scopeAccessService.assertCanAccessDepartment(departmentId);
        }
    }

    private LocationSnapshot snapshotLocation(Equipment equipment) {
        return new LocationSnapshot(
                equipment.getCurrentLocationType(),
                equipment.getDepartmentId(),
                equipment.getCurrentWarehouseId(),
                equipment.getOutsideReason(),
                equipment.getOutsideTakenBy(),
                equipment.getOutsideRecipientUserId(),
                equipment.getOutsideStartedDate(),
                equipment.getOutsideExpectedReturnDate(),
                equipment.getOutsideDestination(),
                equipment.getOutsideReasonNote(),
                equipment.getResponsibleDepartmentId()
        );
    }

    private void writeLocationHistory(Equipment equipment,
                                      LocationSnapshot from,
                                      LocationSnapshot to,
                                      String note) {
        EquipmentLocationHistory history = EquipmentLocationHistory.builder()
                .equipmentId(equipment.getId())
                .fromLocationType(from == null ? null : from.locationType())
                .fromDepartmentId(from == null ? null : from.departmentId())
                .fromWarehouseId(from == null ? null : from.warehouseId())
                .fromOutsideReason(from == null ? null : from.outsideReason())
                .fromOutsideTakenBy(from == null ? null : from.outsideTakenBy())
                .fromOutsideRecipientUserId(from == null ? null : from.outsideRecipientUserId())
                .fromOutsideStartedDate(from == null ? null : from.outsideStartedDate())
                .fromOutsideExpectedReturnDate(from == null ? null : from.outsideExpectedReturnDate())
                .fromOutsideDestination(from == null ? null : from.outsideDestination())
                .fromOutsideReasonNote(from == null ? null : from.outsideReasonNote())
                .toLocationType(to.locationType())
                .toDepartmentId(to.departmentId())
                .toWarehouseId(to.warehouseId())
                .toOutsideReason(to.outsideReason())
                .toOutsideTakenBy(to.outsideTakenBy())
                .toOutsideRecipientUserId(to.outsideRecipientUserId())
                .toOutsideStartedDate(to.outsideStartedDate())
                .toOutsideExpectedReturnDate(to.outsideExpectedReturnDate())
                .toOutsideDestination(to.outsideDestination())
                .toOutsideReasonNote(to.outsideReasonNote())
                .responsibleDepartmentId(to.responsibleDepartmentId())
                .changedBy(scopeAccessService.currentUserIdOrNull())
                .changedAt(Instant.now())
                .note(note)
                .build();
        equipmentLocationHistoryRepository.save(history);
    }

    private void auditLocationChange(Equipment equipment, LocationSnapshot from, LocationSnapshot to, Object newSnapshot) {
        auditBuilderService.log(
                "equipment",
                equipment.getId().toString(),
                AuditAction.UPDATE,
                com.toir.enums.AuditModule.EQUIPMENT,
                "Equipment location changed: %s -> %s".formatted(from.locationType(), to.locationType()),
                from,
                newSnapshot
        );
    }

    private record LocationSnapshot(
            EquipmentLocationType locationType,
            UUID departmentId,
            UUID warehouseId,
            EquipmentOutsideReason outsideReason,
            String outsideTakenBy,
            UUID outsideRecipientUserId,
            LocalDate outsideStartedDate,
            LocalDate outsideExpectedReturnDate,
            String outsideDestination,
            String outsideReasonNote,
            UUID responsibleDepartmentId
    ) {}

    private List<EquipmentDocumentDto> equipmentDocuments(UUID equipmentId) {
        if (equipmentDocumentRepository == null) {
            return List.of();
        }
        return equipmentDocumentRepository.findAllByEquipmentId(equipmentId)
                .stream()
                .map(document -> EquipmentDocumentDto.from(equipmentId, document))
                .filter(Objects::nonNull)
                .toList();
    }

    private com.toir.entity.equipment.EquipmentDocument findEquipmentDocument(UUID equipmentId, UUID documentId) {
        return equipmentDocumentRepository.findByIdAndEquipmentId(documentId, equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment document not found: " + documentId));
    }

    private EquipmentDocumentDto toDocumentDtoWithMetadata(
            UUID equipmentId,
            com.toir.entity.equipment.EquipmentDocument document,
            UUID currentUserId
    ) {
        fileService.getMetadata(document.getFile().getId(), currentUserId);
        return EquipmentDocumentDto.from(equipmentId, document);
    }

    private void cleanupUploadedFiles(List<UUID> fileIds, UUID currentUserId) {
        for (UUID fileId : fileIds) {
            deleteDocumentQuietly(fileId, currentUserId);
        }
    }

    private void deleteDocumentQuietly(UUID fileId, UUID currentUserId) {
        try {
            fileService.delete(fileId, currentUserId);
        } catch (RuntimeException e) {
            log.warn("Failed to cleanup equipment document file '{}': {}", fileId, e.getMessage());
        }
    }

    private UUID currentUserId(AuthenticatedUser user) {
        if (user == null || user.id() == null || user.id().isBlank()) {
            throw RestException.unauthorized("Authenticated user is required");
        }
        return UUID.fromString(user.id());
    }

    private String normalizeDocumentType(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            return null;
        }
        String trimmed = documentType.trim();
        if (trimmed.length() > 64) {
            throw RestException.badRequest("documentType must be 64 characters or fewer");
        }
        return trimmed;
    }

    private List<String> normalizeDocumentNames(List<MultipartFile> files, List<String> documentNames) {
        if (documentNames == null || documentNames.isEmpty()) {
            throw RestException.badRequest("documentNames are required for equipment document uploads");
        }
        if (documentNames.size() != files.size()) {
            throw RestException.badRequest("files and documentNames must have the same length");
        }
        List<String> normalized = new ArrayList<>(documentNames.size());
        for (int i = 0; i < documentNames.size(); i++) {
            String documentName = documentNames.get(i);
            if (documentName == null || documentName.isBlank()) {
                throw RestException.badRequest("documentNames[" + i + "] must not be blank");
            }
            String trimmed = documentName.trim();
            if (trimmed.length() > 255) {
                throw RestException.badRequest("documentNames[" + i + "] must be 255 characters or fewer");
            }
            normalized.add(trimmed);
        }
        return normalized;
    }

    private WarehouseEquipmentStatus resolveWarehousePlacementStatus(WarehouseEquipmentStatus warehouseStatus) {
        if (warehouseStatus == null) {
            return WarehouseEquipmentStatus.AVAILABLE;
        }
        if (warehouseStatus != WarehouseEquipmentStatus.AVAILABLE
                && warehouseStatus != WarehouseEquipmentStatus.OUT_OF_SERVICE) {
            throw RestException.badRequest("warehouseStatus for WAREHOUSE target must be AVAILABLE or OUT_OF_SERVICE");
        }
        return warehouseStatus;
    }

    private void validateClientProvidedCode(String code) {
        if (code != null && !code.isBlank()) {
            throw RestException.badRequest("Equipment code is generated by system and must not be provided");
        }
    }

    private boolean isEquipmentTypeChanged(UUID currentEquipmentTypeId, UUID requestedEquipmentTypeId) {
        return requestedEquipmentTypeId != null && !Objects.equals(currentEquipmentTypeId, requestedEquipmentTypeId);
    }

    private void validateAttributesForTypeChange(boolean equipmentTypeChanged, List<?> attributes) {
        if (equipmentTypeChanged && attributes == null) {
            throw RestException.badRequest("Attributes are required when equipment type changes.");
        }
    }

    private void validateCreatePlacement(UUID departmentId, UUID warehouseId) {
        if (departmentId == null && warehouseId == null) {
            throw RestException.badRequest("departmentId or warehouseId is required");
        }
    }

    private void validateAverageOperatingLifeForCreate(Long averageOperatingLifeHours) {
        if (averageOperatingLifeHours == null) {
            throw RestException.badRequest("averageOperatingLifeHours is required");
        }
        validateAverageOperatingLifeForUpdate(averageOperatingLifeHours);
    }

    private void validateAverageOperatingLifeForUpdate(Long averageOperatingLifeHours) {
        if (averageOperatingLifeHours != null && averageOperatingLifeHours <= 0) {
            throw RestException.badRequest("averageOperatingLifeHours must be positive");
        }
    }

    private void validateDepartmentExists(UUID departmentId) {
        if (departmentId == null) {
            return;
        }
        departmentRepository.findByIdAndIsDeletedFalse(departmentId)
                .orElseThrow(() -> RestException.notFound("Department not found: " + departmentId));
    }

    private void validateWarehouseExists(UUID warehouseId) {
        if (warehouseId == null) {
            return;
        }
        warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                .orElseThrow(() -> RestException.notFound("Warehouse not found: " + warehouseId));
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "EQ-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("EQ", year, sequence);
        while (repository.existsByCodeAndIsDeletedFalse(code)) {
            sequence++;
            code = formatCode("EQ", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }

    private void applyForUpdate(Equipment entity, EquipmentUpdateRequest request) {
        entity.setName(request.name() != null ? request.name() : entity.getName());
        entity.setInventoryNumber(request.inventoryNumber()  != null ? request.inventoryNumber() : entity.getInventoryNumber());
        entity.setTechnicalNumber(request.technicalNumber() != null ? request.technicalNumber() : entity.getTechnicalNumber());
        entity.setSerialNumber(request.serialNumber()  != null ? request.serialNumber() : entity.getSerialNumber());
        entity.setModel(request.model() != null ? request.model() : entity.getModel());
        entity.setEquipmentTypeId(request.equipmentTypeId() != null ? request.equipmentTypeId() : entity.getEquipmentTypeId());
        entity.setDepartmentId(request.departmentId() != null ? request.departmentId() : entity.getDepartmentId());
        entity.setLocationId(request.locationId() != null ? request.locationId() : entity.getLocationId());
        entity.setParentId(request.parentId());
        entity.setCriticalityClassId(request.criticalityClassId()  != null ? request.criticalityClassId() : entity.getCriticalityClassId());
        entity.setResponsibleId(request.responsibleId() != null ? request.responsibleId() : entity.getResponsibleId());
        entity.setManufacturer(request.manufacturer() != null ? request.manufacturer() : entity.getManufacturer());
        entity.setCategory(request.category() != null ? request.category() : entity.getCategory());
        entity.setCommissionedAt(request.commissionedAt() != null ? request.commissionedAt() : entity.getCommissionedAt());
        entity.setArrivalDate(request.arrivalDate() != null ? request.arrivalDate() : entity.getArrivalDate());
        entity.setWarrantyUntil(request.warrantyUntil() != null ? request.warrantyUntil() : entity.getWarrantyUntil());
        applyWarrantyForUpdate(entity, request);
        entity.setAverageOperatingLifeHours(request.averageOperatingLifeHours() != null
                ? request.averageOperatingLifeHours()
                : entity.getAverageOperatingLifeHours());
        entity.setOperationStartDate(request.operationStartDate() != null
                ? request.operationStartDate()
                : entity.getOperationStartDate());
        entity.setExpectedLifetimeMonths(request.expectedLifetimeMonths() != null
                ? request.expectedLifetimeMonths()
                : entity.getExpectedLifetimeMonths());
        entity.setExpectedLifetimeYears(request.expectedLifetimeYears() != null
                ? request.expectedLifetimeYears()
                : entity.getExpectedLifetimeYears());
        entity.setDescription(request.description() != null ? request.description() : entity.getDescription());
    }

    private void validateWarrantyAttachment(Boolean hasWarranty, UUID warrantyAttachmentId) {
        if (Boolean.TRUE.equals(hasWarranty) && warrantyAttachmentId != null) {
            getFileAssetOrThrow(warrantyAttachmentId);
        }
    }

    private void validateWarrantyAttachmentForUpdate(EquipmentUpdateRequest request) {
        if (Boolean.FALSE.equals(request.hasWarranty())) {
            return;
        }
        if (request.warrantyAttachmentId() != null) {
            getFileAssetOrThrow(request.warrantyAttachmentId());
        }
    }

    private FileAsset getFileAssetOrThrow(UUID fileAssetId) {
        return fileAssetRepository.findByIdAndIsDeletedFalse(fileAssetId)
                .orElseThrow(() -> RestException.notFound("File not found: " + fileAssetId));
    }

    private void applyWarrantyForUpdate(Equipment entity, EquipmentUpdateRequest request) {
        if (Boolean.FALSE.equals(request.hasWarranty())) {
            entity.setHasWarranty(false);
            entity.setWarrantyAttachmentId(null);
            entity.setWarrantyStartDate(null);
            entity.setWarrantyEndDate(null);
            return;
        }
        if (Boolean.TRUE.equals(request.hasWarranty())) {
            entity.setHasWarranty(true);
        }
        if (request.warrantyAttachmentId() != null) {
            entity.setHasWarranty(true);
            entity.setWarrantyAttachmentId(request.warrantyAttachmentId());
        }
        if (request.warrantyStartDate() != null) {
            entity.setWarrantyStartDate(request.warrantyStartDate());
        }
        if (request.warrantyEndDate() != null) {
            entity.setWarrantyEndDate(request.warrantyEndDate());
        }
    }

    private void validateWarrantyDateRangeForUpdate(Equipment entity, EquipmentUpdateRequest request) {
        if (Boolean.FALSE.equals(request.hasWarranty())) {
            return;
        }
        LocalDate startDate = request.warrantyStartDate() != null
                ? request.warrantyStartDate()
                : entity.getWarrantyStartDate();
        LocalDate endDate = request.warrantyEndDate() != null
                ? request.warrantyEndDate()
                : entity.getWarrantyEndDate();
        validateWarrantyDateRange(startDate, endDate);
    }

    private void validateWarrantyDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw RestException.badRequest("Warranty end date must be after or equal to warranty start date");
        }
    }

    private void validateNoDirectStatusChange(Equipment entity, EquipmentStatus requestedStatus) {
        if (requestedStatus == null || requestedStatus == entity.getStatus()) {
            return;
        }
        throw RestException.badRequest("Equipment status changes must use the dedicated status endpoint");
    }

    private void validateParent(UUID equipmentId, UUID parentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(equipmentId)) {
            throw RestException.badRequest("Equipment cannot be parent of itself");
        }

        Equipment parent = repository.findByIdAndIsDeletedFalse(parentId)
                .orElseThrow(() -> RestException.notFound("Parent equipment not found: " + parentId));
        UUID currentParentId = parent.getParentId();
        Set<UUID> visited = new HashSet<>();
        while (currentParentId != null) {
            if (!visited.add(currentParentId)) {
                throw RestException.conflict("Circular equipment parent chain detected");
            }
            if (currentParentId.equals(equipmentId)) {
                throw RestException.badRequest("Equipment parent chain cannot be circular");
            }
            UUID finalCurrentParentId = currentParentId;
            Equipment currentParent = repository.findByIdAndIsDeletedFalse(currentParentId)
                    .orElseThrow(() -> RestException.notFound("Parent equipment not found: " + finalCurrentParentId));
            currentParentId = currentParent.getParentId();
        }
    }

    @Transactional(readOnly = true)
    public EquipmentStatsResponse getEquipmentStats(
            String search,
            EquipmentCategory category,
            UUID departmentId,
            UUID equipmentTypeId
    ) {
        String searchPattern = toSearchPattern(search);

        EquipmentStatsProjection stats = repository.getEquipmentStats(
                searchPattern,
                category,
                departmentId,
                equipmentTypeId,
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.DECOMMISSIONED
        );

        return new EquipmentStatsResponse(
                safe(stats.getTotalInRegistry()),
                safe(stats.getActive()),
                safe(stats.getInRepair()),
                safe(stats.getDecommissioned())
        );
    }

    private String toSearchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        return "%" + search.trim().toLowerCase() + "%";
    }

    private long safe(Long value) {
        return value == null ? 0L : value;
    }
}
