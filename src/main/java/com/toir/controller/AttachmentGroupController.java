package com.toir.controller;

import com.toir.dto.attachment.AttachmentGroupDto;
import com.toir.dto.file.PresignedUrlResponse;
import com.toir.exception.RestException;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.service.attachment.AttachmentGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attachments/groups")
@Tag(name = "attachments")
@RequiredArgsConstructor
public class AttachmentGroupController {

    private final AttachmentGroupService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("""
            hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or
            hasAuthority('EQUIPMENT_UPDATE') or hasAuthority('WORK_ORDER_UPDATE') or
            hasAuthority('WORK_ORDER_CREATE') or hasAuthority('STOCK_RECEIVE') or
            hasAuthority('STOCK_ISSUE') or hasAuthority('REPAIR_REQUEST_UPDATE') or
            hasAuthority('APPROVAL_UPDATE')
            """)
    @Operation(summary = "Create an attachment group and upload one or more files")
    public ResponseEntity<AttachmentGroupDto> createGroup(
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam String targetType,
            @RequestParam UUID targetId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "labels", required = false) List<String> labels,
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createGroup(title, description, targetType, targetId, files, labels, user));
    }

    @PostMapping(value = "/{groupId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("""
            hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or
            hasAuthority('EQUIPMENT_UPDATE') or hasAuthority('WORK_ORDER_UPDATE') or
            hasAuthority('WORK_ORDER_CREATE') or hasAuthority('STOCK_RECEIVE') or
            hasAuthority('STOCK_ISSUE') or hasAuthority('REPAIR_REQUEST_UPDATE') or
            hasAuthority('APPROVAL_UPDATE')
            """)
    @Operation(summary = "Add files to an existing attachment group")
    public ResponseEntity<AttachmentGroupDto> addFiles(
            @PathVariable UUID groupId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "labels", required = false) List<String> labels,
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.addFiles(groupId, files, labels, user));
    }

    @GetMapping("/{groupId}")
    @PreAuthorize("""
            hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or
            hasAuthority('EQUIPMENT_READ') or hasAuthority('WORK_ORDER_READ') or
            hasAuthority('STOCK_READ') or hasAuthority('REPAIR_REQUEST_READ') or
            hasAuthority('APPROVAL_READ')
            """)
    public ResponseEntity<AttachmentGroupDto> getGroup(
            @PathVariable UUID groupId,
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.ok(service.getGroup(groupId, user));
    }

    @GetMapping
    @PreAuthorize("""
            hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or
            hasAuthority('EQUIPMENT_READ') or hasAuthority('WORK_ORDER_READ') or
            hasAuthority('STOCK_READ') or hasAuthority('REPAIR_REQUEST_READ') or
            hasAuthority('APPROVAL_READ')
            """)
    public ResponseEntity<List<AttachmentGroupDto>> listGroups(
            @RequestParam String targetType,
            @RequestParam UUID targetId,
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.ok(service.listGroups(targetType, targetId, user));
    }

    @GetMapping("/{groupId}/files/{fileId}/presigned-url")
    @PreAuthorize("""
            hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or
            hasAuthority('EQUIPMENT_READ') or hasAuthority('WORK_ORDER_READ') or
            hasAuthority('STOCK_READ') or hasAuthority('REPAIR_REQUEST_READ') or
            hasAuthority('APPROVAL_READ')
            """)
    public ResponseEntity<PresignedUrlResponse> getFilePresignedUrl(
            @PathVariable UUID groupId,
            @PathVariable UUID fileId,
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.ok(service.getFilePresignedUrl(groupId, fileId, user));
    }

    @GetMapping("/{groupId}/files/{fileId}/download")
    @PreAuthorize("""
            hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or
            hasAuthority('EQUIPMENT_READ') or hasAuthority('WORK_ORDER_READ') or
            hasAuthority('STOCK_READ') or hasAuthority('REPAIR_REQUEST_READ') or
            hasAuthority('APPROVAL_READ')
            """)
    public ResponseEntity<Resource> downloadFile(
            @PathVariable UUID groupId,
            @PathVariable UUID fileId,
            @CurrentUser AuthenticatedUser user
    ) {
        AttachmentGroupDto group = service.getGroup(groupId, user);
        AttachmentGroupDto.FileItem file = group.files().stream()
                .filter(item -> fileId.equals(item.fileId()))
                .findFirst()
                .orElseThrow(() -> RestException.notFound("Attachment group file not found: " + fileId));
        Resource resource = service.downloadFile(groupId, fileId, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.originalName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    @DeleteMapping("/{groupId}")
    @PreAuthorize("""
            hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or
            hasAuthority('EQUIPMENT_UPDATE') or hasAuthority('WORK_ORDER_UPDATE') or
            hasAuthority('WORK_ORDER_CREATE') or hasAuthority('STOCK_RECEIVE') or
            hasAuthority('STOCK_ISSUE') or hasAuthority('REPAIR_REQUEST_UPDATE') or
            hasAuthority('APPROVAL_UPDATE')
            """)
    public ResponseEntity<Void> deleteGroup(
            @PathVariable UUID groupId,
            @CurrentUser AuthenticatedUser user
    ) {
        service.deleteGroup(groupId, user);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{groupId}/files/{fileId}")
    @PreAuthorize("""
            hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or
            hasAuthority('EQUIPMENT_UPDATE') or hasAuthority('WORK_ORDER_UPDATE') or
            hasAuthority('WORK_ORDER_CREATE') or hasAuthority('STOCK_RECEIVE') or
            hasAuthority('STOCK_ISSUE') or hasAuthority('REPAIR_REQUEST_UPDATE') or
            hasAuthority('APPROVAL_UPDATE')
            """)
    public ResponseEntity<Void> removeFile(
            @PathVariable UUID groupId,
            @PathVariable UUID fileId,
            @CurrentUser AuthenticatedUser user
    ) {
        service.removeFile(groupId, fileId, user);
        return ResponseEntity.noContent().build();
    }
}
