package com.toir.controller;

import com.toir.dto.equipmentattribute.EquipmentAttributeValueDto;
import com.toir.dto.file.PresignedUrlResponse;
import com.toir.dto.vehicle.VehicleDetailDto;
import com.toir.dto.vehicle.VehicleDocumentDto;
import com.toir.dto.vehicle.VehicleStatsResponse;
import com.toir.entity.equipment.Equipment;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.enums.EquipmentAttributeDataType;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.ErrorType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.security.ScopeAccessService;
import com.toir.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VehicleControllerContractTest {

    @Mock
    VehicleService service;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    EquipmentRepository equipmentRepository;

    private MockMvc mockMvc;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new VehicleController(service, scopeAccessService, equipmentRepository))
                .setCustomArgumentResolvers(new TestCurrentUserResolver(currentUserId))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(equipmentRepository.findByIdAndIsDeletedFalse(any()))
                .thenAnswer(invocation -> Optional.of(vehicleEquipment(invocation.getArgument(0), UUID.randomUUID())));
    }

    @Test
    void statsWithoutFiltersReturnsVehicleStats() throws Exception {
        VehicleStatsResponse response = new VehicleStatsResponse(
                11,
                9,
                1,
                1
        );

        when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(service.getStats(null, null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/vehicles/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(11))
                .andExpect(jsonPath("$.active").value(9))
                .andExpect(jsonPath("$.inRepair").value(1))
                .andExpect(jsonPath("$.outOfService").value(1));

        verify(scopeAccessService).enforceDepartmentScope(null);
        verify(service).getStats(null, null);
    }

    @Test
    void statsWithFiltersPassesScopedDepartmentAndSearchToService() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID scopedDepartmentId = departmentId;

        VehicleStatsResponse response = new VehicleStatsResponse(
                4,
                3,
                1,
                0
        );

        when(scopeAccessService.enforceDepartmentScope(departmentId)).thenReturn(scopedDepartmentId);
        when(service.getStats(scopedDepartmentId, "kamaz")).thenReturn(response);

        mockMvc.perform(get("/api/v1/vehicles/stats")
                        .param("departmentId", departmentId.toString())
                        .param("search", "kamaz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.active").value(3))
                .andExpect(jsonPath("$.inRepair").value(1))
                .andExpect(jsonPath("$.outOfService").value(0));

        verify(scopeAccessService).enforceDepartmentScope(departmentId);
        verify(service).getStats(scopedDepartmentId, "kamaz");
    }

    @Test
    void getVehicleDetailReturnsOfficialAttributes() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID attributeId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        VehicleDetailDto response = new VehicleDetailDto(
                null,
                new VehicleDetailDto.Details(
                        UUID.randomUUID(),
                        "01A123AA",
                        null,
                        "MAN",
                        "TGS",
                        2022,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of()
                ),
                List.of(new EquipmentAttributeValueDto(
                        attributeId,
                        equipmentId,
                        definitionId,
                        "payload_capacity",
                        "Payload capacity",
                        "Грузоподъемность",
                        "Yuk ko'tarish",
                        EquipmentAttributeDataType.NUMBER,
                        "kg",
                        false,
                        null,
                        List.of(),
                        "vehicle_metrics",
                        10,
                        null,
                        12000.0,
                        null,
                        null,
                        null,
                        null
                )),
                List.of()
        );
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(vehicleEquipment(equipmentId, UUID.randomUUID())));
        when(service.findByEquipmentId(equipmentId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/vehicles/{equipmentId}", equipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes[0].key").value("payload_capacity"))
                .andExpect(jsonPath("$.attributes[0].unit.name").value("kg"))
                .andExpect(jsonPath("$.attributes[0].valueNumber").value(12000.0));

        verify(service).findByEquipmentId(equipmentId);
    }

    @Test
    void attachDocumentUploadsMultipartDocument() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        VehicleDetailDto response = vehicleDetail(fileId);
        when(service.attachDocument(eq(equipmentId), any(), eq(currentUserId))).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/vehicles/{equipmentId}/document", equipmentId)
                        .file(new MockMultipartFile("document", "vehicle-passport.pdf", "application/pdf", "%PDF-1.4\n".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleDetails.document.id").value(fileId.toString()))
                .andExpect(jsonPath("$.vehicleDetails.document.downloadUrl").value(org.hamcrest.Matchers.nullValue()));

        verify(service).attachDocument(eq(equipmentId), any(), eq(currentUserId));
    }

    @Test
    void attachDocumentsUploadsMultipleMultipartFilesWithFilesKey() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID firstDocumentId = UUID.randomUUID();
        UUID firstFileId = UUID.randomUUID();
        UUID secondDocumentId = UUID.randomUUID();
        UUID secondFileId = UUID.randomUUID();
        when(service.attachDocuments(eq(equipmentId), any(), eq(List.of("Technical Passport", "Insurance Document")), eq("TECHNICAL"), any()))
                .thenReturn(List.of(
                        vehicleDocument(firstDocumentId, firstFileId, "passport.pdf", "TECHNICAL", "Technical Passport"),
                        vehicleDocument(secondDocumentId, secondFileId, "insurance.pdf", "TECHNICAL", "Insurance Document")
                ));

        mockMvc.perform(multipart("/api/v1/vehicles/{equipmentId}/documents", equipmentId)
                        .file(new MockMultipartFile("files", "passport.pdf", "application/pdf", "%PDF-1.4\n".getBytes()))
                        .file(new MockMultipartFile("files", "insurance.pdf", "application/pdf", "%PDF-1.4\n".getBytes()))
                        .param("documentNames", "Technical Passport", "Insurance Document")
                        .param("documentType", "TECHNICAL"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(firstDocumentId.toString()))
                .andExpect(jsonPath("$[0].fileId").value(firstFileId.toString()))
                .andExpect(jsonPath("$[0].documentType").value("TECHNICAL"))
                .andExpect(jsonPath("$[0].documentName").value("Technical Passport"))
                .andExpect(jsonPath("$[1].id").value(secondDocumentId.toString()))
                .andExpect(jsonPath("$[1].fileId").value(secondFileId.toString()))
                .andExpect(jsonPath("$[1].documentName").value("Insurance Document"));

        verify(service).attachDocuments(eq(equipmentId), any(), eq(List.of("Technical Passport", "Insurance Document")), eq("TECHNICAL"), any());
    }

    @Test
    void attachDocumentsUnauthorizedReturnsForbidden() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(service.attachDocuments(eq(equipmentId), any(), eq(List.of("Technical Passport")), isNull(), any()))
                .thenThrow(RestException.forbidden("Vehicle access denied"));

        mockMvc.perform(multipart("/api/v1/vehicles/{equipmentId}/documents", equipmentId)
                        .file(new MockMultipartFile("files", "vehicle-passport.pdf", "application/pdf", "%PDF-1.4\n".getBytes()))
                        .param("documentNames", "Technical Passport"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Vehicle access denied"));
    }

    @Test
    void attachDocumentsInvalidFilePropagatesValidationError() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(service.attachDocuments(eq(equipmentId), any(), eq(List.of("Suspicious File")), isNull(), any()))
                .thenThrow(new RestException("File type is not allowed", HttpStatus.UNSUPPORTED_MEDIA_TYPE));

        mockMvc.perform(multipart("/api/v1/vehicles/{equipmentId}/documents", equipmentId)
                        .file(new MockMultipartFile("files", "bad.exe", "application/octet-stream", "MZ".getBytes()))
                        .param("documentNames", "Suspicious File"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("File type is not allowed"));
    }

    @Test
    void attachDocumentUnauthorizedReturnsForbidden() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(service.attachDocument(eq(equipmentId), any(), eq(currentUserId)))
                .thenThrow(RestException.forbidden("Vehicle access denied"));

        mockMvc.perform(multipart("/api/v1/vehicles/{equipmentId}/document", equipmentId)
                        .file(new MockMultipartFile("document", "vehicle-passport.pdf", "application/pdf", "%PDF-1.4\n".getBytes())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Vehicle access denied"));
    }

    @Test
    void attachDocumentInvalidFilePropagatesValidationError() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(service.attachDocument(eq(equipmentId), any(), eq(currentUserId)))
                .thenThrow(new RestException("File type is not allowed", HttpStatus.UNSUPPORTED_MEDIA_TYPE));

        mockMvc.perform(multipart("/api/v1/vehicles/{equipmentId}/document", equipmentId)
                        .file(new MockMultipartFile("document", "bad.exe", "application/octet-stream", "MZ".getBytes())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("File type is not allowed"));
    }

    @Test
    void attachDocumentStorageAccessFailureReturnsClearServerError() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(service.attachDocument(eq(equipmentId), any(), eq(currentUserId)))
                .thenThrow(RestException.restThrow(ErrorType.FILE_STORAGE_ACCESS_DENIED));

        mockMvc.perform(multipart("/api/v1/vehicles/{equipmentId}/document", equipmentId)
                        .file(new MockMultipartFile("document", "vehicle-passport.pdf", "application/pdf", "%PDF-1.4\n".getBytes())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("File storage access denied or misconfigured"));
    }

    @Test
    void getDocumentReturnsMetadata() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(service.getDocument(equipmentId, currentUserId)).thenReturn(vehicleDocument(equipmentId, documentId, fileId, "vehicle-passport.pdf", null));

        mockMvc.perform(get("/api/v1/vehicles/{equipmentId}/document", equipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documentId.toString()))
                .andExpect(jsonPath("$.fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.presignedUrlEndpoint").value("/api/v1/vehicles/" + equipmentId + "/documents/" + documentId + "/presigned-url"));
    }

    @Test
    void listDocumentsEndpointReturnsPaginatedContent() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(service.getDocuments(eq(equipmentId), any()))
                .thenReturn(List.of(vehicleDocument(documentId, fileId, "vehicle-passport.pdf", "TECHNICAL", "Technical Passport")));

        mockMvc.perform(get("/api/v1/vehicles/{equipmentId}/documents", equipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(documentId.toString()))
                .andExpect(jsonPath("$.content[0].fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.content[0].documentType").value("TECHNICAL"))
                .andExpect(jsonPath("$.content[0].documentName").value("Technical Passport"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void getSpecificDocumentEndpointWorks() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(service.getDocument(eq(equipmentId), eq(documentId), any()))
                .thenReturn(vehicleDocument(documentId, fileId, "vehicle-passport.pdf", null));

        mockMvc.perform(get("/api/v1/vehicles/{equipmentId}/documents/{documentId}", equipmentId, documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documentId.toString()))
                .andExpect(jsonPath("$.fileId").value(fileId.toString()));
    }

    @Test
    void getDocumentPresignedUrlDelegatesToService() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(service.getDocumentPresignedUrl(eq(equipmentId), eq(documentId), any())).thenReturn(PresignedUrlResponse.builder()
                .fileId(fileId)
                .url("http://signed")
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build());

        mockMvc.perform(get("/api/v1/vehicles/{equipmentId}/documents/{documentId}/presigned-url", equipmentId, documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.url").value("http://signed"));
    }

    @Test
    void deleteDocumentDelegatesToService() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/vehicles/{equipmentId}/documents/{documentId}", equipmentId, documentId))
                .andExpect(status().isNoContent());

        verify(service).deleteDocument(eq(equipmentId), eq(documentId), any());
    }

    private VehicleDetailDto vehicleDetail(UUID fileId) {
        return new VehicleDetailDto(
                null,
                new VehicleDetailDto.Details(
                        UUID.randomUUID(),
                        "01A123AA",
                        null,
                        "MAN",
                        "TGS",
                        2022,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        documentRef(fileId),
                        List.of()
                )
        );
    }

    private VehicleDetailDto.DocumentRef documentRef(UUID fileId) {
        return new VehicleDetailDto.DocumentRef(
                fileId,
                "vehicle-passport.pdf",
                "vehicle-passport.pdf",
                "application/pdf",
                123L,
                null,
                null
        );
    }

    private VehicleDocumentDto vehicleDocument(UUID documentId, UUID fileId, String originalName, String documentType) {
        return vehicleDocument(UUID.randomUUID(), documentId, fileId, originalName, documentType, originalName);
    }

    private VehicleDocumentDto vehicleDocument(
            UUID documentId,
            UUID fileId,
            String originalName,
            String documentType,
            String documentName
    ) {
        return vehicleDocument(UUID.randomUUID(), documentId, fileId, originalName, documentType, documentName);
    }

    private VehicleDocumentDto vehicleDocument(UUID equipmentId, UUID documentId, UUID fileId, String originalName, String documentType) {
        return vehicleDocument(equipmentId, documentId, fileId, originalName, documentType, originalName);
    }

    private VehicleDocumentDto vehicleDocument(
            UUID equipmentId,
            UUID documentId,
            UUID fileId,
            String originalName,
            String documentType,
            String documentName
    ) {
        return new VehicleDocumentDto(
                documentId,
                fileId,
                documentType,
                documentName,
                originalName,
                "application/pdf",
                123L,
                "/api/v1/vehicles/" + equipmentId + "/documents/" + documentId + "/download",
                "/api/v1/vehicles/" + equipmentId + "/documents/" + documentId + "/presigned-url",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private Equipment vehicleEquipment(UUID equipmentId, UUID departmentId) {
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setCode("VH-2026-0001");
        equipment.setName("Truck");
        equipment.setInventoryNumber("INV-1");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(departmentId);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.VEHICLE);
        return equipment;
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
}
