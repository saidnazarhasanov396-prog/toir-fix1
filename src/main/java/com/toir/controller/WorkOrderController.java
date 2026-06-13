package com.toir.controller;

import com.toir.dto.workorder.CloseWorkOrderRequest;
import com.toir.dto.workorder.CompleteWorkOrderRequest;
import com.toir.dto.workorder.WorkOrderCalendarSummaryResponse;
import com.toir.dto.workorder.WorkOrderDocumentDto;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderPerformerOptionDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.dto.workorder.WorkOrderStatsResponse;
import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.file.PresignedUrlResponse;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.WorkOrderStatus;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.security.ScopeAccessService;
import com.toir.service.ApprovalService;
import com.toir.service.WorkOrderService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/work-orders")
@Tag(name = "work-orders")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService service;
    private final WorkOrderRepository repository;
    private final ScopeAccessService scopeAccessService;
    private final ApprovalService approvalService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<Page<WorkOrderDto>> list(
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Instant plannedFrom,
            @RequestParam(required = false) Instant plannedTo,
            @RequestParam(required = false, defaultValue = "updatedAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        return ResponseEntity
                .ok(service.search(
                        status,
                        scopedDepartment(departmentId),
                        equipmentId,
                        page,
                        size,
                        search,
                        plannedFrom,
                        plannedTo,
                        sort));
    }

    @GetMapping("/calendar-summary")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<WorkOrderCalendarSummaryResponse> calendarSummary(
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String search,
            @RequestParam int year,
            @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(service.calendarSummary(
                status,
                scopedDepartment(departmentId),
                equipmentId,
                search,
                year,
                month));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<WorkOrderStatsResponse> stats(
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(service.getStats(status, scopedDepartment(departmentId), equipmentId, search));
    }

    @GetMapping("/options/performers")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ') or hasAuthority('WORK_ORDER_CREATE')")
    public ResponseEntity<List<WorkOrderPerformerOptionDto>> performerOptions(
            @RequestParam(required = false) UUID departmentId) {
        return ResponseEntity.ok(service.performerOptions(scopedDepartment(departmentId)));
    }

    @GetMapping("/mobile-feed")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<Page<WorkOrderDto>> mobileFeed(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(service.mobileFeed(scopedDepartment(departmentId), equipmentId, search, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<WorkOrderDto> get(@PathVariable UUID id) {
        assertCanAccessWorkOrder(workOrderOrThrow(id));
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_UPDATE') or hasAuthority('WORK_ORDER_CREATE')")
    @Operation(summary = "Attach work order documents with matching client-provided document names")
    public ResponseEntity<List<WorkOrderDocumentDto>> attachDocuments(
            @PathVariable UUID id,
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
        assertCanAccessWorkOrder(workOrderOrThrow(id));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.attachDocuments(
                        id,
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

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<Page<WorkOrderDocumentDto>> getDocuments(
            @PathVariable UUID id,
            @CurrentUser AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        assertCanAccessWorkOrder(workOrderOrThrow(id));
        return ResponseEntity.ok(PaginationUtils.page(service.getDocuments(id, user), page, size));
    }

    @GetMapping("/{id}/documents/{documentId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<WorkOrderDocumentDto> getDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessWorkOrder(workOrderOrThrow(id));
        return ResponseEntity.ok(service.getDocument(id, documentId, user));
    }

    @GetMapping("/{id}/documents/{documentId}/presigned-url")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<PresignedUrlResponse> getDocumentPresignedUrl(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessWorkOrder(workOrderOrThrow(id));
        return ResponseEntity.ok(service.getDocumentPresignedUrl(id, documentId, user));
    }

    @GetMapping("/{id}/documents/{documentId}/download")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessWorkOrder(workOrderOrThrow(id));
        WorkOrderDocumentDto document = service.getDocument(id, documentId, user);
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
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_UPDATE') or hasAuthority('WORK_ORDER_CREATE')")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanAccessWorkOrder(workOrderOrThrow(id));
        service.deleteDocument(id, documentId, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_CREATE')")
    public ResponseEntity<WorkOrderDto> create(@Valid @RequestBody WorkOrderRequest request) {
        if (request.departmentId() != null) {
            assertCanAccessDepartmentForMutation(request.departmentId());
        }
        UUID creatorId = currentUserId();
        WorkOrderDto created = creatorId == null ? service.create(request) : service.create(request, creatorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_APPROVE')")
    public ResponseEntity<ApprovalRequestDto> approve(@PathVariable UUID id, @RequestParam UUID approverId) {
        WorkOrder workOrder = workOrderOrThrow(id);
        assertCanAccessWorkOrder(workOrder);
        service.validateCanApprove(id);
        UUID requesterId = currentUserId();
        return ResponseEntity.ok(approvalService.createOrReuseApprovalForDocument(
                "WORK_ORDER",
                id,
                ApprovalActionType.APPROVE,
                requesterId == null ? approverId : requesterId,
                approverId,
                "WORK_ORDER_APPROVER",
                "Work order approval: " + workOrder.getNumber(),
                "Approval request for work order " + workOrder.getNumber()
        ));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_START')")
    public ResponseEntity<WorkOrderDto> start(@PathVariable UUID id) {
        assertCanAccessWorkOrder(workOrderOrThrow(id));
        return ResponseEntity.ok(service.start(id));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_COMPLETE')")
    public ResponseEntity<WorkOrderDto> complete(@PathVariable UUID id,
                                                 @Valid @RequestBody CompleteWorkOrderRequest request) {
        assertCanAccessWorkOrder(workOrderOrThrow(id));
        return ResponseEntity.ok(service.complete(id, request));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_CLOSE')")
    public ResponseEntity<WorkOrderDto> close(@PathVariable UUID id,
                                              @Valid @RequestBody CloseWorkOrderRequest request) {
        assertCanAccessWorkOrder(workOrderOrThrow(id));
        return ResponseEntity.ok(service.close(id, request));
    }

    private UUID scopedDepartment(UUID requestedDepartmentId) {
        UUID scopedDepartmentId = scopeAccessService.enforceDepartmentScope(requestedDepartmentId);
        if (!scopeAccessService.isScopeAdmin() && scopeAccessService.currentDepartmentIdOrNull() == null) {
            throw new AccessDeniedException("Access denied by work order department scope");
        }
        return scopedDepartmentId;
    }

    private WorkOrder workOrderOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + id));
    }

    private void assertCanAccessWorkOrder(WorkOrder workOrder) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (scopeAccessService.canAccessDepartment(workOrder.getDepartmentId())) {
            return;
        }
        if (workOrder.getPerformer() != null
                && scopeAccessService.canAccessAssignedUser(workOrder.getPerformer().getUserId())) {
            return;
        }
        throw new AccessDeniedException("Access denied by work order department or performer scope");
    }

    private void assertCanAccessDepartmentForMutation(UUID departmentId) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (departmentId == null || !scopeAccessService.canAccessDepartment(departmentId)) {
            throw new AccessDeniedException("Access denied by work order department scope");
        }
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        if (user == null || user.id() == null || user.id().isBlank()) {
            return null;
        }
        return UUID.fromString(user.id());
    }
}
