package com.toir.controller;

import com.toir.dto.attachment.AttachmentGroupDto;
import com.toir.dto.file.PresignedUrlResponse;
import com.toir.enums.AttachmentTargetType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUserArgumentResolver;
import com.toir.service.attachment.AttachmentGroupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AttachmentGroupControllerContractTest {

    @Mock
    AttachmentGroupService service;

    private MockMvc mockMvc;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        authenticate(currentUserId);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AttachmentGroupController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createGroupAcceptsMultipartFilesAndReturnsGroup() throws Exception {
        UUID targetId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID firstFileId = UUID.randomUUID();
        UUID secondFileId = UUID.randomUUID();
        when(service.createGroup(
                eq("Passport"),
                eq("Driver passport"),
                eq("EQUIPMENT"),
                eq(targetId),
                eq(null),
                eq(null),
                any(),
                eq(List.of("front", "back")),
                any()
        ))
                .thenReturn(group(groupId, targetId, firstFileId, secondFileId));

        mockMvc.perform(multipart("/api/v1/attachments/groups")
                        .file(new MockMultipartFile("files", "front.pdf", "application/pdf", "%PDF-1.4\n".getBytes()))
                        .file(new MockMultipartFile("files", "back.pdf", "application/pdf", "%PDF-1.4\n".getBytes()))
                        .param("title", "Passport")
                        .param("description", "Driver passport")
                        .param("targetType", "EQUIPMENT")
                        .param("targetId", targetId.toString())
                        .param("labels", "front", "back"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(groupId.toString()))
                .andExpect(jsonPath("$.title").value("Passport"))
                .andExpect(jsonPath("$.targetType").value("EQUIPMENT"))
                .andExpect(jsonPath("$.files[0].fileId").value(firstFileId.toString()))
                .andExpect(jsonPath("$.files[1].fileId").value(secondFileId.toString()));
    }

    @Test
    void createGroupRejectsTargetWhenUserOnlyHasUnrelatedStockPermission() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(
                        new AuthenticatedUser(userId.toString(), "stock", "stock@example.com", "Stock User", null, "USER", List.of()),
                        null,
                        List.of(new SimpleGrantedAuthority("STOCK_RECEIVE"))
                )
        );

        mockMvc.perform(multipart("/api/v1/attachments/groups")
                        .file(new MockMultipartFile("files", "front.pdf", "application/pdf", "%PDF-1.4\n".getBytes()))
                        .param("title", "Passport")
                        .param("targetType", "EQUIPMENT")
                        .param("targetId", targetId.toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void addFilesToExistingGroupUsesGroupEndpoint() throws Exception {
        UUID targetId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID firstFileId = UUID.randomUUID();
        UUID secondFileId = UUID.randomUUID();
        when(service.getGroup(eq(groupId), any())).thenReturn(group(groupId, targetId, firstFileId, secondFileId));
        when(service.addFiles(eq(groupId), any(), eq(List.of("back")), any()))
                .thenReturn(group(groupId, targetId, firstFileId, secondFileId));

        mockMvc.perform(multipart("/api/v1/attachments/groups/{groupId}/files", groupId)
                        .file(new MockMultipartFile("files", "back.pdf", "application/pdf", "%PDF-1.4\n".getBytes()))
                        .param("labels", "back"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.files[1].fileId").value(secondFileId.toString()));
    }

    @Test
    void listGetDeleteAndFileEndpointsDelegateToService() throws Exception {
        UUID targetId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID firstFileId = UUID.randomUUID();
        UUID secondFileId = UUID.randomUUID();
        AttachmentGroupDto dto = group(groupId, targetId, firstFileId, secondFileId);
        when(service.listGroups(eq("EQUIPMENT"), eq(targetId), any())).thenReturn(List.of(dto));
        when(service.getGroup(eq(groupId), any())).thenReturn(dto);
        when(service.downloadFile(eq(groupId), eq(firstFileId), any()))
                .thenReturn(new ByteArrayResource("pdf".getBytes()));
        when(service.getFilePresignedUrl(eq(groupId), eq(firstFileId), any()))
                .thenReturn(PresignedUrlResponse.builder()
                        .fileId(firstFileId)
                        .url("https://storage.example/front")
                        .expiresAt(LocalDateTime.now().plusMinutes(15))
                        .build());

        mockMvc.perform(get("/api/v1/attachments/groups")
                        .param("targetType", "EQUIPMENT")
                        .param("targetId", targetId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(groupId.toString()));

        mockMvc.perform(get("/api/v1/attachments/groups/{groupId}", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[0].fileId").value(firstFileId.toString()));

        mockMvc.perform(get("/api/v1/attachments/groups/{groupId}/files/{fileId}/download", groupId, firstFileId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("front.pdf")));

        mockMvc.perform(get("/api/v1/attachments/groups/{groupId}/files/{fileId}/presigned-url", groupId, firstFileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://storage.example/front"));

        mockMvc.perform(delete("/api/v1/attachments/groups/{groupId}/files/{fileId}", groupId, firstFileId))
                .andExpect(status().isNoContent());
        verify(service).removeFile(eq(groupId), eq(firstFileId), any());

        mockMvc.perform(delete("/api/v1/attachments/groups/{groupId}", groupId))
                .andExpect(status().isNoContent());
        verify(service).deleteGroup(eq(groupId), any());
    }

    private AttachmentGroupDto group(UUID groupId, UUID targetId, UUID firstFileId, UUID secondFileId) {
        return new AttachmentGroupDto(
                groupId,
                "Passport",
                "Driver passport",
                AttachmentTargetType.EQUIPMENT,
                targetId,
                null,
                null,
                currentUserId,
                LocalDateTime.now(),
                List.of(
                        file(groupId, firstFileId, "front.pdf", 0, "front"),
                        file(groupId, secondFileId, "back.pdf", 1, "back")
                )
        );
    }

    private AttachmentGroupDto.FileItem file(UUID groupId, UUID fileId, String originalName, int orderNumber, String label) {
        return new AttachmentGroupDto.FileItem(
                UUID.randomUUID(),
                fileId,
                originalName,
                fileId + ".pdf",
                "application/pdf",
                10L,
                orderNumber,
                label,
                currentUserId,
                LocalDateTime.now(),
                "/api/v1/attachments/groups/" + groupId + "/files/" + fileId + "/download",
                "/api/v1/attachments/groups/" + groupId + "/files/" + fileId + "/presigned-url"
        );
    }

    private void authenticate(UUID userId) {
        AuthenticatedUser user = new AuthenticatedUser(
                userId.toString(),
                "user",
                "user@example.com",
                "User",
                null,
                "USER",
                List.of("EQUIPMENT_READ", "EQUIPMENT_UPDATE")
        );
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(
                        user,
                        null,
                        List.of(
                                new SimpleGrantedAuthority("EQUIPMENT_READ"),
                                new SimpleGrantedAuthority("EQUIPMENT_UPDATE")
                        )
                )
        );
    }
}
