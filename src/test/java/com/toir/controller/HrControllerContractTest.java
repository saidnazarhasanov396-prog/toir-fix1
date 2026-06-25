package com.toir.controller;

import com.toir.controller.users.HrController;
import com.toir.dto.hr.EmployeeDto;
import com.toir.dto.hr.EmployeeFilterRequest;
import com.toir.dto.hr.EmployeePictureDto;
import com.toir.dto.hr.EmployeeStatsResponse;
import com.toir.dto.hr.EmployeeSpecialisationDto;
import com.toir.dto.hr.EmployeeSpecialisationRequest;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.security.SecurityScope;
import com.toir.service.users.EmployeePictureService;
import com.toir.service.users.HrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HrControllerContractTest {

    @Mock
    HrService service;

    @Mock
    SecurityScope securityScope;

    @Mock
    EmployeePictureService pictureService;

    private MockMvc mockMvc;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new HrController(service, securityScope, pictureService))
                .setCustomArgumentResolvers(new TestCurrentUserResolver(currentUserId))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listEmployeesReturnsDepartmentNameAndBrigadeName() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();

        Page<EmployeeDto> page = getEmployeeDtos(employeeId, departmentId, brigadeId);

        when(securityScope.enforceDepartmentScope(null)).thenReturn(null);
        when(service.listEmployees(eq(-1), eq(20), any(EmployeeFilterRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/hr/employees")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(employeeId.toString()))
                .andExpect(jsonPath("$.content[0].departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.content[0].departmentName").value("Mechanical"))
                .andExpect(jsonPath("$.content[0].brigadeId").value(brigadeId.toString()))
                .andExpect(jsonPath("$.content[0].brigadeName").value("Repair Brigade A"));

        ArgumentCaptor<EmployeeFilterRequest> filterCaptor = ArgumentCaptor.forClass(EmployeeFilterRequest.class);
        verify(service).listEmployees(eq(-1), eq(20), filterCaptor.capture());
        assertThat(filterCaptor.getValue().departmentId()).isNull();
    }
    @Test
    void getEmployeeReturnsDepartmentNameAndBrigadeName() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();

        EmployeeDto employee = new EmployeeDto(
                employeeId,
                "EMP-001",
                "Ali",
                "Valiyev",
                "Akmalovich",
                "Engineer",
                departmentId,
                "Mechanical",
                brigadeId,
                "Repair Brigade A",
                null,
                UUID.fromString("20000000-0000-0000-0000-000000050001"),
                "Механик",
                "Mechanic",
                "Mexanik",
                LocalDate.of(2025, 1, 10),
                null,
                "A",
                "+998901112233",
                "ali@example.com",
                true
        );

        when(service.getEmployee(employeeId)).thenReturn(employee);

        mockMvc.perform(get("/api/v1/hr/employees/{id}", employeeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(employeeId.toString()))
                .andExpect(jsonPath("$.departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.departmentName").value("Mechanical"))
                .andExpect(jsonPath("$.brigadeId").value(brigadeId.toString()))
                .andExpect(jsonPath("$.brigadeName").value("Repair Brigade A"))
                .andExpect(jsonPath("$.specialisationId").value("20000000-0000-0000-0000-000000050001"))
                .andExpect(jsonPath("$.specialisationNameRu").value("Механик"))
                .andExpect(jsonPath("$.specialisationNameEn").value("Mechanic"))
                .andExpect(jsonPath("$.specialisationNameUz").value("Mexanik"));

        verify(service).getEmployee(employeeId);
    }

    @Test
    void attachEmployeePicturesUploadsMultipleImages() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID firstPictureId = UUID.randomUUID();
        UUID secondPictureId = UUID.randomUUID();
        when(pictureService.uploadPictures(eq(employeeId), any(), eq(List.of("Portrait", "Badge")), eq("PROFILE"), any()))
                .thenReturn(List.of(
                        employeePicture(employeeId, firstPictureId, "Portrait", "portrait.png"),
                        employeePicture(employeeId, secondPictureId, "Badge", "badge.webp")
                ));

        mockMvc.perform(multipart("/api/v1/hr/employees/{employeeId}/pictures", employeeId)
                        .file(new MockMultipartFile("files", "portrait.png", "image/png", "png".getBytes()))
                        .file(new MockMultipartFile("files", "badge.webp", "image/webp", "webp".getBytes()))
                        .param("pictureNames", "Portrait", "Badge")
                        .param("pictureType", "PROFILE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(firstPictureId.toString()))
                .andExpect(jsonPath("$[0].employeeId").value(employeeId.toString()))
                .andExpect(jsonPath("$[0].pictureName").value("Portrait"))
                .andExpect(jsonPath("$[0].downloadUrl").value("/api/v1/hr/employee-pictures/" + firstPictureId + "/download"))
                .andExpect(jsonPath("$[1].pictureName").value("Badge"));

        verify(pictureService).uploadPictures(eq(employeeId), any(), eq(List.of("Portrait", "Badge")), eq("PROFILE"), any());
    }

    @Test
    void listEmployeePicturesReturnsPaginatedContent() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID pictureId = UUID.randomUUID();
        when(pictureService.getPictures(eq(employeeId), any()))
                .thenReturn(List.of(employeePicture(employeeId, pictureId, "Portrait", "portrait.png")));

        mockMvc.perform(get("/api/v1/hr/employees/{employeeId}/pictures", employeeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(pictureId.toString()))
                .andExpect(jsonPath("$.content[0].downloadUrl").value("/api/v1/hr/employee-pictures/" + pictureId + "/download"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void downloadEmployeePictureReturnsInlineImage() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID pictureId = UUID.randomUUID();
        when(pictureService.getPicture(eq(pictureId), any()))
                .thenReturn(employeePicture(employeeId, pictureId, "Portrait", "portrait.png"));
        when(pictureService.downloadPicture(eq(pictureId), any()))
                .thenReturn(new ByteArrayResource("png".getBytes()));

        mockMvc.perform(get("/api/v1/hr/employee-pictures/{pictureId}/download", pictureId))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).isEqualTo("image/png"));
    }

    @Test
    void deleteEmployeePictureReturnsNoContent() throws Exception {
        UUID pictureId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/hr/employee-pictures/{pictureId}", pictureId))
                .andExpect(status().isNoContent());

        verify(pictureService).deletePicture(eq(pictureId), any());
    }

    @Test
    void employeeSpecialisationCrudRoutesDelegateToService() throws Exception {
        UUID id = UUID.randomUUID();
        EmployeeSpecialisationDto dto = new EmployeeSpecialisationDto(
                id,
                "Механик",
                "Mechanic",
                "Mexanik",
                true
        );

        when(service.listEmployeeSpecialisations()).thenReturn(List.of(dto));
        when(service.getEmployeeSpecialisation(id)).thenReturn(dto);
        when(service.createEmployeeSpecialisation(any(EmployeeSpecialisationRequest.class))).thenReturn(dto);
        when(service.updateEmployeeSpecialisation(eq(id), any(EmployeeSpecialisationRequest.class))).thenReturn(dto);

        String payload = """
                {
                  "nameRu": "Механик",
                  "nameEn": "Mechanic",
                  "nameUz": "Mexanik",
                  "active": true
                }
                """;

        mockMvc.perform(get("/api/v1/hr/employee-specialisations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].nameRu").value("Механик"))
                .andExpect(jsonPath("$[0].nameEn").value("Mechanic"))
                .andExpect(jsonPath("$[0].nameUz").value("Mexanik"));

        mockMvc.perform(get("/api/v1/hr/employee-specialisations/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));

        mockMvc.perform(post("/api/v1/hr/employee-specialisations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));

        mockMvc.perform(put("/api/v1/hr/employee-specialisations/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));

        mockMvc.perform(patch("/api/v1/hr/employee-specialisations/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));

        mockMvc.perform(delete("/api/v1/hr/employee-specialisations/{id}", id))
                .andExpect(status().isNoContent());

        verify(service).deleteEmployeeSpecialisation(id);
    }

    @Test
    void listEmployeesPassesDepartmentAndBrigadeFiltersToService() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();

        Page<EmployeeDto> page = getEmployeeDtos(employeeId, departmentId, brigadeId);

        when(securityScope.enforceDepartmentScope(departmentId)).thenReturn(departmentId);
        when(service.listEmployees(eq(-1), eq(20), any(EmployeeFilterRequest.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/hr/employees")
                        .param("page", "0")
                        .param("size", "20")
                        .param("search", "Ali")
                        .param("activeOnly", "true")
                        .param("departmentId", departmentId.toString())
                        .param("brigadeId", brigadeId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.content[0].brigadeId").value(brigadeId.toString()));

        ArgumentCaptor<EmployeeFilterRequest> filterCaptor = ArgumentCaptor.forClass(EmployeeFilterRequest.class);
        verify(service).listEmployees(eq(-1), eq(20), filterCaptor.capture());
        EmployeeFilterRequest filter = filterCaptor.getValue();
        assertThat(filter.search()).isEqualTo("Ali");
        assertThat(filter.activeOnly()).isTrue();
        assertThat(filter.departmentId()).isEqualTo(departmentId);
        assertThat(filter.brigadeId()).isEqualTo(brigadeId);
    }

    @Test
    void listEmployeesPassesWorkRoleFilterToService() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        Page<EmployeeDto> page = getEmployeeDtos(employeeId, departmentId, null);

        when(securityScope.enforceDepartmentScope(departmentId)).thenReturn(departmentId);
        when(service.listEmployees(eq(-1), eq(20), any(EmployeeFilterRequest.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/hr/employees")
                        .param("page", "0")
                        .param("size", "20")
                        .param("activeOnly", "true")
                        .param("departmentId", departmentId.toString())
                        .param("workRoleCode", "DRIVER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].departmentId").value(departmentId.toString()));

        ArgumentCaptor<EmployeeFilterRequest> filterCaptor = ArgumentCaptor.forClass(EmployeeFilterRequest.class);
        verify(service).listEmployees(eq(-1), eq(20), filterCaptor.capture());
        assertThat(filterCaptor.getValue().workRoleCode()).isEqualTo("DRIVER");
    }

    @Test
    void listEmployeesUsesScopedDepartmentFilter() throws Exception {
        UUID requestedDepartmentId = UUID.randomUUID();
        UUID scopedDepartmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        Page<EmployeeDto> page = getEmployeeDtos(employeeId, scopedDepartmentId, brigadeId);

        when(securityScope.enforceDepartmentScope(requestedDepartmentId))
                .thenReturn(scopedDepartmentId);
        when(service.listEmployees(eq(-1), eq(20), any(EmployeeFilterRequest.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/hr/employees")
                        .param("page", "0")
                        .param("size", "20")
                        .param("departmentId", requestedDepartmentId.toString())
                        .param("brigadeId", brigadeId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].departmentId").value(scopedDepartmentId.toString()));

        verify(securityScope).enforceDepartmentScope(requestedDepartmentId);
        ArgumentCaptor<EmployeeFilterRequest> filterCaptor = ArgumentCaptor.forClass(EmployeeFilterRequest.class);
        verify(service).listEmployees(eq(-1), eq(20), filterCaptor.capture());
        assertThat(filterCaptor.getValue().departmentId()).isEqualTo(scopedDepartmentId);
        assertThat(filterCaptor.getValue().brigadeId()).isEqualTo(brigadeId);
    }

    @Test
    void listEmployeesBindsAdvancedEmployeeFilters() throws Exception {
        UUID requestedDepartmentId = UUID.randomUUID();
        UUID scopedDepartmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID specialisationId = UUID.randomUUID();
        Page<EmployeeDto> page = getEmployeeDtos(UUID.randomUUID(), scopedDepartmentId, brigadeId);

        when(securityScope.enforceDepartmentScope(requestedDepartmentId)).thenReturn(scopedDepartmentId);
        when(service.listEmployees(eq(1), eq(50), any(EmployeeFilterRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/hr/employees")
                        .param("page", "2")
                        .param("size", "50")
                        .param("search", "mechanic")
                        .param("activeOnly", "true")
                        .param("departmentId", requestedDepartmentId.toString())
                        .param("brigadeId", brigadeId.toString())
                        .param("workRoleCode", "MECHANIC")
                        .param("personnelNumber", "TAB-100")
                        .param("firstName", "Ali")
                        .param("lastName", "Karimov")
                        .param("middleName", "Valiyevich")
                        .param("position", "Mechanic")
                        .param("userId", userId.toString())
                        .param("specialisationId", specialisationId.toString())
                        .param("hireDateFrom", "2026-06-01")
                        .param("hireDateTo", "2026-06-19")
                        .param("terminatedDateFrom", "2026-06-10")
                        .param("terminatedDateTo", "2026-06-18")
                        .param("grade", "10")
                        .param("phone", "+998")
                        .param("email", "ali@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].departmentId").value(scopedDepartmentId.toString()));

        ArgumentCaptor<EmployeeFilterRequest> filterCaptor = ArgumentCaptor.forClass(EmployeeFilterRequest.class);
        verify(service).listEmployees(eq(1), eq(50), filterCaptor.capture());
        EmployeeFilterRequest filter = filterCaptor.getValue();
        assertThat(filter.search()).isEqualTo("mechanic");
        assertThat(filter.activeOnly()).isTrue();
        assertThat(filter.departmentId()).isEqualTo(scopedDepartmentId);
        assertThat(filter.brigadeId()).isEqualTo(brigadeId);
        assertThat(filter.workRoleCode()).isEqualTo("MECHANIC");
        assertThat(filter.personnelNumber()).isEqualTo("TAB-100");
        assertThat(filter.firstName()).isEqualTo("Ali");
        assertThat(filter.lastName()).isEqualTo("Karimov");
        assertThat(filter.middleName()).isEqualTo("Valiyevich");
        assertThat(filter.position()).isEqualTo("Mechanic");
        assertThat(filter.userId()).isEqualTo(userId);
        assertThat(filter.specialisationId()).isEqualTo(specialisationId);
        assertThat(filter.hireDateFrom()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(filter.hireDateTo()).isEqualTo(LocalDate.of(2026, 6, 19));
        assertThat(filter.terminatedDateFrom()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(filter.terminatedDateTo()).isEqualTo(LocalDate.of(2026, 6, 18));
        assertThat(filter.grade()).isEqualTo("10");
        assertThat(filter.phone()).isEqualTo("+998");
        assertThat(filter.email()).isEqualTo("ali@example.com");
    }


    @Test
    void employeeStatsWithoutFiltersReturnsStats() throws Exception {
        EmployeeStatsResponse response = new EmployeeStatsResponse(
                27,
                10,
                0,
                1
        );

        when(securityScope.enforceDepartmentScope(null)).thenReturn(null);
        when(service.getEmployeeStats(any(EmployeeFilterRequest.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/hr/employees/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(27))
                .andExpect(jsonPath("$.active").value(10))
                .andExpect(jsonPath("$.terminated").value(0))
                .andExpect(jsonPath("$.withoutEmail").value(1));

        ArgumentCaptor<EmployeeFilterRequest> filterCaptor = ArgumentCaptor.forClass(EmployeeFilterRequest.class);
        verify(service).getEmployeeStats(filterCaptor.capture());
        assertThat(filterCaptor.getValue().departmentId()).isNull();
    }

    @Test
    void employeeStatsWithFiltersPassesParamsToService() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();

        EmployeeStatsResponse response = new EmployeeStatsResponse(
                5,
                4,
                1,
                2
        );

        when(securityScope.enforceDepartmentScope(departmentId)).thenReturn(departmentId);
        when(service.getEmployeeStats(any(EmployeeFilterRequest.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/hr/employees/stats")
                        .param("departmentId", departmentId.toString())
                        .param("brigadeId", brigadeId.toString())
                        .param("search", "Ali"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.active").value(4))
                .andExpect(jsonPath("$.terminated").value(1))
                .andExpect(jsonPath("$.withoutEmail").value(2));

        ArgumentCaptor<EmployeeFilterRequest> filterCaptor = ArgumentCaptor.forClass(EmployeeFilterRequest.class);
        verify(service).getEmployeeStats(filterCaptor.capture());
        EmployeeFilterRequest filter = filterCaptor.getValue();
        assertThat(filter.departmentId()).isEqualTo(departmentId);
        assertThat(filter.brigadeId()).isEqualTo(brigadeId);
        assertThat(filter.search()).isEqualTo("Ali");
    }

    

    private static Page<EmployeeDto> getEmployeeDtos(UUID employeeId, UUID departmentId, UUID brigadeId) {
        EmployeeDto employee = new EmployeeDto(
                employeeId,
                "EMP-001",
                "Ali",
                "Valiyev",
                "Akmalovich",
                "Engineer",
                departmentId,
                "Mechanical",
                brigadeId,
                "Repair Brigade A",
                null,
                LocalDate.of(2025, 1, 10),
                null,
                "A",
                "+998901112233",
                "ali@example.com",
                true
        );

        return new PageImpl<>(
                List.of(employee),
                PageRequest.of(0, 20),
                1
        );


    }

    private static EmployeePictureDto employeePicture(UUID employeeId, UUID pictureId, String pictureName, String originalName) {
        return new EmployeePictureDto(
                pictureId,
                employeeId,
                pictureName,
                "PROFILE",
                originalName,
                "image/png",
                123L,
                LocalDateTime.parse("2026-06-25T06:00:00"),
                UUID.randomUUID(),
                "/api/v1/hr/employee-pictures/" + pictureId + "/download"
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
