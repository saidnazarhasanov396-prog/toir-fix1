package com.toir.service;

import com.toir.dto.hr.*;
import com.toir.entity.Department;
import com.toir.entity.UploadedFile;
import com.toir.entity.users.Brigade;
import com.toir.entity.users.Employee;
import com.toir.entity.users.EmployeePicture;
import com.toir.entity.users.EmployeeSpecialisation;
import com.toir.enums.DepartmentType;
import com.toir.enums.FileCategory;
import com.toir.repository.TimesheetEntryRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.projects.BrigadeRepository;
import com.toir.repository.projects.EmployeeStatsProjection;
import com.toir.repository.users.EmployeePictureRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.EmployeeSpecialisationRepository;
import com.toir.repository.users.EmployeeWorkRoleAssignmentRepository;
import com.toir.repository.users.EmployeeWorkRoleCodeProjection;
import com.toir.repository.users.EmployeeWorkRoleRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.users.HrService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrServiceTest {

    @Mock
    EmployeeRepository employeeRepository;

    @Mock
    TimesheetEntryRepository timesheetRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    BrigadeRepository brigadeRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    EmployeeWorkRoleAssignmentRepository employeeWorkRoleAssignmentRepository;

    @Mock
    EmployeeWorkRoleRepository employeeWorkRoleRepository;

    @Mock
    EmployeeSpecialisationRepository employeeSpecialisationRepository;

    @Mock
    EmployeePictureRepository employeePictureRepository;

    @InjectMocks
    HrService service;

    @BeforeEach
    void setUpScopeAdminBypass() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(employeeSpecialisationRepository.findByIdAndIsDeletedFalse(any(UUID.class)))
                .thenAnswer(invocation -> Optional.of(specialisation(invocation.getArgument(0))));
    }

    @Test
    void listEmployeesIncludesDepartmentNameAndBrigadeName() {
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        Employee employee = employee(employeeId, departmentId, brigadeId);

        Department department = department(departmentId, "Mechanical");
        Brigade brigade = brigade(brigadeId, "Repair Brigade A");

        when(employeeRepository.searchEmployees(
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(
                List.of(employee),
                PageRequest.of(0, 20),
                1
        ));

        when(departmentRepository.findAllByIdInAndIsDeletedFalse(List.of(departmentId)))
                .thenReturn(List.of(department));
        when(brigadeRepository.findAllByIdInAndIsDeletedFalse(List.of(brigadeId)))
                .thenReturn(List.of(brigade));

        var result = service.listEmployees(0, 20, null, null, null, null);

        assertThat(result.getContent()).hasSize(1);

        EmployeeDto dto = result.getContent().getFirst();

        assertThat(dto.id()).isEqualTo(employeeId);
        assertThat(dto.departmentId()).isEqualTo(departmentId);
        assertThat(dto.departmentName()).isEqualTo("Mechanical");
        assertThat(dto.brigadeId()).isEqualTo(brigadeId);
        assertThat(dto.brigadeName()).isEqualTo("Repair Brigade A");

        verify(departmentRepository).findAllByIdInAndIsDeletedFalse(List.of(departmentId));
        verify(brigadeRepository).findAllByIdInAndIsDeletedFalse(List.of(brigadeId));
    }

    @Test
    void listEmployeesIncludesPrimaryPictureForTableAvatar() {
        UUID departmentId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID pictureId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Employee employee = employee(employeeId, departmentId, null);
        EmployeePicture picture = employeePicture(
                pictureId,
                employee,
                uploadedPictureFile(fileId, "portrait.png")
        );

        when(employeeRepository.searchEmployees(
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(
                List.of(employee),
                PageRequest.of(0, 20),
                1
        ));
        when(employeePictureRepository.findPrimaryCandidatesByEmployeeIds(List.of(employeeId)))
                .thenReturn(List.of(picture));

        var result = service.listEmployees(0, 20, null, null, null, null);

        EmployeeDto dto = result.getContent().getFirst();
        assertThat(dto.primaryPicture()).isNotNull();
        assertThat(dto.primaryPicture().id()).isEqualTo(pictureId);
        assertThat(dto.primaryPicture().employeeId()).isEqualTo(employeeId);
        assertThat(dto.primaryPicture().downloadUrl())
                .isEqualTo("/api/v1/hr/employee-pictures/" + pictureId + "/download");
    }

    @Test
    void listEmployeesPassesDepartmentAndBrigadeFiltersToRepository() {
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        Employee employee = employee(employeeId, departmentId, brigadeId);

        Department department = department(departmentId, "Mechanical");
        Brigade brigade = brigade(brigadeId, "Repair Brigade A");

        when(employeeRepository.searchEmployees(
                "Ali",
                null,
                null,
                true,
                departmentId,
                brigadeId,
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(
                List.of(employee),
                PageRequest.of(0, 20),
                1
        ));

        when(departmentRepository.findAllByIdInAndIsDeletedFalse(List.of(departmentId)))
                .thenReturn(List.of(department));
        when(brigadeRepository.findAllByIdInAndIsDeletedFalse(List.of(brigadeId)))
                .thenReturn(List.of(brigade));

        var result = service.listEmployees(0, 20, "Ali", true, departmentId, brigadeId);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().departmentId()).isEqualTo(departmentId);
        assertThat(result.getContent().getFirst().brigadeId()).isEqualTo(brigadeId);

        verify(employeeRepository).searchEmployees(
                "Ali",
                null,
                null,
                true,
                departmentId,
                brigadeId,
                PageRequest.of(0, 20)
        );
    }

    @Test
    void listEmployeesWithWorkRoleFilterUsesWorkRoleRepositoryQueryAndIncludesCodes() {
        UUID departmentId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Employee employee = employee(employeeId, departmentId, null);

        when(employeeRepository.searchEmployeesByWorkRole(
                null,
                null,
                null,
                true,
                departmentId,
                null,
                "DRIVER",
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(
                List.of(employee),
                PageRequest.of(0, 20),
                1
        ));
        when(employeeWorkRoleAssignmentRepository.findActiveWorkRoleCodesByEmployeeIds(List.of(employeeId)))
                .thenReturn(List.of(workRoleCode(employeeId, "DRIVER")));

        var result = service.listEmployees(0, 20, null, true, departmentId, null, "DRIVER");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().workRoleCodes()).containsExactly("DRIVER");
        verify(employeeRepository).searchEmployeesByWorkRole(
                null,
                null,
                null,
                true,
                departmentId,
                null,
                "DRIVER",
                PageRequest.of(0, 20)
        );
    }

    @Test
    void listEmployeesForNonExistingDepartmentReturnsEmptyPage() {
        UUID departmentId = UUID.randomUUID();

        when(employeeRepository.searchEmployees(
                null,
                null,
                null,
                null,
                departmentId,
                null,
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(
                List.of(),
                PageRequest.of(0, 20),
                0
        ));

        var result = service.listEmployees(0, 20, null, null, departmentId, null);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verify(employeeRepository).searchEmployees(
                null,
                null,
                null,
                null,
                departmentId,
                null,
                PageRequest.of(0, 20)
        );
    }

    @Test
    void getEmployeeStatsWithoutFiltersReturnsStats() {
        EmployeeStatsProjection projection = statsProjection(27L, 10L, 0L, 1L);

        when(employeeRepository.getEmployeeStats(null, null, null))
                .thenReturn(projection);

        EmployeeStatsResponse result = service.getEmployeeStats(null, null, null);

        assertThat(result.total()).isEqualTo(27);
        assertThat(result.active()).isEqualTo(10);
        assertThat(result.terminated()).isZero();
        assertThat(result.withoutEmail()).isEqualTo(1);

        verify(employeeRepository).getEmployeeStats(null, null, null);
    }

    @Test
    void getEmployeeStatsWithFiltersPassesNormalizedSearchPattern() {
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();

        EmployeeStatsProjection projection = statsProjection(5L, 4L, 1L, 2L);

        when(employeeRepository.getEmployeeStats(departmentId, brigadeId, "%ali%"))
                .thenReturn(projection);

        EmployeeStatsResponse result = service.getEmployeeStats(
                departmentId,
                brigadeId,
                "  Ali  "
        );

        assertThat(result.total()).isEqualTo(5);
        assertThat(result.active()).isEqualTo(4);
        assertThat(result.terminated()).isEqualTo(1);
        assertThat(result.withoutEmail()).isEqualTo(2);

        verify(employeeRepository).getEmployeeStats(departmentId, brigadeId, "%ali%");
    }

    @Test
    void getEmployeeStatsMapsNullProjectionValuesToZero() {
        EmployeeStatsProjection projection = statsProjection(null, null, null, null);

        when(employeeRepository.getEmployeeStats(null, null, null))
                .thenReturn(projection);

        EmployeeStatsResponse result = service.getEmployeeStats(null, null, null);

        assertThat(result.total()).isZero();
        assertThat(result.active()).isZero();
        assertThat(result.terminated()).isZero();
        assertThat(result.withoutEmail()).isZero();
    }

    @Test
    void listEmployeesWithBlankDepartmentAndBrigadeReturnsNullNames() {
        UUID employeeId = UUID.randomUUID();

        Employee employee = employee(employeeId, null, null);

        when(employeeRepository.searchEmployees(
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(
                List.of(employee),
                PageRequest.of(0, 20),
                1
        ));

        var result = service.listEmployees(0, 20, null, null, null, null);

        EmployeeDto dto = result.getContent().getFirst();

        assertThat(dto.id()).isEqualTo(employeeId);
        assertThat(dto.departmentId()).isNull();
        assertThat(dto.departmentName()).isNull();
        assertThat(dto.brigadeId()).isNull();
        assertThat(dto.brigadeName()).isNull();
    }

    @Test
    void getEmployeeIncludesDepartmentNameAndBrigadeName() {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();

        Employee employee = employee(employeeId, departmentId, brigadeId);
        Department department = department(departmentId, "Mechanical");
        Brigade brigade = brigade(brigadeId, "Repair Brigade A");

        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId))
                .thenReturn(Optional.of(employee));
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(List.of(departmentId)))
                .thenReturn(List.of(department));
        when(brigadeRepository.findAllByIdInAndIsDeletedFalse(List.of(brigadeId)))
                .thenReturn(List.of(brigade));

        EmployeeDto result = service.getEmployee(employeeId);

        assertThat(result.id()).isEqualTo(employeeId);
        assertThat(result.departmentId()).isEqualTo(departmentId);
        assertThat(result.departmentName()).isEqualTo("Mechanical");
        assertThat(result.brigadeId()).isEqualTo(brigadeId);
        assertThat(result.brigadeName()).isEqualTo("Repair Brigade A");

        verify(employeeRepository).findByIdAndIsDeletedFalse(employeeId);
        verify(departmentRepository).findAllByIdInAndIsDeletedFalse(List.of(departmentId));
        verify(brigadeRepository).findAllByIdInAndIsDeletedFalse(List.of(brigadeId));
    }

    @Test
    void getEmployeeIncludesSpecialisationNames() {
        UUID employeeId = UUID.randomUUID();
        UUID specialisationId = UUID.randomUUID();

        Employee employee = employee(employeeId, null, null);
        employee.setSpecialisationId(specialisationId);

        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId))
                .thenReturn(Optional.of(employee));
        when(employeeSpecialisationRepository.findAllByIdInAndIsDeletedFalse(List.of(specialisationId)))
                .thenReturn(List.of(specialisation(specialisationId)));

        EmployeeDto result = service.getEmployee(employeeId);

        assertThat(result.specialisationId()).isEqualTo(specialisationId);
        assertThat(result.specialisationNameRu()).isEqualTo("Механик");
        assertThat(result.specialisationNameEn()).isEqualTo("Mechanic");
        assertThat(result.specialisationNameUz()).isEqualTo("Mexanik");
    }

    @Test
    void createEmployeeReturnsDepartmentNameAndBrigadeName() {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();
        UUID specialisationId = UUID.randomUUID();

        EmployeeRequest request = new EmployeeRequest(
                "EMP-001",
                "Ali",
                "Valiyev",
                "Akmalovich",
                "Engineer",
                departmentId,
                brigadeId,
                null,
                specialisationId,
                LocalDate.of(2025, 1, 10),
                null,
                "A",
                "+998901112233",
                "ali@example.com",
                true
        );

        Department department = department(departmentId, "Mechanical");
        Brigade brigade = brigade(brigadeId, "Repair Brigade A");

        when(employeeRepository.existsByPersonnelNumberAndIsDeletedFalse("EMP-001"))
                .thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee saved = invocation.getArgument(0);
            saved.setId(employeeId);
            return saved;
        });
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(List.of(departmentId)))
                .thenReturn(List.of(department));
        when(brigadeRepository.findAllByIdInAndIsDeletedFalse(List.of(brigadeId)))
                .thenReturn(List.of(brigade));

        EmployeeDto result = service.createEmployee(request);

        assertThat(result.id()).isEqualTo(employeeId);
        assertThat(result.departmentId()).isEqualTo(departmentId);
        assertThat(result.departmentName()).isEqualTo("Mechanical");
        assertThat(result.brigadeId()).isEqualTo(brigadeId);
        assertThat(result.brigadeName()).isEqualTo("Repair Brigade A");
        assertThat(result.specialisationId()).isEqualTo(specialisationId);

        verify(employeeRepository).existsByPersonnelNumberAndIsDeletedFalse("EMP-001");
        verify(employeeRepository).save(any(Employee.class));
    }

    @Test
    void updateEmployeeReturnsDepartmentNameAndBrigadeName() {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();
        UUID specialisationId = UUID.randomUUID();

        Employee existing = employee(employeeId, UUID.randomUUID(), null);

        EmployeeRequest request = new EmployeeRequest(
                "EMP-001",
                "Ali",
                "Valiyev",
                "Akmalovich",
                "Senior Engineer",
                departmentId,
                brigadeId,
                null,
                specialisationId,
                LocalDate.of(2025, 1, 10),
                null,
                "A",
                "+998901112233",
                "ali@example.com",
                true
        );

        Department department = department(departmentId, "Mechanical");
        Brigade brigade = brigade(brigadeId, "Repair Brigade A");

        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId))
                .thenReturn(Optional.of(existing));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(List.of(departmentId)))
                .thenReturn(List.of(department));
        when(brigadeRepository.findAllByIdInAndIsDeletedFalse(List.of(brigadeId)))
                .thenReturn(List.of(brigade));

        EmployeeDto result = service.updateEmployee(employeeId, request);

        assertThat(result.id()).isEqualTo(employeeId);
        assertThat(result.position()).isEqualTo("Senior Engineer");
        assertThat(result.departmentId()).isEqualTo(departmentId);
        assertThat(result.departmentName()).isEqualTo("Mechanical");
        assertThat(result.brigadeId()).isEqualTo(brigadeId);
        assertThat(result.brigadeName()).isEqualTo("Repair Brigade A");
        assertThat(result.specialisationId()).isEqualTo(specialisationId);

        verify(employeeRepository).findByIdAndIsDeletedFalse(employeeId);
        verify(employeeRepository).save(any(Employee.class));
    }

    @Test
    void createEmployeeWithInvalidSpecialisationIdFails() {
        UUID specialisationId = UUID.randomUUID();
        EmployeeRequest request = new EmployeeRequest(
                "EMP-404",
                "Ali",
                "Valiyev",
                "Akmalovich",
                "Engineer",
                null,
                null,
                null,
                specialisationId,
                LocalDate.of(2025, 1, 10),
                null,
                "A",
                "+998901112233",
                "ali@example.com",
                true
        );

        when(employeeRepository.existsByPersonnelNumberAndIsDeletedFalse("EMP-404"))
                .thenReturn(false);
        when(employeeSpecialisationRepository.findByIdAndIsDeletedFalse(specialisationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createEmployee(request))
                .hasMessageContaining("Employee specialisation not found");
    }

    @Test
    void employeeSpecialisationCrudWorks() {
        UUID specialisationId = UUID.randomUUID();
        EmployeeSpecialisation existing = specialisation(specialisationId);
        EmployeeSpecialisation saved = specialisation(specialisationId);
        saved.setNameRu("Электрик");
        saved.setNameEn("Electrician");
        saved.setNameUz("Elektrik");
        EmployeeSpecialisation requestEntity = specialisation(UUID.randomUUID());

        when(employeeSpecialisationRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(existing));
        when(employeeSpecialisationRepository.findByIdAndIsDeletedFalse(specialisationId))
                .thenReturn(Optional.of(existing));
        when(employeeSpecialisationRepository.save(any(EmployeeSpecialisation.class)))
                .thenAnswer(invocation -> {
                    EmployeeSpecialisation entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(requestEntity.getId());
                    }
                    return entity;
                });

        assertThat(service.listEmployeeSpecialisations()).hasSize(1);
        assertThat(service.getEmployeeSpecialisation(specialisationId).nameEn()).isEqualTo("Mechanic");

        EmployeeSpecialisationDto created = service.createEmployeeSpecialisation(
                new EmployeeSpecialisationRequest("Электрик", "Electrician", "Elektrik", true)
        );
        assertThat(created.nameRu()).isEqualTo("Электрик");

        EmployeeSpecialisationDto updated = service.updateEmployeeSpecialisation(
                specialisationId,
                new EmployeeSpecialisationRequest(saved.getNameRu(), saved.getNameEn(), saved.getNameUz(), false)
        );
        assertThat(updated.nameEn()).isEqualTo("Electrician");
        assertThat(updated.active()).isFalse();

        service.deleteEmployeeSpecialisation(specialisationId);
        assertThat(existing.isDeleted()).isTrue();
    }

    private Employee employee(UUID employeeId, UUID departmentId, UUID brigadeId) {
        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setPersonnelNumber("EMP-001");
        employee.setFirstName("Ali");
        employee.setLastName("Valiyev");
        employee.setMiddleName("Akmalovich");
        employee.setPosition("Engineer");
        employee.setDepartmentId(departmentId);
        employee.setBrigadeId(brigadeId);
        employee.setHireDate(LocalDate.of(2025, 1, 10));
        employee.setGrade("A");
        employee.setPhone("+998901112233");
        employee.setEmail("ali@example.com");
        employee.setActive(true);
        employee.setDeleted(false);
        return employee;
    }

    private UploadedFile uploadedPictureFile(UUID fileId, String originalName) {
        UploadedFile file = new UploadedFile();
        file.setId(fileId);
        file.setOriginalName(originalName);
        file.setStoredName(fileId + ".png");
        file.setObjectName("employee-pictures/" + fileId + ".png");
        file.setContentType("image/png");
        file.setSize(123L);
        file.setExtension("png");
        file.setCategory(FileCategory.EMPLOYEE_PICTURE);
        file.setUploadedBy(UUID.randomUUID());
        file.setDeleted(false);
        return file;
    }

    private EmployeePicture employeePicture(UUID pictureId, Employee employee, UploadedFile file) {
        EmployeePicture picture = new EmployeePicture();
        picture.setId(pictureId);
        picture.setEmployee(employee);
        picture.setFile(file);
        picture.setPictureName("Portrait");
        picture.setPictureType("PROFILE");
        picture.setUploadedBy(UUID.randomUUID());
        picture.setUploadedAt(LocalDateTime.parse("2026-06-25T09:00:00"));
        picture.setDeleted(false);
        return picture;
    }

    private static EmployeeWorkRoleCodeProjection workRoleCode(UUID employeeId, String code) {
        return new EmployeeWorkRoleCodeProjection() {
            @Override
            public UUID getEmployeeId() {
                return employeeId;
            }

            @Override
            public String getCode() {
                return code;
            }
        };
    }

    private Department department(UUID departmentId, String name) {
        Department department = new Department();
        department.setId(departmentId);
        department.setCode("DEP-001");
        department.setName(name);
        department.setType(DepartmentType.ADMINISTRATION);
        department.setDeleted(false);
        return department;
    }

    private Brigade brigade(UUID brigadeId, String name) {
        Brigade brigade = new Brigade();
        brigade.setId(brigadeId);
        brigade.setCode("BR-001");
        brigade.setName(name);
        brigade.setActive(true);
        brigade.setDeleted(false);
        return brigade;
    }

    private EmployeeStatsProjection statsProjection(
            Long total,
            Long active,
            Long terminated,
            Long withoutEmail
    ) {
        return new EmployeeStatsProjection() {
            @Override
            public Long getTotal() {
                return total;
            }

            @Override
            public Long getActive() {
                return active;
            }

            @Override
            public Long getTerminated() {
                return terminated;
            }

            @Override
            public Long getWithoutEmail() {
                return withoutEmail;
            }
        };
    }

    private EmployeeSpecialisation specialisation(UUID id) {
        EmployeeSpecialisation specialisation = new EmployeeSpecialisation();
        specialisation.setId(id);
        specialisation.setNameRu("Механик");
        specialisation.setNameEn("Mechanic");
        specialisation.setNameUz("Mexanik");
        specialisation.setActive(true);
        specialisation.setDeleted(false);
        return specialisation;
    }
}
