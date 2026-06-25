package com.toir.service.users;

import com.toir.dto.file.FileResponse;
import com.toir.dto.file.UploadFileResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
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
class EmployeePictureServiceTest {

    @Mock
    EmployeeRepository employeeRepository;

    @Mock
    EmployeePictureRepository employeePictureRepository;

    @Mock
    UploadedFileRepository uploadedFileRepository;

    @Mock
    FileService fileService;

    @Mock
    ScopeAccessService scopeAccessService;

    EmployeePictureService service;
    UUID userId;

    @BeforeEach
    void setUp() {
        service = new EmployeePictureService(
                employeeRepository,
                employeePictureRepository,
                uploadedFileRepository,
                fileService,
                scopeAccessService
        );
        userId = UUID.randomUUID();
    }

    @Test
    void uploadPicturesStoresImagesThroughFileServiceAndReturnsPictureHandles() {
        UUID employeeId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Employee employee = employee(employeeId);
        UploadedFile uploadedFile = uploadedFile(fileId, "portrait.png", "image/png");
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(Optional.of(employee));
        when(scopeAccessService.canAccessDepartment(employee.getDepartmentId())).thenReturn(true);
        when(fileService.upload(any(), eq(FileCategory.EMPLOYEE_PICTURE), eq(userId)))
                .thenReturn(UploadFileResponse.builder().id(fileId).build());
        when(uploadedFileRepository.findByIdAndDeletedFalse(fileId)).thenReturn(Optional.of(uploadedFile));
        when(employeePictureRepository.saveAllAndFlush(any()))
                .thenAnswer(invocation -> {
                    List<EmployeePicture> pictures = invocation.getArgument(0);
                    pictures.getFirst().setId(UUID.randomUUID());
                    return pictures;
                });

        var result = service.uploadPictures(
                employeeId,
                List.of(new MockMultipartFile("files", "portrait.png", "image/png", "png".getBytes())),
                List.of("Portrait"),
                "PROFILE",
                authenticatedUser()
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().employeeId()).isEqualTo(employeeId);
        assertThat(result.getFirst().pictureName()).isEqualTo("Portrait");
        assertThat(result.getFirst().downloadUrl()).startsWith("/api/v1/hr/employee-pictures/");
        verify(fileService).upload(any(), eq(FileCategory.EMPLOYEE_PICTURE), eq(userId));
    }

    @Test
    void uploadPicturesRejectsNonImageBeforeStorage() {
        UUID employeeId = UUID.randomUUID();
        Employee employee = employee(employeeId);
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(Optional.of(employee));
        when(scopeAccessService.canAccessDepartment(employee.getDepartmentId())).thenReturn(true);

        assertThatThrownBy(() -> service.uploadPictures(
                employeeId,
                List.of(new MockMultipartFile("files", "passport.pdf", "application/pdf", "%PDF".getBytes())),
                List.of("Passport scan"),
                null,
                authenticatedUser()
        ))
                .isInstanceOf(RestException.class)
                .hasMessage("files[0] must be an image file (jpeg, png, webp, or gif)");

        verify(fileService, never()).upload(any(), any(), any());
    }

