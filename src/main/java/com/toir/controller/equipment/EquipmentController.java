package com.toir.controller.equipment;
import com.toir.dto.equipment.*;
import com.toir.dto.file.PresignedUrlResponse;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentOutsideReason;
import com.toir.enums.EquipmentStatus;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.security.ScopeAccessService;
import com.toir.service.equipment.EquipmentService;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/equipment")
@Tag(name = "equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService service;
    private final EquipmentRepository repository;
    private final ScopeAccessService scopeAccessService;
    private final EquipmentStatusLifecycleService statusLifecycleService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<Page<EquipmentDto>> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentTypeId,
            @RequestParam(required = false) EquipmentStatus status,
            @RequestParam(required = false) EquipmentCategory category,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) EquipmentLocationType locationType,
            @RequestParam(required = false) EquipmentOutsideReason outsideReason,
            @RequestParam(defaultValue = "false") boolean overdueOnly,
            @RequestParam(defaultValue = "false") boolean availableForReplacement,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, size);
        return ResponseEntity.ok(service.search(
                scopedDepartment(),
                departmentId,
                equipmentTypeId,
                status,
                category,
                warehouseId,
                locationType,
                outsideReason,
                overdueOnly,
                availableForReplacement,
                search,
                safePage,
                safePageSize));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<EquipmentDetailDto> get(@PathVariable UUID id) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        return ResponseEntity.ok(service.findDetailById(id));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public EquipmentStatsResponse getEquipmentStats(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) EquipmentCategory category,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentTypeId
    ) {
        UUID scopedDepartmentId = scopedDepartment(departmentId);

        return service.getEquipmentStats(
                search,
                category,
                scopedDepartmentId,
                equipmentTypeId
        );
    }

    @GetMapping("/{id}/children")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<Page<EquipmentDto>> children(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        return ResponseEntity.ok(service.findChildren(id, Math.max(0, page), Math.max(1, size)));
    }

    @PostMapping
    @Operation(
            summary = "Create equipment",
            description = "At least one of departmentId or warehouseId is required. " +
                    "If warehouseId is provided, the created equipment is assigned in warehouse equipment as AVAILABLE. " +
                    "Equipment code is system-generated and must not be provided by client."
    )
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_CREATE')")
    public ResponseEntity<EquipmentDto> create(@Valid @RequestBody EquipmentCreateRequest request) {
        if (request.departmentId() != null) {
            scopeAccessService.assertCanAccessEquipmentScope(null, request.departmentId());
            scopeAccessService.assertCanAccessDepartment(request.departmentId());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<EquipmentDto> update(@PathVariable UUID id, @Valid @RequestBody EquipmentUpdateRequest request) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        if (request.departmentId() != null) {
            scopeAccessService.assertCanAccessDepartment(request.departmentId());
        }
        return ResponseEntity.ok(service.update(id, request));
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    @Operation(summary = "Attach equipment documents with matching client-provided document names")
    public ResponseEntity<List<EquipmentDocumentDto>> attachDocuments(
            @PathVariable UUID id,
            @Parameter(description = "Document files. Must have the same item count as documentNames.")
            @RequestParam("files") List<MultipartFile> files,
            @Parameter(description = "Document names/titles in the same order as files.")
            @RequestParam(value = "documentNames", required = false) List<String> documentNames,
            @RequestParam(required = false) String documentType,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.attachDocuments(id, files, documentNames, documentType, user));
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<List<EquipmentDocumentDto>> getDocuments(
            @PathVariable UUID id,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        return ResponseEntity.ok(service.getDocuments(id, user));
    }

    @GetMapping("/{id}/documents/{documentId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<EquipmentDocumentDto> getDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        return ResponseEntity.ok(service.getDocument(id, documentId, user));
    }

    @GetMapping("/{id}/documents/{documentId}/presigned-url")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<PresignedUrlResponse> getDocumentPresignedUrl(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        return ResponseEntity.ok(service.getDocumentPresignedUrl(id, documentId, user));
    }

    @GetMapping("/{id}/documents/{documentId}/download")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        EquipmentDocumentDto document = service.getDocument(id, documentId, user);
        Resource resource = service.downloadDocument(id, documentId, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.originalName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        service.deleteDocument(id, documentId, user);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<EquipmentStatusHistoryResponse> updateStatus(@PathVariable UUID id,
                                                                       @Valid @RequestBody EquipmentStatusChangeRequest request) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        return ResponseEntity.ok(statusLifecycleService.changeStatusManually(
                id,
                request,
                scopeAccessService.currentUserIdOrNull()
        ));
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<Page<EquipmentStatusHistoryResponse>> statusHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        return ResponseEntity.ok(statusLifecycleService.getHistory(
                id,
                PageRequest.of(Math.max(0, page), Math.max(1, size))
        ));
    }

    @PatchMapping("/{id}/placement")
    @Operation(
            summary = "Move equipment between warehouse and department",
            description = "Preferred frontend endpoint for equipment placement movement. " +
                    "Use targetType=WAREHOUSE to move equipment into warehouse inventory, " +
                    "or targetType=DEPARTMENT to install into a department."
    )
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_TRANSFER')")
    public ResponseEntity<EquipmentDto> updatePlacement(@PathVariable UUID id,
                                                         @Valid @RequestBody EquipmentPlacementRequest request) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        if (request.departmentId() != null) {
            scopeAccessService.assertCanAccessDepartment(request.departmentId());
        }
        return ResponseEntity.ok(service.updatePlacement(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        assertCanAccessEquipment(equipmentOrThrow(id));
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private UUID scopedDepartment(UUID requestedDepartmentId) {
        UUID scopedDepartmentId = scopeAccessService.enforceDepartmentScope(requestedDepartmentId);
        if (!scopeAccessService.isScopeAdmin() && scopeAccessService.currentDepartmentIdOrNull() == null) {
            throw new AccessDeniedException("Access denied by equipment department scope");
        }
        return scopedDepartmentId;
    }

    private Equipment equipmentOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + id));
    }

    private void assertCanAccessEquipment(Equipment equipment) {
        scopeAccessService.assertCanAccessEquipmentScope(
                equipment.getResponsibleDepartmentId(),
                equipment.getDepartmentId()
        );
    }

    private UUID scopedDepartment() {
        if (scopeAccessService.isScopeAdmin()) {
            return null;
        }
        UUID currentDepartmentId = scopeAccessService.currentDepartmentIdOrNull();
        if (currentDepartmentId == null) {
            throw new AccessDeniedException("Access denied by equipment department scope");
        }
        return currentDepartmentId;
    }
}
