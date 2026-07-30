package com.toir.service.users;

import com.toir.dto.file.UploadFileResponse;
import com.toir.enums.FileCategory;
import com.toir.service.file_management.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileAvatarStorageService {

    private final FileService fileService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UploadFileResponse upload(MultipartFile file, UUID currentUserId) {
        return fileService.upload(file, FileCategory.USER_AVATAR, currentUserId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteOwnedFile(UUID fileId, UUID currentUserId) {
        fileService.delete(fileId, currentUserId);
    }
}
