package com.toir.security;

import com.toir.controller.contractor.ContractorContractController;
import com.toir.controller.contractor.ContractorController;
import com.toir.controller.contractor.ContractorWorkController;
import com.toir.dto.contractor.ContractorDto;
import com.toir.service.contactor.ContractorContractService;
import com.toir.service.contactor.ContractorService;
import com.toir.service.contactor.ContractorWorkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        ContractorController.class,
        ContractorWorkController.class,
        ContractorContractController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacContractorSecurityTest.SecurityBeans.class
})
class RbacContractorSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;
    @MockBean
    ContractorService contractorService;
    @MockBean
    ContractorWorkService contractorWorkService;
    @MockBean
    ContractorContractService contractorContractService;

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
    void unauthenticatedCannotReadContractors() throws Exception {
        mockMvc.perform(get("/api/v1/contractors?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PROCUREMENT_READ)
    void procurementReadAloneCannotReadContractors() throws Exception {
        mockMvc.perform(get("/api/v1/contractors?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadContractorsWithContent() throws Exception {
        UUID contractorId = UUID.randomUUID();
        when(contractorService.findAll(isNull(), isNull(), isNull())).thenReturn(List.of(
                new ContractorDto(contractorId, "CTR-ADMIN", "Admin Contractor", null, null, null, null, null, null, null)
        ));

        mockMvc.perform(get("/api/v1/contractors?page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(contractorId.toString()));
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.CONTRACTOR_READ)
    void contractorReadCanReadContractorsAndRelatedRegisters() throws Exception {
        UUID contractorId = UUID.randomUUID();
        when(contractorService.findAll(isNull(), isNull(), isNull())).thenReturn(List.of(
                new ContractorDto(contractorId, "CTR-1", "Contractor", null, null, null, null, null, null, null)
        ));
        when(contractorWorkService.findByContractor(any())).thenReturn(List.of());
        when(contractorContractService.findByContractor(contractorId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/contractors?page=0&size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(contractorId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/contractor-works?contractorId={contractorId}&page=0&size=1", contractorId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/contractor-contracts?contractorId={contractorId}&page=0&size=1", contractorId))
                .andExpect(status().isOk());
    }
}
