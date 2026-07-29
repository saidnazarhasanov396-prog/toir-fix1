package com.toir.controller.maintenance;

import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewRequest;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewResponse;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewSummary;
import com.toir.dto.maintenanceschedule.MaintenanceScheduleOption;
import com.toir.enums.MaintenanceScheduleRecurrenceAnchor;
import com.toir.enums.MaintenanceScheduleScopeType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.security.ScopeAccessService;
import com.toir.service.maintanance.MaintenanceScheduleService;
import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MaintenanceScheduleControllerContractTest {

    @Mock
    MaintenanceScheduleService service;

    @Mock
    ScopeAccessService scopeAccessService;

    @Test
    void previewUsesServerEnforcedDepartmentScopeAndReturnsStatelessResult() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID requestedDepartmentId = UUID.randomUUID();
        UUID scopedDepartmentId = UUID.randomUUID();
        when(scopeAccessService.enforceDepartmentScope(requestedDepartmentId))
                .thenReturn(scopedDepartmentId);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(scopedDepartmentId);
        when(service.preview(any())).thenReturn(new MaintenanceSchedulePreviewResponse(
                List.of(),
                new MaintenanceSchedulePreviewSummary(0, 0, 0, 1)
        ));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new MaintenanceScheduleController(service, scopeAccessService)
        ).build();

        mvc.perform(post("/api/v1/maintenance-schedule/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fromDate": "2026-01-01",
                                  "toDate": "2026-12-31",
                                  "scopeType": "EQUIPMENT",
                                  "equipmentIds": ["%s"],
                                  "departmentId": "%s",
                                  "anchorMode": "CURRENT"
                                }
                                """.formatted(equipmentId, requestedDepartmentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.summary.unmatchedCount").value(1));

        ArgumentCaptor<MaintenanceSchedulePreviewRequest> captor =
                ArgumentCaptor.forClass(MaintenanceSchedulePreviewRequest.class);
        org.mockito.Mockito.verify(service).preview(captor.capture());
        assertThat(captor.getValue().departmentId()).isEqualTo(scopedDepartmentId);
        assertThat(captor.getValue().scopeType()).isEqualTo(MaintenanceScheduleScopeType.EQUIPMENT);
    }

    @Test
    void previewPreservesWeekdayShiftPolicyWhenApplyingDepartmentScope() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID requestedDepartmentId = UUID.randomUUID();
        UUID scopedDepartmentId = UUID.randomUUID();
        when(scopeAccessService.enforceDepartmentScope(requestedDepartmentId))
                .thenReturn(scopedDepartmentId);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(scopedDepartmentId);
        when(service.preview(any())).thenReturn(new MaintenanceSchedulePreviewResponse(
                List.of(),
                new MaintenanceSchedulePreviewSummary(0, 0, 0, 1)
        ));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new MaintenanceScheduleController(service, scopeAccessService)
        ).build();

        mvc.perform(post("/api/v1/maintenance-schedule/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fromDate": "2026-07-28",
                                  "toDate": "2027-07-27",
                                  "scopeType": "EQUIPMENT",
                                  "equipmentIds": ["%s"],
                                  "departmentId": "%s",
                                  "anchorMode": "CURRENT",
                                  "shiftFromExcludedWeekdays": true,
                                  "excludedWeekdays": [
                                    "TUESDAY",
                                    "WEDNESDAY",
                                    "THURSDAY",
                                    "FRIDAY",
                                    "SATURDAY",
                                    "SUNDAY"
                                  ],
                                  "recurrenceAnchor": "REGULATION_DATE"
                                }
                                """.formatted(equipmentId, requestedDepartmentId)))
                .andExpect(status().isOk());

        ArgumentCaptor<MaintenanceSchedulePreviewRequest> captor =
                ArgumentCaptor.forClass(MaintenanceSchedulePreviewRequest.class);
        org.mockito.Mockito.verify(service).preview(captor.capture());
        MaintenanceSchedulePreviewRequest scopedRequest = captor.getValue();
        assertThat(scopedRequest.departmentId()).isEqualTo(scopedDepartmentId);
        assertThat(scopedRequest.shiftFromExcludedWeekdays()).isTrue();
        assertThat(scopedRequest.excludedWeekdays()).containsExactlyInAnyOrder(
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY
        );
        assertThat(scopedRequest.recurrenceAnchor())
                .isEqualTo(MaintenanceScheduleRecurrenceAnchor.REGULATION_DATE);
    }

    @Test
    void previewDeniesNonAdminWithoutDepartmentScope() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(scopeAccessService.enforceDepartmentScope(any())).thenReturn(UUID.randomUUID());
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(null);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new MaintenanceScheduleController(service, scopeAccessService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/api/v1/maintenance-schedule/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fromDate": "2026-01-01",
                                  "toDate": "2026-12-31",
                                  "scopeType": "EQUIPMENT",
                                  "equipmentIds": ["%s"],
                                  "departmentId": "%s",
                                  "anchorMode": "CURRENT"
                                }
                                """.formatted(equipmentId, UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void optionsUsesServerEnforcedScopeAndReturnsStandardPage() throws Exception {
        UUID requestedDepartmentId = UUID.randomUUID();
        UUID scopedDepartmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        when(scopeAccessService.enforceDepartmentScope(requestedDepartmentId))
                .thenReturn(scopedDepartmentId);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(scopedDepartmentId);
        when(service.options(
                MaintenanceScheduleScopeType.EQUIPMENT_TYPE,
                scopedDepartmentId,
                "насос",
                0,
                20
        )).thenReturn(new PageImpl<>(
                List.of(new MaintenanceScheduleOption(
                        typeId,
                        "TYPE-1",
                        "Насос",
                        MaintenanceScheduleScopeType.EQUIPMENT_TYPE,
                        4
                )),
                PageRequest.of(0, 20),
                1
        ));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new MaintenanceScheduleController(service, scopeAccessService)
        ).build();

        mvc.perform(get("/api/v1/maintenance-schedule/options")
                        .param("scopeType", "EQUIPMENT_TYPE")
                        .param("departmentId", requestedDepartmentId.toString())
                        .param("search", " насос ")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(typeId.toString()))
                .andExpect(jsonPath("$.content[0].scopeType").value("EQUIPMENT_TYPE"))
                .andExpect(jsonPath("$.content[0].eligibleEquipmentCount").value(4))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void optionsDeniesNonAdminWithoutDepartmentScope() throws Exception {
        when(scopeAccessService.enforceDepartmentScope(any())).thenReturn(UUID.randomUUID());
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(null);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new MaintenanceScheduleController(service, scopeAccessService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/api/v1/maintenance-schedule/options")
                        .param("scopeType", "EQUIPMENT")
                        .param("size", "20"))
                .andExpect(status().isForbidden());
    }
}
