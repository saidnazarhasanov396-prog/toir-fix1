package com.toir.security;

import com.toir.controller.InspectionController;
import com.toir.dto.inspection.InspectionDashboardSummaryDto;
import com.toir.dto.inspection.InspectionRoundDto;
import com.toir.dto.inspection.InspectionRoundResultDto;
import com.toir.dto.inspection.InspectionRouteDto;
import com.toir.enums.InspectionRoundStatus;
import com.toir.service.InspectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InspectionController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacInspectionSecurityTest.SecurityBeans.class
})
class RbacInspectionSecurityTest {

    private static final String INSPECTION_UPDATE = "INSPECTION_UPDATE";
    private static final String INSPECTION_DELETE = "INSPECTION_DELETE";

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    InspectionService inspectionService;

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
    void unauthenticatedCannotReadInspections() throws Exception {
        mockMvc.perform(get("/api/v1/inspection-routes"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/inspection-rounds?page=0&size=1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/inspection-dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadInspections() throws Exception {
        mockMvc.perform(get("/api/v1/inspection-routes"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/inspection-dashboard/summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.INSPECTION_READ)
    void inspectionReadCanReadRoutesAndRounds() throws Exception {
        UUID routeId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        when(inspectionService.findRoutes(null, null, null)).thenReturn(List.of(routeDto(routeId)));
        when(inspectionService.getRoute(routeId)).thenReturn(routeDto(routeId));
        when(inspectionService.listRounds(null, null, null)).thenReturn(List.of(roundDto(roundId, routeId)));
        when(inspectionService.getRound(roundId)).thenReturn(roundDto(roundId, routeId));
        when(inspectionService.getDashboardSummary(null)).thenReturn(summaryDto());

        mockMvc.perform(get("/api/v1/inspection-routes"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/inspection-routes/{id}", routeId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/inspection-rounds?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/inspection-rounds/{id}", roundId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/inspection-dashboard/summary"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadInspections() throws Exception {
        when(inspectionService.findRoutes(null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/inspection-routes"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadInspections() throws Exception {
        when(inspectionService.findRoutes(null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/inspection-routes"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.INSPECTION_CREATE)
    void inspectionCreateCanCreateRoute() throws Exception {
        when(inspectionService.createRoute(any())).thenReturn(routeDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/inspection-routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routePayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = INSPECTION_UPDATE)
    void inspectionUpdateCanUpdateRouteCheckpointAndResult() throws Exception {
        UUID routeId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        when(inspectionService.updateRoute(eq(routeId), any())).thenReturn(routeDto(routeId));
        when(inspectionService.addCheckpoint(eq(routeId), any())).thenReturn(routeDto(routeId));
        when(inspectionService.recordResult(eq(roundId), any()))
                .thenReturn(resultDto(roundId, checkpointId));

        mockMvc.perform(put("/api/v1/inspection-routes/{id}", routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routePayload()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/inspection-routes/{id}/checkpoints", routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkpointPayload()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/inspection-rounds/{id}/results", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resultPayload(checkpointId)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.INSPECTION_START)
    void inspectionStartCanStartRound() throws Exception {
        UUID routeId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        when(inspectionService.startRound(eq(routeId), any())).thenReturn(roundDto(roundId, routeId));

        mockMvc.perform(post("/api/v1/inspection-routes/{routeId}/start", routeId))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.INSPECTION_COMPLETE)
    void inspectionCompleteCanCompleteRound() throws Exception {
        UUID routeId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        when(inspectionService.completeRound(roundId, null)).thenReturn(roundDto(roundId, routeId));

        mockMvc.perform(post("/api/v1/inspection-rounds/{id}/complete", roundId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = INSPECTION_DELETE)
    void inspectionDeleteCanDeleteRouteAndCancelRound() throws Exception {
        UUID routeId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        when(inspectionService.cancelRound(roundId, "duplicate")).thenReturn(roundDto(roundId, routeId));

        mockMvc.perform(delete("/api/v1/inspection-routes/{id}", routeId))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/inspection-rounds/{id}/cancel?reason=duplicate", roundId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.INSPECTION_READ)
    void inspectionReadCannotMutateInspections() throws Exception {
        UUID routeId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/inspection-routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routePayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/inspection-routes/{id}", routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routePayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/inspection-routes/{id}", routeId))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/inspection-routes/{id}/checkpoints", routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkpointPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/inspection-routes/{routeId}/start", routeId))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/inspection-rounds/{id}/results", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resultPayload(checkpointId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/inspection-rounds/{id}/complete", roundId))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/inspection-rounds/{id}/cancel?reason=duplicate", roundId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotMutateInspections() throws Exception {
        mockMvc.perform(post("/api/v1/inspection-routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routePayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotMutateInspections() throws Exception {
        mockMvc.perform(post("/api/v1/inspection-routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routePayload()))
                .andExpect(status().isForbidden());
    }

    private InspectionRouteDto routeDto(UUID id) {
        return new InspectionRouteDto(
                id,
                "IR-001",
                "Pump inspection",
                UUID.randomUUID(),
                "DAILY",
                30,
                "Daily inspection",
                true,
                List.of()
        );
    }

    private InspectionRoundDto roundDto(UUID id, UUID routeId) {
        return new InspectionRoundDto(
                id,
                routeId,
                UUID.randomUUID(),
                Instant.parse("2026-05-01T08:00:00Z"),
                null,
                InspectionRoundStatus.IN_PROGRESS,
                0,
                0,
                null,
                List.of()
        );
    }

    private InspectionRoundResultDto resultDto(UUID roundId, UUID checkpointId) {
        return new InspectionRoundResultDto(
                UUID.randomUUID(),
                roundId,
                checkpointId,
                "OK",
                10.0,
                "bar",
                "ok",
                null,
                List.of()
        );
    }

    private InspectionDashboardSummaryDto summaryDto() {
        return new InspectionDashboardSummaryDto(
                Instant.parse("2026-06-22T06:00:00Z"),
                LocalDate.parse("2026-06-22"),
                1,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of()
        );
    }

    private String routePayload() {
        return """
                {
                  "code": "IR-001",
                  "name": "Pump inspection",
                  "frequency": "DAILY",
                  "targetDurationMin": 30,
                  "active": true,
                  "checkpoints": [
                    {
                      "orderIndex": 1,
                      "title": "Pump seal",
                      "checkType": "VISUAL",
                      "mandatory": true
                    }
                  ]
                }
                """;
    }

    private String checkpointPayload() {
        return """
                {
                  "orderIndex": 2,
                  "title": "Pressure",
                  "checkType": "MEASUREMENT",
                  "expectedUnit": "bar",
                  "mandatory": true
                }
                """;
    }

    private String resultPayload(UUID checkpointId) {
        return """
                {
                  "checkpointId": "%s",
                  "status": "OK",
                  "measuredValue": 10,
                  "measuredUnit": "bar",
                  "comment": "ok"
                }
                """.formatted(checkpointId);
    }
}
