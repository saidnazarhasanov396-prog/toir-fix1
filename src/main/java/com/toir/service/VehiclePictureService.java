package com.toir.service;

import com.toir.dto.file.UploadFileResponse;
import com.toir.dto.vehicle.VehiclePictureDto;
import com.toir.entity.UploadedFile;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.entity.equipment.VehiclePicture;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.FileCategory;
import com.toir.exception.RestException;
import com.toir.repository.UploadedFileRepository;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.VehiclePictureRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.security.ScopeAccessService;
import com.toir.service.file_management.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VehiclePictureService {

    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final EquipmentRepository equipmentRepository;
    private final VehicleDetailsRepository vehicleDetailsRepository;
    private final VehiclePictureRepository vehiclePictureRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final FileService fileService;
    private final ScopeAccessService scopeAccessService;

    @Transactional
    public List<VehiclePictureDto> uploadPictures(
            UUID equipmentId,
            List<MultipartFile> files,
            List<String> pictureNames,
            String pictureType,
            AuthenticatedUser user
    ) {
        UUID currentUserId = currentUserId(user);
        Equipment equipment = vehicleEquipmentOrThrow(equipmentId);
        enforceVehicleAccess(equipment);
        if (files == null || files.isEmpty()) {
            throw RestException.badRequest("At least one vehicle picture file is required");
        }
        validateImageFiles(files);
        List<String> normalizedPictureNames = normalizePictureNames(files, pictureNames);
        String normalizedPictureType = normalizePictureType(pictureType);
        VehicleDetails details = vehicleDetailsOrThrow(equipmentId);

        List<UUID> uploadedFileIds = new ArrayList<>();
        try {
            List<VehiclePicture> pictures = new ArrayList<>(files.size());
            for (int i = 0; i < files.size(); i++) {
                UploadFileResponse uploaded = fileService.upload(files.get(i), FileCategory.VEHICLE_PICTURE, currentUserId);
                uploadedFileIds.add(uploaded.id());
                UploadedFile uploadedFile = uploadedFileRepository.findByIdAndDeletedFalse(uploaded.id())
                        .orElseThrow(() -> RestException.notFound("Uploaded file not found: " + uploaded.id()));
                pictures.add(VehiclePicture.builder()
                        .vehicleDetails(details)
                        .file(uploadedFile)
                        .pictureType(normalizedPictureType)
                        .pictureName(normalizedPictureNames.get(i))
                        .uploadedBy(currentUserId)
                        .build());
            }

            return vehiclePictureRepository.saveAllAndFlush(pictures).stream()
                    .map(picture -> VehiclePictureDto.from(equipmentId, picture))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (RuntimeException e) {
            cleanupUploadedFiles(uploadedFileIds, currentUserId);
            if (e instanceof RestException restException) {
                throw restException;
            }
            throw RestException.conflict("Could not attach vehicle pictures");
        }
    }

    @Transactional(readOnly = true)
    public List<VehiclePictureDto> getPictures(UUID equipmentId, AuthenticatedUser user) {
        UUID currentUserId = currentUserId(user);
        Equipment equipment = vehicleEquipmentOrThrow(equipmentId);
        enforceVehicleAccess(equipment);
        return vehiclePictureRepository.findAllByEquipmentId(equipmentId)
                .stream()
                .map(picture -> toDtoWithMetadata(equipmentId, picture, currentUserId))
                .toList();
    }

    @Transactional(readOnly = true)
    public VehiclePictureDto getPicture(UUID pictureId, AuthenticatedUser user) {
        UUID currentUserId = currentUserId(user);
        VehiclePicture picture = pictureOrThrow(pictureId);
        Equipment equipment = vehicleEquipmentOrThrow(picture.getVehicleDetails().getEquipmentId());
        enforceVehicleAccess(equipment);
        return toDtoWithMetadata(equipment.getId(), picture, currentUserId);
    }

    @Transactional(readOnly = true)
    public Resource downloadPicture(UUID pictureId, AuthenticatedUser user) {
        UUID currentUserId = currentUserId(user);
        VehiclePicture picture = pictureOrThrow(pictureId);
        Equipment equipment = vehicleEquipmentOrThrow(picture.getVehicleDetails().getEquipmentId());
        enforceVehicleAccess(equipment);
        return fileService.download(picture.getFile().getId(), currentUserId);
    }

    @Transactional
    public void deletePicture(UUID pictureId, AuthenticatedUser user) {
        UUID currentUserId = currentUserId(user);
        VehiclePicture picture = pictureOrThrow(pictureId);
        Equipment equipment = vehicleEquipmentOrThrow(picture.getVehicleDetails().getEquipmentId());
        enforceVehicleAccess(equipment);
        picture.setDeleted(true);
        vehiclePictureRepository.save(picture);
        fileService.delete(picture.getFile().getId(), currentUserId);
    }

    private Equipment vehicleEquipmentOrThrow(UUID equipmentId) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        if (equipment.getCategory() != EquipmentCategory.VEHICLE) {
            throw RestException.badRequest("Equipment is not a vehicle: " + equipmentId);
        }
        return equipment;
    }

    private VehicleDetails vehicleDetailsOrThrow(UUID equipmentId) {
        return vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Vehicle details not found: " + equipmentId));
    }

    private VehiclePicture pictureOrThrow(UUID pictureId) {
        return vehiclePictureRepository.findByIdActive(pictureId)
                .orElseThrow(() -> RestException.notFound("Vehicle picture not found: " + pictureId));
    }

    private VehiclePictureDto toDtoWithMetadata(UUID equipmentId, VehiclePicture picture, UUID currentUserId) {
        fileService.getMetadata(picture.getFile().getId(), currentUserId);
        return VehiclePictureDto.from(equipmentId, picture);
    }

    private void enforceVehicleAccess(Equipment equipment) {
        scopeAccessService.assertCanAccessEquipmentScope(
                equipment.getResponsibleDepartmentId(),
                equipment.getDepartmentId()
        );
    }

    private void validateImageFiles(List<MultipartFile> files) {
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String contentType = file == null ? null : file.getContentType();
            if (contentType == null || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType.toLowerCase())) {
                throw RestException.badRequest("files[" + i + "] must be an image file (jpeg, png, webp, or gif)");
            }
        }
    }

    private List<String> normalizePictureNames(List<MultipartFile> files, List<String> pictureNames) {
        if (pictureNames != null && !pictureNames.isEmpty() && pictureNames.size() != files.size()) {
            throw RestException.badRequest("files and pictureNames must have the same length");
        }
        List<String> normalized = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            String pictureName = pictureNames == null || pictureNames.isEmpty() ? defaultPictureName(files.get(i)) : pictureNames.get(i);
            if (pictureName == null || pictureName.isBlank()) {
                throw RestException.badRequest("pictureNames[" + i + "] must not be blank");
            }
            String trimmed = pictureName.trim();
            if (trimmed.length() > 255) {
                throw RestException.badRequest("pictureNames[" + i + "] must be 255 characters or fewer");
            }
            normalized.add(trimmed);
        }
        return normalized;
    }

    private String normalizePictureType(String pictureType) {
        if (pictureType == null || pictureType.isBlank()) {
            return null;
        }
        String trimmed = pictureType.trim();
        if (trimmed.length() > 64) {
            throw RestException.badRequest("pictureType must be 64 characters or fewer");
        }
        return trimmed;
    }

    private String defaultPictureName(MultipartFile file) {
        if (file != null && file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()) {
            return file.getOriginalFilename().trim();
        }
        return "Vehicle picture";
    }

    private void cleanupUploadedFiles(List<UUID> fileIds, UUID currentUserId) {
        for (UUID fileId : fileIds) {
            try {
                fileService.delete(fileId, currentUserId);
            } catch (RuntimeException e) {
                log.warn("Failed to cleanup vehicle picture file '{}': {}", fileId, e.getMessage());
            }
        }
    }

    private UUID currentUserId(AuthenticatedUser user) {
        if (user == null || user.id() == null || user.id().isBlank()) {
            throw RestException.unauthorized("Authenticated user is required");
        }
        return UUID.fromString(user.id());
    }
}
