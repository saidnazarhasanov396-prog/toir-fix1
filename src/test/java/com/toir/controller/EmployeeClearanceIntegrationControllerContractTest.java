package com.toir.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toir.dto.integration.EmployeeClearanceReceiptV2Request;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.integration.ErpEmployeeClearanceOutboxService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EmployeeClearanceIntegrationControllerContractTest {
    private ErpEmployeeClearanceOutboxService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ErpEmployeeClearanceOutboxService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new EmployeeClearanceIntegrationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void acceptsCaseBoundV2ReceiptAtDedicatedEndpoint() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID offboardingCaseId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/integrations/offboarding/employee-clearances/v2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": "%s",
                                  "offboardingCaseId": "%s",
                                  "cleared": true,
                                  "sourceRevision": 15,
                                  "evidenceReference": "TOIR-OFFBOARD-15"
                                }
                                """.formatted(employeeId, offboardingCaseId)))
                .andExpect(status().isAccepted());

        ArgumentCaptor<EmployeeClearanceReceiptV2Request> request =
                ArgumentCaptor.forClass(EmployeeClearanceReceiptV2Request.class);
        verify(service).queueV2(request.capture());
        assertThat(request.getValue()).isEqualTo(new EmployeeClearanceReceiptV2Request(
                employeeId, offboardingCaseId, true, 15L, "TOIR-OFFBOARD-15"));
    }
}
