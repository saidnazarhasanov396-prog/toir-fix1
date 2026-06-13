package com.toir.service;

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
import com.toir.entity.equipment.VehicleDocument;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.FileCategory;
import com.toir.enums.VehicleRegistrationPlateType;
import com.toir.exception.RestException;
import com.toir.repository.UploadedFileRepository;
import com.toir.repository.VehicleDocumentRepository;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.projection.VehicleStatsProjection;
import com.toir.security.AuthenticatedUser;
import com.toir.security.SecurityScope;
import com.toir.service.equipment.EquipmentAttributeService;
import com.toir.service.equipment.EquipmentManualAttributeService;
import com.toir.service.equipment.EquipmentService;
import com.toir.service.file_management.FileService;
import com.toir.util.AuditBuilderService;
import com.toir.util.CodeGenerationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VehicleService {

    private final EquipmentRepository equipmentRepository;
    private final VehicleDetailsRepository vehicleDetailsRepository;
    private final EquipmentService equipmentService;
    private final AuditBuilderService auditBuilderService;
    private final FileService fileService;
    private final UploadedFileRepository uploadedFileRepository;
    private final SecurityScope securityScope;
    private final EquipmentAttributeService equipmentAttributeService;
    private final EquipmentManualAttributeService equipmentManualAttributeService;
    private final VehicleDocumentRepository vehicleDocumentRepository;

    @Transactional(readOnly = true)
    public Page<VehicleSummaryDto> list(UUID departmentId, EquipmentStatus status, VehicleRegistrationPlateType plateType,
                                        String search, int page, int pageSize) {
        int safePage = Math.max(page, 0);
        int safePageSize = Math.max(pageSize, 1);
        Page<Equipment> equipmentPage = vehicleDetailsRepository.searchVehicleEquipment(
                departmentId,
                status,
                plateType,
                EquipmentCategory.VEHICLE,
                search,
                org.springframework.data.domain.PageRequest.of(safePage, safePageSize)
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
        return new FixedTotalPage<>(items, enrichedEquipmentPage.getPageable(), enrichedEquipmentPage.getTotalElements());
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
        Equipment equipment = new Equipment();
        equipment.setCode(nextEquipmentCode());
        applyEquipment(equipment, request);
        Equipment savedEquipment = equipmentRepository.save(equipment);

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
        boolean equipmentTypeChanged = isEquipmentTypeChanged(equipment.getEquipmentTypeId(), request.equipmentTypeId());
        validateAttributesForTypeChange(equipmentTypeChanged, request.attributes());
        applyEquipment(equipment, request);
        applyDetails(details, request);

        Equipment newEquipment = equipmentRepository.save(equipment);
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
        UUID currentUserId = currentUserId(user);
        Equipment equipment = findVehicleEquipment(equipmentId);
        enforceVehicleAccess(equipment);
        if (files == null || files.isEmpty()) {
            throw RestException.badRequest("At least one vehicle document file is required");
        }
        List<String> normalizedDocumentNames = normalizeDocumentNames(files, documentNames);
        VehicleDetails details = findVehicleDetails(equipmentId);

        List<UUID> uploadedFileIds = new ArrayList<>();
        try {
            List<VehicleDocument> documents = new ArrayList<>(files.size());
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                String contentType = file.getContentType();
                if (contentType == null || !ALLOWED_VEHICLE_DOCUMENT_CONTENT_TYPES.contains(contentType)) {
                    throw RestException.badRequest("Unsupported file type: " + contentType);
                }
                String type = (documentTypes != null && i < documentTypes.size()) ? normalizeDocumentType(documentTypes.get(i)) : null;
                String number = (documentNumbers != null && i < documentNumbers.size()) ? documentNumbers.get(i) : null;
                UploadFileResponse uploaded = fileService.upload(file, FileCategory.VEHICLE_DOCUMENT, currentUserId);
                uploadedFileIds.add(uploaded.id());
                UploadedFile uploadedFile = uploadedFileRepository.findByIdAndDeletedFalse(uploaded.id())
                        .orElseThrow(() -> RestException.notFound("Uploaded file not found: " + uploaded.id()));
                documents.add(VehicleDocument.builder()
                        .vehicleDetails(details)
                        .file(uploadedFile)
                        .documentType(type)
                        .documentNumber(number)
                        .documentName(normalizedDocumentNames.get(i))
                        .build());
            }

            return vehicleDocumentRepository.saveAllAndFlush(documents).stream()
                    .map(document -> VehicleDocumentDto.from(equipmentId, document))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (RuntimeException e) {
            cleanupUploadedFiles(uploadedFileIds, currentUserId);
            if (e instanceof RestException restException) {
                throw restException;
            }
            throw RestException.conflict("Could not attach vehicle documents");
        }
    }

    @Transactional(readOnly = true)
    public List<VehicleDocumentDto> getDocuments(UUID equipmentId, AuthenticatedUser user) {
        UUID currentUserId = currentUserId(user);
        Equipment equipment = findVehicleEquipment(equipmentId);
        enforceVehicleAccess(equipment);
        return vehicleDocumentRepository.findAllByEquipmentId(equipmentId)
                .stream()
                .map(document -> toDocumentDtoWithMetadata(equipmentId, document, currentUserId))
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleDocumentDto getDocument(UUID equipmentId, UUID documentId, AuthenticatedUser user) {
        UUID currentUserId = currentUserId(user);
        Equipment equipment = findVehicleEquipment(equipmentId);
        enforceVehicleAccess(equipment);
        VehicleDocument document = findVehicleDocument(equipmentId, documentId);
        return toDocumentDtoWithMetadata(equipmentId, document, currentUserId);
    }

    @Transactional(readOnly = true)
    public VehicleDocumentDto getDocument(UUID equipmentId, UUID currentUserId) {
        Equipment equipment = findVehicleEquipment(equipmentId);
        enforceVehicleAccess(equipment);
        Optional<VehicleDocument> latestDocument = vehicleDocumentRepository.findAllByEquipmentId(equipmentId)
                .stream()
                .findFirst();
        if (latestDocument.isPresent()) {
            return toDocumentDtoWithMetadata(equipmentId, latestDocument.get(), currentUserId);
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
        UUID currentUserId = currentUserId(user);
        Equipment equipment = findVehicleEquipment(equipmentId);
        enforceVehicleAccess(equipment);
        VehicleDocument document = findVehicleDocument(equipmentId, documentId);
        return fileService.getPresignedUrl(document.getFile().getId(), currentUserId);
    }

    @Transactional(readOnly = true)
    public Resource downloadDocument(UUID equipmentId, UUID documentId, AuthenticatedUser user) {
        UUID currentUserId = currentUserId(user);
        Equipment equipment = findVehicleEquipment(equipmentId);
        enforceVehicleAccess(equipment);
        VehicleDocument document = findVehicleDocument(equipmentId, documentId);
        return fileService.download(document.getFile().getId(), currentUserId);
    }

    @Transactional(readOnly = true)
    public PresignedUrlResponse getDocumentPresignedUrl(UUID equipmentId, UUID currentUserId) {
        Equipment equipment = findVehicleEquipment(equipmentId);
        enforceVehicleAccess(equipment);
        Optional<VehicleDocument> latestDocument = vehicleDocumentRepository.findAllByEquipmentId(equipmentId)
                .stream()
                .findFirst();
        if (latestDocument.isPresent()) {
            return fileService.getPresignedUrl(latestDocument.get().getFile().getId(), currentUserId);
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
        Optional<VehicleDocument> latestDocument = vehicleDocumentRepository.findAllByEquipmentId(equipmentId)
                .stream()
                .findFirst();
        if (latestDocument.isPresent()) {
            return fileService.download(latestDocument.get().getFile().getId(), currentUserId);
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
        UUID currentUserId = currentUserId(user);
        Equipment equipment = findVehicleEquipment(equipmentId);
        enforceVehicleAccess(equipment);
        VehicleDocument document = findVehicleDocument(equipmentId, documentId);
        vehicleDocumentRepository.delete(document);
        fileService.delete(document.getFile().getId(), currentUserId);
    }

    @Transactional
    public void deleteDocument(UUID equipmentId, UUID currentUserId) {
        Equipment equipment = findVehicleEquipment(equipmentId);
        enforceVehicleAccess(equipment);
        Optional<VehicleDocument> latestDocument = vehicleDocumentRepository.findAllByEquipmentId(equipmentId)
                .stream()
                .findFirst();
        if (latestDocument.isPresent()) {
            VehicleDocument document = latestDocument.get();
            vehicleDocumentRepository.delete(document);
            fileService.delete(document.getFile().getId(), currentUserId);
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
        equipment.setDepartmentId(request.departmentId());
        equipment.setLocationId(request.locationId());
        equipment.setStatus(request.status() != null ? request.status() : EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.VEHICLE);
        equipment.setManufacturer(request.brand());
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
        details.setAssignedDriverId(request.assignedDriverId());
        details.setCurrentOdometerKm(request.currentOdometerKm() != null ? request.currentOdometerKm() : 0);
        details.setCurrentEngineHours(request.currentEngineHours() != null ? request.currentEngineHours() : 0);
        details.setRegistrationCertificateNumber(request.registrationCertificateNumber());
        details.setInsurancePolicyNumber(request.insurancePolicyNumber());
        details.setInsuranceExpiryDate(request.insuranceExpiryDate());
        details.setTechnicalInspectionExpiryDate(request.technicalInspectionExpiryDate());
        details.setGpsDeviceId(request.gpsDeviceId());
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
