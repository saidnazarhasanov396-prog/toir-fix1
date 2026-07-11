package com.toir.service;

import com.toir.entity.FileAsset;
import com.toir.repository.FileAssetRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.service.file_management.FileValidator;
import com.toir.service.file_management.LocalFileResourceResolver;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileAssetServiceTest {

    @Mock
    FileAssetRepository repository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    LocalFileResourceResolver localFileResourceResolver;

    @Mock
    FileValidator fileValidator;

    @Mock
    FileAssetAccessService accessService;

    @TempDir
    Path storageDir;

    @Test
    void uploadUsesValidatedMetadataAndOpaqueStoredFilename() {
        UUID uploaderId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../evil.pdf",
                "application/x-msdownload",
                "%PDF-1.4\n".getBytes()
        );
        when(fileValidator.validate(file)).thenReturn(FileValidator.ValidatedFile.builder()
                .originalName("evil.pdf")
                .extension("pdf")
                .contentType("application/pdf")
                .size(file.getSize())
                .build());
        when(repository.save(any(FileAsset.class))).thenAnswer(invocation -> {
            FileAsset asset = invocation.getArgument(0);
            asset.setId(UUID.randomUUID());
            return asset;
        });

        FileAssetService service = new FileAssetService(
                repository,
                auditBuilderService,
                localFileResourceResolver,
                fileValidator,
                accessService,
                storageDir.toString()
        );

        service.upload(file, "equipment-warranty", "eq-1", uploaderId);

        ArgumentCaptor<FileAsset> captor = ArgumentCaptor.forClass(FileAsset.class);
        verify(repository).save(captor.capture());
        FileAsset saved = captor.getValue();
        assertThat(saved.getOriginalName()).isEqualTo("evil.pdf");
        assertThat(saved.getMimeType()).isEqualTo("application/pdf");
        assertThat(saved.getSizeBytes()).isEqualTo(file.getSize());
        assertThat(saved.getFileName()).endsWith(".pdf");
        assertThat(saved.getFileName()).doesNotContain("evil");
        assertThat(saved.getFileName()).doesNotContain("/");
        assertThat(Path.of(saved.getStoragePath()).normalize()).startsWith(storageDir.toAbsolutePath().normalize());
        assertThat(Files.exists(Path.of(saved.getStoragePath()))).isTrue();
    }

    @Test
    void uploadRemovesLocalFileWhenMetadataSaveFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.pdf",
                "application/pdf",
                "%PDF-1.4\n".getBytes()
        );
        when(fileValidator.validate(file)).thenReturn(FileValidator.ValidatedFile.builder()
                .originalName("report.pdf")
                .extension("pdf")
                .contentType("application/pdf")
                .size(file.getSize())
                .build());
        when(repository.save(any(FileAsset.class))).thenThrow(new RuntimeException("database unavailable"));

        assertThatThrownBy(() -> service().upload(file, "equipment-warranty", "eq-1", UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("database unavailable");

        try (var storedFiles = Files.list(storageDir)) {
            assertThat(storedFiles).isEmpty();
        }
    }

    @Test
    void uploadRemovesLocalFileWhenAuditSchedulingFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "%PDF-1.4\n".getBytes());
        when(fileValidator.validate(file)).thenReturn(FileValidator.ValidatedFile.builder()
                .originalName("report.pdf")
                .extension("pdf")
                .contentType("application/pdf")
                .size(file.getSize())
                .build());
        when(repository.save(any(FileAsset.class))).thenAnswer(invocation -> {
            FileAsset asset = invocation.getArgument(0);
            asset.setId(UUID.randomUUID());
            return asset;
        });
        org.mockito.Mockito.doThrow(new RuntimeException("audit unavailable"))
                .when(auditBuilderService)
                .log(any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service().upload(file, "equipment-warranty", "eq-1", UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("audit unavailable");

        try (var storedFiles = Files.list(storageDir)) {
            assertThat(storedFiles).isEmpty();
        }
    }


    @Test
    void downloadChecksOwningEntityAccessForKnownEntityFiles() {
        UUID fileId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FileAsset asset = asset(fileId, "RepairRequest", requestId.toString(), UUID.randomUUID());
        when(repository.findByIdAndIsDeletedFalse(fileId)).thenReturn(Optional.of(asset));

        FileAssetService service = service();

        service.download(fileId, user(userId));

        verify(accessService).assertCanAccess(asset, user(userId));
        verify(localFileResourceResolver).load(asset.getStoragePath());
    }

    @Test
    void downloadRejectsKnownEntityFileWhenEntityScopeDeniesAccess() {
        UUID fileId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FileAsset asset = asset(fileId, "RepairRequest", requestId.toString(), UUID.randomUUID());
        when(repository.findByIdAndIsDeletedFalse(fileId)).thenReturn(Optional.of(asset));
        org.mockito.Mockito.doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(accessService)
                .assertCanAccess(asset, user(userId));

        FileAssetService service = service();

        assertThatThrownBy(() -> service.download(fileId, user(userId)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessage("denied");
        verify(localFileResourceResolver, never()).load(any());
    }

    @Test
    void downloadRejectsMiscFileForNonUploader() {
        UUID fileId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        FileAsset asset = asset(fileId, "misc", "", uploaderId);
        AuthenticatedUser currentUser = user(otherUserId);
        when(repository.findByIdAndIsDeletedFalse(fileId)).thenReturn(Optional.of(asset));
        org.mockito.Mockito.doThrow(new org.springframework.security.access.AccessDeniedException("Access denied by file owner scope"))
                .when(accessService)
                .assertCanAccess(asset, currentUser);

        FileAssetService service = service();

        assertThatThrownBy(() -> service.download(fileId, currentUser))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessage("Access denied by file owner scope");
        verify(localFileResourceResolver, never()).load(any());
    }

    @Test
    void findByEntityChecksEntityAccessBeforeReturningFiles() {
        UUID requestId = UUID.randomUUID();
        FileAsset asset = asset(UUID.randomUUID(), "RepairRequest", requestId.toString(), UUID.randomUUID());
        AuthenticatedUser currentUser = user(UUID.randomUUID());
        when(repository.findAllByEntityTypeAndEntityIdAndIsDeletedFalse("RepairRequest", requestId.toString()))
                .thenReturn(List.of(asset));
        when(accessService.canAccess(asset, currentUser)).thenReturn(true);

        FileAssetService service = service();

        assertThat(service.findByEntity("RepairRequest", requestId.toString(), currentUser))
                .hasSize(1);
        verify(accessService).assertCanAccessEntityReference("RepairRequest", requestId.toString(), currentUser);
    }

    private FileAssetService service() {
        return new FileAssetService(
                repository,
                auditBuilderService,
                localFileResourceResolver,
                fileValidator,
                accessService,
                storageDir.toString()
        );
    }

    private AuthenticatedUser user(UUID userId) {
        return new AuthenticatedUser(userId.toString(), "user", "user@example.com", "User", null, "VIEWER", List.of());
    }

    private FileAsset asset(UUID id, String entityType, String entityId, UUID uploadedById) {
        FileAsset asset = new FileAsset();
        asset.setId(id);
        asset.setFileName(id + ".pdf");
        asset.setOriginalName("file.pdf");
        asset.setMimeType("application/pdf");
        asset.setSizeBytes(42);
        asset.setStoragePath(storageDir.resolve(id + ".pdf").toString());
        asset.setEntityType(entityType);
        asset.setEntityId(entityId);
        asset.setUploadedById(uploadedById);
        asset.setCreatedAt(Instant.now());
        return asset;
    }

}
