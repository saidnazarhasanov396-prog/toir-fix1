package com.toir.service.attachment;

import com.toir.dto.attachment.AttachmentGroupDto;
import com.toir.dto.attachment.AttachmentPhotoSummary;
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
import com.toir.repository.attachment.AttachmentTargetPhotoCountProjection;
import com.toir.repository.attachment.AttachmentTargetPrimaryPhotoProjection;
import com.toir.security.AuthenticatedUser;
import com.toir.service.file_management.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentGroupService {

    private static final int MAX_FILES_PER_GROUP = 25;

    private final AttachmentGroupRepository groupRepository;
    private final AttachmentGroupItemRepository itemRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final FileService fileService;
    private final AttachmentTargetAccessService targetAccessService;

    @Transactional
    public AttachmentGroupDto createGroup(
            String title,
            String description,
            String targetType,
            UUID targetId,
            List<MultipartFile> files,
            List<String> labels,
            AuthenticatedUser user
    ) {
        return createGroup(title, description, targetType, targetId, null, null, files, labels, user);
    }

    @Transactional
    public AttachmentGroupDto createGroup(
            String title,
            String description,
            String targetType,
            UUID targetId,
            String documentType,
            String documentNumber,
            List<MultipartFile> files,
            List<String> labels,
            AuthenticatedUser user
    ) {
        UUID currentUserId = currentUserId(user);
        AttachmentTargetType normalizedTargetType = AttachmentTargetType.from(targetType);
        targetAccessService.assertCanAccess(normalizedTargetType, targetId);
        validateFiles(files);
        validateFileLimit(files.size());
        List<String> normalizedLabels = normalizeLabels(files, labels);
        FileCategory category = targetAccessService.fileCategoryFor(normalizedTargetType);
        List<UUID> uploadedFileIds = new ArrayList<>();
        try {
            List<UploadedFile> uploadedFiles = uploadFiles(files, category, currentUserId, uploadedFileIds);
            AttachmentGroup group = AttachmentGroup.builder()
                    .title(normalizeTitle(title))
                    .description(trimToNull(description))
                    .targetType(normalizedTargetType)
                    .targetId(targetId)
                    .documentType(normalizeToken(documentType, "documentType", 64))
                    .documentNumber(normalizeToken(documentNumber, "documentNumber", 128))
                    .createdBy(currentUserId)
                    .deleted(false)
                    .build();
            for (int i = 0; i < uploadedFiles.size(); i++) {
                group.addItem(uploadedFiles.get(i), i, normalizedLabels.get(i));
            }
            return AttachmentGroupDto.from(groupRepository.saveAndFlush(group));
        } catch (RuntimeException e) {
            cleanupUploadedFiles(uploadedFileIds, currentUserId);
            throw e;
        }
    }

    @Transactional
    public AttachmentGroupDto addFiles(UUID groupId, List<MultipartFile> files, List<String> labels, AuthenticatedUser user) {
        UUID currentUserId = currentUserId(user);
        AttachmentGroup group = groupOrThrow(groupId);
        targetAccessService.assertCanAccess(group.getTargetType(), group.getTargetId());
        validateFiles(files);
        int existingCount = activeItems(group).size();
        validateFileLimit(existingCount + files.size());
        List<String> normalizedLabels = normalizeLabels(files, labels);
        int nextOrderNumber = activeItems(group).stream()
                .map(AttachmentGroupItem::getOrderNumber)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
        FileCategory category = targetAccessService.fileCategoryFor(group.getTargetType());
        List<UUID> uploadedFileIds = new ArrayList<>();
        try {
            List<UploadedFile> uploadedFiles = uploadFiles(files, category, currentUserId, uploadedFileIds);
            for (int i = 0; i < uploadedFiles.size(); i++) {
                group.addItem(uploadedFiles.get(i), nextOrderNumber + i, normalizedLabels.get(i));
            }
            return AttachmentGroupDto.from(groupRepository.saveAndFlush(group));
        } catch (RuntimeException e) {
            cleanupUploadedFiles(uploadedFileIds, currentUserId);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public AttachmentGroupDto getGroup(UUID groupId, AuthenticatedUser user) {
        currentUserId(user);
        AttachmentGroup group = groupOrThrow(groupId);
        targetAccessService.assertCanAccess(group.getTargetType(), group.getTargetId());
        return AttachmentGroupDto.from(group);
    }

    @Transactional(readOnly = true)
    public List<AttachmentGroupDto> listGroups(String targetType, UUID targetId, AuthenticatedUser user) {
        currentUserId(user);
        AttachmentTargetType normalizedTargetType = AttachmentTargetType.from(targetType);
        targetAccessService.assertCanAccess(normalizedTargetType, targetId);
        return groupRepository.findActiveByTarget(normalizedTargetType, targetId)
                .stream()
                .map(AttachmentGroupDto::from)
                .toList();
    }

    @Transactional
    public void deleteGroup(UUID groupId, AuthenticatedUser user) {
        UUID currentUserId = currentUserId(user);
        AttachmentGroup group = groupOrThrow(groupId);
        targetAccessService.assertCanAccess(group.getTargetType(), group.getTargetId());
        activeFileIds(group).forEach(fileId -> fileService.delete(fileId, currentUserId));
        group.setDeleted(true);
        group.setDeletedAt(LocalDateTime.now());
        groupRepository.save(group);
    }

    @Transactional
    public void removeFile(UUID groupId, UUID fileId, AuthenticatedUser user) {
        UUID currentUserId = currentUserId(user);
        AttachmentGroup group = groupOrThrow(groupId);
        targetAccessService.assertCanAccess(group.getTargetType(), group.getTargetId());
        AttachmentGroupItem item = activeItems(group).stream()
                .filter(candidate -> candidate.getFile() != null && fileId.equals(candidate.getFile().getId()))
                .findFirst()
                .orElseThrow(() -> RestException.notFound("Attachment group file not found: " + fileId));
        group.getItems().remove(item);
        groupRepository.saveAndFlush(group);
        fileService.delete(fileId, currentUserId);
    }

    @Transactional(readOnly = true)
    public PresignedUrlResponse getFilePresignedUrl(UUID groupId, UUID fileId, AuthenticatedUser user) {
        currentUserId(user);
        AttachmentGroup group = groupOrThrow(groupId);
        targetAccessService.assertCanAccess(group.getTargetType(), group.getTargetId());
        assertFileInGroup(group, fileId);
        return fileService.getPresignedUrlForAuthorizedFile(fileId);
    }

    @Transactional(readOnly = true)
    public Resource downloadFile(UUID groupId, UUID fileId, AuthenticatedUser user) {
        currentUserId(user);
        AttachmentGroup group = groupOrThrow(groupId);
        targetAccessService.assertCanAccess(group.getTargetType(), group.getTargetId());
        assertFileInGroup(group, fileId);
        return fileService.downloadAuthorizedFile(fileId);
    }

    @Transactional(readOnly = true)
    public AttachmentGroupDto findGroupByTargetAndFile(
            AttachmentTargetType targetType,
            UUID targetId,
            UUID fileId,
            AuthenticatedUser user
    ) {
        currentUserId(user);
        targetAccessService.assertCanAccess(targetType, targetId);
        return groupRepository.findActiveByTargetAndFileId(targetType, targetId, fileId)
                .map(AttachmentGroupDto::from)
                .orElseThrow(() -> RestException.notFound("Attachment file not found: " + fileId));
    }

    @Transactional
    public void removeFileByTarget(
            AttachmentTargetType targetType,
            UUID targetId,
            UUID fileId,
            AuthenticatedUser user
    ) {
        AttachmentGroupDto group = findGroupByTargetAndFile(targetType, targetId, fileId, user);
        removeFile(group.id(), fileId, user);
    }

    @Transactional(readOnly = true)
    public Map<UUID, AttachmentPhotoSummary> getPhotoSummaries(AttachmentTargetType targetType, Collection<UUID> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> countByTargetId = new HashMap<>();
        for (AttachmentTargetPhotoCountProjection row : groupRepository.countActiveImagesByTargetIds(targetType, targetIds)) {
            countByTargetId.put(row.getTargetId(), row.getPhotoCount());
        }
        if (countByTargetId.isEmpty()) {
            return Map.of();
        }
        Map<UUID, AttachmentTargetPrimaryPhotoProjection> primaryByTargetId = new HashMap<>();
        for (AttachmentTargetPrimaryPhotoProjection row : groupRepository.findPrimaryActiveImageByTargetIds(targetType.name(), targetIds)) {
            primaryByTargetId.put(row.getTargetId(), row);
        }
        Map<UUID, AttachmentPhotoSummary> summaries = new HashMap<>();
        countByTargetId.forEach((targetId, count) -> {
            AttachmentTargetPrimaryPhotoProjection primary = primaryByTargetId.get(targetId);
            String downloadUrl = primary == null
                    ? null
                    : "/api/v1/attachments/groups/" + primary.getGroupId() + "/files/" + primary.getFileId() + "/download";
            summaries.put(targetId, new AttachmentPhotoSummary(count.intValue(), downloadUrl));
        });
        return summaries;
    }

    private AttachmentGroup groupOrThrow(UUID groupId) {
        if (groupId == null) {
            throw RestException.badRequest("groupId is required");
        }
        return groupRepository.findByIdWithItems(groupId)
                .orElseThrow(() -> RestException.notFound("Attachment group not found: " + groupId));
    }

    private List<UploadedFile> uploadFiles(
            List<MultipartFile> files,
            FileCategory category,
            UUID currentUserId,
            List<UUID> uploadedFileIds
    ) {
        List<UploadedFile> uploadedFiles = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            UploadFileResponse uploaded = fileService.upload(file, category, currentUserId);
            uploadedFileIds.add(uploaded.id());
            uploadedFiles.add(uploadedFileRepository.findByIdAndDeletedFalse(uploaded.id())
                    .orElseThrow(() -> RestException.notFound("Uploaded file not found: " + uploaded.id())));
        }
        return uploadedFiles;
    }

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw RestException.badRequest("At least one attachment file is required");
        }
    }

    private void validateFileLimit(int fileCount) {
        if (fileCount > MAX_FILES_PER_GROUP) {
            throw RestException.badRequest("An attachment group cannot contain more than 25 files");
        }
    }

    private List<String> normalizeLabels(List<MultipartFile> files, List<String> labels) {
        if (labels != null && !labels.isEmpty() && labels.size() != files.size()) {
            throw RestException.badRequest("files and labels must have the same length");
        }
        List<String> result = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            result.add(labels == null || labels.isEmpty() ? null : normalizeToken(labels.get(i), "labels[" + i + "]", 255));
        }
        return result;
    }

    private String normalizeTitle(String title) {
        String normalized = normalizeToken(title, "title", 255);
        if (!StringUtils.hasText(normalized)) {
            throw RestException.badRequest("title is required");
        }
        return normalized;
    }

    private String normalizeToken(String value, String field, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw RestException.badRequest(field + " must be " + maxLength + " characters or fewer");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<AttachmentGroupItem> activeItems(AttachmentGroup group) {
        if (group.getItems() == null) {
            return List.of();
        }
        return group.getItems().stream()
                .filter(item -> item.getFile() != null && !Boolean.TRUE.equals(item.getFile().getDeleted()))
                .sorted(Comparator.comparing(
                        AttachmentGroupItem::getOrderNumber,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private List<UUID> activeFileIds(AttachmentGroup group) {
        return activeItems(group).stream()
                .map(AttachmentGroupItem::getFile)
                .map(UploadedFile::getId)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf));
    }

    private void assertFileInGroup(AttachmentGroup group, UUID fileId) {
        if (activeItems(group).stream().noneMatch(item -> fileId.equals(item.getFile().getId()))) {
            throw RestException.notFound("Attachment group file not found: " + fileId);
        }
    }

    private void cleanupUploadedFiles(List<UUID> fileIds, UUID currentUserId) {
        for (UUID fileId : fileIds) {
            try {
                fileService.delete(fileId, currentUserId);
            } catch (RuntimeException ignored) {
                // Best-effort rollback keeps the original upload/linking error visible.
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
