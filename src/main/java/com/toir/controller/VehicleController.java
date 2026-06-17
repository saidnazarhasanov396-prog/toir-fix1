package com.toir.controller;

import com.toir.dto.file.PresignedUrlResponse;
import com.toir.dto.vehicle.VehicleDetailDto;
import com.toir.dto.vehicle.VehicleDocumentDto;
import com.toir.dto.vehicle.VehicleDrivingSessionResponse;
import com.toir.dto.vehicle.VehicleDrivingSessionReturnRequest;
import com.toir.dto.vehicle.VehicleDrivingSessionStartRequest;
import com.toir.dto.vehicle.VehiclePictureDto;
import com.toir.dto.vehicle.VehicleRequest;
import com.toir.dto.vehicle.VehicleStatsResponse;
import com.toir.dto.vehicle.VehicleSummaryDto;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.VehicleRegistrationPlateType;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.security.ScopeAccessService;
import com.toir.service.VehicleDrivingSessionService;
import com.toir.service.VehicleService;
import com.toir.service.VehiclePictureService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/vehicles")
@Tag(name = "vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService service;
    private final ScopeAccessService scopeAccessService;
    private final EquipmentRepository equipmentRepository;
    private final VehiclePictureService pictureService;
    private final VehicleDrivingSessionService drivingSessionService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<Page<VehicleSummaryDto>> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) EquipmentStatus status,
            @RequestParam(required = false) VehicleRegistrationPlateType plateType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(service.list(
                scopedDepartment(departmentId),
                status,
                plateType,
                search,
                page,
                size
        ));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<VehicleStatsResponse> stats(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(service.getStats(
                scopedDepartment(departmentId),
                search
        ));
    }

    @GetMapping("/{equipmentId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<VehicleDetailDto> get(@PathVariable UUID equipmentId) {
        assertCanAccessVehicleEquipment(vehicleEquipmentOrThrow(equipmentId));
        return ResponseEntity.ok(service.findByEquipmentId(equipmentId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_CREATE')")
    public ResponseEntity<VehicleDetailDto> create(@Valid @RequestBody VehicleRequest request) {
        if (request.departmentId() != null) {
            scopeAccessService.assertCanAccessDepartment(request.departmentId());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{equipmentId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<VehicleDetailDto> update(@PathVariable UUID equipmentId, @Valid @RequestBody VehicleRequest request) {
        assertCanAccessVehicleEquipment(vehicleEquipmentOrThrow(equipmentId));
        if (request.departmentId() != null) {
            scopeAccessService.assertCanAccessDepartment(request.departmentId());
        }
        return ResponseEntity.ok(service.update(equipmentId, request));
    }

    @PostMapping(value = "/{equipmentId}/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<VehicleDetailDto> attachDocument(
            @PathVariable UUID equipmentId,
            @RequestParam("document") MultipartFile document,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessVehicleEquipment(vehicleEquipmentOrThrow(equipmentId));
        return ResponseEntity.ok(service.attachDocument(equipmentId, document, currentUserId(user)));
    }

    @PostMapping(value = "/{equipmentId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    @Operation(summary = "Attach vehicle documents with matching client-provided document names")
    public ResponseEntity<List<VehicleDocumentDto>> attachDocuments(
            @PathVariable UUID equipmentId,
            @Parameter(description = "Document files. Must have the same item count as documentNames.")
            @RequestParam("files") List<MultipartFile> files,
            @Parameter(description = "Document names/titles in the same order as files.")
            @RequestParam(value = "documentNames", required = false) List<String> documentNames,
            @RequestParam(required = false) String documentType,
            @Parameter(description = "Document type per file, in the same order as files.")
            @RequestParam(value = "documentTypes", required = false) List<String> documentTypes,
            @Parameter(description = "Document number per file, in the same order as files.")
            @RequestParam(value = "documentNumbers", required = false) List<String> documentNumbers,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessVehicleEquipment(vehicleEquipmentOrThrow(equipmentId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.attachDocuments(
                        equipmentId,
                        files,
                        documentNames,
                        effectiveDocumentTypes(files, documentTypes, documentType),
                        documentNumbers,
                        user));
    }

    private static List<String> effectiveDocumentTypes(
            List<MultipartFile> files,
            List<String> documentTypes,
            String documentType
    ) {
        if (documentTypes != null && !documentTypes.isEmpty()) {
            return documentTypes;
        }
        if (documentType == null || documentType.isBlank() || files == null) {
            return null;
        }
        return Collections.nCopies(files.size(), documentType);
    }

    @GetMapping("/{equipmentId}/documents")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<Page<VehicleDocumentDto>> getDocuments(
            @PathVariable UUID equipmentId,
            @CurrentUser AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        assertCanAccessVehicleEquipment(vehicleEquipmentOrThrow(equipmentId));
        return ResponseEntity.ok(PaginationUtils.page(service.getDocuments(equipmentId, user), page, size));
    }

    @GetMapping("/{equipmentId}/documents/{documentId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<VehicleDocumentDto> getDocument(
            @PathVariable UUID equipmentId,
            @PathVariable UUID documentId,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessVehicleEquipment(vehicleEquipmentOrThrow(equipmentId));
        return ResponseEntity.ok(service.getDocument(equipmentId, documentId, user));
    }

    @GetMapping("/{equipmentId}/documents/{documentId}/presigned-url")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<PresignedUrlResponse> getDocumentPresignedUrl(
            @PathVariable UUID equipmentId,
            @PathVariable UUID documentId,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessVehicleEquipment(vehicleEquipmentOrThrow(equipmentId));
        return ResponseEntity.ok(service.getDocumentPresignedUrl(equipmentId, documentId, user));
    }

    @GetMapping("/{equipmentId}/documents/{documentId}/download")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable UUID equipmentId,
            @PathVariable UUID documentId,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessVehicleEquipment(vehicleEquipmentOrThrow(equipmentId));
        VehicleDocumentDto document = service.getDocument(equipmentId, documentId, user);
        Resource resource = service.downloadDocument(equipmentId, documentId, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.originalName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    @DeleteMapping("/{equipmentId}/documents/{documentId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID equipmentId,
            @PathVariable UUID documentId,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessVehicleEquipment(vehicleEquipmentOrThrow(equipmentId));
        service.deleteDocument(equipmentId, documentId, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{equipmentId}/pictures", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    @Operation(summary = "Attach vehicle pictures with optional matching client-provided picture names")
    public ResponseEntity<List<VehiclePictureDto>> attachPictures(
            @PathVariable UUID equipmentId,
            @Parameter(description = "Image files. If pictureNames is provided, it must have the same item count as files.")
            @RequestParam("files") List<MultipartFile> files,
            @Parameter(description = "Picture names/titles in the same order as files.")
            @RequestParam(value = "pictureNames", required = false) List<String> pictureNames,
            @RequestParam(required = false) String pictureType,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessVehicleEquipment(vehicleEquipmentOrThrow(equipmentId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pictureService.uploadPictures(equipmentId, files, pictureNames, pictureType, user));
    }

    @GetMapping("/{equipmentId}/pictures")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<Page<VehiclePictureDto>> getPictures(
            @PathVariable UUID equipmentId,
            @CurrentUser AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        assertCanAccessVehicleEquipment(vehicleEquipmentOrThrow(equipmentId));
        return ResponseEntity.ok(PaginationUtils.page(pictureService.getPictures(equipmentId, user), page, size));
    }

    @PostMapping("/{equipmentId}/driving-sessions/start")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<VehicleDrivingSessionResponse> startDrivingSession(
            @PathVariable UUID equipmentId,
            @Valid @RequestBody VehicleDrivingSessionStartRequest request,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessVehicleEquipment(vehicleEquipmentOrThrow(equipmentId));
        return ResponseEntity.ok(drivingSessionService.start(equipmentId, request, currentUserId(user)));
    }

    @PostMapping("/{equipmentId}/driving-sessions/{sessionId}/return")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<VehicleDrivingSessionResponse> returnDrivingSession(
            @PathVariable UUID equipmentId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody VehicleDrivingSessionReturnRequest request,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessVehicleEquipment(vehicleEquipmentOrThrow(equipmentId));
        return ResponseEntity.ok(drivingSessionService.returnVehicle(equipmentId, sessionId, request, currentUserId(user)));
    }

    @GetMapping("/{equipmentId}/driving-sessions")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<Page<VehicleDrivingSessionResponse>> drivingSessionHistory(
            @PathVariable UUID equipmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        assertCanAccessVehicleEquipment(vehicleEquipmentOrThrow(equipmentId));
        return ResponseEntity.ok(drivingSessionService.history(
                equipmentId,
                PageRequest.of(Math.max(0, page), Math.max(1, size))
        ));
    }

    @GetMapping("/pictures/{pictureId}/download")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<Resource> downloadPicture(
            @PathVariable UUID pictureId,
            @CurrentUser AuthenticatedUser user
    ) {
        VehiclePictureDto picture = pictureService.getPicture(pictureId, user);
        Resource resource = pictureService.downloadPicture(pictureId, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(picture.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(picture.originalName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    @DeleteMapping("/pictures/{pictureId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<Void> deletePicture(
            @PathVariable UUID pictureId,
            @CurrentUser AuthenticatedUser user
    ) {
        pictureService.deletePicture(pictureId, user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{equipmentId}/document")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<VehicleDocumentDto> getDocument(
            @PathVariable UUID equipmentId,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessVehicleEquipment(vehicleEquipmentOrThrow(equipmentId));
        return ResponseEntity.ok(service.getDocument(equipmentId, currentUserId(user)));
    }

    @GetMapping("/{equipmentId}/document/presigned-url")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<PresignedUrlResponse> getDocumentPresignedUrl(
            @PathVariable UUID equipmentId,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessVehicleEquipment(vehicleEquipmentOrThrow(equipmentId));
        return ResponseEntity.ok(service.getDocumentPresignedUrl(equipmentId, currentUserId(user)));
    }

    @GetMapping("/{equipmentId}/document/download")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable UUID equipmentId,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessVehicleEquipment(vehicleEquipmentOrThrow(equipmentId));
        UUID currentUserId = currentUserId(user);
        VehicleDocumentDto document = service.getDocument(equipmentId, currentUserId);
        Resource resource = service.downloadDocument(equipmentId, currentUserId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.originalName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    @DeleteMapping("/{equipmentId}/document")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID equipmentId,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessVehicleEquipment(vehicleEquipmentOrThrow(equipmentId));
        service.deleteDocument(equipmentId, currentUserId(user));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{equipmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID equipmentId) {
        assertCanAccessVehicleEquipment(vehicleEquipmentOrThrow(equipmentId));
        service.delete(equipmentId);
        return ResponseEntity.noContent().build();
    }

    private UUID scopedDepartment(UUID requestedDepartmentId) {
        UUID scopedDepartmentId = scopeAccessService.enforceDepartmentScope(requestedDepartmentId);
        if (!scopeAccessService.isScopeAdmin() && scopeAccessService.currentDepartmentIdOrNull() == null) {
            throw new AccessDeniedException("Access denied by vehicle department scope");
        }
        return scopedDepartmentId;
    }

    private Equipment vehicleEquipmentOrThrow(UUID equipmentId) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        if (equipment.getCategory() != EquipmentCategory.VEHICLE) {
            throw RestException.badRequest("Equipment is not a vehicle: " + equipmentId);
        }
        return equipment;
    }

    private void assertCanAccessVehicleEquipment(Equipment equipment) {
        scopeAccessService.assertCanAccessEquipmentScope(
                equipment.getResponsibleDepartmentId(),
                equipment.getDepartmentId()
        );
    }

    private UUID currentUserId(AuthenticatedUser user) {
        if (user == null || user.id() == null || user.id().isBlank()) {
            throw RestException.unauthorized("Authenticated user is required");
        }
        return UUID.fromString(user.id());
    }
}
