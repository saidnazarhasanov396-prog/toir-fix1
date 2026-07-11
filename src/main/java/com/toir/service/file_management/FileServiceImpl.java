package com.toir.service.file_management;

import com.toir.dto.file.FileResponse;
import com.toir.dto.file.PresignedUrlResponse;
import com.toir.dto.file.UploadFileResponse;
import com.toir.entity.UploadedFile;
import com.toir.enums.ErrorType;
import com.toir.enums.FileCategory;
import com.toir.exception.RestException;
import com.toir.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final int PRESIGNED_URL_EXPIRY_MINUTES = 15;

    private final S3Service s3Service;
    private final UploadedFileRepository uploadedFileRepository;
    private final FileValidator fileValidator;
    private final LocalFileResourceResolver localFileResourceResolver;

    @Override
    @Transactional
    public UploadFileResponse upload(MultipartFile file, FileCategory category, UUID currentUserId) {
        assertCurrentUser(currentUserId);
        FileCategory safeCategory = category != null ? category : FileCategory.OTHER;
        FileValidator.ValidatedFile validated = fileValidator.validate(file);
        UUID storedId = UUID.randomUUID();
        String storedName = storedId + "." + validated.extension();
        String objectName = buildObjectName(safeCategory, storedName);

        s3Service.store(new ValidatedContentTypeMultipartFile(file, validated.contentType()), objectName);
        try {
            UploadedFile uploadedFile = UploadedFile.builder()
                    .originalName(validated.originalName())
                    .storedName(storedName)
                    .objectName(objectName)
                    .url(null)
                    .contentType(validated.contentType())
                    .extension(validated.extension())
                    .size(validated.size())
                    .uploadedBy(currentUserId)
                    .category(safeCategory)
                    .deleted(false)
                    .build();
            UploadedFile saved = uploadedFileRepository.save(uploadedFile);
            log.info("Uploaded file metadata saved with id '{}'", saved.getId());
            return UploadFileResponse.from(saved);
        } catch (RuntimeException e) {
            rollbackObject(objectName);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FileResponse getMetadata(UUID fileId, UUID currentUserId) {
        UploadedFile file = findOwnedFile(fileId, currentUserId);
        return FileResponse.from(file);
    }

    @Override
    @Transactional(readOnly = true)
    public FileResponse getMetadataForAuthorizedFile(UUID fileId) {
        UploadedFile file = findActiveFile(fileId);
        return FileResponse.from(file);
    }

    @Override
    @Transactional(readOnly = true)
    public PresignedUrlResponse getPresignedUrl(UUID fileId, UUID currentUserId) {
        UploadedFile file = findOwnedFile(fileId, currentUserId);
        return presignedUrl(file);
    }

    @Override
    @Transactional(readOnly = true)
    public PresignedUrlResponse getPresignedUrlForAuthorizedFile(UUID fileId) {
        UploadedFile file = findActiveFile(fileId);
        return presignedUrl(file);
    }

    @Override
    @Transactional
    public void delete(UUID fileId, UUID currentUserId) {
        UploadedFile file = findOwnedFile(fileId, currentUserId);
        softDelete(file);
    }

    @Override
    @Transactional
    public void deleteAuthorizedFile(UUID fileId) {
        UploadedFile file = findActiveFile(fileId);
        softDelete(file);
    }

    private void softDelete(UploadedFile file) {
        s3Service.delete(file.getObjectName());
        file.setDeleted(true);
        file.setDeletedAt(LocalDateTime.now());
        uploadedFileRepository.save(file);
        log.info("Soft deleted uploaded file '{}'", file.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Resource download(UUID fileId, UUID currentUserId) {
        UploadedFile file = findOwnedFile(fileId, currentUserId);
        return load(file);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadAuthorizedFile(UUID fileId) {
        UploadedFile file = findActiveFile(fileId);
        return load(file);
    }

    private PresignedUrlResponse presignedUrl(UploadedFile file) {
        String url = s3Service.generatePresignedUrl(file.getObjectName(), PRESIGNED_URL_EXPIRY_MINUTES);
        return PresignedUrlResponse.builder()
                .fileId(file.getId())
                .url(url)
                .expiresAt(LocalDateTime.now().plusMinutes(PRESIGNED_URL_EXPIRY_MINUTES))
                .build();
    }

    private Resource load(UploadedFile file) {
        if (LocalFileResourceResolver.looksLikeLocalReference(file.getObjectName())) {
            return localFileResourceResolver.load(file.getObjectName());
        }
        if (LocalFileResourceResolver.looksLikeLocalReference(file.getUrl())) {
            return localFileResourceResolver.load(file.getUrl());
        }
        return s3Service.load(file.getObjectName());
    }

    private UploadedFile findActiveFile(UUID fileId) {
        return uploadedFileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> RestException.restThrow(ErrorType.FILE_NOT_FOUND));
    }

    private UploadedFile findOwnedFile(UUID fileId, UUID currentUserId) {
        assertCurrentUser(currentUserId);
        UploadedFile file = findActiveFile(fileId);
        if (!currentUserId.equals(file.getUploadedBy())) {
            throw RestException.restThrow(ErrorType.FILE_ACCESS_DENIED);
        }
        return file;
    }

    private void assertCurrentUser(UUID currentUserId) {
        if (currentUserId == null) {
            throw RestException.unauthorized("Authenticated user is required");
        }
    }

    private String buildObjectName(FileCategory category, String storedName) {
        LocalDate now = LocalDate.now();
        return category.folder()
                + "/" + now.getYear()
                + "/" + String.format(Locale.ROOT, "%02d", now.getMonthValue())
                + "/" + storedName;
    }

    private void rollbackObject(String objectName) {
        try {
            s3Service.delete(objectName);
        } catch (RuntimeException rollbackError) {
            log.warn("Failed to rollback uploaded object '{}': {}", objectName, rollbackError.getMessage());
        }
    }

    private record ValidatedContentTypeMultipartFile(
            MultipartFile delegate,
            String validatedContentType
    ) implements MultipartFile {

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public String getOriginalFilename() {
            return delegate.getOriginalFilename();
        }

        @Override
        public String getContentType() {
            return validatedContentType;
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public long getSize() {
            return delegate.getSize();
        }

        @Override
        public byte[] getBytes() throws IOException {
            return delegate.getBytes();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return delegate.getInputStream();
        }

        @Override
        public void transferTo(File destination) throws IOException, IllegalStateException {
            delegate.transferTo(destination);
        }
    }

}
