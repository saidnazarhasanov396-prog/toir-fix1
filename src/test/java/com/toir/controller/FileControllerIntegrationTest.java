package com.toir.controller;

import com.toir.dto.file.FileResponse;
import com.toir.dto.file.PresignedUrlResponse;
import com.toir.dto.file.UploadFileResponse;
import com.toir.enums.FileCategory;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.service.file_management.FileService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FileControllerIntegrationTest {

    @Mock
    FileService fileService;

    private MockMvc mockMvc;
    private TestCurrentUserResolver currentUserResolver;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        currentUserResolver = new TestCurrentUserResolver(ownerId);
        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileService))
                .setCustomArgumentResolvers(currentUserResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void uploadWorks() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(fileService.upload(any(), eq(FileCategory.DOCUMENT), eq(ownerId)))
                .thenReturn(uploadResponse(fileId));

        mockMvc.perform(multipart("/api/files/upload")
                        .file(pdf("file", "report.pdf"))
                        .param("category", "DOCUMENT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(fileId.toString()))
                .andExpect(jsonPath("$.category").value("DOCUMENT"));
    }

    @Test
    void uploadMultipleWorks() throws Exception {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(fileService.uploadMultiple(any(), eq(FileCategory.DOCUMENT), eq(ownerId)))
                .thenReturn(List.of(uploadResponse(firstId), uploadResponse(secondId)));

        mockMvc.perform(multipart("/api/files/upload/multiple")
                        .file(pdf("files", "one.pdf"))
                        .file(pdf("files", "two.pdf"))
                        .param("category", "DOCUMENT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getMetadataWorksForOwner() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(fileService.getMetadata(fileId, ownerId)).thenReturn(fileResponse(fileId, ownerId));

        mockMvc.perform(get("/api/files/{id}", fileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId.toString()));
    }

    @Test
    void getMetadataReturns403ForNonOwner() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(fileService.getMetadata(fileId, ownerId))
                .thenThrow(RestException.forbidden("File access denied"));

        mockMvc.perform(get("/api/files/{id}", fileId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("File access denied"));
    }

    @Test
    void getPresignedUrlWorks() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(fileService.getPresignedUrl(fileId, ownerId)).thenReturn(PresignedUrlResponse.builder()
                .fileId(fileId)
                .url("http://signed-url")
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build());

        mockMvc.perform(get("/api/files/{id}/presigned-url", fileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("http://signed-url"));
    }

    @Test
    void deleteWorks() throws Exception {
        UUID fileId = UUID.randomUUID();

        mockMvc.perform(delete("/api/files/{id}", fileId))
                .andExpect(status().isNoContent());

        verify(fileService).delete(fileId, ownerId);
    }

    @Test
    void invalidFileReturnsProperStatus() throws Exception {
        when(fileService.upload(any(), eq(FileCategory.OTHER), eq(ownerId)))
                .thenThrow(new RestException("File type is not allowed", HttpStatus.UNSUPPORTED_MEDIA_TYPE));

        mockMvc.perform(multipart("/api/files/upload")
                        .file(new MockMultipartFile("file", "bad.exe", "application/octet-stream", "MZ".getBytes())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("File type is not allowed"));
    }

    private MockMultipartFile pdf(String fieldName, String fileName) {
        return new MockMultipartFile(fieldName, fileName, "application/pdf", "%PDF-1.4\n".getBytes());
    }

    private UploadFileResponse uploadResponse(UUID fileId) {
        return UploadFileResponse.builder()
                .id(fileId)
                .originalName("report.pdf")
                .storedName(UUID.randomUUID() + ".pdf")
                .url(null)
                .contentType("application/pdf")
                .extension("pdf")
                .size(10L)
                .category(FileCategory.DOCUMENT)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private FileResponse fileResponse(UUID fileId, UUID uploadedBy) {
        return FileResponse.builder()
                .id(fileId)
                .originalName("report.pdf")
                .storedName(UUID.randomUUID() + ".pdf")
                .url(null)
                .contentType("application/pdf")
                .extension("pdf")
                .size(10L)
                .uploadedBy(uploadedBy)
                .category(FileCategory.DOCUMENT)
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .build();
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
