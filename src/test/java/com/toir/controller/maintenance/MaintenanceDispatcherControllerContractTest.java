package com.toir.controller.maintenance;

import com.toir.dto.maintenanceworkspace.MaintenanceDispatcherQueues;
import com.toir.dto.maintenanceworkspace.MaintenanceDispatcherSummary;
import com.toir.dto.maintenanceworkspace.MaintenanceWorkspaceFilter;
import com.toir.service.maintenanceworkspace.MaintenanceDispatcherService;
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
class MaintenanceDispatcherControllerContractTest {

    @Mock
    MaintenanceDispatcherService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MaintenanceDispatcherController(service)).build();
    }

    @Test
    void queuesBindsWorkspaceFiltersAndReturnsFilteredSummary() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Instant dueFrom = Instant.parse("2026-07-01T00:00:00Z");
        Instant dueTo = Instant.parse("2026-07-31T23:59:59Z");
        when(service.queues(any(MaintenanceWorkspaceFilter.class))).thenReturn(
                new MaintenanceDispatcherQueues(
                        new MaintenanceDispatcherSummary(0, 0, 0, 0, 0, 0, 0, 0),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );

        mockMvc.perform(get("/api/v1/maintenance/dispatcher/queues")
                        .param("search", "pump")
                        .param("departmentId", departmentId.toString())
                        .param("equipmentId", equipmentId.toString())
                        .param("priority", "HIGH")
                        .param("status", "OPEN")
                        .param("objectType", "WORK_ORDER")
                        .param("dueFrom", dueFrom.toString())
                        .param("dueTo", dueTo.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(0));

        ArgumentCaptor<MaintenanceWorkspaceFilter> captor =
                ArgumentCaptor.forClass(MaintenanceWorkspaceFilter.class);
        verify(service).queues(captor.capture());
        MaintenanceWorkspaceFilter filter = captor.getValue();
        assertThat(filter.search()).isEqualTo("pump");
        assertThat(filter.departmentId()).isEqualTo(departmentId);
        assertThat(filter.equipmentId()).isEqualTo(equipmentId);
        assertThat(filter.priority()).isEqualTo("HIGH");
        assertThat(filter.status()).isEqualTo("OPEN");
        assertThat(filter.objectType()).isEqualTo("WORK_ORDER");
        assertThat(filter.dueFrom()).isEqualTo(dueFrom);
        assertThat(filter.dueTo()).isEqualTo(dueTo);
    }

    @Test
    void summaryUsesSameWorkspaceFiltersAsQueues() throws Exception {
        UUID departmentId = UUID.randomUUID();
        when(service.summary(any(MaintenanceWorkspaceFilter.class))).thenReturn(
                new MaintenanceDispatcherSummary(1, 1, 0, 0, 0, 0, 0, 0)
        );

        mockMvc.perform(get("/api/v1/maintenance/dispatcher/summary")
                        .param("departmentId", departmentId.toString())
                        .param("objectType", "REPAIR_REQUEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        ArgumentCaptor<MaintenanceWorkspaceFilter> captor =
                ArgumentCaptor.forClass(MaintenanceWorkspaceFilter.class);
        verify(service).summary(captor.capture());
        assertThat(captor.getValue().departmentId()).isEqualTo(departmentId);
        assertThat(captor.getValue().objectType()).isEqualTo("REPAIR_REQUEST");
    }
}
