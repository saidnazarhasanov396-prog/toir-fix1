package com.toir.controller;

import com.toir.controller.sparepartlifecycle.SparePartLifeRuleController;
import com.toir.dto.sparepartlifecycle.SparePartLifeLimitDto;
import com.toir.dto.sparepartlifecycle.SparePartLifeRuleDto;
import com.toir.dto.sparepartlifecycle.SparePartLifeRuleFilter;
import com.toir.dto.sparepartlifecycle.SparePartLifeRuleRequest;
import com.toir.enums.sparepartlifecycle.SparePartCalendarUnit;
import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartLifeCombinationMode;
import com.toir.enums.sparepartlifecycle.SparePartLifeLimitKind;
import com.toir.enums.sparepartlifecycle.SparePartLifeRuleScope;
import com.toir.security.CorsProperties;
import com.toir.security.JwtAuthenticationEntryPoint;
import com.toir.security.JwtAuthenticationFilter;
import com.toir.security.JwtService;
import com.toir.security.RestAccessDeniedHandler;
import com.toir.security.ScopeAccessService;
import com.toir.security.SecurityAccessService;
import com.toir.security.SecurityConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SparePartLifeRuleController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        SparePartLifeRuleControllerWebTest.SecurityBeans.class
})
class SparePartLifeRuleControllerWebTest {

    private static final String PROOF_LIMIT = "9999999999999.123456";
    private static final String PROOF_WARNING = "0.000001";

    @Autowired MockMvc mockMvc;
    @MockBean JwtService jwtService;
    @MockBean ScopeAccessService scopeAccessService;
    @MockBean com.toir.service.sparepartlifecycle.SparePartLifeRuleService service;

    @TestConfiguration
    static class SecurityBeans {
        @Bean
        CorsProperties corsProperties() {
            CorsProperties properties = new CorsProperties();
            properties.setAllowedOriginPatterns(List.of("http://localhost:3000"));
            return properties;
        }
    }

    // ---- B1: database-backed filtering, pagination, sort delegation ----