    @Test
    void getPicturesUsesAuthorizedMetadataAfterEmployeeScopeCheck() {
        UUID employeeId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Employee employee = employee(employeeId);
        EmployeePicture picture = employeePicture(employee, uploadedFile(fileId, "portrait.png", "image/png"));
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(Optional.of(employee));
        when(scopeAccessService.canAccessDepartment(employee.getDepartmentId())).thenReturn(true);
        when(employeePictureRepository.findAllByEmployeeId(employeeId)).thenReturn(List.of(picture));
        when(fileService.getMetadataForAuthorizedFile(fileId)).thenReturn(fileResponse(fileId));

        var result = service.getPictures(employeeId, authenticatedUser());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().downloadUrl()).isEqualTo("/api/v1/hr/employee-pictures/" + picture.getId() + "/download");
        verify(fileService).getMetadataForAuthorizedFile(fileId);
        verify(fileService, never()).getMetadata(eq(fileId), any(UUID.class));
    }

    @Test
    void downloadPictureUsesAuthorizedFileDownloadAfterEmployeeScopeCheck() {
        UUID fileId = UUID.randomUUID();
        Employee employee = employee(UUID.randomUUID());
        EmployeePicture picture = employeePicture(employee, uploadedFile(fileId, "portrait.png", "image/png"));
        when(employeePictureRepository.findByIdActive(picture.getId())).thenReturn(Optional.of(picture));
        when(scopeAccessService.canAccessDepartment(employee.getDepartmentId())).thenReturn(true);
        when(fileService.downloadAuthorizedFile(fileId)).thenReturn(new ByteArrayResource("image".getBytes()));

        var resource = service.downloadPicture(picture.getId(), authenticatedUser());

        assertThat(resource).isNotNull();
        verify(fileService).downloadAuthorizedFile(fileId);
        verify(fileService, never()).download(eq(fileId), any(UUID.class));
    }

    @Test
    void deletePictureUsesAuthorizedFileDeleteAfterEmployeeScopeCheck() {
        UUID fileId = UUID.randomUUID();
        Employee employee = employee(UUID.randomUUID());
        EmployeePicture picture = employeePicture(employee, uploadedFile(fileId, "portrait.png", "image/png"));
        when(employeePictureRepository.findByIdActive(picture.getId())).thenReturn(Optional.of(picture));
        when(scopeAccessService.canAccessDepartment(employee.getDepartmentId())).thenReturn(true);

        service.deletePicture(picture.getId(), authenticatedUser());

        assertThat(picture.isDeleted()).isTrue();
        verify(employeePictureRepository).save(picture);
        verify(fileService).deleteAuthorizedFile(fileId);
        verify(fileService, never()).delete(eq(fileId), any(UUID.class));
    }

    @Test
    void currentAssignedUserCanReadOwnEmployeePicturesWithoutDepartmentScope() {
        UUID employeeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Employee employee = employee(employeeId);
        employee.setUserId(userId);
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(Optional.of(employee));
        when(scopeAccessService.canAccessDepartment(employee.getDepartmentId())).thenReturn(false);
        when(scopeAccessService.canAccessEmployee(employeeId)).thenReturn(false);
        when(scopeAccessService.canAccessAssignedUser(userId)).thenReturn(true);
        when(employeePictureRepository.findAllByEmployeeId(employeeId)).thenReturn(List.of());

        var result = service.getPictures(employeeId, authenticatedUser());

        assertThat(result).isEmpty();
    }

    private Employee employee(UUID id) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setPersonnelNumber("EMP-" + id.toString().substring(0, 8));
        employee.setFirstName("Ali");
        employee.setLastName("Valiyev");
        employee.setPosition("Mechanic");
        employee.setDepartmentId(UUID.randomUUID());
        employee.setActive(true);
        return employee;
    }

    private UploadedFile uploadedFile(UUID id, String originalName, String contentType) {
        UploadedFile file = new UploadedFile();
        file.setId(id);
        file.setOriginalName(originalName);
        file.setStoredName(id + ".png");
        file.setObjectName("employee-pictures/" + id + ".png");
        file.setContentType(contentType);
        file.setSize(123L);
        file.setExtension("png");
        file.setUploadedBy(UUID.randomUUID());
        file.setCategory(FileCategory.EMPLOYEE_PICTURE);
        file.setDeleted(false);
        return file;
    }

    private EmployeePicture employeePicture(Employee employee, UploadedFile file) {
        EmployeePicture picture = new EmployeePicture();
        picture.setId(UUID.randomUUID());
        picture.setEmployee(employee);
        picture.setFile(file);
        picture.setPictureName("Portrait");
        picture.setPictureType("PROFILE");
        picture.setUploadedBy(UUID.randomUUID());
        picture.setUploadedAt(LocalDateTime.parse("2026-06-25T06:00:00"));
        return picture;
    }

    private FileResponse fileResponse(UUID fileId) {
        return FileResponse.builder()
                .id(fileId)
                .originalName("portrait.png")
                .contentType("image/png")
                .extension("png")
                .size(123L)
                .uploadedBy(UUID.randomUUID())
                .category(FileCategory.EMPLOYEE_PICTURE)
                .deleted(false)
                .build();
    }

    private AuthenticatedUser authenticatedUser() {
        return new AuthenticatedUser(userId.toString(), "user", "user@example.com", "User", null, "USER", List.of());
    }
}
