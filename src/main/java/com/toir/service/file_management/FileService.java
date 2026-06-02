package com.toir.service.file_management;

import com.toir.dto.file.FileResponse;
import com.toir.dto.file.PresignedUrlResponse;
import com.toir.dto.file.UploadFileResponse;
import com.toir.enums.FileCategory;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface FileService {
    UploadFileResponse upload(MultipartFile file, FileCategory category, UUID currentUserId);

    FileResponse getMetadata(UUID fileId, UUID currentUserId);

    PresignedUrlResponse getPresignedUrl(UUID fileId, UUID currentUserId);

    void delete(UUID fileId, UUID currentUserId);

    Resource download(UUID fileId, UUID currentUserId);
}
