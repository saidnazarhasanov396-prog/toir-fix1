package com.toir.security;

import com.toir.controller.WorkOrderMaterialReadinessController;
import com.toir.dto.workorder.WorkOrderMaterialReadinessDto;
import com.toir.dto.workorder.WorkOrderMaterialReadinessRowDto;
import com.toir.enums.MaterialReadinessStatus;
import com.toir.exception.RestException;
import com.toir.service.WorkOrderMaterialReadinessService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WorkOrderMaterialReadinessController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacWorkOrderMaterialReadinessSecurityTest.SecurityBeans.class
})
class RbacWorkOrderMaterialReadinessSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    WorkOrderMaterialReadinessService readinessService;

    @TestConfiguration
    static class SecurityBeans {
        @Bean
        CorsProperties corsProperties() {
            CorsProperties props = new CorsProperties();
            props.setAllowedOriginPatterns(List.of("http://localhost:3000"));
            return props;
        }
    }

    @Test
    void unauthenticatedCannotReadMaterialReadiness() throws Exception {
        mockMvc.perform(get("/api/v1/work-orders/{id}/material-readiness", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadMaterialReadiness() throws Exception {
        mockMvc.perform(get("/api/v1/work-orders/{id}/material-readiness", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WORK_ORDER_READ)
    void workOrderReadCanReadMaterialReadinessJsonShape() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        when(readinessService.getReadiness(workOrderId)).thenReturn(new WorkOrderMaterialReadinessDto(
                workOrderId,
                equipmentId,
                MaterialReadinessStatus.PARTIAL,
                true,
                Instant.parse("2026-07-06T10:00:00Z"),
                List.of(new WorkOrderMaterialReadinessRowDto(
                        requirementId,
                        sparePartId,
                        "Bearing",
                        "pcs",
                        java.math.BigDecimal.valueOf(5),
                        java.math.BigDecimal.ONE,
                        java.math.BigDecimal.valueOf(2),
                        java.math.BigDecimal.ONE,
                        java.math.BigDecimal.valueOf(3),
                        MaterialReadinessStatus.PARTIAL,
                        true,
                        null,
                        null,
                        null,
                        "Use matched kit"
                ))
        ));

        mockMvc.perform(get("/api/v1/work-orders/{id}/material-readiness", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workOrderId").value(workOrderId.toString()))
                .andExpect(jsonPath("$.equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.overallStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.blocking").value(true))
                .andExpect(jsonPath("$.checkedAt").value("2026-07-06T10:00:00Z"))
                .andExpect(jsonPath("$.rows[0].requirementId").value(requirementId.toString()))
                .andExpect(jsonPath("$.rows[0].sparePartId").value(sparePartId.toString()))
                .andExpect(jsonPath("$.rows[0].sparePartName").value("Bearing"))
                .andExpect(jsonPath("$.rows[0].unit").value("pcs"))
                .andExpect(jsonPath("$.rows[0].requiredQty").value(5))
                .andExpect(jsonPath("$.rows[0].reservedQty").value(1))
                .andExpect(jsonPath("$.rows[0].issuedQty").value(2))
                .andExpect(jsonPath("$.rows[0].returnedQty").value(1))
                .andExpect(jsonPath("$.rows[0].shortageQty").value(3))
                .andExpect(jsonPath("$.rows[0].readinessStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.rows[0].blocking").value(true))
                .andExpect(jsonPath("$.rows[0].expectedDate").doesNotExist())
                .andExpect(jsonPath("$.rows[0].sourceSystem").doesNotExist())
                .andExpect(jsonPath("$.rows[0].lastSyncedAt").doesNotExist())
                .andExpect(jsonPath("$.rows[0].notes").value("Use matched kit"));
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadMaterialReadiness() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(readinessService.getReadiness(workOrderId)).thenReturn(emptyReadiness(workOrderId));

        mockMvc.perform(get("/api/v1/work-orders/{id}/material-readiness", workOrderId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadMaterialReadiness() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(readinessService.getReadiness(workOrderId)).thenReturn(emptyReadiness(workOrderId));

        mockMvc.perform(get("/api/v1/work-orders/{id}/material-readiness", workOrderId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WORK_ORDER_READ)
    void missingWorkOrderReturnsNotFound() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(readinessService.getReadiness(workOrderId))
                .thenThrow(RestException.notFound("Work order not found: " + workOrderId));

        mockMvc.perform(get("/api/v1/work-orders/{id}/material-readiness", workOrderId))
                .andExpect(status().isNotFound());
    }

    private WorkOrderMaterialReadinessDto emptyReadiness(UUID workOrderId) {
        return new WorkOrderMaterialReadinessDto(
                workOrderId,
                null,
                MaterialReadinessStatus.NOT_REQUIRED,
                false,
                Instant.parse("2026-07-06T10:00:00Z"),
                List.of()
        );
    }
}
