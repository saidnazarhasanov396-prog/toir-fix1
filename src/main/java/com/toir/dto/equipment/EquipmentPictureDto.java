package com.toir.dto.equipment;

import com.toir.entity.UploadedFile;
import com.toir.entity.equipment.EquipmentPicture;

import java.time.LocalDateTime;
import java.util.UUID;

public record EquipmentPictureDto(
        UUID id,
        UUID equipmentId,
        String pictureName,
        String pictureType,
        String originalName,
        String contentType,
        Long size,
        LocalDateTime uploadedAt,
        UUID uploadedBy,
        String downloadUrl
) {
    public static EquipmentPictureDto from(UUID equipmentId, EquipmentPicture picture) {
        if (picture == null || picture.getFile() == null || Boolean.TRUE.equals(picture.getFile().getDeleted())) {
            return null;
        }
        UploadedFile file = picture.getFile();
        return new EquipmentPictureDto(
                picture.getId(),
                equipmentId,
                picture.getPictureName(),
                picture.getPictureType(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSize(),
                picture.getUploadedAt(),
                picture.getUploadedBy(),
                "/api/v1/equipment/pictures/" + picture.getId() + "/download"
        );
    }
}
