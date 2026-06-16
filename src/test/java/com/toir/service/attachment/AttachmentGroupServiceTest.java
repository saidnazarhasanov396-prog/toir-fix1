package com.toir.service.attachment;

import com.toir.dto.attachment.AttachmentGroupDto;
import com.toir.dto.file.PresignedUrlResponse;
import com.toir.dto.file.UploadFileResponse;
import com.toir.entity.UploadedFile;
import com.toir.entity.attachment.AttachmentGroup;
import com.toir.entity.attachment.AttachmentGroupItem;
import com.toir.enums.AttachmentTargetType;
import com.toir.enums.FileCategory;
import com.toir.exception.RestException;
import com.toir.repository.UploadedFileRepository;
import com.toir.repository.attachment.AttachmentGroupItemRepository;
import com.toir.repository.attachment.AttachmentGroupRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.service.file_management.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
class AttachmentGroupServiceTest {

    @Mock
    AttachmentGroupRepository groupRepository;

    @Mock
    AttachmentGroupItemRepository itemRepository;

    @Mock
    UploadedFileRepository uploadedFileRepository;

    @Mock
    FileService fileService;

    @Mock
    AttachmentTargetAccessService targetAccessService;

    private AttachmentGroupService service;
    private UUID userId;
    private AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        service = new AttachmentGroupService(
                groupRepository,
                itemRepository,
                uploadedFileRepository,
                fileService,
                targetAccessService
        );
        userId = UUID.randomUUID();
        user = new AuthenticatedUser(userId.toString(), "user", "user@example.com", "User", null, "USER", List.of());
    }

    @Test
    void createGroupUploadsMultipleFilesIntoOneLogicalDocument() {
        UUID targetId = UUID.randomUUID();
        UUID firstFileId = UUID.randomUUID();
        UUID secondFileId = UUID.randomUUID();
        MockMultipartFile front = file("front.pdf", "application/pdf");
        MockMultipartFile back = file("back.pdf", "application/pdf");
        when(targetAccessService.assertCanAccess(AttachmentTargetType.EQUIPMENT, targetId)).thenReturn(AttachmentTargetType.EQUIPMENT);
        when(targetAccessService.fileCategoryFor(AttachmentTargetType.EQUIPMENT)).thenReturn(FileCategory.EQUIPMENT_DOCUMENT);
        when(fileService.upload(front, FileCategory.EQUIPMENT_DOCUMENT, userId)).thenReturn(upload(firstFileId, "front.pdf"));
        when(fileService.upload(back, FileCategory.EQUIPMENT_DOCUMENT, userId)).thenReturn(upload(secondFileId, "back.pdf"));
        when(uploadedFileRepository.findByIdAndDeletedFalse(firstFileId)).thenReturn(Optional.of(uploadedFile(firstFileId, "front.pdf")));
        when(uploadedFileRepository.findByIdAndDeletedFalse(secondFileId)).thenReturn(Optional.of(uploadedFile(secondFileId, "back.pdf")));
        when(groupRepository.saveAndFlush(any(AttachmentGroup.class))).thenAnswer(invocation -> {
            AttachmentGroup group = invocation.getArgument(0);
            group.setId(UUID.randomUUID());
            group.setCreatedAt(LocalDateTime.now());
            return group;
        });

        AttachmentGroupDto result = service.createGroup(
                "Passport",
                "Driver passport",
                "EQUIPMENT",
                targetId,
                List.of(front, back),
                List.of("front", "back"),
                user
        );

        assertThat(result.title()).isEqualTo("Passport");
        assertThat(result.targetType()).isEqualTo(AttachmentTargetType.EQUIPMENT);
        assertThat(result.files()).hasSize(2);
        assertThat(result.files()).extracting(AttachmentGroupDto.FileItem::orderNumber)
                .containsExactly(0, 1);
        assertThat(result.files()).extracting(AttachmentGroupDto.FileItem::label)
                .containsExactly("front", "back");

        ArgumentCaptor<AttachmentGroup> captor = ArgumentCaptor.forClass(AttachmentGroup.class);
        verify(groupRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getItems()).hasSize(2);
        assertThat(captor.getValue().getItems()).extracting(AttachmentGroupItem::getFile)
                .extracting(UploadedFile::getId)
                .containsExactly(firstFileId, secondFileId);
    }

    @Test
    void createGroupRejectsEmptyFilesBeforeUpload() {
        UUID targetId = UUID.randomUUID();
        when(targetAccessService.assertCanAccess(AttachmentTargetType.EQUIPMENT, targetId)).thenReturn(AttachmentTargetType.EQUIPMENT);

        assertThatThrownBy(() -> service.createGroup(
                "Passport",
                null,
                "EQUIPMENT",
                targetId,
                List.of(),
                null,
                user
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("At least one attachment file is required");

        verify(fileService, never()).upload(any(), any(), any());
    }

    @Test
    void addFilesAppendsToExistingGroup() {
        UUID groupId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID existingFileId = UUID.randomUUID();
        UUID addedFileId = UUID.randomUUID();
        AttachmentGroup group = group(groupId, AttachmentTargetType.WORK_ORDER, targetId, "Completion");
        group.addItem(uploadedFile(existingFileId, "act.pdf"), 0, "act");
        MockMultipartFile evidence = file("evidence.pdf", "application/pdf");
        when(groupRepository.findByIdWithItems(groupId)).thenReturn(Optional.of(group));
        when(targetAccessService.assertCanAccess(AttachmentTargetType.WORK_ORDER, targetId)).thenReturn(AttachmentTargetType.WORK_ORDER);
        when(targetAccessService.fileCategoryFor(AttachmentTargetType.WORK_ORDER)).thenReturn(FileCategory.WORK_ORDER_DOCUMENT);
        when(fileService.upload(evidence, FileCategory.WORK_ORDER_DOCUMENT, userId)).thenReturn(upload(addedFileId, "evidence.pdf"));
        when(uploadedFileRepository.findByIdAndDeletedFalse(addedFileId)).thenReturn(Optional.of(uploadedFile(addedFileId, "evidence.pdf")));
        when(groupRepository.saveAndFlush(group)).thenReturn(group);

        AttachmentGroupDto result = service.addFiles(groupId, List.of(evidence), List.of("evidence"), user);

        assertThat(result.files()).hasSize(2);
        assertThat(result.files()).extracting(AttachmentGroupDto.FileItem::orderNumber)
                .containsExactly(0, 1);
        verify(groupRepository).saveAndFlush(group);
    }

    @Test
    void listGroupsValidatesTargetAccessAndReturnsGroups() {
        UUID targetId = UUID.randomUUID();
        AttachmentGroup first = group(UUID.randomUUID(), AttachmentTargetType.VEHICLE, targetId, "Passport");
        first.addItem(uploadedFile(UUID.randomUUID(), "front.pdf"), 0, "front");
        when(targetAccessService.assertCanAccess(AttachmentTargetType.VEHICLE, targetId)).thenReturn(AttachmentTargetType.VEHICLE);
        when(groupRepository.findActiveByTarget(AttachmentTargetType.VEHICLE, targetId)).thenReturn(List.of(first));

        List<AttachmentGroupDto> result = service.listGroups("VEHICLE", targetId, user);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("Passport");
        assertThat(result.getFirst().files()).hasSize(1);
    }

    @Test
    void removeFileDeletesOneAssociationAndUploadedFile() {
        UUID groupId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        AttachmentGroup group = group(groupId, AttachmentTargetType.EQUIPMENT, targetId, "Passport");
        group.addItem(uploadedFile(fileId, "front.pdf"), 0, "front");
        group.addItem(uploadedFile(UUID.randomUUID(), "back.pdf"), 1, "back");
        when(groupRepository.findByIdWithItems(groupId)).thenReturn(Optional.of(group));
        when(targetAccessService.assertCanAccess(AttachmentTargetType.EQUIPMENT, targetId)).thenReturn(AttachmentTargetType.EQUIPMENT);
        when(groupRepository.saveAndFlush(group)).thenReturn(group);

        service.removeFile(groupId, fileId, user);

        assertThat(group.getItems()).hasSize(1);
        verify(fileService).delete(fileId, userId);
    }

    @Test
    void deleteGroupDeletesAssociationsAndUploadedFiles() {
        UUID groupId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID firstFileId = UUID.randomUUID();
        UUID secondFileId = UUID.randomUUID();
        AttachmentGroup group = group(groupId, AttachmentTargetType.WORK_ORDER, targetId, "Passport");
        group.addItem(uploadedFile(firstFileId, "front.pdf"), 0, "front");
        group.addItem(uploadedFile(secondFileId, "back.pdf"), 1, "back");
        when(groupRepository.findByIdWithItems(groupId)).thenReturn(Optional.of(group));
        when(targetAccessService.assertCanAccess(AttachmentTargetType.WORK_ORDER, targetId)).thenReturn(AttachmentTargetType.WORK_ORDER);

        service.deleteGroup(groupId, user);

        assertThat(group.getDeleted()).isTrue();
        verify(fileService).delete(firstFileId, userId);
        verify(fileService).delete(secondFileId, userId);
        verify(groupRepository).save(group);
    }

    @Test
    void presignedUrlRequiresGroupMembership() {
        UUID groupId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        AttachmentGroup group = group(groupId, AttachmentTargetType.EQUIPMENT, targetId, "Passport");
        group.addItem(uploadedFile(fileId, "front.pdf"), 0, "front");
        PresignedUrlResponse response = PresignedUrlResponse.builder()
                .fileId(fileId)
                .url("https://storage.example/front")
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();
        when(groupRepository.findByIdWithItems(groupId)).thenReturn(Optional.of(group));
        when(targetAccessService.assertCanAccess(AttachmentTargetType.EQUIPMENT, targetId)).thenReturn(AttachmentTargetType.EQUIPMENT);
        when(fileService.getPresignedUrlForAuthorizedFile(fileId)).thenReturn(response);

        PresignedUrlResponse result = service.getFilePresignedUrl(groupId, fileId, user);

        assertThat(result.url()).isEqualTo("https://storage.example/front");
    }

    private MockMultipartFile file(String name, String contentType) {
        return new MockMultipartFile("files", name, contentType, "%PDF-1.4\n".getBytes());
    }

    private UploadFileResponse upload(UUID id, String originalName) {
        return UploadFileResponse.builder()
                .id(id)
                .originalName(originalName)
                .storedName(id + ".pdf")
                .contentType("application/pdf")
                .extension("pdf")
                .size(10L)
                .category(FileCategory.DOCUMENT)
                .build();
    }

    private UploadedFile uploadedFile(UUID id, String originalName) {
        return UploadedFile.builder()
                .id(id)
                .originalName(originalName)
                .storedName(id + ".pdf")
                .objectName("documents/2026/06/" + id + ".pdf")
                .contentType("application/pdf")
                .extension("pdf")
                .size(10L)
                .uploadedBy(userId)
                .category(FileCategory.DOCUMENT)
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private AttachmentGroup group(UUID id, AttachmentTargetType targetType, UUID targetId, String title) {
        AttachmentGroup group = AttachmentGroup.builder()
                .id(id)
                .title(title)
                .targetType(targetType)
                .targetId(targetId)
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .deleted(false)
                .build();
        group.setItems(new java.util.ArrayList<>());
        return group;
    }
}
