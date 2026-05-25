package com.toir.controller;

import com.toir.dto.file.FileResponse;
import com.toir.dto.file.PresignedUrlResponse;
import com.toir.dto.file.UploadFileResponse;
import com.toir.enums.FileCategory;
import com.toir.exception.RestException;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.service.file_management.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadFileResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "OTHER") FileCategory category,
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fileService.upload(file, category, currentUserId(user)));
    }

    @PostMapping(value = "/upload/multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<UploadFileResponse>> uploadMultiple(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(defaultValue = "OTHER") FileCategory category,
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fileService.uploadMultiple(files, category, currentUserId(user)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileResponse> getMetadata(
            @PathVariable UUID id,
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.ok(fileService.getMetadata(id, currentUserId(user)));
    }

    @GetMapping("/{id}/presigned-url")
    public ResponseEntity<PresignedUrlResponse> getPresignedUrl(
            @PathVariable UUID id,
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.ok(fileService.getPresignedUrl(id, currentUserId(user)));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable UUID id,
            @CurrentUser AuthenticatedUser user
    ) {
        UUID currentUserId = currentUserId(user);
        FileResponse metadata = fileService.getMetadata(id, currentUserId);
        Resource resource = fileService.download(id, currentUserId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(metadata.originalName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @CurrentUser AuthenticatedUser user
    ) {
        fileService.delete(id, currentUserId(user));
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(AuthenticatedUser user) {
        if (user == null || user.id() == null || user.id().isBlank()) {
            throw RestException.unauthorized("Authenticated user is required");
        }
        return UUID.fromString(user.id());
    }
}
