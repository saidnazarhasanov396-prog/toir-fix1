package com.toir.security;

import com.toir.controller.users.HrController;
import com.toir.dto.hr.EmployeeDto;
import com.toir.dto.hr.EmployeePictureDto;
import com.toir.dto.hr.EmployeeRequest;
import com.toir.dto.hr.EmployeeStatsResponse;
import com.toir.dto.hr.TimesheetEntryDto;
import com.toir.dto.hr.TimesheetEntryRequest;
import com.toir.enums.TimesheetStatus;
import com.toir.service.users.EmployeePictureService;
import com.toir.service.users.HrService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HrController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacHrSecurityTest.SecurityBeans.class
})
class RbacHrSecurityTest {

    private static final String TIMESHEET_READ = "TIMESHEET_READ";
    private static final String TIMESHEET_CREATE = "TIMESHEET_CREATE";
    private static final String TIMESHEET_UPDATE = "TIMESHEET_UPDATE";
    private static final String TIMESHEET_APPROVE = "TIMESHEET_APPROVE";
    private static final String TIMESHEET_DELETE = "TIMESHEET_DELETE";

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    HrService hrService;

    @MockBean
    EmployeePictureService employeePictureService;

    @MockBean
    SecurityScope securityScope;

    @TestConfiguration
    static class SecurityBeans {
        @Bean
        CorsProperties corsProperties() {
            CorsProperties properties = new CorsProperties();
            properties.setAllowedOriginPatterns(List.of("http://localhost:3000"));
            return properties;
        }
    }

