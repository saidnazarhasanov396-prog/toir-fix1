package com.toir.service;

import com.toir.dto.file.UploadFileResponse;
import com.toir.entity.UploadedFile;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.entity.equipment.VehiclePicture;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.FileCategory;
import com.toir.enums.VehicleType;
import com.toir.exception.RestException;
import com.toir.repository.UploadedFileRepository;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.VehiclePictureRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.security.ScopeAccessService;
import com.toir.service.file_management.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

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
class VehiclePictureServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    VehicleDetailsRepository vehicleDetailsRepository;

    @Mock
    VehiclePictureRepository vehiclePictureRepository;

    @Mock
    UploadedFileRepository uploadedFileRepository;

    @Mock
    FileService fileService;

    @Mock
    ScopeAccessService scopeAccessService;

    VehiclePictureService service;
    UUID userId;

    @BeforeEach
    void setUp() {
        service = new VehiclePictureService(
                equipmentRepository,
                vehicleDetailsRepository,
                vehiclePictureRepository,
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
        Equipment equipment = vehicleEquipment(equipmentId);
        VehicleDetails details = vehicleDetails(equipmentId);
        UploadedFile uploadedFile = uploadedFile(fileId, "front.png", "image/png");
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(fileService.upload(any(), eq(FileCategory.VEHICLE_PICTURE), eq(userId)))
                .thenReturn(UploadFileResponse.builder().id(fileId).build());
        when(uploadedFileRepository.findByIdAndDeletedFalse(fileId)).thenReturn(Optional.of(uploadedFile));
        when(vehiclePictureRepository.saveAllAndFlush(any()))
                .thenAnswer(invocation -> {
                    List<VehiclePicture> pictures = invocation.getArgument(0);
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
        assertThat(result.getFirst().downloadUrl()).startsWith("/api/v1/vehicles/pictures/");
        verify(fileService).upload(any(), eq(FileCategory.VEHICLE_PICTURE), eq(userId));
        verify(scopeAccessService).assertCanAccessEquipmentScope(null, equipment.getDepartmentId());
    }

    @Test
    void uploadPicturesRejectsNonImageBeforeStorage() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(vehicleEquipment(equipmentId)));

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

    private Equipment vehicleEquipment(UUID id) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("VH-2026-0001");
        equipment.setName("Truck");
        equipment.setInventoryNumber("INV-1");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(UUID.randomUUID());
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.VEHICLE);
        return equipment;
    }

    private VehicleDetails vehicleDetails(UUID equipmentId) {
        VehicleDetails details = new VehicleDetails();
        details.setId(UUID.randomUUID());
        details.setEquipmentId(equipmentId);
        details.setPlateNumber("01A123AA");
        details.setVehicleType(VehicleType.TRUCK);
        return details;
    }

    private UploadedFile uploadedFile(UUID id, String originalName, String contentType) {
        UploadedFile file = new UploadedFile();
        file.setId(id);
        file.setOriginalName(originalName);
        file.setStoredName(id + ".png");
        file.setObjectName("vehicle-pictures/" + id + ".png");
        file.setContentType(contentType);
        file.setSize(123L);
        file.setDeleted(false);
        return file;
    }

    private AuthenticatedUser authenticatedUser() {
        return new AuthenticatedUser(userId.toString(), "user", "user@example.com", "User", null, "USER", List.of());
    }
}
