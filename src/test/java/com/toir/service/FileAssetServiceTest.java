package com.toir.service;

import com.toir.entity.FileAsset;
import com.toir.repository.FileAssetRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
}
