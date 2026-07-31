package com.toir.service.equipment;

import com.toir.dto.equipment.*;
import com.toir.dto.attachment.AttachmentGroupDto;
import com.toir.dto.file.PresignedUrlResponse;
import com.toir.dto.file.UploadFileResponse;
import com.toir.dto.mxik.MxikRefDto;
import com.toir.entity.Counteragent;
import com.toir.entity.FileAsset;
import com.toir.entity.Mxik;
import com.toir.entity.UploadedFile;
import com.toir.repository.equipment.EquipmentStatsProjection;
import com.toir.dto.warehouse.WarehouseEquipmentAssignRequest;
import com.toir.entity.Department;
import com.toir.entity.DowntimeEvent;
import com.toir.entity.Location;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentAttributeDefinition;
import com.toir.entity.equipment.EquipmentAttributeValue;
import com.toir.entity.equipment.EquipmentDocument;
import com.toir.entity.equipment.EquipmentDocumentFile;
import com.toir.entity.equipment.EquipmentLocationHistory;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.equipment.EquipmentPassport;
import com.toir.entity.equipment.EquipmentType;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.users.Employee;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.enums.AuditAction;
import com.toir.enums.AttachmentTargetType;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentCommissioningStatus;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentOutsideReason;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.EquipmentUsageSessionStatus;
import com.toir.enums.FileCategory;
import com.toir.enums.MeterType;
import com.toir.enums.PlacementType;
import com.toir.enums.PlacementTargetType;
import com.toir.enums.RequestStatus;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkType;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseEquipmentItemRepository;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.EquipmentUsageSessionRepository;
import com.toir.repository.FileAssetRepository;
import com.toir.repository.MxikRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.LocationRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentLocationHistoryRepository;
import com.toir.repository.equipment.EquipmentCommissioningActRepository;
import com.toir.repository.equipment.EquipmentAttributeDefinitionRepository;
import com.toir.repository.equipment.EquipmentAttributeValueRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentPassportRepository;
import com.toir.repository.equipment.EquipmentDocumentFileRepository;
import com.toir.repository.equipment.EquipmentDocumentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.repository.UploadedFileRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.security.ScopeAccessService;
import com.toir.service.file_management.FileService;
import com.toir.service.attachment.AttachmentGroupService;
import com.toir.service.CounteragentService;
import com.toir.service.WarehouseEquipmentItemService;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
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
    private final EquipmentCommissioningActRepository equipmentCommissioningActRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;
    private final EquipmentPassportRepository passportRepository;
    private final EquipmentAttributeDefinitionRepository attributeDefinitionRepository;
    private final EquipmentAttributeValueRepository attributeValueRepository;
    private final EquipmentMeterRepository equipmentMeterRepository;
    private final WarehouseRepository warehouseRepository;
    private final CounteragentService counteragentService;
    private final MxikRepository mxikRepository;
    private final WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;
    private final EquipmentLocationHistoryRepository equipmentLocationHistoryRepository;
    private final EquipmentUsageSessionRepository equipmentUsageSessionRepository;
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
    private final EquipmentDocumentFileRepository equipmentDocumentFileRepository;
    private final AttachmentGroupService attachmentGroupService;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final ScopeAccessService scopeAccessService;
    private static final int MAX_EQUIPMENT_DOCUMENT_FILES = 25;
    private static final Set<String> DTO_SORT_FIELDS = Set.of(
            "producedYear",
            "status",
            "criticalityClass",
            "commissioningDate",
            "createdAt",
            "averageOperatingLifeHours",
            "expectedLifetimeMonths",
            "expectedLifetimeYears",
            "expectedLifetimeHours",
            "lifetimeLimitValue",
            "lifetimeBaselineValue",
            "lifetimeWarningPercent",
            "lifetimeCurrentValue",
            "lifetimeTargetValue",
            "lifetimeRemainingValue",
            "lifetimeConsumedPercent",
            "averageDailyUsage",
            "daysOfResourceRemaining",
            "forecastConsumedResource",
            "forecastRemainingResource",
            "forecastAvgUsagePerActiveDay",
            "forecastRemainingActiveDays"
    );
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
        return search(scopeDepartmentId, departmentId, equipmentTypeId, status, category, warehouseId, locationType,
                outsideReason, overdueOnly, availableForReplacement, (Boolean) null, search, page, pageSize);
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
                                     Boolean hasWarranty,
                                     String search,
                                     int page,
                                     int pageSize) {
        return search(scopeDepartmentId, departmentId, equipmentTypeId, status, category, warehouseId, locationType,
                outsideReason, overdueOnly, availableForReplacement, hasWarranty, search, page, pageSize, null, "asc");
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
                                     UUID mxikId,
                                     String search,
                                     int page,
                                     int pageSize) {
        return search(scopeDepartmentId, departmentId, equipmentTypeId, status, category, warehouseId, locationType,
                outsideReason, overdueOnly, availableForReplacement, null, mxikId, search, page, pageSize);
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
                                     Boolean hasWarranty,
                                     UUID mxikId,
                                     String search,
                                     int page,
                                     int pageSize) {
        return search(scopeDepartmentId, departmentId, equipmentTypeId, status, category, warehouseId, locationType,
                outsideReason, overdueOnly, availableForReplacement, hasWarranty, mxikId, search, page, pageSize, null, "asc");
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
                                     int pageSize,
                                     String sortBy,
                                     String sortDir) {
        return search(scopeDepartmentId, departmentId, equipmentTypeId, status, category, warehouseId, locationType,
                outsideReason, overdueOnly, availableForReplacement, null, search, page, pageSize, sortBy, sortDir);
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
                                     Boolean hasWarranty,
                                     String search,
                                     int page,
                                     int pageSize,
                                     String sortBy,
                                     String sortDir) {
        return search(scopeDepartmentId, departmentId, equipmentTypeId, status, category, warehouseId, locationType,
                outsideReason, overdueOnly, availableForReplacement, hasWarranty, null, search, page, pageSize, sortBy, sortDir);
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
                                     Boolean hasWarranty,
                                     UUID mxikId,
                                     String search,
                                     int page,
                                     int pageSize,
                                     String sortBy,
                                     String sortDir) {
        String searchPattern = null;
        if (search != null && !search.isBlank()) {
            searchPattern = "%" + search.trim().toLowerCase() + "%";
        }
        boolean dtoSort = isDtoSort(sortBy);
        Pageable pageable = dtoSort ? Pageable.unpaged() : PaginationUtils.pageRequest(page, pageSize);
        Page<Equipment> items;
        if (availableForReplacement) {
            if (warehouseId == null) {
                throw RestException.badRequest("warehouseId is required when availableForReplacement is true");
            }
            warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                    .orElseThrow(() -> RestException.notFound("Warehouse not found: " + warehouseId));
            if (mxikId == null) {
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
                        hasWarranty,
                        searchPattern,
                        pageable
                );
            } else {
                items = repository.searchAvailableForReplacementWithMxik(
                        warehouseId,
                        WarehouseEquipmentStatus.AVAILABLE,
                        EquipmentLocationType.WAREHOUSE,
                        WorkType.REPLACEMENT,
                        FINAL_WORK_ORDER_STATUSES,
                        scopeDepartmentId,
                        equipmentTypeId,
                        status,
                        category,
                        mxikId,
                        hasWarranty,
                        searchPattern,
                        pageable
                );
            }
        } else {
            if (mxikId == null) {
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
                        hasWarranty,
                        searchPattern,
                        pageable
                );
            } else {
                items = repository.searchWithMxik(
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
                        mxikId,
                        hasWarranty,
                        searchPattern,
                        pageable
                );
            }
        }
        Page<EquipmentDto> enriched = enrich(items);
        if (!dtoSort) {
            return enriched;
        }
        Map<UUID, java.time.Instant> createdAtById = items.getContent().stream()
                .collect(Collectors.toMap(Equipment::getId, Equipment::getCreatedAt, (left, right) -> left));
        List<EquipmentDto> sorted = enriched.getContent().stream()
                .sorted(equipmentComparator(sortBy, sortDir, createdAtById))
                .toList();
        return PaginationUtils.page(sorted, page, pageSize);
    }

    private boolean isDtoSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return false;
        }
        return DTO_SORT_FIELDS.contains(sortBy.trim());
    }

    private Comparator<EquipmentDto> equipmentComparator(String sortBy, String sortDir, Map<UUID, java.time.Instant> createdAtById) {
        Comparator<EquipmentDto> comparator = switch (sortBy.trim()) {
            case "producedYear" -> nullableComparator(EquipmentDto::producedYear);
            case "status" -> nullableComparator(EquipmentDto::status);
            case "criticalityClass" -> nullableComparator(dto -> dto.criticalityClassId() == null ? null : dto.criticalityClassId().toString());
            case "commissioningDate" -> nullableComparator(EquipmentDto::commissionedAt);
            case "createdAt" -> nullableComparator(dto -> createdAtById.get(dto.id()));
            case "averageOperatingLifeHours" -> nullableComparator(EquipmentDto::averageOperatingLifeHours);
            case "expectedLifetimeMonths" -> nullableComparator(EquipmentDto::expectedLifetimeMonths);
            case "expectedLifetimeYears" -> nullableComparator(EquipmentDto::expectedLifetimeYears);
            case "expectedLifetimeHours" -> nullableComparator(EquipmentDto::expectedLifetimeHours);
            case "lifetimeLimitValue" -> nullableComparator(EquipmentDto::lifetimeLimitValue);
            case "lifetimeBaselineValue" -> nullableComparator(EquipmentDto::lifetimeBaselineValue);
            case "lifetimeWarningPercent" -> nullableComparator(EquipmentDto::lifetimeWarningPercent);
            case "lifetimeCurrentValue" -> nullableComparator(EquipmentDto::lifetimeCurrentValue);
            case "lifetimeTargetValue" -> nullableComparator(EquipmentDto::lifetimeTargetValue);
            case "lifetimeRemainingValue" -> nullableComparator(EquipmentDto::lifetimeRemainingValue);
            case "lifetimeConsumedPercent" -> nullableComparator(EquipmentDto::lifetimeConsumedPercent);
            case "averageDailyUsage" -> nullableComparator(EquipmentDto::averageDailyUsage);
            case "daysOfResourceRemaining" -> nullableComparator(EquipmentDto::daysOfResourceRemaining);
            case "forecastConsumedResource" -> nullableComparator(EquipmentDto::forecastConsumedResource);
            case "forecastRemainingResource" -> nullableComparator(EquipmentDto::forecastRemainingResource);
            case "forecastAvgUsagePerActiveDay" -> nullableComparator(EquipmentDto::forecastAvgUsagePerActiveDay);
            case "forecastRemainingActiveDays" -> nullableComparator(EquipmentDto::forecastRemainingActiveDays);
            default -> throw RestException.badRequest("Unsupported equipment sort: " + sortBy);
        };
        return "desc".equalsIgnoreCase(sortDir) ? comparator.reversed() : comparator;
    }

    private static <T, U extends Comparable<? super U>> Comparator<T> nullableComparator(Function<T, U> extractor) {
        return Comparator.comparing(extractor, Comparator.nullsLast(Comparator.naturalOrder()));
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
        int repairCount = (int) repairRequestEntities.stream()
                .filter(r -> r.getStatus() == RequestStatus.COMPLETED || r.getStatus() == RequestStatus.CLOSED)
                .count();

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
        int repairsCount = (int) workOrderEntities.stream()
                .filter(w -> w.getWorkType() == WorkType.REPAIR)
                .count();
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
                repairCount,
                repairsCount,
                repairRequests,
                defects,
                workOrders,
                downtimeEvents,
                equipmentAttributeService == null ? List.of() : equipmentAttributeService.findValues(id),
                equipmentManualAttributeService == null ? List.of() : equipmentManualAttributeService.list(id),
                equipmentDocuments(id)
        );
    }

    @Transactional(readOnly = true)
    public Page<EquipmentLocationHistoryResponse> locationHistory(UUID id, Pageable pageable) {
        getOrThrow(id);
        return equipmentLocationHistoryRepository
                .findAllByEquipmentIdAndIsDeletedFalseOrderByChangedAtDesc(id, pageable)
                .map(history -> EquipmentLocationHistoryResponse.from(history, nextLocationChangeAt(history)));
    }

    private static final Set<String> ALLOWED_EQUIPMENT_DOCUMENT_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    @Transactional
    public List<EquipmentDocumentDto> attachDocuments(
            UUID equipmentId,
            List<MultipartFile> files,
            List<String> documentNames,
            List<String> documentTypes,
            List<String> documentNumbers,
            AuthenticatedUser user
    ) {
        if (files == null || files.isEmpty()) {
            throw RestException.badRequest("At least one equipment document file is required");
        }
        getOrThrow(equipmentId);
        List<String> normalizedDocumentNames = normalizeDocumentNames(files, documentNames);
        List<String> normalizedDocumentTypes = normalizeDocumentTypes(files, documentTypes);
        List<String> normalizedDocumentNumbers = normalizeDocumentNumbers(files, documentNumbers);
        List<EquipmentDocumentDto> result = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            AttachmentGroupDto group = attachmentGroupService.createGroup(
                    normalizedDocumentNames.get(i),
                    null,
                    "EQUIPMENT",
                    equipmentId,
                    normalizedDocumentTypes.get(i),
                    normalizedDocumentNumbers.get(i),
                    List.of(files.get(i)),
                    null,
                    user
            );
            EquipmentDocumentDto dto = EquipmentDocumentDto.fromAttachmentGroup(equipmentId, group);
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    @Transactional
    public EquipmentDocumentDto attachDocumentFiles(
            UUID equipmentId,
            List<MultipartFile> files,
            String documentName,
            String documentType,
            String documentNumber,
            AuthenticatedUser user
    ) {
        getOrThrow(equipmentId);
        validateEquipmentDocumentFiles(files);
        validateEquipmentDocumentFileLimit(files.size());
        String normalizedDocumentName = normalizeDocumentName(documentName);
        String normalizedDocumentType = normalizeDocumentType(documentType);
        String normalizedDocumentNumber = normalizeDocumentNumber(documentNumber, 0);
        AttachmentGroupDto group = attachmentGroupService.createGroup(
                normalizedDocumentName,
                null,
                "EQUIPMENT",
                equipmentId,
                normalizedDocumentType,
                normalizedDocumentNumber,
                files,
                null,
                user
        );
        return EquipmentDocumentDto.fromAttachmentGroup(equipmentId, group);
    }

    @Transactional
    public EquipmentDocumentDto attachDocumentFiles(
            UUID equipmentId,
            UUID documentId,
            List<MultipartFile> files,
            AuthenticatedUser user
    ) {
        getOrThrow(equipmentId);
        validateEquipmentDocumentFiles(files);
        equipmentGroupOrThrow(equipmentId, documentId, user);
        AttachmentGroupDto group = attachmentGroupService.addFiles(documentId, files, null, user);
        return EquipmentDocumentDto.fromAttachmentGroup(equipmentId, group);
    }

    @Transactional(readOnly = true)
    public List<EquipmentDocumentDto> getDocuments(UUID equipmentId, AuthenticatedUser user) {
        getOrThrow(equipmentId);
        return attachmentGroupService.listGroups("EQUIPMENT", equipmentId, user)
                .stream()
                .map(group -> EquipmentDocumentDto.fromAttachmentGroup(equipmentId, group))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public EquipmentDocumentDto getDocument(UUID equipmentId, UUID documentId, AuthenticatedUser user) {
        getOrThrow(equipmentId);
        return EquipmentDocumentDto.fromAttachmentGroup(equipmentId, equipmentGroupOrThrow(equipmentId, documentId, user));
    }

    @Transactional(readOnly = true)
    public PresignedUrlResponse getDocumentPresignedUrl(UUID equipmentId, UUID documentId, AuthenticatedUser user) {
        getOrThrow(equipmentId);
        AttachmentGroupDto group = equipmentGroupOrThrow(equipmentId, documentId, user);
        return attachmentGroupService.getFilePresignedUrl(documentId, primaryFile(group).fileId(), user);
    }

    @Transactional(readOnly = true)
    public Resource downloadDocument(UUID equipmentId, UUID documentId, AuthenticatedUser user) {
        getOrThrow(equipmentId);
        AttachmentGroupDto group = equipmentGroupOrThrow(equipmentId, documentId, user);
        return attachmentGroupService.downloadFile(documentId, primaryFile(group).fileId(), user);
    }

    @Transactional(readOnly = true)
    public PresignedUrlResponse getDocumentFilePresignedUrl(
            UUID equipmentId,
            UUID documentId,
            UUID fileId,
            AuthenticatedUser user
    ) {
        getOrThrow(equipmentId);
        equipmentGroupOrThrow(equipmentId, documentId, user);
        return attachmentGroupService.getFilePresignedUrl(documentId, fileId, user);
    }

    @Transactional(readOnly = true)
    public Resource downloadDocumentFile(
            UUID equipmentId,
            UUID documentId,
            UUID fileId,
            AuthenticatedUser user
    ) {
        getOrThrow(equipmentId);
        equipmentGroupOrThrow(equipmentId, documentId, user);
        return attachmentGroupService.downloadFile(documentId, fileId, user);
    }

    @Transactional
    public void deleteDocument(UUID equipmentId, UUID documentId, AuthenticatedUser user) {
        getOrThrow(equipmentId);
        equipmentGroupOrThrow(equipmentId, documentId, user);
        attachmentGroupService.deleteGroup(documentId, user);
    }

    @Transactional
    public void deleteDocumentFile(UUID equipmentId, UUID documentId, UUID fileId, AuthenticatedUser user) {
        getOrThrow(equipmentId);
        equipmentGroupOrThrow(equipmentId, documentId, user);
        attachmentGroupService.removeFile(documentId, fileId, user);
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
        validateCreateRequiredFields(request);
        validateClientProvidedCode(request.code());
        assertCanAccessLocationBeforePersistence(location);
        validateLocationReferences(location);
        if (repository.existsByInventoryNumberAndIsDeletedFalse(request.inventoryNumber())) {
            throw RestException.conflict("Inventory number already exists: " + request.inventoryNumber());
        }
        validateParent(null, request.parentId());
        validateWarrantyDateRange(request.warrantyStartDate(), request.warrantyEndDate());
        validateWarrantyAttachment(request.hasWarranty(), request.warrantyAttachmentId());
        validateCounteragentReferences(request);
        validateMxik(request.mxikId());
        validateResponsibleEmployee(request.responsibleId());
        Equipment entity = new Equipment();
        entity.setCode(nextCode());
        apply(entity, request);
        applyLocation(entity, location);
        validateRequiredEquipmentFields(entity);
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
        validateCounteragentReferencesForUpdate(entity, request);
        validateMxik(request.mxikId());

        applyForUpdate(entity, request);
        if (location != null) {
            assertNoOpenUsageSession(entity.getId());
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
        assertNoOpenUsageSession(equipment.getId());
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

    private void assertNoOpenUsageSession(UUID equipmentId) {
        if (equipmentUsageSessionRepository.existsByEquipmentIdAndStatusAndIsDeletedFalse(
                equipmentId,
                EquipmentUsageSessionStatus.OPEN
        )) {
            throw RestException.conflict("Equipment has an open usage session and cannot be transferred");
        }
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
        Set<UUID> responsibleIds = collectIds(items, Equipment::getResponsibleId);
        Set<UUID> locIds = collectIds(items, Equipment::getLocationId);
        Set<UUID> typeIds = collectIds(items, Equipment::getEquipmentTypeId);
        Set<UUID> parentIds = collectIds(items, Equipment::getParentId);
        Set<UUID> currentWarehouseIds = collectIds(items, Equipment::getCurrentWarehouseId);
        Set<UUID> warrantyAttachmentIds = collectIds(items, Equipment::getWarrantyAttachmentId);
        Set<UUID> counteragentIds = collectIds(items, Equipment::getCounteragentId);
        Set<UUID> warrantyCounteragentIds = collectIds(items, Equipment::getWarrantyCounteragentId);
        Set<UUID> mxikIds = collectIds(items, Equipment::getMxikId);
        Set<UUID> equipmentIds = items.stream().map(Equipment::getId).collect(Collectors.toSet());
        Set<UUID> equipmentIdsWithCreatedAct = new HashSet<>(
                equipmentCommissioningActRepository.findEquipmentIdsWithStatuses(
                        equipmentIds,
                        EnumSet.of(
                                EquipmentCommissioningStatus.DRAFT,
                                EquipmentCommissioningStatus.PENDING_APPROVAL,
                                EquipmentCommissioningStatus.APPROVED
                        )
                )
        );

        Map<UUID, Department> deptMap = byId(departmentRepository.findAllByIdInAndIsDeletedFalse(deptIds), Department::getId);
        Map<UUID, Employee> employeeMap = responsibleIds.isEmpty()
                ? Collections.emptyMap()
                : byId(employeeRepository.findAllByIdInAndIsDeletedFalse(responsibleIds), Employee::getId);
        items.stream()
                .filter(equipment -> equipment.getResponsibleId() != null)
                .filter(equipment -> !employeeMap.containsKey(equipment.getResponsibleId()))
                .findFirst()
                .ifPresent(equipment -> {
                    throw new DataIntegrityViolationException(
                            "Equipment %s references unresolved responsible Employee %s"
                                    .formatted(equipment.getId(), equipment.getResponsibleId())
                    );
                });
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
        Set<UUID> allCounteragentIds = new HashSet<>(counteragentIds);
        allCounteragentIds.addAll(warrantyCounteragentIds);
        Map<UUID, Counteragent> counteragentMap = allCounteragentIds.isEmpty()
                ? Collections.emptyMap()
                : byId(counteragentService.load(allCounteragentIds), Counteragent::getId);
        Map<UUID, Mxik> mxikMap = mxikIds.isEmpty()
                ? Collections.emptyMap()
                : byId(mxikRepository.findAllByIdInAndIsDeletedFalse(mxikIds), Mxik::getId);
        Map<UUID, EquipmentPassport> passportMap = passportRepository
                .findAllByEquipmentIdInAndIsDeletedFalse(equipmentIds).stream()
                .collect(Collectors.toMap(EquipmentPassport::getEquipmentId, Function.identity(), (a, b) -> a));
        Map<UUID, List<EquipmentAttributeDefinition>> requiredDefinitionsByType =
                requiredPassportDefinitionsByType(typeIds);
        Map<UUID, Map<UUID, EquipmentAttributeValue>> valuesByEquipment =
                passportValuesByEquipment(equipmentIds);
        Map<UUID, FileAsset> warrantyAttachmentMap = warrantyAttachmentIds.isEmpty()
                ? Collections.emptyMap()
                : byId(fileAssetRepository.findAllByIdInAndIsDeletedFalse(warrantyAttachmentIds), FileAsset::getId);
        Map<UUID, List<EquipmentMeter>> activeMetersByEquipment = equipmentIds.isEmpty()
                ? Collections.emptyMap()
                : equipmentMeterRepository.findAllByEquipmentIdInAndActiveTrueAndIsDeletedFalse(equipmentIds)
                        .stream()
                        .collect(Collectors.groupingBy(EquipmentMeter::getEquipmentId));

        return items.stream()
                .map(e -> {
                    EquipmentDto.Ref departmentRef = deptRef(deptMap.get(e.getDepartmentId()));
                    EquipmentDto.ResponsibleRef responsibleRef = responsibleRef(employeeMap.get(e.getResponsibleId()));
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
                            warrantyAttachmentMap.get(e.getWarrantyAttachmentId()),
                            passportCompleteness(
                                    e,
                                    requiredDefinitionsByType.getOrDefault(e.getEquipmentTypeId(), List.of()),
                                    valuesByEquipment.getOrDefault(e.getId(), Map.of())
                            ),
                            resolveLifetimeMeter(e, activeMetersByEquipment.getOrDefault(e.getId(), List.of())),
                            responsibleRef,
                            equipmentIdsWithCreatedAct.contains(e.getId()),
                            counteragentMap.get(e.getCounteragentId()),
                            counteragentMap.get(e.getWarrantyCounteragentId()),
                            MxikRefDto.from(mxikMap.get(e.getMxikId()))
                    );
                })
                .toList();
    }

    private EquipmentMeter resolveLifetimeMeter(Equipment equipment, List<EquipmentMeter> activeMeters) {
        if (activeMeters.isEmpty()) {
            return null;
        }
        UUID configuredMeterId = equipment.getLifetimeMeterId();
        if (configuredMeterId != null) {
            return activeMeters.stream()
                    .filter(meter -> configuredMeterId.equals(meter.getId()))
                    .findFirst()
                    .orElse(null);
        }
        MeterType type = effectiveLifetimeCounterType(equipment);
        if (type == null) {
            return null;
        }
        return activeMeters.stream()
                .filter(meter -> meter.getMeterType() == type)
                .max(Comparator.comparingDouble(EquipmentMeter::getCurrentValue))
                .orElse(null);
    }

    private MeterType effectiveLifetimeCounterType(Equipment equipment) {
        if (equipment.getLifetimeCounterType() != null) {
            return equipment.getLifetimeCounterType();
        }
        return equipment.getExpectedLifetimeHours() != null && equipment.getExpectedLifetimeHours() > 0
                ? MeterType.ENGINE_HOURS
                : null;
    }

    private Map<UUID, List<EquipmentAttributeDefinition>> requiredPassportDefinitionsByType(Set<UUID> typeIds) {
        if (typeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return attributeDefinitionRepository.findAllByEquipmentTypeIdInAndIsDeletedFalse(typeIds).stream()
                .filter(EquipmentAttributeDefinition::isRequired)
                .collect(Collectors.groupingBy(
                        EquipmentAttributeDefinition::getEquipmentTypeId,
                        Collectors.collectingAndThen(Collectors.toList(), definitions -> definitions.stream()
                                .sorted(Comparator.comparing(
                                                EquipmentAttributeDefinition::getSortOrder,
                                                Comparator.nullsLast(Integer::compareTo))
                                        .thenComparing(EquipmentAttributeDefinition::getLabel,
                                                Comparator.nullsLast(String::compareToIgnoreCase)))
                                .toList())
                ));
    }

    private Map<UUID, Map<UUID, EquipmentAttributeValue>> passportValuesByEquipment(Set<UUID> equipmentIds) {
        if (equipmentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return attributeValueRepository.findAllByEquipmentIdInAndIsDeletedFalse(equipmentIds).stream()
                .collect(Collectors.groupingBy(
                        EquipmentAttributeValue::getEquipmentId,
                        Collectors.toMap(
                                EquipmentAttributeValue::getAttributeDefinitionId,
                                Function.identity(),
                                (a, b) -> a
                        )
                ));
    }

    private static EquipmentDto.PassportCompletenessRef passportCompleteness(
            Equipment equipment,
            List<EquipmentAttributeDefinition> requiredDefinitions,
            Map<UUID, EquipmentAttributeValue> valuesByDefinition
    ) {
        List<EquipmentDto.MissingPassportFieldRef> missingFields = new ArrayList<>();
        addMissingGlobalField(missingFields, equipment.getCriticalityClassId(),
                "criticalityClassId", "Criticality class", 0);
        addMissingGlobalField(missingFields, hasEquipmentLocation(equipment) ? Boolean.TRUE : null,
                "locationId", "Location", 1);
        addMissingGlobalField(missingFields, equipment.getCommissionedAt(),
                "commissionedAt", "Commissioning date", 2);
        addMissingGlobalField(missingFields, equipment.getResponsibleId(),
                "responsibleId", "Responsible person", 3);
        requiredDefinitions.stream()
                .filter(definition -> !hasPassportValue(valuesByDefinition.get(definition.getId())))
                .map(definition -> new EquipmentDto.MissingPassportFieldRef(
                        definition.getId(),
                        definition.getKey(),
                        definition.getLabel(),
                        definition.getGroupName(),
                        definition.getSortOrder() == null ? 0 : definition.getSortOrder(),
                        true
                ))
                .forEach(missingFields::add);
        int requiredCount = 4 + requiredDefinitions.size();
        int missingCriticalCount = missingFields.size();
        int filledCount = requiredCount - missingCriticalCount;
        boolean complete = missingCriticalCount == 0;
        return new EquipmentDto.PassportCompletenessRef(
                complete,
                requiredCount,
                filledCount,
                missingCriticalCount,
                0,
                missingFields,
                complete ? null : "Missing required passport fields",
                complete ? null : "Fill equipment passport",
                complete ? null : "/equipment/" + equipment.getId() + "/passport"
        );
    }

    private static void addMissingGlobalField(
            List<EquipmentDto.MissingPassportFieldRef> missingFields,
            Object value,
            String key,
            String label,
            int sortOrder
    ) {
        if (value == null) {
            missingFields.add(new EquipmentDto.MissingPassportFieldRef(
                    null, key, label, "GLOBAL", sortOrder, true
            ));
        }
    }

    private static boolean hasPassportValue(EquipmentAttributeValue value) {
        return value != null
                && (StringUtils.hasText(value.getValueText())
                || value.getValueNumber() != null
                || value.getValueDate() != null
                || value.getValueBoolean() != null
                || StringUtils.hasText(value.getValueOption())
                || StringUtils.hasText(value.getValueJson()));
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

    private static EquipmentDto.ResponsibleRef responsibleRef(Employee emp) {
        if (emp == null) {
            return null;
        }
        String fullName = buildEmployeeFullName(emp.getLastName(), emp.getFirstName(), emp.getMiddleName());
        return new EquipmentDto.ResponsibleRef(
                emp.getId(),
                emp.getPersonnelNumber(),
                fullName,
                emp.getPhone()
        );
    }

    private static String buildEmployeeFullName(String lastName, String firstName, String middleName) {
        StringBuilder sb = new StringBuilder();
        if (lastName != null && !lastName.isBlank()) {
            sb.append(lastName);
        }
        if (firstName != null && !firstName.isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(firstName);
        }
        if (middleName != null && !middleName.isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(middleName);
        }
        return sb.toString();
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
                p.getPassportNumber(), p.getPowerKw(), p.getVoltageV(), p.getPressureBar(),
                p.getFactoryNumber(), p.getManufacturerSerial(),
                p.getProductivity() != null ? p.getProductivity() : java.util.List.of(),
                p.getInstallDate(), p.getLastInspectionDate(), p.getNotes());
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

    private void validateCreateRequiredFields(EquipmentCreateRequest request) {
        List<String> missing = new ArrayList<>();
        if (request.criticalityClassId() == null) missing.add("criticalityClassId");
        if (request.commissionedAt() == null) missing.add("commissionedAt");
        if (request.responsibleId() == null) missing.add("responsibleId");
        throwIfRequiredFieldsMissing(missing);
    }

    private void validateRequiredEquipmentFields(Equipment equipment) {
        List<String> missing = new ArrayList<>();
        if (equipment.getCriticalityClassId() == null) missing.add("criticalityClassId");
        if (!hasEquipmentLocation(equipment)) missing.add("locationId");
        if (equipment.getCommissionedAt() == null) missing.add("commissionedAt");
        if (equipment.getResponsibleId() == null) missing.add("responsibleId");
        throwIfRequiredFieldsMissing(missing);
    }

    private static boolean hasEquipmentLocation(Equipment equipment) {
        if (equipment.getLocationId() != null) {
            return true;
        }
        if (equipment.getCurrentLocationType() == EquipmentLocationType.DEPARTMENT) {
            return equipment.getDepartmentId() != null;
        }
        if (equipment.getCurrentLocationType() == EquipmentLocationType.WAREHOUSE) {
            return equipment.getCurrentWarehouseId() != null;
        }
        if (equipment.getCurrentLocationType() == EquipmentLocationType.OUTSIDE_FACILITY) {
            return StringUtils.hasText(equipment.getOutsideDestination());
        }
        return equipment.getDepartmentId() != null || equipment.getCurrentWarehouseId() != null;
    }

    private void throwIfRequiredFieldsMissing(List<String> missing) {
        if (!missing.isEmpty()) {
            throw RestException.badRequest(
                    "EQUIPMENT_REQUIRED_FIELDS_MISSING: " + String.join(",", missing)
            );
        }
    }

    private void apply(Equipment entity, EquipmentCreateRequest request) {
        entity.setName(request.name());
        entity.setInventoryNumber(request.inventoryNumber());
        entity.setTechnicalNumber(request.technicalNumber());
        entity.setSerialNumber(request.serialNumber());
        entity.setModel(request.model());
        entity.setProducedYear(request.producedYear());
        entity.setEquipmentTypeId(request.equipmentTypeId());
        entity.setMxikId(request.mxikId());
        entity.setDepartmentId(request.departmentId());
        entity.setLocationId(request.locationId());
        entity.setParentId(request.parentId());
        entity.setCriticalityClassId(request.criticalityClassId());
        entity.setResponsibleId(request.responsibleId());
        entity.setCounteragentId(request.counteragentId());
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
        entity.setWarrantyCounteragentId(Boolean.TRUE.equals(request.hasWarranty()) ? effectiveWarrantyCounteragentId(request) : null);
        entity.setOperationStartDate(request.operationStartDate());
        entity.setExpectedLifetimeMonths(request.expectedLifetimeMonths());
        entity.setExpectedLifetimeYears(request.expectedLifetimeYears());
        entity.setExpectedLifetimeHours(request.expectedLifetimeHours());
        entity.setAverageDailyUsage(request.averageDailyUsage());
        applyDynamicLifetimeForCreate(entity, request);
        entity.setDaysOfResourceRemaining(null);
        entity.setAverageOperatingLifeHours(calculateAverageOperatingLifeHours(
                request.expectedLifetimeYears(),
                request.expectedLifetimeMonths(),
                request.expectedLifetimeHours(),
                effectiveLifetimeCounterType(entity),
                entity.getLifetimeLimitValue()
        ));
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

    private Instant nextLocationChangeAt(EquipmentLocationHistory history) {
        if (history == null || history.getChangedAt() == null) {
            return null;
        }
        return equipmentLocationHistoryRepository
                .findFirstByEquipmentIdAndIsDeletedFalseAndChangedAtAfterOrderByChangedAtAsc(
                        history.getEquipmentId(),
                        history.getChangedAt()
                )
                .map(EquipmentLocationHistory::getChangedAt)
                .orElse(null);
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
        Optional<AuthenticatedUser> currentUser = scopeAccessService == null
                ? Optional.empty()
                : Optional.ofNullable(scopeAccessService.currentUser()).orElse(Optional.empty());
        if (currentUser.isPresent()) {
            return attachmentGroupService.listGroups("EQUIPMENT", equipmentId, currentUser.get())
                    .stream()
                    .map(group -> EquipmentDocumentDto.fromAttachmentGroup(equipmentId, group))
                    .filter(Objects::nonNull)
                    .toList();
        }
        if (equipmentDocumentRepository == null) {
            return List.of();
        }
        return equipmentDocumentRepository.findAllByEquipmentId(equipmentId)
                .stream()
                .map(document -> EquipmentDocumentDto.from(equipmentId, document))
                .filter(Objects::nonNull)
                .toList();
    }

    private AttachmentGroupDto equipmentGroupOrThrow(UUID equipmentId, UUID documentId, AuthenticatedUser user) {
        AttachmentGroupDto group = attachmentGroupService.getGroup(documentId, user);
        if (group.targetType() != AttachmentTargetType.EQUIPMENT
                || !Objects.equals(group.targetId(), equipmentId)) {
            throw RestException.notFound("Equipment document not found: " + documentId);
        }
        return group;
    }

    private EquipmentDocument findEquipmentDocument(UUID equipmentId, UUID documentId) {
        return equipmentDocumentRepository.findByIdAndEquipmentId(documentId, equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment document not found: " + documentId));
    }

    private EquipmentDocumentFile findEquipmentDocumentFile(UUID equipmentId, UUID documentId, UUID fileId) {
        return equipmentDocumentFileRepository.findActiveByDocumentIdAndFileIdAndEquipmentId(documentId, fileId, equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment document file not found: " + fileId));
    }

    private AttachmentGroupDto.FileItem primaryFile(AttachmentGroupDto group) {
        if (group == null || group.files() == null || group.files().isEmpty()) {
            throw RestException.notFound("Attachment group file not found");
        }
        return group.files().getFirst();
    }

    private EquipmentDocumentDto toDocumentDtoWithMetadata(
            UUID equipmentId,
            EquipmentDocument document,
            UUID currentUserId
    ) {
        activeDocumentFileIds(document).forEach(fileId -> fileService.getMetadata(fileId, currentUserId));
        return EquipmentDocumentDto.from(equipmentId, document);
    }

    private List<UploadedFile> uploadEquipmentDocumentFiles(
            List<MultipartFile> files,
            UUID currentUserId,
            List<UUID> uploadedFileIds
    ) {
        List<UploadedFile> uploadedFiles = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            UploadFileResponse uploaded = fileService.upload(file, FileCategory.EQUIPMENT_DOCUMENT, currentUserId);
            uploadedFileIds.add(uploaded.id());
            UploadedFile uploadedFile = uploadedFileRepository.findByIdAndDeletedFalse(uploaded.id())
                    .orElseThrow(() -> RestException.notFound("Uploaded file not found: " + uploaded.id()));
            uploadedFiles.add(uploadedFile);
        }
        return uploadedFiles;
    }

    private void validateEquipmentDocumentFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw RestException.badRequest("At least one equipment document file is required");
        }
    }

    private void validateEquipmentDocumentFileLimit(int fileCount) {
        if (fileCount > MAX_EQUIPMENT_DOCUMENT_FILES) {
            throw RestException.badRequest("An equipment document cannot contain more than 25 files");
        }
    }

    private List<EquipmentDocumentFile> activeDocumentFileLinks(EquipmentDocument document) {
        if (document.getFiles() == null || document.getFiles().isEmpty()) {
            return List.of();
        }
        return document.getFiles().stream()
                .filter(link -> link.getFile() != null && !Boolean.TRUE.equals(link.getFile().getDeleted()))
                .sorted(Comparator.comparing(
                        EquipmentDocumentFile::getSortOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();
    }

    private List<UUID> activeDocumentFileIds(EquipmentDocument document) {
        LinkedHashSet<UUID> fileIds = activeDocumentFileLinks(document).stream()
                .map(EquipmentDocumentFile::getFile)
                .map(UploadedFile::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (document.getFile() != null && !Boolean.TRUE.equals(document.getFile().getDeleted())) {
            fileIds.add(document.getFile().getId());
        }
        return List.copyOf(fileIds);
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

    private String normalizeDocumentNumber(String documentNumber, int index) {
        if (documentNumber == null || documentNumber.isBlank()) {
            return null;
        }
        String trimmed = documentNumber.trim();
        if (trimmed.length() > 128) {
            throw RestException.badRequest("documentNumbers[" + index + "] must be 128 characters or fewer");
        }
        return trimmed;
    }

    private List<String> normalizeDocumentTypes(List<MultipartFile> files, List<String> documentTypes) {
        if (documentTypes == null || documentTypes.isEmpty()) {
            return Collections.nCopies(files.size(), null);
        }
        if (documentTypes.size() != files.size()) {
            throw RestException.badRequest("files and documentTypes must have the same length");
        }
        List<String> normalized = new ArrayList<>(documentTypes.size());
        for (int i = 0; i < documentTypes.size(); i++) {
            String normalizedType = normalizeDocumentType(documentTypes.get(i));
            if (normalizedType == null) {
                throw RestException.badRequest("documentTypes[" + i + "] must not be blank");
            }
            normalized.add(normalizedType);
        }
        return normalized;
    }

    private List<String> normalizeDocumentNumbers(List<MultipartFile> files, List<String> documentNumbers) {
        if (documentNumbers == null || documentNumbers.isEmpty()) {
            return Collections.nCopies(files.size(), null);
        }
        if (documentNumbers.size() != files.size()) {
            throw RestException.badRequest("files and documentNumbers must have the same length");
        }
        List<String> normalized = new ArrayList<>(documentNumbers.size());
        for (int i = 0; i < documentNumbers.size(); i++) {
            normalized.add(normalizeDocumentNumber(documentNumbers.get(i), i));
        }
        return normalized;
    }

    private String normalizeDocumentName(String documentName) {
        if (documentName == null || documentName.isBlank()) {
            throw RestException.badRequest("documentName is required for equipment document uploads");
        }
        String trimmed = documentName.trim();
        if (trimmed.length() > 255) {
            throw RestException.badRequest("documentName must be 255 characters or fewer");
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

    private void validateDepartmentExists(UUID departmentId) {
        if (departmentId == null) {
            return;
        }
        departmentRepository.findByIdAndIsDeletedFalse(departmentId)
                .orElseThrow(() -> RestException.notFound("Department not found: " + departmentId));
    }

    private void validateResponsibleEmployee(UUID responsibleId) {
        if (responsibleId == null) {
            return;
        }
        Employee employee = employeeRepository.findByIdAndIsDeletedFalse(responsibleId)
                .orElseThrow(() -> RestException.badRequest("Responsible employee not found: " + responsibleId));
        if (!employee.isActive()) {
            throw RestException.badRequest("Responsible employee is not active: " + responsibleId);
        }
    }

    private void validateMxik(UUID mxikId) {
        if (mxikId == null) {
            return;
        }
        mxikRepository.findByIdAndIsDeletedFalse(mxikId)
                .orElseThrow(() -> RestException.notFound("MXIK not found: " + mxikId));
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
        applyResponsibleUpdate(entity, request);
        entity.setName(request.name() != null ? request.name() : entity.getName());
        entity.setInventoryNumber(request.inventoryNumber()  != null ? request.inventoryNumber() : entity.getInventoryNumber());
        entity.setTechnicalNumber(request.technicalNumber() != null ? request.technicalNumber() : entity.getTechnicalNumber());
        entity.setSerialNumber(request.serialNumber()  != null ? request.serialNumber() : entity.getSerialNumber());
        entity.setModel(request.model() != null ? request.model() : entity.getModel());
        entity.setProducedYear(request.producedYear() != null ? request.producedYear() : entity.getProducedYear());
        entity.setEquipmentTypeId(request.equipmentTypeId() != null ? request.equipmentTypeId() : entity.getEquipmentTypeId());
        entity.setMxikId(request.mxikId() != null ? request.mxikId() : entity.getMxikId());
        entity.setDepartmentId(request.departmentId() != null ? request.departmentId() : entity.getDepartmentId());
        entity.setLocationId(request.locationId() != null ? request.locationId() : entity.getLocationId());
        entity.setParentId(request.parentId());
        entity.setCriticalityClassId(request.criticalityClassId()  != null ? request.criticalityClassId() : entity.getCriticalityClassId());
        entity.setCounteragentId(request.counteragentId() != null ? request.counteragentId() : entity.getCounteragentId());
        entity.setManufacturer(request.manufacturer() != null ? request.manufacturer() : entity.getManufacturer());
        entity.setCategory(request.category() != null ? request.category() : entity.getCategory());
        entity.setCommissionedAt(request.commissionedAt() != null ? request.commissionedAt() : entity.getCommissionedAt());
        entity.setArrivalDate(request.arrivalDate() != null ? request.arrivalDate() : entity.getArrivalDate());
        entity.setWarrantyUntil(request.warrantyUntil() != null ? request.warrantyUntil() : entity.getWarrantyUntil());
        applyWarrantyForUpdate(entity, request);
        entity.setOperationStartDate(request.operationStartDate() != null
                ? request.operationStartDate()
                : entity.getOperationStartDate());
        Integer expectedLifetimeMonths = request.expectedLifetimeMonths() != null
                ? request.expectedLifetimeMonths()
                : entity.getExpectedLifetimeMonths();
        Integer expectedLifetimeYears = request.expectedLifetimeYears() != null
                ? request.expectedLifetimeYears()
                : entity.getExpectedLifetimeYears();
        Long expectedLifetimeHours = request.expectedLifetimeHours() != null
                ? request.expectedLifetimeHours()
                : entity.getExpectedLifetimeHours();
        entity.setExpectedLifetimeMonths(expectedLifetimeMonths);
        entity.setExpectedLifetimeYears(expectedLifetimeYears);
        entity.setExpectedLifetimeHours(expectedLifetimeHours);
        if (request.averageDailyUsage() != null) {
            entity.setAverageDailyUsage(request.averageDailyUsage());
        }
        applyDynamicLifetimeForUpdate(entity, request);
        entity.setDaysOfResourceRemaining(null);
        if (hasExpectedLifetimeChange(request)) {
            entity.setAverageOperatingLifeHours(calculateAverageOperatingLifeHours(
                    expectedLifetimeYears,
                    expectedLifetimeMonths,
                    expectedLifetimeHours,
                    effectiveLifetimeCounterType(entity),
                    entity.getLifetimeLimitValue()
            ));
        }
        entity.setDescription(request.description() != null ? request.description() : entity.getDescription());
    }

    private void applyResponsibleUpdate(Equipment entity, EquipmentUpdateRequest request) {
        if (Boolean.TRUE.equals(request.clearResponsible())) {
            throw RestException.badRequest("EQUIPMENT_REQUIRED_FIELDS_MISSING: responsibleId");
        } else if (request.responsibleId() != null) {
            validateResponsibleEmployee(request.responsibleId());
            entity.setResponsibleId(request.responsibleId());
        }
    }

    private boolean hasExpectedLifetimeChange(EquipmentUpdateRequest request) {
        return request.expectedLifetimeYears() != null
                || request.expectedLifetimeMonths() != null
                || request.expectedLifetimeHours() != null
                || request.lifetimeCounterType() != null
                || request.lifetimeMeterId() != null
                || request.lifetimeLimitValue() != null
                || request.lifetimeBaselineValue() != null
                || request.lifetimeWarningPercent() != null;
    }

    public Long calculateDaysOfResourceRemaining(Double remainingValue, Double averageDailyUsage) {
        return EquipmentLifetimeCalculator.remainingDays(remainingValue, averageDailyUsage);
    }

    private Long calculateAverageOperatingLifeHours(
            Integer expectedLifetimeYears,
            Integer expectedLifetimeMonths,
            Long expectedLifetimeHours,
            MeterType lifetimeCounterType,
            Double lifetimeLimitValue
    ) {
        long yearsHours = expectedLifetimeYears == null ? 0 : expectedLifetimeYears * 365L * 24L;
        long monthsHours = expectedLifetimeMonths == null ? 0 : expectedLifetimeMonths * 30L * 24L;
        long directHours = expectedLifetimeHours == null ? 0 : expectedLifetimeHours;
        long total = yearsHours + monthsHours + directHours;
        if (total > 0) {
            return total;
        }
        if (lifetimeCounterType == MeterType.ENGINE_HOURS
                && lifetimeLimitValue != null
                && lifetimeLimitValue > 0) {
            return Math.round(lifetimeLimitValue);
        }
        if (lifetimeCounterType != null && lifetimeLimitValue != null && lifetimeLimitValue > 0) {
            return null;
        }
        throw RestException.badRequest("Expected lifetime must be specified and greater than zero");
    }

    private void applyDynamicLifetimeForCreate(Equipment entity, EquipmentCreateRequest request) {
        MeterType counterType = request.lifetimeCounterType();
        Double limitValue = request.lifetimeLimitValue();
        UUID meterId = request.lifetimeMeterId();
        Double baselineValue = request.lifetimeBaselineValue();
        Double warningPercent = request.lifetimeWarningPercent();

        if (counterType == null && limitValue == null
                && request.expectedLifetimeHours() != null
                && request.expectedLifetimeHours() > 0) {
            counterType = MeterType.ENGINE_HOURS;
            limitValue = request.expectedLifetimeHours().doubleValue();
        }
        validateDynamicLifetimeConfig(counterType, meterId, limitValue);
        entity.setLifetimeCounterType(counterType);
        entity.setLifetimeMeterId(meterId);
        entity.setLifetimeLimitValue(limitValue);
        entity.setLifetimeBaselineValue(limitValue == null ? baselineValue : defaultIfNull(baselineValue, 0.0));
        entity.setLifetimeWarningPercent(limitValue == null ? warningPercent : defaultIfNull(warningPercent, 10.0));
    }

    private void applyDynamicLifetimeForUpdate(Equipment entity, EquipmentUpdateRequest request) {
        boolean hasDynamicLifetimeRequest = request.lifetimeCounterType() != null
                || request.lifetimeMeterId() != null
                || request.lifetimeLimitValue() != null
                || request.lifetimeBaselineValue() != null
                || request.lifetimeWarningPercent() != null;
        if (hasDynamicLifetimeRequest) {
            MeterType counterType = request.lifetimeCounterType() != null
                    ? request.lifetimeCounterType()
                    : entity.getLifetimeCounterType();
            UUID meterId = request.lifetimeMeterId() != null
                    ? request.lifetimeMeterId()
                    : entity.getLifetimeMeterId();
            Double limitValue = request.lifetimeLimitValue() != null
                    ? request.lifetimeLimitValue()
                    : entity.getLifetimeLimitValue();
            validateDynamicLifetimeConfig(counterType, meterId, limitValue);
            entity.setLifetimeCounterType(counterType);
            entity.setLifetimeMeterId(meterId);
            entity.setLifetimeLimitValue(limitValue);
            entity.setLifetimeBaselineValue(request.lifetimeBaselineValue() != null
                    ? request.lifetimeBaselineValue()
                    : entity.getLifetimeBaselineValue());
            entity.setLifetimeWarningPercent(request.lifetimeWarningPercent() != null
                    ? request.lifetimeWarningPercent()
                    : entity.getLifetimeWarningPercent());
            if (limitValue != null) {
                entity.setLifetimeBaselineValue(defaultIfNull(entity.getLifetimeBaselineValue(), 0.0));
                entity.setLifetimeWarningPercent(defaultIfNull(entity.getLifetimeWarningPercent(), 10.0));
            }
        } else if (request.expectedLifetimeHours() != null
                && request.expectedLifetimeHours() > 0
                && entity.getLifetimeCounterType() == null
                && entity.getLifetimeLimitValue() == null) {
            entity.setLifetimeCounterType(MeterType.ENGINE_HOURS);
            entity.setLifetimeLimitValue(request.expectedLifetimeHours().doubleValue());
            entity.setLifetimeBaselineValue(defaultIfNull(entity.getLifetimeBaselineValue(), 0.0));
            entity.setLifetimeWarningPercent(defaultIfNull(entity.getLifetimeWarningPercent(), 10.0));
        }
    }

    private void validateDynamicLifetimeConfig(MeterType counterType, UUID meterId, Double limitValue) {
        boolean hasConfig = counterType != null || meterId != null || limitValue != null;
        if (!hasConfig) {
            return;
        }
        if (counterType == null || limitValue == null || limitValue <= 0) {
            throw RestException.badRequest("Dynamic lifetime must include counter type and positive limit value");
        }
    }

    private Double defaultIfNull(Double value, double fallback) {
        return value != null ? value : fallback;
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

    private void validateCounteragentReferences(EquipmentCreateRequest request) {
        if (request.counteragentId() != null) {
            loadActiveEquipmentCounteragent(request.counteragentId(), "equipment counteragent");
        }
        UUID warrantyCounteragentId = effectiveWarrantyCounteragentId(request);
        if (warrantyCounteragentId != null && !warrantyCounteragentId.equals(request.counteragentId())) {
            loadActiveEquipmentCounteragent(warrantyCounteragentId, "equipment warranty counteragent");
        }
    }

    private void validateCounteragentReferencesForUpdate(Equipment entity, EquipmentUpdateRequest request) {
        if (request.counteragentId() != null) {
            loadActiveEquipmentCounteragent(request.counteragentId(), "equipment counteragent");
        }
        UUID warrantyCounteragentId = effectiveWarrantyCounteragentIdForUpdate(entity, request);
        if (warrantyCounteragentId != null
                && !warrantyCounteragentId.equals(request.counteragentId())) {
            loadActiveEquipmentCounteragent(warrantyCounteragentId, "equipment warranty counteragent");
        }
    }

    private UUID effectiveWarrantyCounteragentId(EquipmentCreateRequest request) {
        if (!Boolean.TRUE.equals(request.hasWarranty())) {
            return null;
        }
        UUID counteragentId = request.warrantyCounteragentId() != null
                ? request.warrantyCounteragentId()
                : request.counteragentId();
        if (counteragentId == null) {
            throw RestException.badRequest("Warranty counteragent is required when warranty is enabled");
        }
        return counteragentId;
    }

    private UUID effectiveWarrantyCounteragentIdForUpdate(Equipment entity, EquipmentUpdateRequest request) {
        if (Boolean.FALSE.equals(request.hasWarranty())) {
            return null;
        }
        boolean warrantyEnabled = Boolean.TRUE.equals(request.hasWarranty())
                || (request.hasWarranty() == null && Boolean.TRUE.equals(entity.getHasWarranty()));
        if (!warrantyEnabled) {
            return null;
        }
        UUID counteragentId = firstNonNull(
                request.warrantyCounteragentId(),
                entity.getWarrantyCounteragentId(),
                request.counteragentId(),
                entity.getCounteragentId()
        );
        if (counteragentId == null) {
            throw RestException.badRequest("Warranty counteragent is required when warranty is enabled");
        }
        return counteragentId;
    }

    private Counteragent loadActiveEquipmentCounteragent(UUID counteragentId, String context) {
        return counteragentService.loadActive(counteragentId, context);
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
            entity.setWarrantyCounteragentId(null);
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
        if (request.warrantyCounteragentId() != null) {
            entity.setWarrantyCounteragentId(request.warrantyCounteragentId());
        } else if (Boolean.TRUE.equals(entity.getHasWarranty()) && entity.getWarrantyCounteragentId() == null) {
            entity.setWarrantyCounteragentId(entity.getCounteragentId());
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
                EquipmentStatus.STANDBY
        );

        return new EquipmentStatsResponse(
                safe(stats.getTotalInRegistry()),
                safe(stats.getActive()),
                safe(stats.getInRepair()),
                safe(stats.getReserved())
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
