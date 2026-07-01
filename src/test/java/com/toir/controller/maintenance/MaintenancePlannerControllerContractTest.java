package com.toir.controller.maintenance;

import com.toir.dto.maintenanceworkspace.MaintenancePlannerCapacityResponse;
import com.toir.dto.maintenanceworkspace.MaintenanceWorkspaceFilter;
import com.toir.service.maintenanceworkspace.MaintenancePlannerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MaintenancePlannerControllerContractTest {

    @Mock
    MaintenancePlannerService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MaintenancePlannerController(service)).build();
    }

    @Test
    void backlogBindsWorkspaceFilters() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Instant scheduledFrom = Instant.parse("2026-07-01T00:00:00Z");
        Instant scheduledTo = Instant.parse("2026-07-31T23:59:59Z");
        when(service.backlog(any(MaintenanceWorkspaceFilter.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/maintenance/planner/backlog")
                        .param("search", "pump")
                        .param("departmentId", departmentId.toString())
                        .param("equipmentId", equipmentId.toString())
                        .param("priority", "HIGH")
                        .param("status", "PLANNED")
                        .param("readinessStatus", "READY")
                        .param("scheduledFrom", scheduledFrom.toString())
                        .param("scheduledTo", scheduledTo.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        ArgumentCaptor<MaintenanceWorkspaceFilter> captor =
                ArgumentCaptor.forClass(MaintenanceWorkspaceFilter.class);
        verify(service).backlog(captor.capture());
        MaintenanceWorkspaceFilter filter = captor.getValue();
        assertThat(filter.search()).isEqualTo("pump");
        assertThat(filter.departmentId()).isEqualTo(departmentId);
        assertThat(filter.equipmentId()).isEqualTo(equipmentId);
        assertThat(filter.priority()).isEqualTo("HIGH");
        assertThat(filter.status()).isEqualTo("PLANNED");
        assertThat(filter.readinessStatus()).isEqualTo("READY");
        assertThat(filter.scheduledFrom()).isEqualTo(scheduledFrom);
        assertThat(filter.scheduledTo()).isEqualTo(scheduledTo);
    }

    @Test
    void capacityBindsWorkspaceFilters() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(service.capacity(any(MaintenanceWorkspaceFilter.class))).thenReturn(
                new MaintenancePlannerCapacityResponse(1, 1, 0, 0, "filtered")
        );

        mockMvc.perform(get("/api/v1/maintenance/planner/capacity")
                        .param("equipmentId", equipmentId.toString())
                        .param("readinessStatus", "READY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openWorkOrders").value(1));

        ArgumentCaptor<MaintenanceWorkspaceFilter> captor =
                ArgumentCaptor.forClass(MaintenanceWorkspaceFilter.class);
        verify(service).capacity(captor.capture());
        assertThat(captor.getValue().equipmentId()).isEqualTo(equipmentId);
        assertThat(captor.getValue().readinessStatus()).isEqualTo("READY");
    }
}
