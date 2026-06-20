package com.toir.controller;

import com.toir.dto.stockmovement.StockMovementDocumentDto;
import com.toir.dto.stockmovement.StockMovementDto;
import com.toir.dto.stockmovement.StockMovementFileDto;
import com.toir.dto.stockmovement.StockMovementIssueRequest;
import com.toir.dto.stockmovement.StockMovementReceiptRequest;
import com.toir.dto.stockmovement.StockMovementRequest;
import com.toir.enums.StockMovementType;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.service.StockMovementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
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
@RequestMapping("/api/v1/stock-movements")
@Tag(name = "stock-movements")
@RequiredArgsConstructor
@Deprecated(forRemoval = false)
public class StockMovementController {

    private final StockMovementService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('STOCK_READ')")
    public ResponseEntity<Page<StockMovementDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) StockMovementType type,
            @RequestParam(required = false) UUID sparePartId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) UUID responsiblePersonId,
            @RequestParam(required = false) UUID workOrderId
    ) {
        return ResponseEntity.ok(service.findAll(
                page,
                size,
                type,
                sparePartId,
                warehouseId,
                from,
                to,
                responsiblePersonId,
                workOrderId
        ));
    }

    @GetMapping("/{id}/files")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('STOCK_READ')")
    public ResponseEntity<List<StockMovementFileDto>> listFiles(
            @PathVariable UUID id,
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.ok(service.listFiles(id, user));
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('STOCK_READ')")
    public ResponseEntity<List<StockMovementDocumentDto>> listDocuments(
            @PathVariable UUID id,
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.ok(service.listDocuments(id, user));
    }

    @GetMapping("/{id}/documents/{documentId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('STOCK_READ')")
    public ResponseEntity<StockMovementDocumentDto> getDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.ok(service.getDocument(id, documentId, user));
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("""
            hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or
            hasAuthority('STOCK_RECEIVE') or hasAuthority('STOCK_ISSUE')
            """)
    public ResponseEntity<StockMovementDocumentDto> attachDocument(
            @PathVariable UUID id,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("documentName") String documentName,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String documentNumber,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanModifyMovementFiles(id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.attachDocument(id, files, documentName, documentType, documentNumber, user));
    }

    @PostMapping(value = "/{id}/documents/{documentId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("""
            hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or
            hasAuthority('STOCK_RECEIVE') or hasAuthority('STOCK_ISSUE')
            """)
    public ResponseEntity<StockMovementDocumentDto> attachDocumentFiles(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @RequestParam("files") List<MultipartFile> files,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanModifyMovementFiles(id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.attachDocumentFiles(id, documentId, files, user));
    }

    @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("""
            hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or
            hasAuthority('STOCK_RECEIVE') or hasAuthority('STOCK_ISSUE')
            """)
    public ResponseEntity<List<StockMovementFileDto>> attachFiles(
            @PathVariable UUID id,
            @RequestParam("files") List<MultipartFile> files,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanModifyMovementFiles(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.attachFiles(id, files, user));
    }

    @GetMapping("/{id}/files/{fileId}/download")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('STOCK_READ')")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable UUID id,
            @PathVariable UUID fileId,
            @CurrentUser AuthenticatedUser user
    ) {
        StockMovementFileDto file = service.getFile(id, fileId, user);
        Resource resource = service.downloadFile(id, fileId, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.originalName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    @DeleteMapping("/{id}/files/{fileId}")
    @PreAuthorize("""
            hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or
            hasAuthority('STOCK_RECEIVE') or hasAuthority('STOCK_ISSUE')
            """)
    public ResponseEntity<Void> deleteFile(
            @PathVariable UUID id,
            @PathVariable UUID fileId,
            @CurrentUser AuthenticatedUser user
    ) {
        assertCanModifyMovementFiles(id);
        service.deleteFile(id, fileId, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/receipt")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('STOCK_RECEIVE')")
    public ResponseEntity<StockMovementDto> receipt(@Valid @RequestBody StockMovementReceiptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.receipt(request));
    }

    @PostMapping("/issue")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('STOCK_ISSUE')")
    public ResponseEntity<StockMovementDto> issue(@Valid @RequestBody StockMovementIssueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.issue(request));
    }

    @PostMapping
    @PreAuthorize("""
            hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or
            (#request.type() == T(com.toir.enums.StockMovementType).RECEIPT and hasAuthority('STOCK_RECEIVE')) or
            (#request.type() == T(com.toir.enums.StockMovementType).RETURN and hasAuthority('STOCK_RECEIVE')) or
            (#request.type() == T(com.toir.enums.StockMovementType).ISSUE and hasAuthority('STOCK_ISSUE')) or
            (#request.type() == T(com.toir.enums.StockMovementType).TRANSFER and hasAuthority('STOCK_MOVE')) or
            (#request.type() == T(com.toir.enums.StockMovementType).RESERVATION and hasAuthority('STOCK_MOVE')) or
            (#request.type() == T(com.toir.enums.StockMovementType).RELEASE and hasAuthority('STOCK_MOVE')) or
            (#request.type() == T(com.toir.enums.StockMovementType).ADJUSTMENT and hasAuthority('STOCK_ADJUST'))
            """)
    public ResponseEntity<StockMovementDto> create(@Valid @RequestBody StockMovementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    private void assertCanModifyMovementFiles(UUID movementId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (hasAuthority(authentication, "SYSTEM_ADMIN") || hasAuthority(authentication, "*")) {
            return;
        }
        StockMovementType type = service.movementType(movementId);
        if (type == StockMovementType.RECEIPT && hasAuthority(authentication, "STOCK_RECEIVE")) {
            return;
        }
        if (type == StockMovementType.ISSUE && hasAuthority(authentication, "STOCK_ISSUE")) {
            return;
        }
        throw new AccessDeniedException("Access denied by stock movement file permission");
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(item -> authority.equals(item.getAuthority()));
    }
}
