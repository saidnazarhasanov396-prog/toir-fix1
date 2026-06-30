package com.toir.controller.repair;

import com.toir.dto.file.FileAssetDto;
import com.toir.entity.FileAsset;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.service.FileAssetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RepairRequestPhotosControllerContractTest {

    @Mock
    FileAssetService fileAssetService;

    @Mock
    RepairRequestRepository repairRequestRepository;

    private MockMvc mockMvc;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RepairRequestPhotosController(fileAssetService, repairRequestRepository))
                .setCustomArgumentResolvers(new TestCurrentUserResolver(currentUserId))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void uploadReturnsRepairRequestPhotoDownloadUrl() throws Exception {
        UUID requestId = UUID.randomUUID();
        FileAsset asset = photoAsset(UUID.randomUUID(), requestId);
        when(repairRequestRepository.existsByIdAndIsDeletedFalse(requestId)).thenReturn(true);
        when(fileAssetService.upload(any(), eq("RepairRequest"), eq(requestId.toString()), eq(currentUserId)))
                .thenReturn(FileAssetDto.from(asset));

        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "png".getBytes());

        mockMvc.perform(multipart("/api/v1/repair-requests/{requestId}/photos", requestId)
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(asset.getId().toString()))
                .andExpect(jsonPath("$.downloadUrl")
                        .value("/api/v1/repair-requests/" + requestId + "/photos/" + asset.getId() + "/download"));
    }

    @Test
    void listReturnsRepairRequestPhotoDownloadUrls() throws Exception {
        UUID requestId = UUID.randomUUID();
        FileAsset asset = photoAsset(UUID.randomUUID(), requestId);
        when(repairRequestRepository.existsByIdAndIsDeletedFalse(requestId)).thenReturn(true);
        when(fileAssetService.findByEntity(eq("RepairRequest"), eq(requestId.toString()), any(AuthenticatedUser.class)))
                .thenReturn(List.of(FileAssetDto.from(asset)));

        mockMvc.perform(get("/api/v1/repair-requests/{requestId}/photos", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(asset.getId().toString()))
                .andExpect(jsonPath("$.content[0].downloadUrl")
                        .value("/api/v1/repair-requests/" + requestId + "/photos/" + asset.getId() + "/download"));
    }

    @Test
    void downloadReturnsInlineRepairRequestPhoto() throws Exception {
        UUID requestId = UUID.randomUUID();
        FileAsset asset = photoAsset(UUID.randomUUID(), requestId);
        when(repairRequestRepository.existsByIdAndIsDeletedFalse(requestId)).thenReturn(true);
        when(fileAssetService.findAssetByEntity(eq("RepairRequest"), eq(requestId.toString()), eq(asset.getId()), any(AuthenticatedUser.class)))
                .thenReturn(asset);
        when(fileAssetService.downloadForEntity(eq("RepairRequest"), eq(requestId.toString()), eq(asset.getId()), any(AuthenticatedUser.class)))
                .thenReturn(new ByteArrayResource("image-bytes".getBytes()));

        mockMvc.perform(get("/api/v1/repair-requests/{requestId}/photos/{photoId}/download", requestId, asset.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
                .andExpect(content().bytes("image-bytes".getBytes()));
    }

    @Test
    void downloadRejectsPhotoOutsideRepairRequest() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        when(repairRequestRepository.existsByIdAndIsDeletedFalse(requestId)).thenReturn(true);
        when(fileAssetService.findAssetByEntity(eq("RepairRequest"), eq(requestId.toString()), eq(photoId), any(AuthenticatedUser.class)))
                .thenThrow(RestException.notFound("File not found: " + photoId));

        mockMvc.perform(get("/api/v1/repair-requests/{requestId}/photos/{photoId}/download", requestId, photoId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("File not found: " + photoId));
    }

    private FileAsset photoAsset(UUID id, UUID requestId) {
        FileAsset asset = new FileAsset();
        asset.setId(id);
        asset.setFileName(id + ".png");
        asset.setOriginalName("photo.png");
        asset.setMimeType("image/png");
        asset.setSizeBytes(11L);
        asset.setStoragePath("/tmp/" + id + ".png");
        asset.setEntityType("RepairRequest");
        asset.setEntityId(requestId.toString());
        asset.setUploadedById(currentUserId);
        asset.setCreatedAt(Instant.parse("2026-06-19T06:00:00Z"));
        return asset;
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
        public Object resolveArgument(MethodParameter parameter,
                                      ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest,
                                      WebDataBinderFactory binderFactory) {
            return new AuthenticatedUser(userId.toString(), "user", "user@example.com", "User", null, "USER", List.of());
        }
    }
}
