package com.toir.security;

import com.toir.controller.PprPlanController;
import com.toir.dto.pprplanning.PprPlanDto;
import com.toir.entity.PprPlan;
import com.toir.enums.PlanStatus;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.service.ApprovalService;
import com.toir.service.PprGeneratorService;
import com.toir.service.PprPlanService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PprPlanController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        PprPlanEndpointSecurityTest.SecurityBeans.class
})
class PprPlanEndpointSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    PprPlanService pprPlanService;

    @MockBean
    PprGeneratorService pprGeneratorService;

    @MockBean
    PprPlanRepository pprPlanRepository;

    @MockBean
    PprTaskRepository pprTaskRepository;

    @MockBean
    ScopeAccessService scopeAccessService;

    @MockBean
    ApprovalService approvalService;

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
    void unauthenticatedCannotAccessPprPlanDetail() throws Exception {
        mockMvc.perform(get("/api/v1/ppr-plans/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "PPR_PLAN_READ")
    void pprPlanReadAuthorityCanReadDetail() throws Exception {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(pprPlanRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(planEntity(id, departmentId)));
        when(pprPlanService.findById(id)).thenReturn(planDto(id));

        mockMvc.perform(get("/api/v1/ppr-plans/{id}", id))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "read")
    void legacyReadAuthorityCannotMutate() throws Exception {
        mockMvc.perform(post("/api/v1/ppr-plans")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "June plan",
                                  "departmentId": "%s",
                                  "createdById": "%s",
                                  "fromDate": "2026-06-01",
                                  "toDate": "2026-06-30",
                                  "notes": "Planned maintenance"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadAndMutate() throws Exception {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(pprPlanRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(planEntity(id, departmentId)));
        when(pprPlanService.findById(id)).thenReturn(planDto(id));
        when(pprPlanService.create(any())).thenReturn(planDto(UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/ppr-plans/{id}", id))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ppr-plans")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "June plan",
                                  "departmentId": "%s",
                                  "createdById": "%s",
                                  "fromDate": "2026-06-01",
                                  "toDate": "2026-06-30",
                                  "notes": "Planned maintenance"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "*")
    void wildcardAuthorityCanReadAndMutate() throws Exception {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(pprPlanRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(planEntity(id, departmentId)));
        when(pprPlanService.findById(id)).thenReturn(planDto(id));
        when(pprPlanService.create(any())).thenReturn(planDto(UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/ppr-plans/{id}", id))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ppr-plans")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "July plan",
                                  "departmentId": "%s",
                                  "createdById": "%s",
                                  "fromDate": "2026-07-01",
                                  "toDate": "2026-07-31",
                                  "notes": "Generated from regulations"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated());
    }

    private PprPlanDto planDto(UUID id) {
        return new PprPlanDto(
                id,
                "PPR-2026-0001",
                "May plan",
                PlanStatus.DRAFT,
                UUID.randomUUID(),
                "Mechanical",
                UUID.randomUUID(),
                null,
                null,
                List.of(),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );
    }

    private PprPlan planEntity(UUID id, UUID departmentId) {
        PprPlan plan = new PprPlan();
        plan.setId(id);
        plan.setCode("PPR-2026-0001");
        plan.setName("May plan");
        plan.setStartDate(LocalDate.of(2026, 5, 1));
        plan.setEndDate(LocalDate.of(2026, 5, 31));
        plan.setStatus(PlanStatus.DRAFT);
        plan.setDepartmentId(departmentId);
        plan.setCreatedById(UUID.randomUUID());
        return plan;
    }
}
