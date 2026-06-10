package com.toir.dto.vehicle;

import com.toir.entity.UploadedFile;
import com.toir.entity.equipment.VehiclePicture;

import java.time.LocalDateTime;
import java.util.UUID;

public record VehiclePictureDto(
        UUID id,
        UUID vehicleId,
        String pictureName,
        String pictureType,
        String originalName,
        String contentType,
        Long size,
        LocalDateTime uploadedAt,
        UUID uploadedBy,
        String downloadUrl
) {
    public static VehiclePictureDto from(UUID equipmentId, VehiclePicture picture) {
        if (picture == null || picture.getFile() == null || Boolean.TRUE.equals(picture.getFile().getDeleted())) {
            return null;
        }
        UploadedFile file = picture.getFile();
        return new VehiclePictureDto(
                picture.getId(),
                equipmentId,
                picture.getPictureName(),
                picture.getPictureType(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSize(),
                picture.getUploadedAt(),
                picture.getUploadedBy(),
                "/api/v1/vehicles/pictures/" + picture.getId() + "/download"
        );
    }
}
