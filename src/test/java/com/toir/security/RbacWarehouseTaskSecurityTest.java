package com.toir.security;

import com.toir.controller.WarehouseTaskController;
import com.toir.dto.warehouse.WarehouseTaskDto;
import com.toir.enums.WarehouseTaskPriority;
import com.toir.enums.WarehouseTaskSourceType;
import com.toir.enums.WarehouseTaskStatus;
import com.toir.enums.WarehouseTaskType;
import com.toir.service.warehouse.WarehouseTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WarehouseTaskController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacWarehouseTaskSecurityTest.SecurityBeans.class
})
class RbacWarehouseTaskSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    WarehouseTaskService service;

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
    void unauthenticatedCannotReadTasks() throws Exception {
        mockMvc.perform(get("/api/v1/warehouse/tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadTasks() throws Exception {
        mockMvc.perform(get("/api/v1/warehouse/tasks"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_TASK_READ)
    void warehouseTaskReadCanListTasks() throws Exception {
        when(service.findAll(null, null, null, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(taskDto())));

        mockMvc.perform(get("/api/v1/warehouse/tasks"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_TASK_ASSIGN)
    void warehouseTaskAssignCanCreateAssignAndCancel() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(service.create(any())).thenReturn(taskDto());
        when(service.assign(eq(taskId), any())).thenReturn(taskDto());
        when(service.cancel(eq(taskId), any())).thenReturn(taskDto());

        mockMvc.perform(post("/api/v1/warehouse/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskType": "PUTAWAY",
                                  "warehouseId": "%s",
                                  "sourceType": "MANUAL",
                                  "lines": [{"plannedQty": 1}]
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/warehouse/tasks/{id}/assign", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignedToId\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/warehouse/tasks/{id}/cancel", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"duplicate\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_TASK_EXECUTE)
    void warehouseTaskExecuteCanStartScanAndCompleteButCannotCreate() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        when(service.start(taskId)).thenReturn(taskDto());
        when(service.scanConfirm(eq(taskId), eq(lineId), any())).thenReturn(taskDto());
        when(service.complete(eq(taskId), any())).thenReturn(taskDto());

        mockMvc.perform(post("/api/v1/warehouse/tasks/{id}/start", taskId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/warehouse/tasks/{id}/lines/{lineId}/scan-confirm", taskId, lineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/warehouse/tasks/{id}/complete", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/warehouse/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskType": "PUTAWAY",
                                  "warehouseId": "%s",
                                  "sourceType": "MANUAL",
                                  "lines": [{"plannedQty": 1}]
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    private WarehouseTaskDto taskDto() {
        return new WarehouseTaskDto(
                UUID.randomUUID(),
                "WT-2026-00005",
                WarehouseTaskType.PUTAWAY,
                WarehouseTaskStatus.OPEN,
                WarehouseTaskPriority.NORMAL,
                UUID.randomUUID(),
                WarehouseTaskSourceType.MANUAL,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                null,
                null,
                null
        );
    }
}
