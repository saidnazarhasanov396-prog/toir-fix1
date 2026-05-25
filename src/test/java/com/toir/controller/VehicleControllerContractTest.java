package com.toir.controller;

import com.toir.dto.file.PresignedUrlResponse;
import com.toir.dto.vehicle.VehicleDetailDto;
import com.toir.dto.vehicle.VehicleStatsResponse;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.security.SecurityScope;
import com.toir.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    SecurityScope securityScope;

    private MockMvc mockMvc;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new VehicleController(service, securityScope))
                .setCustomArgumentResolvers(new TestCurrentUserResolver(currentUserId))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void statsWithoutFiltersReturnsVehicleStats() throws Exception {
        VehicleStatsResponse response = new VehicleStatsResponse(
                11,
                9,
                1,
                1
        );

        when(securityScope.enforceDepartmentScope(isNull())).thenReturn(null);
        when(service.getStats(null, null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/vehicles/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(11))
                .andExpect(jsonPath("$.active").value(9))
                .andExpect(jsonPath("$.inRepair").value(1))
                .andExpect(jsonPath("$.outOfService").value(1));

        verify(securityScope).enforceDepartmentScope(null);
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

        when(securityScope.enforceDepartmentScope(departmentId)).thenReturn(scopedDepartmentId);
        when(service.getStats(scopedDepartmentId, "kamaz")).thenReturn(response);

        mockMvc.perform(get("/api/v1/vehicles/stats")
                        .param("departmentId", departmentId.toString())
                        .param("search", "kamaz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.active").value(3))
                .andExpect(jsonPath("$.inRepair").value(1))
                .andExpect(jsonPath("$.outOfService").value(0));

        verify(securityScope).enforceDepartmentScope(departmentId);
        verify(service).getStats(scopedDepartmentId, "kamaz");
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
                .andExpect(jsonPath("$.vehicleDetails.document.downloadUrl").value("/api/files/" + fileId + "/download"));

        verify(service).attachDocument(eq(equipmentId), any(), eq(currentUserId));
    }

    @Test
    void getDocumentReturnsMetadata() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(service.getDocument(equipmentId, currentUserId)).thenReturn(documentRef(fileId));

        mockMvc.perform(get("/api/v1/vehicles/{equipmentId}/document", equipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId.toString()))
                .andExpect(jsonPath("$.presignedUrlEndpoint").value("/api/files/" + fileId + "/presigned-url"));
    }

    @Test
    void getDocumentPresignedUrlDelegatesToService() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(service.getDocumentPresignedUrl(equipmentId, currentUserId)).thenReturn(PresignedUrlResponse.builder()
                .fileId(fileId)
                .url("http://signed")
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build());

        mockMvc.perform(get("/api/v1/vehicles/{equipmentId}/document/presigned-url", equipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.url").value("http://signed"));
    }

    @Test
    void deleteDocumentDelegatesToService() throws Exception {
        UUID equipmentId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/vehicles/{equipmentId}/document", equipmentId))
                .andExpect(status().isNoContent());

        verify(service).deleteDocument(equipmentId, currentUserId);
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
                        documentRef(fileId)
                )
        );
    }

    private VehicleDetailDto.DocumentRef documentRef(UUID fileId) {
        return new VehicleDetailDto.DocumentRef(
                fileId,
                "vehicle-passport.pdf",
                "application/pdf",
                123L,
                "/api/files/" + fileId + "/download",
                "/api/files/" + fileId + "/presigned-url"
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
}
