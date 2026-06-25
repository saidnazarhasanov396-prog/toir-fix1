package com.toir.service.users;

import com.toir.dto.file.UploadFileResponse;
import com.toir.dto.hr.EmployeePictureDto;
import com.toir.entity.UploadedFile;
import com.toir.entity.users.Employee;
import com.toir.entity.users.EmployeePicture;
import com.toir.enums.FileCategory;
import com.toir.exception.RestException;
import com.toir.repository.UploadedFileRepository;
import com.toir.repository.users.EmployeePictureRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.security.ScopeAccessService;
import com.toir.service.file_management.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeePictureService {

    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final EmployeeRepository employeeRepository;
    private final EmployeePictureRepository employeePictureRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final FileService fileService;
    private final ScopeAccessService scopeAccessService;

    @Transactional
    public List<EmployeePictureDto> uploadPictures(
            UUID employeeId,
            List<MultipartFile> files,
            List<String> pictureNames,
            String pictureType,
            AuthenticatedUser user
    ) {
        UUID currentUserId = currentUserId(user);
        Employee employee = employeeOrThrow(employeeId);
        enforceEmployeeAccess(employee);
        if (files == null || files.isEmpty()) {
            throw RestException.badRequest("At least one employee picture file is required");
        }
        validateImageFiles(files);
        List<String> normalizedPictureNames = normalizePictureNames(files, pictureNames);
        String normalizedPictureType = normalizePictureType(pictureType);

        List<UUID> uploadedFileIds = new ArrayList<>();
        try {
            List<EmployeePicture> pictures = new ArrayList<>(files.size());
            for (int i = 0; i < files.size(); i++) {
                UploadFileResponse uploaded = fileService.upload(files.get(i), FileCategory.EMPLOYEE_PICTURE, currentUserId);
                uploadedFileIds.add(uploaded.id());
                UploadedFile uploadedFile = uploadedFileRepository.findByIdAndDeletedFalse(uploaded.id())
                        .orElseThrow(() -> RestException.notFound("Uploaded file not found: " + uploaded.id()));
                pictures.add(EmployeePicture.builder()
                        .employee(employee)
                        .file(uploadedFile)
                        .pictureType(normalizedPictureType)
                        .pictureName(normalizedPictureNames.get(i))
                        .uploadedBy(currentUserId)
                        .build());
            }

            return employeePictureRepository.saveAllAndFlush(pictures).stream()
                    .map(picture -> EmployeePictureDto.from(employeeId, picture))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (RuntimeException e) {
            cleanupUploadedFiles(uploadedFileIds, currentUserId);
            if (e instanceof RestException restException) {
                throw restException;
            }
            throw RestException.conflict("Could not attach employee pictures");
        }
    }

    @Transactional(readOnly = true)
    public List<EmployeePictureDto> getPictures(UUID employeeId, AuthenticatedUser user) {
        currentUserId(user);
        Employee employee = employeeOrThrow(employeeId);
        enforceEmployeeAccess(employee);
        return employeePictureRepository.findAllByEmployeeId(employeeId)
                .stream()
                .map(picture -> toDtoWithMetadata(employeeId, picture))
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeePictureDto getPicture(UUID pictureId, AuthenticatedUser user) {
        currentUserId(user);
        EmployeePicture picture = pictureOrThrow(pictureId);
        enforceEmployeeAccess(picture.getEmployee());
        return toDtoWithMetadata(picture.getEmployee().getId(), picture);
    }

    @Transactional(readOnly = true)
    public Resource downloadPicture(UUID pictureId, AuthenticatedUser user) {
        currentUserId(user);
        EmployeePicture picture = pictureOrThrow(pictureId);
        enforceEmployeeAccess(picture.getEmployee());
        return fileService.downloadAuthorizedFile(picture.getFile().getId());
    }

    @Transactional
    public void deletePicture(UUID pictureId, AuthenticatedUser user) {
        currentUserId(user);
        EmployeePicture picture = pictureOrThrow(pictureId);
        enforceEmployeeAccess(picture.getEmployee());
        picture.setDeleted(true);
        employeePictureRepository.save(picture);
        fileService.deleteAuthorizedFile(picture.getFile().getId());
    }

    private Employee employeeOrThrow(UUID employeeId) {
        return employeeRepository.findByIdAndIsDeletedFalse(employeeId)
                .orElseThrow(() -> RestException.notFound("Employee not found: " + employeeId));
    }

    private EmployeePicture pictureOrThrow(UUID pictureId) {
        return employeePictureRepository.findByIdActive(pictureId)
                .orElseThrow(() -> RestException.notFound("Employee picture not found: " + pictureId));
    }

    private EmployeePictureDto toDtoWithMetadata(UUID employeeId, EmployeePicture picture) {
        fileService.getMetadataForAuthorizedFile(picture.getFile().getId());
        return EmployeePictureDto.from(employeeId, picture);
    }

    private void enforceEmployeeAccess(Employee employee) {
        if (employee == null) {
            throw new AccessDeniedException("Access denied by employee scope");
        }
        if (scopeAccessService.isScopeAdmin()
                || scopeAccessService.canAccessDepartment(employee.getDepartmentId())
                || scopeAccessService.canAccessEmployee(employee.getId())
                || scopeAccessService.canAccessAssignedUser(employee.getUserId())) {
            return;
        }
        throw new AccessDeniedException("Access denied by employee scope");
    }

    private void validateImageFiles(List<MultipartFile> files) {
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String contentType = file == null ? null : file.getContentType();
            if (contentType == null || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType.toLowerCase())) {
                throw RestException.badRequest("files[" + i + "] must be an image file (jpeg, png, webp, or gif)");
            }
        }
    }

    private List<String> normalizePictureNames(List<MultipartFile> files, List<String> pictureNames) {
        if (pictureNames != null && !pictureNames.isEmpty() && pictureNames.size() != files.size()) {
            throw RestException.badRequest("files and pictureNames must have the same length");
        }
        List<String> normalized = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            String pictureName = pictureNames == null || pictureNames.isEmpty()
                    ? defaultPictureName(files.get(i))
                    : pictureNames.get(i);
            if (pictureName == null || pictureName.isBlank()) {
                throw RestException.badRequest("pictureNames[" + i + "] must not be blank");
            }
            String trimmed = pictureName.trim();
            if (trimmed.length() > 255) {
                throw RestException.badRequest("pictureNames[" + i + "] must be 255 characters or fewer");
            }
            normalized.add(trimmed);
        }
        return normalized;
    }

    private String normalizePictureType(String pictureType) {
        if (pictureType == null || pictureType.isBlank()) {
            return null;
        }
        String trimmed = pictureType.trim();
        if (trimmed.length() > 64) {
            throw RestException.badRequest("pictureType must be 64 characters or fewer");
        }
        return trimmed;
    }

    private String defaultPictureName(MultipartFile file) {
        if (file != null && file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()) {
            return file.getOriginalFilename().trim();
        }
        return "Employee picture";
    }

    private void cleanupUploadedFiles(List<UUID> fileIds, UUID currentUserId) {
        for (UUID fileId : fileIds) {
            try {
                fileService.delete(fileId, currentUserId);
            } catch (RuntimeException e) {
                log.warn("Failed to cleanup employee picture file '{}': {}", fileId, e.getMessage());
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
