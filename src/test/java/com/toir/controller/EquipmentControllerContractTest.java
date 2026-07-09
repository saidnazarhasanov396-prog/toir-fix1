package com.toir.controller;

import com.toir.controller.equipment.EquipmentController;
import com.toir.dto.equipment.EquipmentDetailDto;
import com.toir.dto.equipment.EquipmentDocumentDto;
import com.toir.dto.equipment.EquipmentDto;
import com.toir.dto.equipment.EquipmentLocationHistoryResponse;
import com.toir.dto.equipment.EquipmentPictureDto;
import com.toir.dto.equipment.EquipmentStatusHistoryResponse;
import com.toir.dto.equipment.EquipmentUsageSessionResponse;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueDto;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentAttributeDataType;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.EquipmentStatusSource;
import com.toir.enums.EquipmentUsageSessionStatus;
import com.toir.enums.PlacementType;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.ScopeAccessService;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.service.equipment.EquipmentService;
import com.toir.service.EquipmentUsageSessionService;
import com.toir.service.equipment.EquipmentPictureService;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import com.toir.dto.equipment.EquipmentStatsResponse;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EquipmentControllerContractTest {

    @Mock
    EquipmentService service;

    @Mock
    EquipmentRepository repository;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    EquipmentStatusLifecycleService statusLifecycleService;

    @Mock
    EquipmentPictureService pictureService;

    @Mock
    EquipmentUsageSessionService usageSessionService;

    private MockMvc mockMvc;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        mockMvc = MockMvcBuilders.standaloneSetup(new EquipmentController(service, repository, scopeAccessService, statusLifecycleService, pictureService, usageSessionService))
                .setCustomArgumentResolvers(new TestCurrentUserResolver(currentUserId))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void attachPicturesUploadsMultipleImages() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID firstPictureId = UUID.randomUUID();
        UUID secondPictureId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipmentEntity(equipmentId, UUID.randomUUID())));
        when(pictureService.uploadPictures(eq(equipmentId), any(), eq(List.of("Front", "Nameplate")), eq("INSPECTION"), any()))
                .thenReturn(List.of(
                        equipmentPicture(equipmentId, firstPictureId, "Front", "front.png"),
                        equipmentPicture(equipmentId, secondPictureId, "Nameplate", "nameplate.webp")
                ));

        mockMvc.perform(multipart("/api/v1/equipment/{equipmentId}/pictures", equipmentId)
                        .file(new MockMultipartFile("files", "front.png", "image/png", "png".getBytes()))
                        .file(new MockMultipartFile("files", "nameplate.webp", "image/webp", "webp".getBytes()))
                        .param("pictureNames", "Front", "Nameplate")
                        .param("pictureType", "INSPECTION"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(firstPictureId.toString()))
                .andExpect(jsonPath("$[0].equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$[0].pictureName").value("Front"))
                .andExpect(jsonPath("$[0].downloadUrl").value("/api/v1/equipment/pictures/" + firstPictureId + "/download"))
                .andExpect(jsonPath("$[1].pictureName").value("Nameplate"));

        verify(pictureService).uploadPictures(eq(equipmentId), any(), eq(List.of("Front", "Nameplate")), eq("INSPECTION"), any());
    }

    @Test
    void attachDocumentsWithSingularDocumentNameCreatesOneMultiFileDocument() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID frontFileId = UUID.randomUUID();
        UUID backFileId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipmentEntity(equipmentId, UUID.randomUUID())));
        when(service.attachDocumentFiles(eq(equipmentId), any(), eq("Technical Passport"), eq("PASSPORT"), eq("PAS-2024-001"), any()))
                .thenReturn(equipmentDocument(equipmentId, documentId, "Technical Passport", "PASSPORT", "PAS-2024-001", frontFileId, backFileId));

        mockMvc.perform(multipart("/api/v1/equipment/{equipmentId}/documents", equipmentId)
                        .file(new MockMultipartFile("files", "front.pdf", "application/pdf", "%PDF-1.4\n".getBytes()))
                        .file(new MockMultipartFile("files", "back.pdf", "application/pdf", "%PDF-1.4\n".getBytes()))
                        .param("documentName", "Technical Passport")
                        .param("documentType", "PASSPORT")
                        .param("documentNumber", "PAS-2024-001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(documentId.toString()))
                .andExpect(jsonPath("$[0].documentName").value("Technical Passport"))
                .andExpect(jsonPath("$[0].documentNumber").value("PAS-2024-001"))
                .andExpect(jsonPath("$[0].fileId").value(frontFileId.toString()))
                .andExpect(jsonPath("$[0].files.length()").value(2))
                .andExpect(jsonPath("$[0].files[0].id").value(frontFileId.toString()))
                .andExpect(jsonPath("$[0].files[1].id").value(backFileId.toString()));

        verify(service).attachDocumentFiles(eq(equipmentId), any(), eq("Technical Passport"), eq("PASSPORT"), eq("PAS-2024-001"), any());
    }

    @Test
    void downloadDocumentFileReturnsSelectedFileBlobWithOriginalFilename() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID frontFileId = UUID.randomUUID();
        UUID backFileId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipmentEntity(equipmentId, UUID.randomUUID())));
        when(service.getDocument(eq(equipmentId), eq(documentId), any()))
                .thenReturn(equipmentDocument(equipmentId, documentId, "Technical Passport", "PASSPORT", null, frontFileId, backFileId));
        when(service.downloadDocumentFile(eq(equipmentId), eq(documentId), eq(backFileId), any()))
                .thenReturn(new ByteArrayResource("pdf".getBytes()));

        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/documents/{documentId}/files/{fileId}/download",
                        equipmentId, documentId, backFileId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("back.pdf")));

        verify(service).downloadDocumentFile(eq(equipmentId), eq(documentId), eq(backFileId), any());
    }

    @Test
    void attachPicturesRejectsNonImageFile() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipmentEntity(equipmentId, UUID.randomUUID())));
        when(pictureService.uploadPictures(eq(equipmentId), any(), eq(List.of("Bad")), eq(null), any()))
                .thenThrow(RestException.badRequest("files[0] must be an image file (jpeg, png, webp, or gif)"));

        mockMvc.perform(multipart("/api/v1/equipment/{equipmentId}/pictures", equipmentId)
                        .file(new MockMultipartFile("files", "bad.pdf", "application/pdf", "%PDF".getBytes()))
                        .param("pictureNames", "Bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("files[0] must be an image file (jpeg, png, webp, or gif)"));
    }

    @Test
    void listPicturesReturnsPaginatedContent() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID pictureId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipmentEntity(equipmentId, UUID.randomUUID())));
        when(pictureService.getPictures(eq(equipmentId), any()))
                .thenReturn(List.of(equipmentPicture(equipmentId, pictureId, "Front", "front.png")));

        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/pictures", equipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(pictureId.toString()))
                .andExpect(jsonPath("$.content[0].downloadUrl").value("/api/v1/equipment/pictures/" + pictureId + "/download"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void downloadPictureReturnsInlineImage() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID pictureId = UUID.randomUUID();
        when(pictureService.getPicture(eq(pictureId), any()))
                .thenReturn(equipmentPicture(equipmentId, pictureId, "Front", "front.png"));
        when(pictureService.downloadPicture(eq(pictureId), any()))
                .thenReturn(new ByteArrayResource("png".getBytes()));

        mockMvc.perform(get("/api/v1/equipment/pictures/{pictureId}/download", pictureId))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).isEqualTo("image/png"));
    }

    @Test
    void deletePictureReturnsNoContent() throws Exception {
        UUID pictureId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/equipment/pictures/{pictureId}", pictureId))
                .andExpect(status().isNoContent());

        verify(pictureService).deletePicture(eq(pictureId), any());
    }

    @Test
    void createWithDepartmentIdOnlyReturnsCreated() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, departmentId);
        when(service.create(any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Compressor A",
                                  "inventoryNumber": "INV-1",
                                  "equipmentTypeId": "%s",
                                  "departmentId": "%s",
                                  "expectedLifetimeHours": 10000
                                }
                                """.formatted(equipmentTypeId, departmentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.code").value("EQ-2026-0020"))
                .andExpect(jsonPath("$.averageOperatingLifeHours").value(10000));
    }

    @Test
    void createAcceptsDynamicAttributesPayload() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, departmentId);
        when(service.create(any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Pump P-101",
                                  "inventoryNumber": "INV-P-101",
                                  "equipmentTypeId": "%s",
                                  "departmentId": "%s",
                                  "expectedLifetimeHours": 10000,
                                  "attributes": [
                                    {
                                      "key": "motor_power",
                                      "valueNumber": 75
                                    }
                                  ]
                                }
                                """.formatted(equipmentTypeId, departmentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));

        ArgumentCaptor<com.toir.dto.equipment.EquipmentCreateRequest> captor =
                ArgumentCaptor.forClass(com.toir.dto.equipment.EquipmentCreateRequest.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue().attributes()).hasSize(1);
        assertThat(captor.getValue().attributes().getFirst().key()).isEqualTo("motor_power");
        assertThat(captor.getValue().attributes().getFirst().valueNumber()).isEqualTo(75.0);
    }

    @Test
    void createWithoutExpectedLifetimeReturnsBadRequest() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(service.create(any())).thenThrow(RestException.badRequest("Expected lifetime must be specified and greater than zero"));

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Compressor A",
                                  "inventoryNumber": "INV-AVG-MISSING",
                                  "equipmentTypeId": "%s",
                                  "departmentId": "%s"
                                }
                                """.formatted(equipmentTypeId, departmentId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Expected lifetime must be specified and greater than zero")));
    }

    @Test
    void createWithZeroExpectedLifetimeHoursReturnsBadRequest() throws Exception {
        assertCreateExpectedLifetimeHoursValidation(0);
    }

    @Test
    void createWithNegativeExpectedLifetimeHoursReturnsBadRequest() throws Exception {
        assertCreateExpectedLifetimeHoursValidation(-1);
    }

    @Test
    void createWithWarehouseIdOnlyReturnsCreated() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, null);
        when(service.create(any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Compressor A",
                                  "inventoryNumber": "INV-2",
                                  "equipmentTypeId": "%s",
                                  "warehouseId": "%s",
                                  "expectedLifetimeHours": 10000
                                }
                                """.formatted(equipmentTypeId, warehouseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void createWithBothDepartmentAndWarehouseReturnsCreated() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, departmentId);
        when(service.create(any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Compressor A",
                                  "inventoryNumber": "INV-3",
                                  "equipmentTypeId": "%s",
                                  "departmentId": "%s",
                                  "warehouseId": "%s",
                                  "expectedLifetimeHours": 10000
                                }
                                """.formatted(equipmentTypeId, departmentId, warehouseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.departmentId").value(departmentId.toString()));
    }

    @Test
    void createWithNeitherDepartmentNorWarehouseReturnsBadRequest() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        when(service.create(any())).thenThrow(RestException.badRequest("departmentId or warehouseId is required"));

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Compressor A",
                                  "inventoryNumber": "INV-4",
                                  "equipmentTypeId": "%s",
                                  "expectedLifetimeHours": 10000
                                }
                                """.formatted(equipmentTypeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("departmentId or warehouseId is required"));
    }

    @Test
    void createWithInvalidWarehouseIdReturnsNotFound() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(service.create(any())).thenThrow(RestException.notFound("Warehouse not found: " + warehouseId));

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Compressor A",
                                  "inventoryNumber": "INV-5",
                                  "equipmentTypeId": "%s",
                                  "warehouseId": "%s",
                                  "expectedLifetimeHours": 10000
                                }
                                """.formatted(equipmentTypeId, warehouseId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Warehouse not found: " + warehouseId));
    }

    @Test
    void createWithInvalidDepartmentIdReturnsNotFound() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(service.create(any())).thenThrow(RestException.notFound("Department not found: " + departmentId));

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Compressor A",
                                  "inventoryNumber": "INV-6",
                                  "equipmentTypeId": "%s",
                                  "departmentId": "%s",
                                  "expectedLifetimeHours": 10000
                                }
                                """.formatted(equipmentTypeId, departmentId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Department not found: " + departmentId));
    }

    @Test
    void createWithClientProvidedCodeReturnsBadRequest() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(service.create(any())).thenThrow(RestException.badRequest("Equipment code is generated by system and must not be provided"));

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "EQ-2026-0017",
                                  "name": "Compressor A",
                                  "inventoryNumber": "INV-7",
                                  "equipmentTypeId": "%s",
                                  "departmentId": "%s",
                                  "expectedLifetimeHours": 10000
                                }
                                """.formatted(equipmentTypeId, departmentId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Equipment code is generated by system and must not be provided"));
    }

    @Test
    void updateWarehouseOnlyEquipmentWithoutDepartmentIdReturnsSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, "EQ-2026-0020", equipmentTypeId, null);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipmentEntity(id, null)));
        when(service.update(eq(id), any())).thenReturn(dto);

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Compressor A Updated",
                                  "inventoryNumber": "INV-2",
                                  "equipmentTypeId": "%s"
                                }
                                """.formatted(equipmentTypeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.code").value("EQ-2026-0020"));
    }

    @Test
    void updateWithDepartmentIdReturnsSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, departmentId);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipmentEntity(id, departmentId)));
        when(service.update(eq(id), any())).thenReturn(dto);

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Compressor A Updated",
                                  "inventoryNumber": "INV-3",
                                  "equipmentTypeId": "%s",
                                  "departmentId": "%s"
                                }
                                """.formatted(equipmentTypeId, departmentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.departmentId").value(departmentId.toString()));
    }

    @Test
    void updateAcceptsDynamicAttributesPayload() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, departmentId);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipmentEntity(id, departmentId)));
        when(service.update(eq(id), any())).thenReturn(dto);

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Pump P-101 Updated",
                                  "attributes": [
                                    {
                                      "key": "motor_power",
                                      "valueNumber": 90
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));

        ArgumentCaptor<com.toir.dto.equipment.EquipmentUpdateRequest> captor =
                ArgumentCaptor.forClass(com.toir.dto.equipment.EquipmentUpdateRequest.class);
        verify(service).update(eq(id), captor.capture());
        assertThat(captor.getValue().attributes()).hasSize(1);
        assertThat(captor.getValue().attributes().getFirst().valueNumber()).isEqualTo(90.0);
    }

    @Test
    void updateWithInvalidDepartmentIdReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipmentEntity(id, departmentId)));
        when(service.update(eq(id), any())).thenThrow(RestException.notFound("Department not found: " + departmentId));

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "departmentId": "%s"
                                }
                                """.formatted(departmentId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Department not found: " + departmentId));
    }

    @Test
    void updateWithClientProvidedCodeReturnsBadRequest() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipmentEntity(id, departmentId)));
        when(service.update(eq(id), any())).thenThrow(RestException.badRequest("Equipment code is generated by system and must not be provided"));

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "EQ-2026-0017",
                                  "name": "Compressor A",
                                  "inventoryNumber": "INV-2",
                                  "equipmentTypeId": "%s",
                                  "departmentId": "%s"
                                }
                                """.formatted(equipmentTypeId, departmentId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Equipment code is generated by system and must not be provided"));
    }

    @Test
    void patchEquipmentStatus_returnsUpdatedStatus() throws Exception {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID changedBy = UUID.randomUUID();
        Equipment equipment = equipmentEntity(id, departmentId);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipment));
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(changedBy);
        when(statusLifecycleService.changeStatusManually(eq(id), any(), eq(changedBy)))
                .thenReturn(new EquipmentStatusHistoryResponse(
                        UUID.randomUUID(),
                        id,
                        EquipmentStatus.ACTIVE,
                        EquipmentStatus.OUT_OF_SERVICE,
                        "Safety lockout",
                        EquipmentStatusSource.MANUAL,
                        changedBy,
                        Instant.parse("2026-05-23T04:00:00Z"),
                        null,
                        null
                ));

        mockMvc.perform(patch("/api/v1/equipment/{id}/status", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "status": "OUT_OF_SERVICE",
                                  "reason": "Safety lockout"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentId").value(id.toString()))
                .andExpect(jsonPath("$.fromStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.toStatus").value("OUT_OF_SERVICE"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.reason").value("Safety lockout"));
    }

    @Test
    void getEquipmentStatusHistory_returnsTransitions() throws Exception {
        UUID id = UUID.randomUUID();
        UUID historyId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipmentEntity(id, null)));
        when(statusLifecycleService.getHistory(eq(id), any()))
                .thenReturn(new PageImpl<>(List.of(new EquipmentStatusHistoryResponse(
                        historyId,
                        id,
                        EquipmentStatus.ACTIVE,
                        EquipmentStatus.IN_REPAIR,
                        "Work order started",
                        EquipmentStatusSource.WORK_ORDER,
                        null,
                        Instant.parse("2026-05-23T04:10:00Z"),
                        "WORK_ORDER",
                        UUID.randomUUID()
                )), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/equipment/{id}/status-history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(historyId.toString()))
                .andExpect(jsonPath("$.content[0].equipmentId").value(id.toString()))
                .andExpect(jsonPath("$.content[0].toStatus").value("IN_REPAIR"))
                .andExpect(jsonPath("$.content[0].source").value("WORK_ORDER"));
    }

    @Test
    void patchEquipmentStatus_withoutReason_returnsBadRequest() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/equipment/{id}/status", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "status": "OUT_OF_SERVICE",
                                  "reason": " "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listWithLocationIdReturnsLocationObject() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        EquipmentDto.Ref locationRef = new EquipmentDto.Ref(locationId, "LOC-001", "Main Workshop");
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, null, locationId, locationRef);

        when(service.search(null, null, null, null, null, null, null, null, false, false, (Boolean) null, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/equipment"))
                .andDo(this::assertNoResolvedException)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].locationId").value(locationId.toString()))
                .andExpect(jsonPath("$.content[0].location.id").value(locationId.toString()))
                .andExpect(jsonPath("$.content[0].location.code").value("LOC-001"))
                .andExpect(jsonPath("$.content[0].location.name").value("Main Workshop"));
    }

    @Test
    void listDeclaresOptionalHasWarrantyRequestParam() {
        Method listMethod = List.of(EquipmentController.class.getDeclaredMethods()).stream()
                .filter(method -> method.getName().equals("list"))
                .findFirst()
                .orElseThrow();

        assertThat(listMethod.getParameters())
                .anySatisfy(parameter -> {
                    RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                    assertThat(requestParam).isNotNull();
                    assertThat(requestParam.name().isBlank() ? parameter.getName() : requestParam.name())
                            .isEqualTo("hasWarranty");
                    assertThat(parameter.getType()).isEqualTo(Boolean.class);
                    assertThat(requestParam.required()).isFalse();
                });
    }

    @Test
    void listWithHasWarrantyTrueIsAccepted() throws Exception {
        when(service.search(null, null, null, null, null, null, null, null, false, false, Boolean.TRUE, null, 0, 20))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/v1/equipment")
                        .param("hasWarranty", "true"))
                .andExpect(status().isOk());

        verify(service).search(null, null, null, null, null, null, null, null, false, false, Boolean.TRUE, null, 0, 20);
    }

    @Test
    void listWithHasWarrantyFalseIsAccepted() throws Exception {
        when(service.search(null, null, null, null, null, null, null, null, false, false, Boolean.FALSE, null, 0, 20))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/v1/equipment")
                        .param("hasWarranty", "false"))
                .andExpect(status().isOk());

        verify(service).search(null, null, null, null, null, null, null, null, false, false, Boolean.FALSE, null, 0, 20);
    }

    @Test
    void listWithVehicleStatusAndHasWarrantyFiltersIsAccepted() throws Exception {
        when(service.search(null, null, null, EquipmentStatus.ACTIVE, EquipmentCategory.VEHICLE, null, null, null,
                false, false, Boolean.TRUE, null, 0, 20))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/v1/equipment")
                        .param("status", "ACTIVE")
                        .param("category", "VEHICLE")
                        .param("hasWarranty", "true")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk());

        verify(service).search(null, null, null, EquipmentStatus.ACTIVE, EquipmentCategory.VEHICLE, null, null, null,
                false, false, Boolean.TRUE, null, 0, 20);
    }

    @Test
    void listWithNullLocationIdReturnsLocationNull() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, null, null, null);

        when(service.search(null, null, null, null, null, null, null, null, false, false, (Boolean) null, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/equipment"))
                .andDo(this::assertNoResolvedException)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].locationId").value(nullValue()))
                .andExpect(jsonPath("$.content[0].location").value(nullValue()));
    }

    @Test
    void listWithMissingLocationRecordDoesNot500() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID missingLocationId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, null, missingLocationId, null);

        when(service.search(null, null, null, null, null, null, null, null, false, false, (Boolean) null, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/equipment"))
                .andDo(this::assertNoResolvedException)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].locationId").value(missingLocationId.toString()))
                .andExpect(jsonPath("$.content[0].location").value(nullValue()));
    }

    @Test
    void listResponseIncludesPlacementObjectForDepartmentEquipment() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        EquipmentDto.Ref departmentRef = new EquipmentDto.Ref(departmentId, "DEP-001", "Main Department");
        EquipmentDto.Ref locationRef = new EquipmentDto.Ref(locationId, "LOC-001", "Main Workshop");
        EquipmentDto.PlacementRef placement = new EquipmentDto.PlacementRef(
                PlacementType.DEPARTMENT,
                departmentRef,
                null,
                null,
                locationRef
        );
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, departmentId, locationId, locationRef, departmentRef, placement);

        when(service.search(null, null, null, null, null, null, null, null, false, false, (Boolean) null, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/equipment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].placement.type").value("DEPARTMENT"))
                .andExpect(jsonPath("$.content[0].placement.department.id").value(departmentId.toString()))
                .andExpect(jsonPath("$.content[0].placement.warehouse").value(nullValue()))
                .andExpect(jsonPath("$.content[0].placement.warehouseStatus").value(nullValue()))
                .andExpect(jsonPath("$.content[0].placement.location.id").value(locationId.toString()));
    }

    @Test
    void listResponseIncludesPlacementObjectForWarehouseEquipment() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        EquipmentDto.Ref warehouseRef = new EquipmentDto.Ref(warehouseId, "WH-001", "Main Warehouse");
        EquipmentDto.PlacementRef placement = new EquipmentDto.PlacementRef(
                PlacementType.WAREHOUSE,
                null,
                warehouseRef,
                WarehouseEquipmentStatus.AVAILABLE,
                warehouseRef
        );
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, null, warehouseId, warehouseRef, null, placement);

        when(service.search(null, null, null, null, null, null, null, null, false, false, (Boolean) null, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/equipment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].placement.type").value("WAREHOUSE"))
                .andExpect(jsonPath("$.content[0].placement.department").value(nullValue()))
                .andExpect(jsonPath("$.content[0].placement.warehouse.id").value(warehouseId.toString()))
                .andExpect(jsonPath("$.content[0].placement.warehouseStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.content[0].placement.location.id").value(warehouseId.toString()));
    }

    @Test
    void detailResponseIncludesRelatedArrays() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        EquipmentDto.Ref departmentRef = new EquipmentDto.Ref(departmentId, "DEP-010", "Assembly");
        EquipmentDto.PlacementRef placement = new EquipmentDto.PlacementRef(
                PlacementType.DEPARTMENT,
                departmentRef,
                null,
                null,
                null
        );
        EquipmentDto equipment = equipmentDto(id, equipmentTypeId, departmentId, null, null, departmentRef, placement);
        EquipmentDetailDto detail = new EquipmentDetailDto(
                equipment,
                0,
                2,
                List.of(new EquipmentDetailDto.RepairRequestShortDto(
                        UUID.randomUUID(),
                        "RR-001",
                        "Seal leak",
                        null,
                        null,
                        "Detected leak"
                )),
                List.of(new EquipmentDetailDto.DefectShortDto(
                        UUID.randomUUID(),
                        "DEF-001",
                        "Bearing overheating",
                        null,
                        null,
                        "Temperature high"
                )),
                List.of(new EquipmentDetailDto.WorkOrderShortDto(
                        UUID.randomUUID(),
                        "WO-001",
                        "Bearing replacement",
                        null,
                        null,
                        null,
                        "Replace bearing"
                )),
                List.of(new EquipmentDetailDto.DowntimeEventShortDto(
                        UUID.randomUUID(),
                        null,
                        null,
                        45,
                        null,
                        "Unexpected stop"
                ))
        );
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipmentEntity(id, departmentId)));
        when(service.findDetailById(id)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/equipment/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipment.placement.type").value("DEPARTMENT"))
                .andExpect(jsonPath("$.equipment.placement.department.id").value(departmentId.toString()))
                .andExpect(jsonPath("$.repairRequests").isArray())
                .andExpect(jsonPath("$.repairRequests.length()").value(1))
                .andExpect(jsonPath("$.repairsCount").value(2))
                .andExpect(jsonPath("$.defects").isArray())
                .andExpect(jsonPath("$.defects.length()").value(1))
                .andExpect(jsonPath("$.workOrders").isArray())
                .andExpect(jsonPath("$.workOrders.length()").value(1))
                .andExpect(jsonPath("$.downtimeEvents").isArray())
                .andExpect(jsonPath("$.downtimeEvents.length()").value(1));
    }

    @Test
    void detailResponseRelatedArraysAreEmptyNotNull() throws Exception {
        UUID id = UUID.randomUUID();
        EquipmentDto equipment = equipmentDto(id, UUID.randomUUID(), null);
        EquipmentDetailDto detail = new EquipmentDetailDto(equipment, List.of(), List.of(), List.of(), List.of());
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipmentEntity(id, null)));
        when(service.findDetailById(id)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/equipment/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repairRequests").isArray())
                .andExpect(jsonPath("$.repairRequests.length()").value(0))
                .andExpect(jsonPath("$.defects").isArray())
                .andExpect(jsonPath("$.defects.length()").value(0))
                .andExpect(jsonPath("$.workOrders").isArray())
                .andExpect(jsonPath("$.workOrders.length()").value(0))
                .andExpect(jsonPath("$.downtimeEvents").isArray())
                .andExpect(jsonPath("$.downtimeEvents.length()").value(0));
    }

    @Test
    void detailResponseIncludesDynamicAttributes() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        EquipmentDto equipment = equipmentDto(id, equipmentTypeId, null);
        EquipmentDetailDto detail = new EquipmentDetailDto(
                equipment,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new EquipmentAttributeValueDto(
                        UUID.randomUUID(),
                        id,
                        definitionId,
                        "motor_power",
                        "Motor Power",
                        "Мощность двигателя",
                        "Dvigatel quvvati",
                        EquipmentAttributeDataType.NUMBER,
                        "kW",
                        true,
                        null,
                        List.of(),
                        "Motor",
                        10,
                        null,
                        75.0,
                        null,
                        null,
                        null,
                        null
                ))
        );
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipmentEntity(id, null)));
        when(service.findDetailById(id)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/equipment/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes").isArray())
                .andExpect(jsonPath("$.attributes[0].key").value("motor_power"))
                .andExpect(jsonPath("$.attributes[0].unit.name").value("kW"))
                .andExpect(jsonPath("$.attributes[0].valueNumber").value(75.0));
    }

    @Test
    void detailUnknownEquipmentReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/equipment/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Equipment not found: " + id));
    }

    @Test
    void listResponsePlacementUnknownIsStable() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        EquipmentDto.PlacementRef placement = new EquipmentDto.PlacementRef(
                PlacementType.UNKNOWN,
                null,
                null,
                null,
                null
        );
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, null, null, null, null, placement);

        when(service.search(null, null, null, null, null, null, null, null, false, false, (Boolean) null, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/equipment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].placement.type").value("UNKNOWN"))
                .andExpect(jsonPath("$.content[0].placement.department").value(nullValue()))
                .andExpect(jsonPath("$.content[0].placement.warehouse").value(nullValue()))
                .andExpect(jsonPath("$.content[0].placement.warehouseStatus").value(nullValue()))
                .andExpect(jsonPath("$.content[0].placement.location").value(nullValue()));
    }

    @Test
    void listPassesMxikFilterToService() throws Exception {
        UUID mxikId = UUID.randomUUID();
        when(service.search(null, null, null, null, null, null, null, null, false, false, (Boolean) null, mxikId, null, 0, 20))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/v1/equipment").param("mxikId", mxikId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(service).search(null, null, null, null, null, null, null, null, false, false, (Boolean) null, mxikId, null, 0, 20);
    }

    @Test
    void listShouldSupportBusinessSearchByCode() throws Exception {
        when(service.search(null, null, null, null, null, null, null, null, false, false, (Boolean) null, "EQ-2026-0012", 0, 20))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        MvcResult result = mockMvc.perform(get("/api/v1/equipment").param("search", "EQ-2026-0012"))
                .andDo(this::assertNoResolvedException)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andReturn();

        assertNull(result.getResolvedException());

        verify(service).search(null, null, null, null, null, null, null, null, false, false, (Boolean) null, "EQ-2026-0012", 0, 20);
    }

    @Test
    void listShouldSupportBusinessSearchByNameAndReturnEmptyPage() throws Exception {
        when(service.search(null, null, null, null, null, null, null, null, false, false, (Boolean) null, "compressor", 0, 20))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        MvcResult result = mockMvc.perform(get("/api/v1/equipment").param("search", "compressor"))
                .andDo(this::assertNoResolvedException)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andReturn();

        assertNull(result.getResolvedException());

        verify(service).search(null, null, null, null, null, null, null, null, false, false, (Boolean) null, "compressor", 0, 20);
    }

    @Test
    void patchPlacementWarehouseSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID warehouseLocationId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, null, warehouseLocationId,
                new EquipmentDto.Ref(warehouseLocationId, "LOC-WH-001", "Warehouse physical location"));
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipmentEntity(id, null)));
        when(service.updatePlacement(eq(id), any())).thenReturn(dto);

        mockMvc.perform(patch("/api/v1/equipment/{id}/placement", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetType": "WAREHOUSE",
                                  "warehouseId": "%s"
                                }
                                """.formatted(warehouseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.departmentId").value(nullValue()))
                .andExpect(jsonPath("$.locationId").value(warehouseLocationId.toString()));
    }

    @Test
    void patchPlacementDepartmentSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, departmentId, null, null);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipmentEntity(id, departmentId)));
        when(service.updatePlacement(eq(id), any())).thenReturn(dto);

        mockMvc.perform(patch("/api/v1/equipment/{id}/placement", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetType": "DEPARTMENT",
                                  "departmentId": "%s"
                                }
                                """.formatted(departmentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.locationId").value(nullValue()));
    }

    @Test
    void patchPlacementInvalidMixedPayloadReturnsBadRequest() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipmentEntity(id, UUID.randomUUID())));
        when(service.updatePlacement(eq(id), any()))
                .thenThrow(RestException.badRequest("warehouseId and departmentId cannot both be provided"));

        mockMvc.perform(patch("/api/v1/equipment/{id}/placement", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetType": "WAREHOUSE",
                                  "warehouseId": "%s",
                                  "departmentId": "%s"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("warehouseId and departmentId cannot both be provided"));
    }

    @Test
    void patchPlacementInvalidWarehouseReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipmentEntity(id, UUID.randomUUID())));
        when(service.updatePlacement(eq(id), any()))
                .thenThrow(RestException.notFound("Warehouse not found: " + warehouseId));

        mockMvc.perform(patch("/api/v1/equipment/{id}/placement", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetType": "WAREHOUSE",
                                  "warehouseId": "%s"
                                }
                                """.formatted(warehouseId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Warehouse not found: " + warehouseId));
    }

    @Test
    void patchPlacementInvalidDepartmentReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipmentEntity(id, departmentId)));
        when(service.updatePlacement(eq(id), any()))
                .thenThrow(RestException.notFound("Department not found: " + departmentId));

        mockMvc.perform(patch("/api/v1/equipment/{id}/placement", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetType": "DEPARTMENT",
                                  "departmentId": "%s"
                                }
                                """.formatted(departmentId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Department not found: " + departmentId));
    }

    @Test
    void locationHistoryReturnsPagedTimelineWithDuration() throws Exception {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Instant changedAt = Instant.parse("2026-06-18T04:05:00Z");
        Instant activeUntil = Instant.parse("2026-06-18T08:08:00Z");
        EquipmentLocationHistoryResponse response = new EquipmentLocationHistoryResponse(
                UUID.randomUUID(),
                id,
                null,
                new EquipmentLocationHistoryResponse.LocationSnapshot(
                        EquipmentLocationType.DEPARTMENT,
                        departmentId,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                departmentId,
                currentUserId,
                changedAt,
                activeUntil,
                243L,
                "Department transfer"
        );
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipmentEntity(id, departmentId)));
        when(service.locationHistory(id, PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/equipment/{id}/location-history", id)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].equipmentId").value(id.toString()))
                .andExpect(jsonPath("$.content[0].to.departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.content[0].activeUntil").exists())
                .andExpect(jsonPath("$.content[0].durationMinutes").value(243));

        verify(service).locationHistory(id, PageRequest.of(0, 20));
    }

    @Test
    void startUsageSessionDelegatesToGenericUsageServiceWithCurrentUser() throws Exception {
        UUID id = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipmentEntity(id, UUID.randomUUID())));
        when(usageSessionService.start(eq(id), any(), eq(currentUserId))).thenReturn(new EquipmentUsageSessionResponse(
                sessionId,
                id,
                operatorId,
                "Operator Ali",
                UUID.randomUUID(),
                Instant.parse("2026-06-18T04:05:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                EquipmentUsageSessionStatus.OPEN,
                currentUserId,
                null,
                "start"
        ));

        mockMvc.perform(post("/api/v1/equipment/{id}/usage-sessions/start", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorEmployeeId": "%s",
                                  "startedAt": "2026-06-18T04:05:00Z",
                                  "note": "start"
                                }
                                """.formatted(operatorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.operatorEmployeeId").value(operatorId.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"));

        verify(usageSessionService).start(eq(id), any(), eq(currentUserId));
    }

    @Test
    void statsWithoutFiltersReturnsEquipmentStats() throws Exception {
        EquipmentStatsResponse response = new EquipmentStatsResponse(
                50,
                9,
                3,
                1
        );

        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(service.getEquipmentStats(null, null, null, null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/equipment/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInRegistry").value(50))
                .andExpect(jsonPath("$.active").value(9))
                .andExpect(jsonPath("$.inRepair").value(3))
                .andExpect(jsonPath("$.decommissioned").value(1));

        verify(scopeAccessService).enforceDepartmentScope(null);
        verify(service).getEquipmentStats(null, null, null, null);
    }

    @Test
    void statsWithFiltersPassesParamsToService() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID scopedDepartmentId = departmentId;
        UUID equipmentTypeId = UUID.randomUUID();

        EquipmentStatsResponse response = new EquipmentStatsResponse(
                12,
                8,
                2,
                2
        );

        when(scopeAccessService.enforceDepartmentScope(departmentId)).thenReturn(scopedDepartmentId);
        when(service.getEquipmentStats(
                "pump",
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                scopedDepartmentId,
                equipmentTypeId
        )).thenReturn(response);

        mockMvc.perform(get("/api/v1/equipment/stats")
                        .param("search", "pump")
                        .param("category", "PRODUCTION_EQUIPMENT")
                        .param("departmentId", departmentId.toString())
                        .param("equipmentTypeId", equipmentTypeId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInRegistry").value(12))
                .andExpect(jsonPath("$.active").value(8))
                .andExpect(jsonPath("$.inRepair").value(2))
                .andExpect(jsonPath("$.decommissioned").value(2));

        verify(scopeAccessService).enforceDepartmentScope(departmentId);
        verify(service).getEquipmentStats(
                "pump",
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                scopedDepartmentId,
                equipmentTypeId
        );
    }

    private EquipmentDto equipmentDto(UUID id, UUID equipmentTypeId, UUID departmentId) {
        return equipmentDto(id, "EQ-2026-0020", equipmentTypeId, departmentId);
    }

    private Equipment equipmentEntity(UUID id, UUID departmentId) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("EQ-2026-0020");
        equipment.setName("Compressor A");
        equipment.setInventoryNumber("INV-1");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(departmentId);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }

    private EquipmentDto equipmentDto(UUID id, String code, UUID equipmentTypeId, UUID departmentId) {
        return equipmentDto(id, code, equipmentTypeId, departmentId, null, null, null, null);
    }

    private EquipmentDto equipmentDto(UUID id,
                                      UUID equipmentTypeId,
                                      UUID departmentId,
                                      UUID locationId,
                                      EquipmentDto.Ref location) {
        return equipmentDto(id, "EQ-2026-0020", equipmentTypeId, departmentId, locationId, location, null, null);
    }

    private EquipmentDto equipmentDto(UUID id,
                                      UUID equipmentTypeId,
                                      UUID departmentId,
                                      UUID locationId,
                                      EquipmentDto.Ref location,
                                      EquipmentDto.Ref departmentRef,
                                      EquipmentDto.PlacementRef placement) {
        return equipmentDto(id, "EQ-2026-0020", equipmentTypeId, departmentId, locationId, location, departmentRef, placement);
    }

    private EquipmentDto equipmentDto(UUID id,
                                      String code,
                                      UUID equipmentTypeId,
                                      UUID departmentId,
                                      UUID locationId,
                                      EquipmentDto.Ref location,
                                      EquipmentDto.Ref departmentRef,
                                      EquipmentDto.PlacementRef placement) {
        return new EquipmentDto(
                id,
                code,
                "Compressor A",
                "INV-1",
                null,
                null,
                null,
                equipmentTypeId,
                departmentId,
                locationId,
                null,
                null,
                null,
                null,
                EquipmentStatus.ACTIVE,
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                null,
                null,
                null,
                10_000L,
                departmentRef,
                location,
                null,
                null,
                null,
                placement
        );
    }

    private EquipmentPictureDto equipmentPicture(UUID equipmentId, UUID pictureId, String pictureName, String originalName) {
        return new EquipmentPictureDto(
                pictureId,
                equipmentId,
                pictureName,
                "INSPECTION",
                originalName,
                "image/png",
                123L,
                LocalDateTime.now(),
                currentUserId,
                "/api/v1/equipment/pictures/" + pictureId + "/download"
        );
    }

    private EquipmentDocumentDto equipmentDocument(
            UUID equipmentId,
            UUID documentId,
            String documentName,
            String documentType,
            String documentNumber,
            UUID frontFileId,
            UUID backFileId
    ) {
        EquipmentDocumentDto.FileRef front = new EquipmentDocumentDto.FileRef(
                frontFileId,
                frontFileId + ".pdf",
                "front.pdf",
                "application/pdf",
                123L,
                "/api/v1/equipment/" + equipmentId + "/documents/" + documentId + "/files/" + frontFileId + "/download"
        );
        EquipmentDocumentDto.FileRef back = new EquipmentDocumentDto.FileRef(
                backFileId,
                backFileId + ".pdf",
                "back.pdf",
                "application/pdf",
                124L,
                "/api/v1/equipment/" + equipmentId + "/documents/" + documentId + "/files/" + backFileId + "/download"
        );
        return new EquipmentDocumentDto(
                documentId,
                frontFileId,
                documentType,
                documentNumber,
                documentName,
                front.originalName(),
                front.mimeType(),
                front.sizeBytes(),
                "/api/v1/equipment/" + equipmentId + "/documents/" + documentId + "/download",
                "/api/v1/equipment/" + equipmentId + "/documents/" + documentId + "/presigned-url",
                LocalDateTime.now(),
                LocalDateTime.now(),
                equipmentId,
                documentName,
                documentType,
                LocalDateTime.now(),
                front,
                List.of(front, back)
        );
    }

    private static class TestCurrentUserResolver implements HandlerMethodArgumentResolver {
        private final UUID userId;

        private TestCurrentUserResolver(UUID userId) {
            this.userId = userId;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return new AuthenticatedUser(userId.toString(), "user", "user@example.com", "User", null, "USER", List.of());
        }
    }

    private void assertCreateExpectedLifetimeHoursValidation(long value) throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Compressor A",
                                  "inventoryNumber": "INV-LIFE-HOURS-%s",
                                  "equipmentTypeId": "%s",
                                  "departmentId": "%s",
                                  "expectedLifetimeHours": %d
                                }
                                """.formatted(value, equipmentTypeId, departmentId, value)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("expectedLifetimeHours")));
    }

    private void assertNoResolvedException(MvcResult result) {
        Exception ex = result.getResolvedException();
        if (ex == null) {
            return;
        }
        Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
        String rootMessage = root != null ? root.getClass().getName() + ": " + root.getMessage() : "n/a";
        fail("Resolved exception: " + ex.getClass().getName() + ": " + ex.getMessage() + "; root cause: " + rootMessage);
    }
}
