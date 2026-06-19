package com.toir.service.equipment;

import com.toir.dto.equipment.EquipmentPictureDto;
import com.toir.dto.file.UploadFileResponse;
import com.toir.entity.UploadedFile;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentPicture;
import com.toir.enums.FileCategory;
import com.toir.exception.RestException;
import com.toir.repository.UploadedFileRepository;
import com.toir.repository.equipment.EquipmentPictureRepository;
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
public class EquipmentPictureService {

    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final EquipmentRepository equipmentRepository;
    private final EquipmentPictureRepository equipmentPictureRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final FileService fileService;
    private final ScopeAccessService scopeAccessService;

    @Transactional
    public List<EquipmentPictureDto> uploadPictures(
            UUID equipmentId,
            List<MultipartFile> files,
            List<String> pictureNames,
            String pictureType,
            AuthenticatedUser user
    ) {
        UUID currentUserId = currentUserId(user);
        Equipment equipment = equipmentOrThrow(equipmentId);
        enforceEquipmentAccess(equipment);
        if (files == null || files.isEmpty()) {
            throw RestException.badRequest("At least one equipment picture file is required");
        }
        validateImageFiles(files);
        List<String> normalizedPictureNames = normalizePictureNames(files, pictureNames);
        String normalizedPictureType = normalizePictureType(pictureType);

        List<UUID> uploadedFileIds = new ArrayList<>();
        try {
            List<EquipmentPicture> pictures = new ArrayList<>(files.size());
            for (int i = 0; i < files.size(); i++) {
                UploadFileResponse uploaded = fileService.upload(files.get(i), FileCategory.EQUIPMENT_PICTURE, currentUserId);
                uploadedFileIds.add(uploaded.id());
                UploadedFile uploadedFile = uploadedFileRepository.findByIdAndDeletedFalse(uploaded.id())
                        .orElseThrow(() -> RestException.notFound("Uploaded file not found: " + uploaded.id()));
                pictures.add(EquipmentPicture.builder()
                        .equipment(equipment)
                        .file(uploadedFile)
                        .pictureType(normalizedPictureType)
                        .pictureName(normalizedPictureNames.get(i))
                        .uploadedBy(currentUserId)
                        .build());
            }

            return equipmentPictureRepository.saveAllAndFlush(pictures).stream()
                    .map(picture -> EquipmentPictureDto.from(equipmentId, picture))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (RuntimeException e) {
            cleanupUploadedFiles(uploadedFileIds, currentUserId);
            if (e instanceof RestException restException) {
                throw restException;
            }
            throw RestException.conflict("Could not attach equipment pictures");
        }
    }

    @Transactional(readOnly = true)
    public List<EquipmentPictureDto> getPictures(UUID equipmentId, AuthenticatedUser user) {
        currentUserId(user);
        Equipment equipment = equipmentOrThrow(equipmentId);
        enforceEquipmentAccess(equipment);
        return equipmentPictureRepository.findAllByEquipmentId(equipmentId)
                .stream()
                .map(picture -> toDtoWithMetadata(equipmentId, picture))
                .toList();
    }

    @Transactional(readOnly = true)
    public EquipmentPictureDto getPicture(UUID pictureId, AuthenticatedUser user) {
        currentUserId(user);
        EquipmentPicture picture = pictureOrThrow(pictureId);
        enforceEquipmentAccess(picture.getEquipment());
        return toDtoWithMetadata(picture.getEquipment().getId(), picture);
    }

    @Transactional(readOnly = true)
    public Resource downloadPicture(UUID pictureId, AuthenticatedUser user) {
        currentUserId(user);
        EquipmentPicture picture = pictureOrThrow(pictureId);
        enforceEquipmentAccess(picture.getEquipment());
        return fileService.downloadAuthorizedFile(picture.getFile().getId());
    }

    @Transactional
    public void deletePicture(UUID pictureId, AuthenticatedUser user) {
        currentUserId(user);
        EquipmentPicture picture = pictureOrThrow(pictureId);
        enforceEquipmentAccess(picture.getEquipment());
        picture.setDeleted(true);
        equipmentPictureRepository.save(picture);
        fileService.deleteAuthorizedFile(picture.getFile().getId());
    }

    private Equipment equipmentOrThrow(UUID equipmentId) {
        return equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
    }

    private EquipmentPicture pictureOrThrow(UUID pictureId) {
        return equipmentPictureRepository.findByIdActive(pictureId)
                .orElseThrow(() -> RestException.notFound("Equipment picture not found: " + pictureId));
    }

    private EquipmentPictureDto toDtoWithMetadata(UUID equipmentId, EquipmentPicture picture) {
        fileService.getMetadataForAuthorizedFile(picture.getFile().getId());
        return EquipmentPictureDto.from(equipmentId, picture);
    }

    private void enforceEquipmentAccess(Equipment equipment) {
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
        return "Equipment picture";
    }

    private void cleanupUploadedFiles(List<UUID> fileIds, UUID currentUserId) {
        for (UUID fileId : fileIds) {
            try {
                fileService.delete(fileId, currentUserId);
            } catch (RuntimeException e) {
                log.warn("Failed to cleanup equipment picture file '{}': {}", fileId, e.getMessage());
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