    @Test
    @WithMockUser(authorities = "SPARE_PART_LIFE_RULE_READ")
    void listDelegatesFiltersPageableAndSortToService() throws Exception {
        UUID partId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        when(service.list(any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/v1/spare-part-life-rules")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sparePartId", partId.toString())
                        .param("equipmentId", equipmentId.toString())
                        .param("scopeType", "EQUIPMENT")
                        .param("active", "true")
                        .param("effectiveAt", "2026-07-01T00:00:00Z")
                        .param("sort", "updatedAt,desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<SparePartLifeRuleFilter> filterCaptor = ArgumentCaptor.forClass(SparePartLifeRuleFilter.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).list(filterCaptor.capture(), pageableCaptor.capture());

        SparePartLifeRuleFilter filter = filterCaptor.getValue();
        assertThat(filter.sparePartId()).isEqualTo(partId);
        assertThat(filter.equipmentId()).isEqualTo(equipmentId);
        assertThat(filter.scopeType()).isEqualTo(SparePartLifeRuleScope.EQUIPMENT);
        assertThat(filter.active()).isTrue();
        assertThat(filter.effectiveAt()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        Sort.Order order = pageable.getSort().getOrderFor("updatedAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @WithMockUser(authorities = "SPARE_PART_LIFE_RULE_READ")
    void listAppliesDeterministicDefaultSortWhenNoneProvided() throws Exception {
        when(service.list(any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/v1/spare-part-life-rules")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).list(any(), pageableCaptor.capture());
        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("updatedAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @WithMockUser(authorities = "SPARE_PART_LIFE_RULE_READ")
    void listRejectsUnsupportedSortFieldWith400() throws Exception {
        mockMvc.perform(get("/api/v1/spare-part-life-rules").param("sort", "password,asc"))
                .andExpect(status().isBadRequest());
        verify(service, never()).list(any(), any());
    }

    @Test
    @WithMockUser(authorities = "SPARE_PART_LIFE_RULE_READ")
    void listSerializesDecimalLimitsAsStrings() throws Exception {
        UUID partId = UUID.randomUUID();
        when(service.list(any(), any())).thenReturn(new PageImpl<>(
                List.of(ruleDto(partId, new BigDecimal(PROOF_LIMIT), new BigDecimal(PROOF_WARNING))),
                PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/spare-part-life-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].limits[0].limitValue").value(PROOF_LIMIT))
                .andExpect(jsonPath("$.content[0].limits[0].warningBeforeValue").value(PROOF_WARNING));
    }

    // ---- B2: decimal-safe JSON contract ----

    @Test
    @WithMockUser(authorities = "SPARE_PART_LIFE_RULE_CREATE")
    void createRoundTripsDecimalStringsExactlyWithoutDoubleConversion() throws Exception {
        UUID partId = UUID.randomUUID();
        when(service.create(any())).thenReturn(
                ruleDto(partId, new BigDecimal(PROOF_LIMIT), new BigDecimal(PROOF_WARNING)));

        String body = """
                {"sparePartId":"%s","combinationMode":"ANY","dueAction":"WARNING_ONLY",
                 "limits":[{"limitKind":"CALENDAR","calendarUnit":"MONTH",
                            "limitValue":"%s","warningBeforeValue":"%s","sequence":0}]}
                """.formatted(partId, PROOF_LIMIT, PROOF_WARNING);

        mockMvc.perform(post("/api/v1/spare-part-life-rules")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.limits[0].limitValue").value(PROOF_LIMIT))
                .andExpect(jsonPath("$.limits[0].warningBeforeValue").value(PROOF_WARNING));

        ArgumentCaptor<SparePartLifeRuleRequest> captor = ArgumentCaptor.forClass(SparePartLifeRuleRequest.class);
        verify(service).create(captor.capture());
        BigDecimal receivedLimit = captor.getValue().limits().get(0).limitValue();
        BigDecimal receivedWarning = captor.getValue().limits().get(0).warningBeforeValue();
        assertThat(receivedLimit).isEqualByComparingTo(new BigDecimal(PROOF_LIMIT));
        assertThat(receivedLimit.toPlainString()).isEqualTo(PROOF_LIMIT);
        assertThat(receivedWarning.toPlainString()).isEqualTo(PROOF_WARNING);
    }

    @Test
    @WithMockUser(authorities = "SPARE_PART_LIFE_RULE_CREATE")
    void createRejectsDecimalSentAsJsonNumber() throws Exception {
        UUID partId = UUID.randomUUID();
        String body = """
                {"sparePartId":"%s","combinationMode":"ANY","dueAction":"WARNING_ONLY",
                 "limits":[{"limitKind":"CALENDAR","calendarUnit":"MONTH",
                            "limitValue":9999999999999.123456,"sequence":0}]}
                """.formatted(partId);

        mockMvc.perform(post("/api/v1/spare-part-life-rules")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(service, never()).create(any());
    }

    @Test
    @WithMockUser(authorities = "SPARE_PART_LIFE_RULE_CREATE")
    void createRejectsDecimalWithScaleAboveSix() throws Exception {
        UUID partId = UUID.randomUUID();
        String body = """
                {"sparePartId":"%s","combinationMode":"ANY","dueAction":"WARNING_ONLY",
                 "limits":[{"limitKind":"CALENDAR","calendarUnit":"MONTH",
                            "limitValue":"0.0000001","sequence":0}]}
                """.formatted(partId);

        mockMvc.perform(post("/api/v1/spare-part-life-rules")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(service, never()).create(any());
    }

    @Test
    @WithMockUser(authorities = "SPARE_PART_LIFE_RULE_CREATE")
    void createRejectsNonDecimalText() throws Exception {
        UUID partId = UUID.randomUUID();
        String body = """
                {"sparePartId":"%s","combinationMode":"ANY","dueAction":"WARNING_ONLY",
                 "limits":[{"limitKind":"CALENDAR","calendarUnit":"MONTH",
                            "limitValue":"abc","sequence":0}]}
                """.formatted(partId);

        mockMvc.perform(post("/api/v1/spare-part-life-rules")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(service, never()).create(any());
    }

    private static Page<SparePartLifeRuleDto> emptyPage() {
        return new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
    }

    private static SparePartLifeRuleDto ruleDto(UUID partId, BigDecimal limitValue, BigDecimal warningBeforeValue) {
        SparePartLifeLimitDto limit = new SparePartLifeLimitDto(
                UUID.randomUUID(),
                SparePartLifeLimitKind.CALENDAR,
                SparePartCalendarUnit.MONTH,
                null,
                null,
                limitValue,
                warningBeforeValue,
                0
        );
        Instant now = Instant.parse("2026-07-01T00:00:00Z");
        return new SparePartLifeRuleDto(
                UUID.randomUUID(),
                partId,
                null,
                null,
                null,
                SparePartLifeRuleScope.CATALOG,
                SparePartLifeCombinationMode.ANY,
                SparePartDueAction.WARNING_ONLY,
                true,
                null,
                null,
                1,
                "Proof rule",
                null,
                List.of(limit),
                now,
                now
        );
    }
}
