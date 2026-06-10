package com.toir.service.file_management;

import com.toir.entity.UploadedFile;
import com.toir.enums.FileCategory;
import com.toir.exception.RestException;
import com.toir.repository.UploadedFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    @Mock
    S3Service s3Service;

    @Mock
    UploadedFileRepository repository;

    @Mock
    FileValidator validator;

    private FileServiceImpl service;
    private LocalFileResourceResolver localFileResourceResolver;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        localFileResourceResolver = new LocalFileResourceResolver("uploads");
        service = new FileServiceImpl(s3Service, repository, validator, localFileResourceResolver);
        ownerId = UUID.randomUUID();
    }

    @Test
    void uploadSavesMetadata() {
        MockMultipartFile file = file("report.pdf");
        when(validator.validate(file)).thenReturn(validated("report.pdf", "pdf", "application/pdf"));
        when(repository.save(any(UploadedFile.class))).thenAnswer(invocation -> {
            UploadedFile uploadedFile = invocation.getArgument(0);
            uploadedFile.setId(UUID.randomUUID());
            return uploadedFile;
        });

        var response = service.upload(file, FileCategory.DOCUMENT, ownerId);

        assertThat(response.id()).isNotNull();
        assertThat(response.originalName()).isEqualTo("report.pdf");
        ArgumentCaptor<UploadedFile> captor = ArgumentCaptor.captor();
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getObjectName()).startsWith("documents/");
    }

    @Test
    void uploadDeletesMinioObjectIfDbSaveFails() {
        MockMultipartFile file = file("report.pdf");
        when(validator.validate(file)).thenReturn(validated("report.pdf", "pdf", "application/pdf"));
        when(repository.save(any(UploadedFile.class))).thenThrow(new RuntimeException("db"));

        assertThatThrownBy(() -> service.upload(file, FileCategory.DOCUMENT, ownerId))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<String> objectName = ArgumentCaptor.captor();
        verify(s3Service).delete(objectName.capture());
        assertThat(objectName.getValue()).startsWith("documents/");
    }

    @Test
    void getMetadataRejectsNonOwner() {
        UUID otherUser = UUID.randomUUID();
        UploadedFile uploadedFile = uploadedFile(ownerId);
        when(repository.findByIdAndDeletedFalse(uploadedFile.getId())).thenReturn(Optional.of(uploadedFile));

        assertThatThrownBy(() -> service.getMetadata(uploadedFile.getId(), otherUser))
                .isInstanceOf(RestException.class)
                .hasMessage("File access denied");
    }

    @Test
    void deleteSoftDeletesMetadata() {
        UploadedFile uploadedFile = uploadedFile(ownerId);
        when(repository.findByIdAndDeletedFalse(uploadedFile.getId())).thenReturn(Optional.of(uploadedFile));
        when(repository.save(uploadedFile)).thenReturn(uploadedFile);

        service.delete(uploadedFile.getId(), ownerId);

        assertThat(uploadedFile.getDeleted()).isTrue();
        assertThat(uploadedFile.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteCallsS3Delete() {
        UploadedFile uploadedFile = uploadedFile(ownerId);
        when(repository.findByIdAndDeletedFalse(uploadedFile.getId())).thenReturn(Optional.of(uploadedFile));
        when(repository.save(uploadedFile)).thenReturn(uploadedFile);

        service.delete(uploadedFile.getId(), ownerId);

        verify(s3Service).delete(uploadedFile.getObjectName());
    }

    @Test
    void downloadUsesLocalResolverForLegacyFileUrlWithEncodedSpaces() throws Exception {
        Path storageRoot = Files.createTempDirectory("toir-file-service");
        Path filePath = storageRoot.resolve("Screenshot 2026.png");
        Files.writeString(filePath, "image-bytes", StandardCharsets.UTF_8);
        service = new FileServiceImpl(
                s3Service,
                repository,
                validator,
                new LocalFileResourceResolver(storageRoot.toString()));
        UploadedFile uploadedFile = uploadedFile(ownerId);
        uploadedFile.setObjectName(filePath.toUri().toString().replace("Screenshot%202026.png", "./Screenshot%202026.png"));
        uploadedFile.setContentType("image/png");
        when(repository.findByIdAndDeletedFalse(uploadedFile.getId())).thenReturn(Optional.of(uploadedFile));

        var resource = service.download(uploadedFile.getId(), ownerId);

        assertThat(resource.contentLength()).isEqualTo("image-bytes".getBytes(StandardCharsets.UTF_8).length);
        assertThat(resource.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("image-bytes");
    }

    @Test
    void downloadReturnsNotFoundForMissingLegacyLocalFile() {
        Path storageRoot = Path.of("uploads").toAbsolutePath().normalize();
        service = new FileServiceImpl(
                s3Service,
                repository,
                validator,
                new LocalFileResourceResolver(storageRoot.toString()));
        UploadedFile uploadedFile = uploadedFile(ownerId);
        uploadedFile.setObjectName(storageRoot.resolve("missing file.pdf").toUri().toString());
        when(repository.findByIdAndDeletedFalse(uploadedFile.getId())).thenReturn(Optional.of(uploadedFile));

        assertThatThrownBy(() -> service.download(uploadedFile.getId(), ownerId))
                .isInstanceOf(RestException.class)
                .hasMessage("File not found");
    }

    private MockMultipartFile file(String name) {
        return new MockMultipartFile("file", name, "application/pdf", "%PDF-1.4\n".getBytes());
    }

    private FileValidator.ValidatedFile validated(String originalName, String extension, String contentType) {
        return FileValidator.ValidatedFile.builder()
                .originalName(originalName)
                .extension(extension)
                .contentType(contentType)
                .size(10L)
                .build();
    }

    private UploadedFile uploadedFile(UUID uploadedBy) {
        return UploadedFile.builder()
                .id(UUID.randomUUID())
                .originalName("report.pdf")
                .storedName(UUID.randomUUID() + ".pdf")
                .objectName("documents/2026/05/report.pdf")
                .contentType("application/pdf")
                .extension("pdf")
                .size(10L)
                .uploadedBy(uploadedBy)
                .category(FileCategory.DOCUMENT)
                .deleted(false)
                .build();
    }
}
