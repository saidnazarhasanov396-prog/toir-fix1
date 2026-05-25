package com.toir.service.file_management;

import com.toir.config.MinioProperties;
import com.toir.enums.ErrorType;
import com.toir.exception.RestException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3ServiceImpl implements S3Service {

    private static final long MAX_FILE_SIZE = 150 * 1024 * 1024L;

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @PostConstruct
    public void init() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.getBucketName())
                    .build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(properties.getBucketName())
                        .build());
            }
            log.info("MinIO bucket '{}' is ready.", properties.getBucketName());
        } catch (Exception e) {
            log.error("MinIO initialization failed for bucket={}: {}", properties.getBucketName(), e.toString(), e);
        }
    }

    @Override
    public void store(MultipartFile file, String objectName) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucketName())
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.info("Stored file object '{}'", objectName);
        } catch (Exception e) {
            log.error("MinIO upload failed for bucket={}, object={}: {}",
                    properties.getBucketName(), objectName, e.toString(), e);
            throw mapStorageException(e, ErrorType.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public void delete(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            return;
        }
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(properties.getBucketName())
                            .object(objectName)
                            .build()
            );
            log.info("Deleted file object '{}'", objectName);
        } catch (Exception e) {
            log.error("MinIO delete failed for bucket={}, object={}: {}",
                    properties.getBucketName(), objectName, e.toString(), e);
            throw mapStorageException(e, ErrorType.FILE_DELETE_FAILED);
        }
    }

    @Override
    public Resource load(String objectName) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(properties.getBucketName())
                    .object(objectName)
                    .build());
            return new InputStreamResource(minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(properties.getBucketName())
                            .object(objectName)
                            .build()
            ));
        } catch (Exception e) {
            log.error("MinIO download failed for bucket={}, object={}: {}",
                    properties.getBucketName(), objectName, e.toString(), e);
            throw mapStorageException(e, ErrorType.FILE_DOWNLOAD_FAILED);
        }
    }

    @Override
    public String generatePresignedUrl(String objectName, int expiryMinutes) {
        try {
            int expiry = Math.max(1, expiryMinutes);
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(properties.getBucketName())
                            .object(objectName)
                            .expiry(expiry, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            log.error("Presigned URL generation failed for bucket={}, object={}: {}",
                    properties.getBucketName(), objectName, e.toString(), e);
            throw mapStorageException(e, ErrorType.PRESIGNED_URL_FAILED);
        }
    }

    private RestException mapStorageException(Exception e, ErrorType fallback) {
        if (e instanceof ErrorResponseException errorResponseException
                && errorResponseException.errorResponse() != null) {
            return RestException.restThrow(mapStorageErrorCode(errorResponseException.errorResponse().code(), fallback));
        }
        return RestException.restThrow(fallback);
    }

    static ErrorType mapStorageErrorCode(String code, ErrorType fallback) {
        if ("AccessDenied".equals(code)
                || "InvalidAccessKeyId".equals(code)
                || "SignatureDoesNotMatch".equals(code)
                || "InvalidToken".equals(code)
                || "ExpiredToken".equals(code)) {
            return ErrorType.FILE_STORAGE_ACCESS_DENIED;
        }
        if ("NoSuchBucket".equals(code)
                || "InvalidBucketName".equals(code)
                || "NoSuchKey".equals(code)) {
            return ErrorType.FILE_STORAGE_CONFIGURATION_FAILED;
        }
        return fallback;
    }

    @Deprecated
    @Override
    public String uploadFile(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw RestException.restThrow(ErrorType.FILE_TOO_LARGE);
        }
        String objectName = "legacy/" + LocalDate.now().getYear() + "/"
                + String.format("%02d", LocalDate.now().getMonthValue()) + "/"
                + UUID.randomUUID() + extensionOf(file.getOriginalFilename());
        store(file, objectName);
        return buildPublicLikeUrl(objectName);
    }

    @Deprecated
    @Override
    public List<String> uploadFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> uploadedUrls = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    uploadedUrls.add(uploadFile(file));
                }
            }
            return uploadedUrls;
        } catch (Exception e) {
            uploadedUrls.forEach(this::deleteFile);
            throw RestException.restThrow(ErrorType.UPLOAD_FILES_FAILED);
        }
    }

    @Deprecated
    @Override
    public void deleteFile(String fileUrl) {
        String objectName = extractObjectName(fileUrl);
        if (StringUtils.hasText(objectName)) {
            delete(objectName);
        }
    }

    private String buildPublicLikeUrl(String objectName) {
        String baseUrl = StringUtils.hasText(properties.getPublicUrl())
                ? properties.getPublicUrl()
                : properties.getEndpoint();
        return baseUrl.replaceAll("/+$", "") + "/" + properties.getBucketName() + "/" + objectName;
    }

    private String extractObjectName(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return null;
        }
        try {
            String path = URI.create(fileUrl).getPath();
            String prefix = "/" + properties.getBucketName() + "/";
            int index = path.indexOf(prefix);
            if (index >= 0) {
                return path.substring(index + prefix.length());
            }
        } catch (IllegalArgumentException ignored) {
        }
        int bucketIndex = fileUrl.indexOf(properties.getBucketName() + "/");
        if (bucketIndex >= 0) {
            return fileUrl.substring(bucketIndex + properties.getBucketName().length() + 1);
        }
        return fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.')).toLowerCase();
    }
}
