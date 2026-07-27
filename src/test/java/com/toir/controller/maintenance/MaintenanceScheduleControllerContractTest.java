package com.toir.controller.maintenance;

import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewRequest;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewResponse;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewSummary;
import com.toir.enums.MaintenanceScheduleScopeType;
import com.toir.security.ScopeAccessService;
import com.toir.service.maintanance.MaintenanceScheduleService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
