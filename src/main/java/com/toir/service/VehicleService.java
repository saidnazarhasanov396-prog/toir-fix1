package com.toir.service;

import com.toir.dto.attachment.AttachmentGroupDto;
import com.toir.dto.equipment.EquipmentDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueDto;
import com.toir.dto.file.PresignedUrlResponse;
import com.toir.dto.file.UploadFileResponse;
import com.toir.dto.vehicle.VehicleDetailDto;
import com.toir.dto.vehicle.VehicleDocumentDto;
import com.toir.dto.vehicle.VehicleRequest;
import com.toir.dto.vehicle.VehicleStatsResponse;
import com.toir.dto.vehicle.VehicleSummaryDto;
import com.toir.entity.UploadedFile;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentLocationHistory;
import com.toir.entity.equipment.VehicleDocument;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.entity.users.Employee;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.AttachmentTargetType;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.FileCategory;
import com.toir.enums.MeterType;
import com.toir.enums.VehicleRegistrationPlateType;
import com.toir.exception.RestException;
import com.toir.repository.UploadedFileRepository;
import com.toir.repository.MxikRepository;
import com.toir.repository.VehicleDocumentRepository;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.equipment.EquipmentLocationHistoryRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.projection.VehicleStatsProjection;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.EmployeeWorkRoleAssignmentRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.security.SecurityScope;
import com.toir.service.equipment.EquipmentAttributeService;
import com.toir.service.equipment.EquipmentManualAttributeService;
import com.toir.service.equipment.EquipmentService;
import com.toir.service.attachment.AttachmentGroupService;
import com.toir.service.file_management.FileService;
import com.toir.service.sparepartlifecycle.VehicleMeterProjectionGuard;
import com.toir.util.AuditBuilderService;
import com.toir.util.CodeGenerationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VehicleService {

    private final EquipmentRepository equipmentRepository;
    private final EquipmentLocationHistoryRepository equipmentLocationHistoryRepository;
    private final VehicleDetailsRepository vehicleDetailsRepository;
    private final MxikRepository mxikRepository;
    private final EquipmentService equipmentService;
    private final AuditBuilderService auditBuilderService;
    private final FileService fileService;
    private final UploadedFileRepository uploadedFileRepository;
    private final SecurityScope securityScope;
    private final EquipmentAttributeService equipmentAttributeService;
    private final EquipmentManualAttributeService equipmentManualAttributeService;
    private final VehicleDocumentRepository vehicleDocumentRepository;
    private final AttachmentGroupService attachmentGroupService;
    private final EmployeeRepository employeeRepository;
    private final EmployeeWorkRoleAssignmentRepository employeeWorkRoleAssignmentRepository;
    private final VehicleMeterProjectionGuard vehicleMeterProjectionGuard;

    private static final Set<String> DTO_SORT_FIELDS = Set.of(
            "status",
            "vehicleType",
            "insuranceExpiryDate",
            "technicalInspectionExpiryDate",
            "assignedDriverUsageLimitMinutes",
            "manufactureYear",
            "averageDailyUsage",
            "lifetimeLimitValue",
            "lifetimeBaselineValue",
            "lifetimeWarningPercent",
            "currentOdometerKm",
            "currentEngineHours"
    );

    @Value("${toir.vehicle.driver-role-required:false}")
    private boolean driverRoleRequired;

    @Transactional(readOnly = true)
    public Page<VehicleSummaryDto> list(UUID departmentId, EquipmentStatus status, VehicleRegistrationPlateType plateType,
                                        String search, int page, int pageSize) {
        return list(departmentId, status, plateType, search, page, pageSize, null, "asc");
    }

    @Transactional(readOnly = true)
    public Page<VehicleSummaryDto> list(UUID departmentId, EquipmentStatus status, VehicleRegistrationPlateType plateType,
                                        UUID mxikId, String search, int page, int pageSize) {
        return list(departmentId, status, plateType, mxikId, search, page, pageSize, null, "asc");
    }

    @Transactional(readOnly = true)
    public Page<VehicleSummaryDto> list(UUID departmentId, EquipmentStatus status, VehicleRegistrationPlateType plateType,
                                        String search, int page, int pageSize, String sortBy, String sortDir) {
        return list(departmentId, status, plateType, null, search, page, pageSize, sortBy, sortDir);
    }

    @Transactional(readOnly = true)
    public Page<VehicleSummaryDto> list(UUID departmentId, EquipmentStatus status, VehicleRegistrationPlateType plateType,
                                        UUID mxikId, String search, int page, int pageSize, String sortBy, String sortDir) {
        int safePage = Math.max(page, 0);
        int safePageSize = Math.max(pageSize, 1);
        boolean dtoSort = isDtoSort(sortBy);
        Pageable pageable = dtoSort
                ? Pageable.unpaged()
                : org.springframework.data.domain.PageRequest.of(safePage, safePageSize);
        Page<Equipment> equipmentPage = mxikId == null
                ? vehicleDetailsRepository.searchVehicleEquipment(
                        departmentId,
                        status,
                        plateType,
                        EquipmentCategory.VEHICLE,
                        search,
                        pageable
                )
                : vehicleDetailsRepository.searchVehicleEquipmentWithMxik(
                        departmentId,
                        status,
                        plateType,
                        EquipmentCategory.VEHICLE,
                        mxikId,
                        search,
                        pageable
                );
        Page<EquipmentDto> enrichedEquipmentPage = equipmentService.enrich(equipmentPage);
        Map<UUID, VehicleDetails> detailsByEquipment = vehicleDetailsRepository
                .findAllByEquipmentIdInAndIsDeletedFalse(enrichedEquipmentPage.getContent().stream().map(EquipmentDto::id).toList())
                .stream()
                .collect(Collectors.toMap(VehicleDetails::getEquipmentId, Function.identity(), (a, b) -> a));
        List<VehicleSummaryDto> items = enrichedEquipmentPage.getContent().stream()
                .map(equipment -> {
                    VehicleDetails details = detailsByEquipment.get(equipment.id());
                    return details == null ? null : VehicleSummaryDto.from(equipment, details);
                })
                .filter(Objects::nonNull)
                .toList();
        if (dtoSort) {
            List<VehicleSummaryDto> sorted = items.stream()
                    .sorted(vehicleComparator(sortBy, sortDir))
                    .toList();
            return com.toir.util.PaginationUtils.page(sorted, safePage, safePageSize);
        }
        return new FixedTotalPage<>(items, enrichedEquipmentPage.getPageable(), enrichedEquipmentPage.getTotalElements());
    }

    private boolean isDtoSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return false;
        }
        return DTO_SORT_FIELDS.contains(sortBy.trim());
    }

    private Comparator<VehicleSummaryDto> vehicleComparator(String sortBy, String sortDir) {
        Comparator<VehicleSummaryDto> comparator = switch (sortBy.trim()) {
            case "status" -> nullableComparator(VehicleSummaryDto::status);
            case "vehicleType" -> nullableComparator(VehicleSummaryDto::vehicleType);
            case "insuranceExpiryDate" -> nullableComparator(VehicleSummaryDto::insuranceExpiryDate);
            case "technicalInspectionExpiryDate" -> nullableComparator(VehicleSummaryDto::technicalInspectionExpiryDate);
            case "assignedDriverUsageLimitMinutes" -> nullableComparator(VehicleSummaryDto::assignedDriverUsageLimitMinutes);
            case "manufactureYear" -> nullableComparator(VehicleSummaryDto::manufactureYear);
            case "averageDailyUsage" -> nullableComparator(VehicleSummaryDto::averageDailyUsage);
            case "lifetimeLimitValue" -> nullableComparator(VehicleSummaryDto::lifetimeLimitValue);
            case "lifetimeBaselineValue" -> nullableComparator(VehicleSummaryDto::lifetimeBaselineValue);
            case "lifetimeWarningPercent" -> nullableComparator(VehicleSummaryDto::lifetimeWarningPercent);
            case "currentOdometerKm" -> Comparator.comparingDouble(VehicleSummaryDto::currentOdometerKm);
            case "currentEngineHours" -> Comparator.comparingDouble(VehicleSummaryDto::currentEngineHours);
            default -> throw RestException.badRequest("Unsupported vehicle sort: " + sortBy);
        };
        return "desc".equalsIgnoreCase(sortDir) ? comparator.reversed() : comparator;
    }

    private static <T, U extends Comparable<? super U>> Comparator<T> nullableComparator(Function<T, U> extractor) {
        return Comparator.comparing(extractor, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    @Transactional(readOnly = true)
    public VehicleStatsResponse getStats(UUID departmentId, String search) {
        String searchPattern = toSearchPattern(search);

        VehicleStatsProjection stats = vehicleDetailsRepository.getVehicleStats(
                departmentId,
                EquipmentCategory.VEHICLE,
                searchPattern,
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.OUT_OF_SERVICE
        );

        return new VehicleStatsResponse(
                safe(stats.getTotal()),
                safe(stats.getActive()),
                safe(stats.getInRepair()),
                safe(stats.getOutOfService())
        );
    }


    @Transactional(readOnly = true)
    public VehicleDetailDto findByEquipmentId(UUID equipmentId) {
        EquipmentDto equipment = equipmentService.findById(equipmentId);
        if (equipment.category() != EquipmentCategory.VEHICLE) {
            throw RestException.badRequest("Equipment is not a vehicle: " + equipmentId);
        }
        VehicleDetails details = vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Vehicle details not found: " + equipmentId));
        return VehicleDetailDto.from(
                equipment,
                details,
                vehicleDocumentRepository.findAllByEquipmentId(equipmentId),
                officialAttributes(equipmentId),
                equipmentManualAttributeService == null ? List.of() : equipmentManualAttributeService.list(equipmentId)
        );
    }

    @Transactional
    public VehicleDetailDto create(VehicleRequest request) {
        CodeGenerationUtils.rejectClientProvidedCode(request.code());
        validateUniqueCreate(request);
        validateMxik(request.mxikId());
        Equipment equipment = new Equipment();
        equipment.setCode(nextEquipmentCode());
        applyEquipment(equipment, request);
        validateAssignedDriver(request.assignedDriverId(), request.assignedDriverUsageLimitMinutes(), request.departmentId(), null);
        Equipment savedEquipment = equipmentRepository.save(equipment);
        writeInitialLocationHistory(savedEquipment);

        VehicleDetails details = new VehicleDetails();
        details.setEquipmentId(savedEquipment.getId());
        applyDetails(details, request);
        VehicleDetails savedDetails = vehicleDetailsRepository.save(details);
        if (equipmentAttributeService != null) {
            equipmentAttributeService.upsertValues(
                    savedEquipment,
                    request.attributes() == null ? List.of() : request.attributes()
            );
        }
        if (equipmentManualAttributeService != null && request.manualAttributes() != null && !request.manualAttributes().isEmpty()) {
            equipmentManualAttributeService.replaceAll(
                    savedEquipment.getId(),
                    new com.toir.dto.equipmentmanualattribute.BulkEquipmentManualAttributeRequest(request.manualAttributes())
            );
        }

        auditBuilderService.log(
                "vehicle",
                savedEquipment.getId().toString(),
                AuditAction.CREATE,
                AuditModule.VEHICLE,
                "Транспорт создан",
                null,
                Map.of(equipment, savedDetails)
        );

        return VehicleDetailDto.from(
                equipmentService.findById(savedEquipment.getId()),
                savedDetails,
                List.of(),
                officialAttributes(savedEquipment.getId()),
                equipmentManualAttributeService == null ? List.of() : equipmentManualAttributeService.list(savedEquipment.getId())
        );
    }

    @Transactional
    public VehicleDetailDto update(UUID equipmentId, VehicleRequest request) {
        CodeGenerationUtils.rejectClientProvidedCode(request.code());
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        if (equipment.getCategory() != EquipmentCategory.VEHICLE) {
            throw RestException.badRequest("Equipment is not a vehicle: " + equipmentId);
        }
        VehicleDetails details = vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Vehicle details not found: " + equipmentId));

        validateUniqueUpdate(equipmentId, equipment, details, request);
        validateMxik(request.mxikId());
        boolean equipmentTypeChanged = isEquipmentTypeChanged(equipment.getEquipmentTypeId(), request.equipmentTypeId());
        validateAttributesForTypeChange(equipmentTypeChanged, request.attributes());
        validateAssignedDriver(request.assignedDriverId(), request.assignedDriverUsageLimitMinutes(), request.departmentId(), equipmentId);
        vehicleMeterProjectionGuard.assertCompatibleUpdate(
                equipmentId,
                details,
                request.currentOdometerKm(),
                request.currentEngineHours()
        );
        VehicleLocationSnapshot fromLocation = vehicleLocationSnapshot(equipment);
        applyEquipment(equipment, request);
        applyDetails(details, request);

        Equipment newEquipment = equipmentRepository.save(equipment);
        writeLocationHistoryIfChanged(
                newEquipment,
                fromLocation,
                vehicleLocationSnapshot(newEquipment),
                "Vehicle department updated"
        );
        VehicleDetails newDetails = vehicleDetailsRepository.save(details);
        if (equipmentAttributeService != null && request.attributes() != null) {
            equipmentAttributeService.upsertValues(newEquipment, request.attributes());
        }
        if (equipmentManualAttributeService != null && request.manualAttributes() != null && !request.manualAttributes().isEmpty()) {
            equipmentManualAttributeService.replaceAll(
                    newEquipment.getId(),
                    new com.toir.dto.equipmentmanualattribute.BulkEquipmentManualAttributeRequest(request.manualAttributes())
            );
        }

        auditBuilderService.log(
                "vehicle",
                newEquipment.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.VEHICLE,
                "Транспорт обновлен",
                Map.of(equipment, details),
                Map.of(newEquipment, newDetails)
        );

        return VehicleDetailDto.from(
                equipmentService.findById(equipmentId),
                details,
                vehicleDocumentRepository.findAllByEquipmentId(equipmentId),
                officialAttributes(equipmentId),
                equipmentManualAttributeService == null ? List.of() : equipmentManualAttributeService.list(equipmentId)
        );
    }

    @Transactional
    public VehicleDetailDto attachDocument(UUID equipmentId, MultipartFile document, UUID currentUserId) {
        attachDocuments(equipmentId, List.of(document), List.of(legacyDocumentName(document)), null, null, authenticatedUser(currentUserId));
        return findByEquipmentId(equipmentId);
    }

    private static final Set<String> ALLOWED_VEHICLE_DOCUMENT_CONTENT_TYPES = Set.of(
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
    public List<VehicleDocumentDto> attachDocuments(
            UUID equipmentId,
            List<MultipartFile> files,
            List<String> documentNames,
            List<String> documentTypes,
            List<String> documentNumbers,
            AuthenticatedUser user
    ) {
        Equipment equipment = findVehicleEquipment(equipmentId);
        enforceVehicleAccess(equipment);
        if (files == null || files.isEmpty()) {
            throw RestException.badRequest("At least one vehicle document file is required");
        }
        List<String> normalizedDocumentNames = normalizeDocumentNames(files, documentNames);
        List<String> normalizedDocumentTypes = normalizeDocumentTypes(files, documentTypes);
        List<String> normalizedDocumentNumbers = normalizeDocumentNumbers(files, documentNumbers);
        List<VehicleDocumentDto> result = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            AttachmentGroupDto group = attachmentGroupService.createGroup(
                    normalizedDocumentNames.get(i),
                    null,
                    "VEHICLE",
                    equipmentId,
                    normalizedDocumentTypes.get(i),
                    normalizedDocumentNumbers.get(i),
                    List.of(files.get(i)),
                    null,
                    user
            );
            VehicleDocumentDto dto = VehicleDocumentDto.fromAttachmentGroup(equipmentId, group);
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<VehicleDocumentDto> getDocuments(UUID equipmentId, AuthenticatedUser user) {
        Equipment equipment = findVehicleEquipment(equipmentId);
        enforceVehicleAccess(equipment);
        return attachmentGroupService.listGroups("VEHICLE", equipmentId, user)
                .stream()
                .map(group -> VehicleDocumentDto.fromAttachmentGroup(equipmentId, group))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleDocumentDto getDocument(UUID equipmentId, UUID documentId, AuthenticatedUser user) {
        Equipment equipment = findVehicleEquipment(equipmentId);
        enforceVehicleAccess(equipment);
        return VehicleDocumentDto.fromAttachmentGroup(equipmentId, attachmentGroupService.getGroup(documentId, user));
    }

    @Transactional(readOnly = true)
    public VehicleDocumentDto getDocument(UUID equipmentId, UUID currentUserId) {
        Equipment equipment = findVehicleEquipment(equipmentId);
        enforceVehicleAccess(equipment);
        AuthenticatedUser user = authenticatedUser(currentUserId);
        List<AttachmentGroupDto> groups = attachmentGroupService.listGroups("VEHICLE", equipmentId, user);
        if (!groups.isEmpty()) {
            return VehicleDocumentDto.fromAttachmentGroup(equipmentId, groups.getFirst());
        }
        VehicleDetails details = findVehicleDetails(equipmentId);
        UploadedFile documentFile = details.getDocumentFile();
        if (documentFile == null || Boolean.TRUE.equals(documentFile.getDeleted())) {
            throw RestException.notFound("Vehicle document not found: " + equipmentId);
        }
        fileService.getMetadata(documentFile.getId(), currentUserId);
        return legacyDocumentDto(equipmentId, documentFile);
    }

    @Transactional(readOnly = true)
    public PresignedUrlResponse getDocumentPresignedUrl(UUID equipmentId, UUID documentId, AuthenticatedUser user) {
        Equipment equipment = findVehicleEquipment(equipmentId);
        enforceVehicleAccess(equipment);
        AttachmentGroupDto group = attachmentGroupService.getGroup(documentId, user);
        return attachmentGroupService.getFilePresignedUrl(documentId, primaryFile(group).fileId(), user);
    }

    @Transactional(readOnly = true)
    public Resource downloadDocument(UUID equipmentId, UUID documentId, AuthenticatedUser user) {
        Equipment equipment = findVehicleEquipment(equipmentId);
        enforceVehicleAccess(equipment);
        AttachmentGroupDto group = attachmentGroupService.getGroup(documentId, user);
        return attachmentGroupService.downloadFile(documentId, primaryFile(group).fileId(), user);
    }

    @Transactional(readOnly = true)
    public PresignedUrlResponse getDocumentPresignedUrl(UUID equipmentId, UUID currentUserId) {
        Equipment equipment = findVehicleEquipment(equipmentId);
        enforceVehicleAccess(equipment);
        AuthenticatedUser user = authenticatedUser(currentUserId);
        List<AttachmentGroupDto> groups = attachmentGroupService.listGroups("VEHICLE", equipmentId, user);
        if (!groups.isEmpty()) {
            AttachmentGroupDto group = groups.getFirst();
            return attachmentGroupService.getFilePresignedUrl(group.id(), primaryFile(group).fileId(), user);
        }
        VehicleDetails details = findVehicleDetails(equipmentId);
        UploadedFile documentFile = details.getDocumentFile();
        if (documentFile == null || Boolean.TRUE.equals(documentFile.getDeleted())) {
            throw RestException.notFound("Vehicle document not found: " + equipmentId);
        }
        return fileService.getPresignedUrl(documentFile.getId(), currentUserId);
    }

    @Transactional(readOnly = true)
    public Resource downloadDocument(UUID equipmentId, UUID currentUserId) {
        Equipment equipment = findVehicleEquipment(equipmentId);
        enforceVehicleAccess(equipment);
        AuthenticatedUser user = authenticatedUser(currentUserId);
        List<AttachmentGroupDto> groups = attachmentGroupService.listGroups("VEHICLE", equipmentId, user);
        if (!groups.isEmpty()) {
            AttachmentGroupDto group = groups.getFirst();
            return attachmentGroupService.downloadFile(group.id(), primaryFile(group).fileId(), user);
        }
        VehicleDetails details = findVehicleDetails(equipmentId);
        UploadedFile documentFile = details.getDocumentFile();
        if (documentFile == null || Boolean.TRUE.equals(documentFile.getDeleted())) {
            throw RestException.notFound("Vehicle document not found: " + equipmentId);
        }
        return fileService.download(documentFile.getId(), currentUserId);
    }

    @Transactional
    public void deleteDocument(UUID equipmentId, UUID documentId, AuthenticatedUser user) {
        Equipment equipment = findVehicleEquipment(equipmentId);
        enforceVehicleAccess(equipment);
        attachmentGroupService.deleteGroup(documentId, user);
    }

    @Transactional
    public void deleteDocument(UUID equipmentId, UUID currentUserId) {
        Equipment equipment = findVehicleEquipment(equipmentId);
        enforceVehicleAccess(equipment);
        AuthenticatedUser user = authenticatedUser(currentUserId);
        List<AttachmentGroupDto> groups = attachmentGroupService.listGroups("VEHICLE", equipmentId, user);
        if (!groups.isEmpty()) {
            attachmentGroupService.deleteGroup(groups.getFirst().id(), user);
            return;
        }
        VehicleDetails details = findVehicleDetails(equipmentId);
        UploadedFile documentFile = details.getDocumentFile();
        if (documentFile == null || Boolean.TRUE.equals(documentFile.getDeleted())) {
            throw RestException.notFound("Vehicle document not found: " + equipmentId);
        }
        details.setDocumentFile(null);
        vehicleDetailsRepository.save(details);
        fileService.delete(documentFile.getId(), currentUserId);
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
            log.warn("Failed to cleanup vehicle document file '{}': {}", fileId, e.getMessage());
        }
    }

    private void enforceVehicleAccess(Equipment equipment) {
        if (securityScope == null || securityScope.isAdmin()) {
            return;
        }
        AuthenticatedUser user = securityScope.currentUser();
        if (user == null || user.departmentId() == null || user.departmentId().isBlank()) {
            return;
        }
        UUID userDepartmentId;
        try {
            userDepartmentId = UUID.fromString(user.departmentId());
        } catch (IllegalArgumentException e) {
            throw RestException.forbidden("Vehicle access denied");
        }
        if (!Objects.equals(equipment.getDepartmentId(), userDepartmentId)) {
            throw RestException.forbidden("Vehicle access denied");
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

    private void validateMxik(UUID mxikId) {
        if (mxikId == null) {
            return;
        }
        mxikRepository.findByIdAndIsDeletedFalse(mxikId)
                .orElseThrow(() -> RestException.notFound("MXIK not found: " + mxikId));
    }

    private List<EquipmentAttributeValueDto> officialAttributes(UUID equipmentId) {
        if (equipmentAttributeService == null) {
            return List.of();
        }
        List<EquipmentAttributeValueDto> attributes = equipmentAttributeService.findValues(equipmentId);
        return attributes == null ? List.of() : List.copyOf(attributes);
    }

    @Transactional
    public void delete(UUID equipmentId) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        VehicleDetails details = vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Vehicle details not found: " + equipmentId));
        details.setDeleted(true);
        equipment.setDeleted(true);

        Equipment saved = equipmentRepository.save(equipment);

        auditBuilderService.log(
                "vehicle",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.VEHICLE,
                "Транспорт удален",
                Map.of(saved, details),
                null
        );
    }

    private Equipment findVehicleEquipment(UUID equipmentId) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        if (equipment.getCategory() != EquipmentCategory.VEHICLE) {
            throw RestException.badRequest("Equipment is not a vehicle: " + equipmentId);
        }
        return equipment;
    }

    private VehicleDetails findVehicleDetails(UUID equipmentId) {
        return vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Vehicle details not found: " + equipmentId));
    }

    private VehicleDocument findVehicleDocument(UUID equipmentId, UUID documentId) {
        return vehicleDocumentRepository.findByIdAndEquipmentId(documentId, equipmentId)
                .orElseThrow(() -> RestException.notFound("Vehicle document not found: " + documentId));
    }

    private VehicleDocumentDto toDocumentDtoWithMetadata(UUID equipmentId, VehicleDocument document, UUID currentUserId) {
        fileService.getMetadata(document.getFile().getId(), currentUserId);
        return VehicleDocumentDto.from(equipmentId, document);
    }

    private VehicleDocumentDto legacyDocumentDto(UUID equipmentId, UploadedFile file) {
        return new VehicleDocumentDto(
                file.getId(),
                file.getId(),
                null,
                null,
                file.getOriginalName(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSize(),
                "/api/v1/vehicles/" + equipmentId + "/document/download",
                "/api/v1/vehicles/" + equipmentId + "/document/presigned-url",
                file.getCreatedAt(),
                file.getCreatedAt()
        );
    }

    private AttachmentGroupDto.FileItem primaryFile(AttachmentGroupDto group) {
        if (group == null || group.files() == null || group.files().isEmpty()) {
            throw RestException.notFound("Attachment group file not found");
        }
        return group.files().getFirst();
    }

    private UUID currentUserId(AuthenticatedUser user) {
        if (user == null || user.id() == null || user.id().isBlank()) {
            throw RestException.unauthorized("Authenticated user is required");
        }
        return UUID.fromString(user.id());
    }

    private AuthenticatedUser authenticatedUser(UUID currentUserId) {
        return new AuthenticatedUser(currentUserId.toString(), null, null, null, null, null, List.of());
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

    private String legacyDocumentName(MultipartFile document) {
        if (document != null && document.getOriginalFilename() != null && !document.getOriginalFilename().isBlank()) {
            return document.getOriginalFilename().trim();
        }
        return "Vehicle document";
    }

    private List<String> normalizeDocumentNames(List<MultipartFile> files, List<String> documentNames) {
        if (documentNames == null || documentNames.isEmpty()) {
            throw RestException.badRequest("documentNames are required for vehicle document uploads");
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

    private void validateUniqueCreate(VehicleRequest request) {
        if (equipmentRepository.existsByInventoryNumberAndIsDeletedFalse(request.inventoryNumber())) {
            throw RestException.conflict("Inventory number already exists: " + request.inventoryNumber());
        }
        if (vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse(request.plateNumber())) {
            throw RestException.conflict("Vehicle plate number already exists: " + request.plateNumber());
        }
        if (hasText(request.vin()) && vehicleDetailsRepository.existsByVinAndIsDeletedFalse(request.vin())) {
            throw RestException.conflict("Vehicle VIN already exists: " + request.vin());
        }
    }

    private void validateUniqueUpdate(UUID equipmentId, Equipment equipment, VehicleDetails details, VehicleRequest request) {
        if (!Objects.equals(equipment.getInventoryNumber(), request.inventoryNumber())) {
            equipmentRepository.findByInventoryNumberAndIsDeletedFalse(request.inventoryNumber())
                    .filter(existing -> !Objects.equals(existing.getId(), equipmentId))
                    .ifPresent(existing -> {
                        throw RestException.conflict("Inventory number already exists: " + request.inventoryNumber());
                    });
        }
        if (!Objects.equals(details.getPlateNumber(), request.plateNumber())) {
            vehicleDetailsRepository.findByPlateNumberAndIsDeletedFalse(request.plateNumber())
                    .filter(existing -> !Objects.equals(existing.getEquipmentId(), equipmentId))
                    .ifPresent(existing -> {
                        throw RestException.conflict("Vehicle plate number already exists: " + request.plateNumber());
                    });
        }

        String currentVin = normalizeBlankToNull(details.getVin());
        String requestedVin = normalizeBlankToNull(request.vin());
        if (requestedVin != null && !Objects.equals(currentVin, requestedVin)) {
            vehicleDetailsRepository.findByVinAndIsDeletedFalse(requestedVin)
                    .filter(existing -> !Objects.equals(existing.getEquipmentId(), equipmentId))
                    .ifPresent(existing -> {
                        throw RestException.conflict("Vehicle VIN already exists: " + requestedVin);
                    });
        }
    }

    private void applyEquipment(Equipment equipment, VehicleRequest request) {
        equipment.setName(request.name());
        equipment.setInventoryNumber(request.inventoryNumber());
        equipment.setTechnicalNumber(request.technicalNumber());
        equipment.setSerialNumber(request.serialNumber());
        equipment.setModel(request.model());
        equipment.setEquipmentTypeId(request.equipmentTypeId());
        equipment.setMxikId(request.mxikId());
        equipment.setDepartmentId(request.departmentId());
        equipment.setLocationId(request.locationId());
        equipment.setStatus(request.status() != null ? request.status() : EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.VEHICLE);
        equipment.setManufacturer(request.brand());
        equipment.setResponsibleId(request.assignedDriverId());
        equipment.setCurrentLocationType(EquipmentLocationType.DEPARTMENT);
        equipment.setCurrentWarehouseId(null);
        equipment.setResponsibleDepartmentId(request.departmentId());
        equipment.setOutsideReason(null);
        equipment.setOutsideTakenBy(null);
        equipment.setOutsideRecipientUserId(null);
        equipment.setOutsideStartedDate(null);
        equipment.setOutsideExpectedReturnDate(null);
        equipment.setOutsideDestination(null);
        equipment.setOutsideReasonNote(null);
        equipment.setProducedYear(request.manufactureYear());
        equipment.setAverageDailyUsage(request.averageDailyUsage());
        applyVehicleLifetime(equipment, request);
        equipment.setDaysOfResourceRemaining(
                equipmentService.calculateDaysOfResourceRemaining(
                        equipment.getLifetimeLimitValue(),
                        equipment.getAverageDailyUsage()
                )
        );
    }

    private void writeInitialLocationHistory(Equipment equipment) {
        VehicleLocationSnapshot toLocation = vehicleLocationSnapshot(equipment);
        EquipmentLocationHistory history = new EquipmentLocationHistory();
        history.setEquipmentId(equipment.getId());
        history.setToLocationType(toLocation.locationType());
        history.setToDepartmentId(toLocation.departmentId());
        history.setToWarehouseId(toLocation.warehouseId());
        history.setResponsibleDepartmentId(toLocation.responsibleDepartmentId());
        history.setChangedBy(currentActorIdOrNull());
        history.setChangedAt(Instant.now());
        history.setNote("Vehicle created");
        equipmentLocationHistoryRepository.save(history);
    }

    private void writeLocationHistoryIfChanged(Equipment equipment,
                                               VehicleLocationSnapshot fromLocation,
                                               VehicleLocationSnapshot toLocation,
                                               String note) {
        if (!locationChanged(fromLocation, toLocation)) {
            return;
        }
        EquipmentLocationHistory history = new EquipmentLocationHistory();
        history.setEquipmentId(equipment.getId());
        history.setFromLocationType(fromLocation.locationType());
        history.setFromDepartmentId(fromLocation.departmentId());
        history.setFromWarehouseId(fromLocation.warehouseId());
        history.setToLocationType(toLocation.locationType());
        history.setToDepartmentId(toLocation.departmentId());
        history.setToWarehouseId(toLocation.warehouseId());
        history.setResponsibleDepartmentId(toLocation.responsibleDepartmentId());
        history.setChangedBy(currentActorIdOrNull());
        history.setChangedAt(Instant.now());
        history.setNote(note);
        equipmentLocationHistoryRepository.save(history);
    }

    private boolean locationChanged(VehicleLocationSnapshot fromLocation, VehicleLocationSnapshot toLocation) {
        return !Objects.equals(fromLocation.locationType(), toLocation.locationType())
                || !Objects.equals(fromLocation.departmentId(), toLocation.departmentId())
                || !Objects.equals(fromLocation.warehouseId(), toLocation.warehouseId())
                || !Objects.equals(fromLocation.responsibleDepartmentId(), toLocation.responsibleDepartmentId());
    }

    private VehicleLocationSnapshot vehicleLocationSnapshot(Equipment equipment) {
        EquipmentLocationType locationType = equipment.getCurrentLocationType();
        if (locationType == null && equipment.getDepartmentId() != null) {
            locationType = EquipmentLocationType.DEPARTMENT;
        }
        UUID departmentId = locationType == EquipmentLocationType.DEPARTMENT
                ? equipment.getDepartmentId()
                : null;
        UUID warehouseId = locationType == EquipmentLocationType.WAREHOUSE
                ? equipment.getCurrentWarehouseId()
                : null;
        UUID responsibleDepartmentId = equipment.getResponsibleDepartmentId() != null
                ? equipment.getResponsibleDepartmentId()
                : departmentId;
        return new VehicleLocationSnapshot(locationType, departmentId, warehouseId, responsibleDepartmentId);
    }

    private UUID currentActorIdOrNull() {
        if (securityScope == null) {
            return null;
        }
        try {
            AuthenticatedUser user = securityScope.currentUser();
            if (user == null || user.id() == null || user.id().isBlank()) {
                return null;
            }
            return UUID.fromString(user.id());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private record VehicleLocationSnapshot(
            EquipmentLocationType locationType,
            UUID departmentId,
            UUID warehouseId,
            UUID responsibleDepartmentId
    ) {
    }

    private void applyVehicleLifetime(Equipment equipment, VehicleRequest request) {
        MeterType counterType = request.lifetimeCounterType();
        Double limitValue = request.lifetimeLimitValue();
        if (counterType == null && limitValue != null) {
            counterType = MeterType.MILEAGE_KM;
        }
        validateVehicleLifetimeConfig(counterType, request.lifetimeMeterId(), limitValue);
        equipment.setLifetimeCounterType(counterType);
        equipment.setLifetimeMeterId(request.lifetimeMeterId());
        equipment.setLifetimeLimitValue(limitValue);
        equipment.setLifetimeBaselineValue(limitValue == null
                ? request.lifetimeBaselineValue()
                : defaultIfNull(request.lifetimeBaselineValue(), 0.0));
        equipment.setLifetimeWarningPercent(limitValue == null
                ? request.lifetimeWarningPercent()
                : defaultIfNull(request.lifetimeWarningPercent(), 10.0));
    }

    private void validateVehicleLifetimeConfig(MeterType counterType, UUID meterId, Double limitValue) {
        boolean hasConfig = counterType != null || meterId != null || limitValue != null;
        if (!hasConfig) {
            return;
        }
        if (counterType == null || limitValue == null || limitValue <= 0) {
            throw RestException.badRequest("Vehicle lifetime must include counter type and positive limit value");
        }
    }

    private static Double defaultIfNull(Double value, double fallback) {
        return value != null ? value : fallback;
    }

    private String nextEquipmentCode() {
        String prefix = "EQ-" + java.time.Year.now().getValue() + "-";
        return CodeGenerationUtils.nextYearSequenceCode(
                "EQ",
                () -> equipmentRepository.maxSequenceByCodePrefix(prefix),
                equipmentRepository::existsByCodeAndIsDeletedFalse
        );
    }

    private void applyDetails(VehicleDetails details, VehicleRequest request) {
        if (request.currentOdometerKm() != null && request.currentOdometerKm() < 0) {
            throw RestException.badRequest("Current odometer cannot be negative");
        }
        if (request.currentEngineHours() != null && request.currentEngineHours() < 0) {
            throw RestException.badRequest("Current engine hours cannot be negative");
        }
        details.setPlateNumber(request.plateNumber());
        details.setPlateType(request.plateType() != null ? request.plateType() : VehicleRegistrationPlateType.UNKNOWN);
        details.setVin(normalizeBlankToNull(request.vin()));
        details.setBrand(request.brand());
        details.setModel(request.model());
        details.setManufactureYear(request.manufactureYear());
        details.setVehicleType(request.vehicleType());
        details.setBodyNumber(request.bodyNumber());
        details.setChassisNumber(request.chassisNumber());
        details.setEngineNumber(request.engineNumber());
        details.setFuelType(request.fuelType());
        details.setFuelTankCapacity(request.fuelTankCapacity());
        details.setCarryingCapacity(request.carryingCapacity());
        details.setSeatCount(request.seatCount());
        applyAssignedDriver(details, request.assignedDriverId(), request.assignedDriverUsageLimitMinutes());
        if (request.currentOdometerKm() != null) {
            details.setCurrentOdometerKm(request.currentOdometerKm());
        }
        if (request.currentEngineHours() != null) {
            details.setCurrentEngineHours(request.currentEngineHours());
        }
        details.setRegistrationCertificateNumber(request.registrationCertificateNumber());
        details.setInsurancePolicyNumber(request.insurancePolicyNumber());
        details.setInsuranceExpiryDate(request.insuranceExpiryDate());
        details.setTechnicalInspectionExpiryDate(request.technicalInspectionExpiryDate());
        details.setGpsDeviceId(request.gpsDeviceId());
    }

    private void validateAssignedDriver(UUID assignedDriverId,
                                        Integer assignedDriverUsageLimitMinutes,
                                        UUID vehicleDepartmentId,
                                        UUID currentEquipmentId) {
        if (assignedDriverId == null && assignedDriverUsageLimitMinutes != null) {
            throw RestException.badRequest("assignedDriverId is required when assignedDriverUsageLimitMinutes is provided");
        }
        if (assignedDriverUsageLimitMinutes != null && assignedDriverUsageLimitMinutes <= 0) {
            throw RestException.badRequest("assignedDriverUsageLimitMinutes must be positive");
        }
        if (assignedDriverId == null) {
            return;
        }
        if (vehicleDepartmentId == null) {
            throw RestException.badRequest("Vehicle department is required before assigning a driver");
        }
        Employee driver = employeeRepository.findByIdAndIsDeletedFalse(assignedDriverId)
                .orElseThrow(() -> RestException.badRequest("Assigned driver employee not found: " + assignedDriverId));
        if (!driver.isActive()) {
            throw RestException.badRequest("Assigned driver employee is not active: " + assignedDriverId);
        }
        if (driverRoleRequired
                && !employeeWorkRoleAssignmentRepository.existsActiveByEmployeeIdAndWorkRoleCode(assignedDriverId, "DRIVER")) {
            throw RestException.badRequest("Assigned employee must have DRIVER work role");
        }
        if (!Objects.equals(driver.getDepartmentId(), vehicleDepartmentId)) {
            throw RestException.badRequest("Assigned driver must be in the same department as the vehicle");
        }
        boolean assignedElsewhere = currentEquipmentId == null
                ? vehicleDetailsRepository.existsByAssignedDriverIdAndIsDeletedFalse(assignedDriverId)
                : vehicleDetailsRepository.existsAssignedDriverOnAnotherVehicle(assignedDriverId, currentEquipmentId);
        if (assignedElsewhere) {
            throw RestException.conflict("Assigned driver already has a default vehicle");
        }
    }

    private void applyAssignedDriver(VehicleDetails details, UUID assignedDriverId, Integer usageLimitMinutes) {
        UUID previousDriverId = details.getAssignedDriverId();
        Integer previousLimitMinutes = details.getAssignedDriverUsageLimitMinutes();
        details.setAssignedDriverId(assignedDriverId);
        if (assignedDriverId == null) {
            details.setAssignedDriverUsageLimitMinutes(null);
            details.setAssignedDriverAssignedBy(null);
            details.setAssignedDriverAssignedAt(null);
            return;
        }
        details.setAssignedDriverUsageLimitMinutes(usageLimitMinutes);
        if (!Objects.equals(previousDriverId, assignedDriverId)
                || !Objects.equals(previousLimitMinutes, usageLimitMinutes)) {
            details.setAssignedDriverAssignedBy(currentActorIdOrNull());
            details.setAssignedDriverAssignedAt(Instant.now());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalizeBlankToNull(String value) {
        return hasText(value) ? value : null;
    }


    private static final class FixedTotalPage<T> implements Page<T> {
        private final List<T> content;
        private final Pageable pageable;
        private final long totalElements;

        private FixedTotalPage(List<T> content, Pageable pageable, long totalElements) {
            this.content = content == null ? List.of() : List.copyOf(content);
            this.pageable = pageable == null ? Pageable.unpaged() : pageable;
            this.totalElements = totalElements;
        }

        @Override
        public int getTotalPages() {
            if (getSize() == 0) {
                return totalElements > 0 ? 1 : 0;
            }
            return (int) Math.ceil((double) totalElements / (double) getSize());
        }

        @Override
        public long getTotalElements() {
            return totalElements;
        }

        @Override
        public int getNumber() {
            return pageable.isPaged() ? pageable.getPageNumber() : 0;
        }

        @Override
        public int getSize() {
            return pageable.isPaged() ? pageable.getPageSize() : content.size();
        }

        @Override
        public int getNumberOfElements() {
            return content.size();
        }

        @Override
        public List<T> getContent() {
            return content;
        }

        @Override
        public boolean hasContent() {
            return !content.isEmpty();
        }

        @Override
        public Sort getSort() {
            return pageable.getSort();
        }

        @Override
        public boolean isFirst() {
            return !hasPrevious();
        }

        @Override
        public boolean isLast() {
            return !hasNext();
        }

        @Override
        public boolean hasNext() {
            return getNumber() + 1 < getTotalPages();
        }

        @Override
        public boolean hasPrevious() {
            return getNumber() > 0;
        }

        @Override
        public Pageable nextPageable() {
            return hasNext() ? pageable.next() : Pageable.unpaged();
        }

        @Override
        public Pageable previousPageable() {
            return hasPrevious() ? pageable.previousOrFirst() : Pageable.unpaged();
        }

        @Override
        public Iterator<T> iterator() {
            return Collections.unmodifiableList(content).iterator();
        }

        @Override
        public <U> Page<U> map(Function<? super T, ? extends U> converter) {
            List<U> mapped = new ArrayList<>(content.size());
            for (T item : content) {
                mapped.add(converter.apply(item));
            }
            return new FixedTotalPage<>(mapped, pageable, totalElements);
        }
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
