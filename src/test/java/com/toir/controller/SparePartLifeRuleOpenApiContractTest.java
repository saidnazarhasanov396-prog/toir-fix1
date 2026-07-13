package com.toir.controller;

import com.toir.controller.sparepartlifecycle.SparePartLifeRuleController;
import com.toir.security.CorsProperties;
import com.toir.security.JwtAuthenticationEntryPoint;
import com.toir.security.JwtAuthenticationFilter;
import com.toir.security.JwtService;
import com.toir.security.RestAccessDeniedHandler;
import com.toir.security.ScopeAccessService;
import com.toir.security.SecurityAccessService;
import com.toir.security.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SparePartLifeRuleController.class,
        properties = "springdoc.api-docs.path=/api/v1/v3/api-docs"
)
@ImportAutoConfiguration({
        SpringDocConfiguration.class,
        SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        SparePartLifeRuleOpenApiContractTest.SecurityBeans.class
})
class SparePartLifeRuleOpenApiContractTest {

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

    @Test
    void listSchemaUsesInlineNamesAndNumericDecimals() throws Exception {
        mockMvc.perform(get("/api/v1/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.schemas.SparePartLifeRuleListDto.properties.sparePartName.type")
                        .value("string"))
                .andExpect(jsonPath(
                        "$.components.schemas.SparePartLifeRuleListDto.properties.equipmentName.type")
                        .value("string"))
                .andExpect(jsonPath(
                        "$.components.schemas.SparePartLifeRuleListDto.properties.sparePartName.nullable")
                        .value(true))
                .andExpect(jsonPath(
                        "$.components.schemas.SparePartLifeRuleListDto.properties.equipmentName.nullable")
                        .value(true))
                .andExpect(jsonPath(
                        "$.components.schemas.SparePartLifeLimitListDto.properties.limitValue.type")
                        .value("number"))
                .andExpect(jsonPath(
                        "$.components.schemas.SparePartLifeLimitListDto.properties.warningBeforeValue.type")
                        .value("number"))
                .andExpect(jsonPath(
                        "$.components.schemas.SparePartLifeLimitListDto.properties.warningBeforeValue.nullable")
                        .value(true))
                .andExpect(jsonPath(
                        "$.components.schemas.SparePartLifeLimitListDto.properties.limitValue.format")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.SparePartLifeLimitListDto.properties.warningBeforeValue.format")
                        .doesNotExist());
    }
}