    @Test
    void unauthenticatedCannotReadHr() throws Exception {
        mockMvc.perform(get("/api/v1/hr/employees?page=0&size=1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/hr/employees/stats"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/hr/timesheet")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-31"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadHr() throws Exception {
        mockMvc.perform(get("/api/v1/hr/employees?page=0&size=1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/hr/timesheet")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-31"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EMPLOYEE_READ)
    void employeeReadCanReadListStatsAndDetailWithDepartmentFilter() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(securityScope.enforceDepartmentScope(departmentId)).thenReturn(departmentId);
        when(hrService.listEmployees(eq(-1), eq(20), eq("Ali"), eq(true), eq(departmentId), isNull()))
                .thenReturn(new PageImpl<>(List.of(employeeDto(employeeId)), PageRequest.of(0, 20), 1));
        when(hrService.getEmployeeStats(eq(departmentId), isNull(), eq("Ali")))
                .thenReturn(new EmployeeStatsResponse(1, 1, 0, 0));
        when(hrService.getEmployee(employeeId)).thenReturn(employeeDto(employeeId));

        mockMvc.perform(get("/api/v1/hr/employees")
                        .param("page", "0")
                        .param("size", "20")
                        .param("search", "Ali")
                        .param("activeOnly", "true")
                        .param("departmentId", departmentId.toString()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/hr/employees/stats")
                        .param("search", "Ali")
                        .param("departmentId", departmentId.toString()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/hr/employees/{id}", employeeId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EMPLOYEE_READ)
    void employeeReadCanReadEmployeePictures() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID pictureId = UUID.randomUUID();
        when(employeePictureService.getPictures(eq(employeeId), any()))
                .thenReturn(List.of(employeePictureDto(employeeId, pictureId)));
        when(employeePictureService.getPicture(eq(pictureId), any()))
                .thenReturn(employeePictureDto(employeeId, pictureId));
        when(employeePictureService.downloadPicture(eq(pictureId), any()))
                .thenReturn(new ByteArrayResource("png".getBytes()));

        mockMvc.perform(get("/api/v1/hr/employees/{id}/pictures", employeeId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/hr/employee-pictures/{pictureId}/download", pictureId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EMPLOYEE_UPDATE)
    void employeeUpdateCanUploadAndDeleteEmployeePictures() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID pictureId = UUID.randomUUID();
        when(employeePictureService.uploadPictures(eq(employeeId), any(), eq(List.of("Portrait")), eq("PROFILE"), any()))
                .thenReturn(List.of(employeePictureDto(employeeId, pictureId)));

        mockMvc.perform(multipart("/api/v1/hr/employees/{id}/pictures", employeeId)
                        .file(new MockMultipartFile("files", "portrait.png", "image/png", "png".getBytes()))
                        .param("pictureNames", "Portrait")
                        .param("pictureType", "PROFILE"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/hr/employee-pictures/{pictureId}", pictureId))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EMPLOYEE_READ)
    void employeeReadCannotMutateEmployeePictures() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID pictureId = UUID.randomUUID();

        mockMvc.perform(multipart("/api/v1/hr/employees/{id}/pictures", employeeId)
                        .file(new MockMultipartFile("files", "portrait.png", "image/png", "png".getBytes())))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/hr/employee-pictures/{pictureId}", pictureId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadHr() throws Exception {
        when(securityScope.enforceDepartmentScope(isNull())).thenReturn(null);
        when(hrService.listEmployees(eq(-1), eq(20), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/hr/employees?page=0&size=20"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadHr() throws Exception {
        when(securityScope.enforceDepartmentScope(isNull())).thenReturn(null);
        when(hrService.listEmployees(eq(-1), eq(20), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/hr/employees?page=0&size=20"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EMPLOYEE_CREATE)
    void employeeCreateCanCreateEmployee() throws Exception {
        when(hrService.createEmployee(any(EmployeeRequest.class))).thenReturn(employeeDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/hr/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EMPLOYEE_UPDATE)
    void employeeUpdateCanUpdateEmployee() throws Exception {
        UUID id = UUID.randomUUID();
        when(hrService.updateEmployee(eq(id), any(EmployeeRequest.class))).thenReturn(employeeDto(id));

        mockMvc.perform(put("/api/v1/hr/employees/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EMPLOYEE_DELETE)
    void employeeDeleteCanDeleteEmployee() throws Exception {
        mockMvc.perform(delete("/api/v1/hr/employees/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EMPLOYEE_READ)
    void employeeReadCannotMutateEmployees() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/hr/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/hr/employees/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/hr/employees/{id}", id))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {PermissionConstants.USER_READ, PermissionConstants.READ_LEGACY})
    void unrelatedAndLegacyReadCannotMutateEmployees() throws Exception {
        mockMvc.perform(post("/api/v1/hr/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = TIMESHEET_READ)
    void timesheetReadCanReadTimesheet() throws Exception {
        UUID employeeId = UUID.randomUUID();
        when(hrService.timesheetFor(eq(employeeId), eq(LocalDate.of(2026, 5, 1)), eq(LocalDate.of(2026, 5, 31))))
                .thenReturn(List.of(timesheetDto(UUID.randomUUID(), employeeId)));

        mockMvc.perform(get("/api/v1/hr/timesheet")
                        .param("employeeId", employeeId.toString())
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-31"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = TIMESHEET_CREATE)
    void timesheetCreateCanCreateEntry() throws Exception {
        when(hrService.createTimesheet(any(TimesheetEntryRequest.class)))
                .thenReturn(timesheetDto(UUID.randomUUID(), UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/hr/timesheet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timesheetPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = TIMESHEET_UPDATE)
    void timesheetUpdateCanUpdateEntry() throws Exception {
        UUID id = UUID.randomUUID();
        when(hrService.updateTimesheet(eq(id), any(TimesheetEntryRequest.class)))
                .thenReturn(timesheetDto(id, UUID.randomUUID()));

        mockMvc.perform(put("/api/v1/hr/timesheet/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timesheetPayload()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = TIMESHEET_APPROVE)
    void timesheetApproveCanApproveEntry() throws Exception {
        UUID id = UUID.randomUUID();
        when(hrService.approveTimesheet(id)).thenReturn(timesheetDto(id, UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/hr/timesheet/{id}/approve", id))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = TIMESHEET_DELETE)
    void timesheetDeleteCanDeleteEntry() throws Exception {
        mockMvc.perform(delete("/api/v1/hr/timesheet/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = TIMESHEET_READ)
    void timesheetReadCannotMutateTimesheet() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/hr/timesheet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timesheetPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/hr/timesheet/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timesheetPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/hr/timesheet/{id}/approve", id))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/hr/timesheet/{id}", id))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {PermissionConstants.USER_READ, PermissionConstants.READ_LEGACY})
    void unrelatedAndLegacyReadCannotMutateTimesheet() throws Exception {
        mockMvc.perform(post("/api/v1/hr/timesheet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timesheetPayload()))
                .andExpect(status().isForbidden());
    }

    private static EmployeeDto employeeDto(UUID id) {
        return new EmployeeDto(
                id,
                "EMP-001",
                "Ali",
                "Valiyev",
                "Akmalovich",
                "Engineer",
                UUID.randomUUID(),
                "Mechanical",
                UUID.randomUUID(),
                "Repair Brigade A",
                null,
                LocalDate.of(2025, 1, 10),
                null,
                "A",
                "+998901112233",
                "ali@example.com",
                true
        );
    }

    private static TimesheetEntryDto timesheetDto(UUID id, UUID employeeId) {
        return new TimesheetEntryDto(
                id,
                employeeId,
                LocalDate.of(2026, 5, 1),
                8,
                0,
                0,
                0,
                null,
                null,
                TimesheetStatus.APPROVED,
                null
        );
    }

    private static EmployeePictureDto employeePictureDto(UUID employeeId, UUID pictureId) {
        return new EmployeePictureDto(
                pictureId,
                employeeId,
                "Portrait",
                "PROFILE",
                "portrait.png",
                "image/png",
                123L,
                LocalDateTime.parse("2026-06-25T06:00:00"),
                UUID.randomUUID(),
                "/api/v1/hr/employee-pictures/" + pictureId + "/download"
        );
    }

    private static String employeePayload() {
        return """
                {
                  "personnelNumber": "EMP-001",
                  "firstName": "Ali",
                  "lastName": "Valiyev",
                  "position": "Engineer",
                  "specialisationId": "%s",
                  "hireDate": "2025-01-10",
                  "active": true
                }
                """.formatted(UUID.randomUUID());
    }

    private static String timesheetPayload() {
        return """
                {
                  "employeeId": "%s",
                  "workDate": "2026-05-01",
                  "hoursRegular": 8,
                  "hoursOvertime": 0,
                  "hoursNight": 0,
                  "hoursHoliday": 0,
                  "status": "DRAFT"
                }
                """.formatted(UUID.randomUUID());
    }
}
