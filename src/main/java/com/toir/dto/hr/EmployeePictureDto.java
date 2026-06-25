package com.toir.dto.hr;

import com.toir.entity.UploadedFile;
import com.toir.entity.users.EmployeePicture;

import java.time.LocalDateTime;
import java.util.UUID;

public record EmployeePictureDto(
        UUID id,
        UUID employeeId,
        String pictureName,
        String pictureType,
        String originalName,
        String contentType,
        Long size,
        LocalDateTime uploadedAt,
        UUID uploadedBy,
        String downloadUrl
) {
    public static EmployeePictureDto from(UUID employeeId, EmployeePicture picture) {
        if (picture == null || picture.getFile() == null || Boolean.TRUE.equals(picture.getFile().getDeleted())) {
            return null;
        }
        UploadedFile file = picture.getFile();
        return new EmployeePictureDto(
                picture.getId(),
                employeeId,
                picture.getPictureName(),
                picture.getPictureType(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSize(),
                picture.getUploadedAt(),
                picture.getUploadedBy(),
                "/api/v1/hr/employee-pictures/" + picture.getId() + "/download"
        );
    }
}
