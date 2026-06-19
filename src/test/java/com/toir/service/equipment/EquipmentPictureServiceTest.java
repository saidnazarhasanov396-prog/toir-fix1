package com.toir.service.equipment;

import com.toir.dto.file.FileResponse;
import com.toir.dto.file.UploadFileResponse;
import com.toir.entity.UploadedFile;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentPicture;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.FileCategory;
import com.toir.exception.RestException;
import com.toir.repository.UploadedFileRepository;
import com.toir.repository.equipment.EquipmentPictureRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.security.ScopeAccessService;
import com.toir.service.file_management.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentPictureServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    EquipmentPictureRepository equipmentPictureRepository;

    @Mock
    UploadedFileRepository uploadedFileRepository;

    @Mock
    FileService fileService;

    @Mock
    ScopeAccessService scopeAccessService;

    EquipmentPictureService service;
    UUID userId;

    @BeforeEach
    void setUp() {
        service = new EquipmentPictureService(
                equipmentRepository,
                equipmentPictureRepository,
                uploadedFileRepository,
                fileService,
                scopeAccessService
        );
        userId = UUID.randomUUID();
    }

    @Test
    void uploadPicturesStoresImagesThroughFileService() {
        UUID equipmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId);
        UploadedFile uploadedFile = uploadedFile(fileId, "front.png", "image/png");
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(fileService.upload(any(), eq(FileCategory.EQUIPMENT_PICTURE), eq(userId)))
                .thenReturn(UploadFileResponse.builder().id(fileId).build());
        when(uploadedFileRepository.findByIdAndDeletedFalse(fileId)).thenReturn(Optional.of(uploadedFile));
        when(equipmentPictureRepository.saveAllAndFlush(any()))
                .thenAnswer(invocation -> {
                    List<EquipmentPicture> pictures = invocation.getArgument(0);
                    pictures.getFirst().setId(UUID.randomUUID());
                    return pictures;
                });

        var result = service.uploadPictures(
                equipmentId,
                List.of(new MockMultipartFile("files", "front.png", "image/png", "png".getBytes())),
                List.of("Front"),
                "INSPECTION",
                authenticatedUser()
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().pictureName()).isEqualTo("Front");
        assertThat(result.getFirst().downloadUrl()).startsWith("/api/v1/equipment/pictures/");
        verify(fileService).upload(any(), eq(FileCategory.EQUIPMENT_PICTURE), eq(userId));
        verify(scopeAccessService).assertCanAccessEquipmentScope(null, equipment.getDepartmentId());
    }

    @Test
    void uploadPicturesRejectsNonImageBeforeStorage() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        assertThatThrownBy(() -> service.uploadPictures(
                equipmentId,
                List.of(new MockMultipartFile("files", "bad.pdf", "application/pdf", "%PDF".getBytes())),
                List.of("Bad"),
                null,
                authenticatedUser()
        ))
                .isInstanceOf(RestException.class)
                .hasMessage("files[0] must be an image file (jpeg, png, webp, or gif)");

        verify(fileService, never()).upload(any(), any(), any());
    }

    @Test
    void getPicturesUsesAuthorizedMetadataAfterEquipmentScopeCheck() {
        UUID equipmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId);
        EquipmentPicture picture = equipmentPicture(equipment, uploadedFile(fileId, "front.png", "image/png"));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(equipmentPictureRepository.findAllByEquipmentId(equipmentId)).thenReturn(List.of(picture));
        when(fileService.getMetadataForAuthorizedFile(fileId)).thenReturn(fileResponse(fileId));

        var result = service.getPictures(equipmentId, authenticatedUser());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().downloadUrl()).isEqualTo("/api/v1/equipment/pictures/" + picture.getId() + "/download");
        verify(scopeAccessService).assertCanAccessEquipmentScope(null, equipment.getDepartmentId());
        verify(fileService).getMetadataForAuthorizedFile(fileId);
        verify(fileService, never()).getMetadata(eq(fileId), any(UUID.class));
    }

    @Test
    void downloadPictureUsesAuthorizedFileDownloadAfterEquipmentScopeCheck() {
        UUID fileId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID());
        EquipmentPicture picture = equipmentPicture(equipment, uploadedFile(fileId, "front.png", "image/png"));
        when(equipmentPictureRepository.findByIdActive(picture.getId())).thenReturn(Optional.of(picture));
        when(fileService.downloadAuthorizedFile(fileId)).thenReturn(new ByteArrayResource("image".getBytes()));

        var resource = service.downloadPicture(picture.getId(), authenticatedUser());

        assertThat(resource).isNotNull();
        verify(scopeAccessService).assertCanAccessEquipmentScope(null, equipment.getDepartmentId());
        verify(fileService).downloadAuthorizedFile(fileId);
        verify(fileService, never()).download(eq(fileId), any(UUID.class));
    }

    @Test
    void deletePictureUsesAuthorizedFileDeleteAfterEquipmentScopeCheck() {
        UUID fileId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID());
        EquipmentPicture picture = equipmentPicture(equipment, uploadedFile(fileId, "front.png", "image/png"));
        when(equipmentPictureRepository.findByIdActive(picture.getId())).thenReturn(Optional.of(picture));

        service.deletePicture(picture.getId(), authenticatedUser());

        assertThat(picture.isDeleted()).isTrue();
        verify(scopeAccessService).assertCanAccessEquipmentScope(null, equipment.getDepartmentId());
        verify(equipmentPictureRepository).save(picture);
        verify(fileService).deleteAuthorizedFile(fileId);
        verify(fileService, never()).delete(eq(fileId), any(UUID.class));
    }

    @Test
    void uploadPicturesAcceptsImageJpgAliasBeforeStorage() {
        UUID equipmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId);
        UploadedFile uploadedFile = uploadedFile(fileId, "front.jpg", "image/jpeg");
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(fileService.upload(any(), eq(FileCategory.EQUIPMENT_PICTURE), eq(userId)))
                .thenReturn(UploadFileResponse.builder().id(fileId).build());
        when(uploadedFileRepository.findByIdAndDeletedFalse(fileId)).thenReturn(Optional.of(uploadedFile));
        when(equipmentPictureRepository.saveAllAndFlush(any()))
                .thenAnswer(invocation -> {
                    List<EquipmentPicture> pictures = invocation.getArgument(0);
                    pictures.getFirst().setId(UUID.randomUUID());
                    return pictures;
                });

        var result = service.uploadPictures(
                equipmentId,
                List.of(new MockMultipartFile("files", "front.jpg", "image/jpg", "jpg".getBytes())),
                List.of("Front"),
                null,
                authenticatedUser()
        );

        assertThat(result).hasSize(1);
        verify(fileService).upload(any(), eq(FileCategory.EQUIPMENT_PICTURE), eq(userId));
    }

    private Equipment equipment(UUID id) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("EQ-2026-0001");
        equipment.setName("Pump");
        equipment.setInventoryNumber("INV-1");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(UUID.randomUUID());
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }

    private UploadedFile uploadedFile(UUID id, String originalName, String contentType) {
        UploadedFile file = new UploadedFile();
        file.setId(id);
        file.setOriginalName(originalName);
        file.setStoredName(id + ".png");
        file.setObjectName("equipment-pictures/" + id + ".png");
        file.setContentType(contentType);
        file.setSize(123L);
        file.setExtension("png");
        file.setUploadedBy(UUID.randomUUID());
        file.setCategory(FileCategory.EQUIPMENT_PICTURE);
        file.setDeleted(false);
        return file;
    }

    private EquipmentPicture equipmentPicture(Equipment equipment, UploadedFile file) {
        EquipmentPicture picture = new EquipmentPicture();
        picture.setId(UUID.randomUUID());
        picture.setEquipment(equipment);
        picture.setFile(file);
        picture.setPictureName("Front");
        picture.setPictureType("INSPECTION");
        picture.setUploadedBy(UUID.randomUUID());
        picture.setUploadedAt(LocalDateTime.parse("2026-06-19T06:00:00"));
        return picture;
    }

    private FileResponse fileResponse(UUID fileId) {
        return FileResponse.builder()
                .id(fileId)
                .originalName("front.png")
                .contentType("image/png")
                .extension("png")
                .size(123L)
                .uploadedBy(UUID.randomUUID())
                .category(FileCategory.EQUIPMENT_PICTURE)
                .deleted(false)
                .build();
    }

    private AuthenticatedUser authenticatedUser() {
        return new AuthenticatedUser(userId.toString(), "user", "user@example.com", "User", null, "USER", List.of());
    }
}
