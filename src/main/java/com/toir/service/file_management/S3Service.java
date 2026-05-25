package com.toir.service.file_management;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface S3Service {
    void store(MultipartFile file, String objectName);

    void delete(String objectName);

    Resource load(String objectName);

    String generatePresignedUrl(String objectName, int expiryMinutes);

    @Deprecated
    String uploadFile(MultipartFile file);

    @Deprecated
    List<String> uploadFiles(List<MultipartFile> files);

    @Deprecated
    void deleteFile(String fileUrl);
}
