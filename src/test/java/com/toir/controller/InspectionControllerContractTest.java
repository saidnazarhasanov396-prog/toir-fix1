package com.toir.controller;

import com.toir.dto.inspection.InspectionRoundDto;
import com.toir.enums.InspectionRoundStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.security.SecurityScope;
import com.toir.service.InspectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InspectionControllerContractTest {

    @Mock
    InspectionService service;

    @Mock
    SecurityScope securityScope;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InspectionController(service, securityScope))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listRoundsWithNoFiltersReturns200EmptyPage() throws Exception {
        when(service.listRounds(null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/inspection-rounds")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void listRoundsWithRouteAndStatusFiltersReturns200() throws Exception {
        UUID routeId = UUID.randomUUID();
        UUID performedBy = UUID.randomUUID();
        InspectionRoundDto dto = new InspectionRoundDto(
                UUID.randomUUID(),
                routeId,
                performedBy,
                Instant.now(),
                null,
                InspectionRoundStatus.IN_PROGRESS,
                0,
                0,
                null,
                List.of()
        );
        when(service.listRounds(eq(routeId), eq(performedBy), eq(InspectionRoundStatus.IN_PROGRESS)))
                .thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/inspection-rounds")
                        .param("routeId", routeId.toString())
                        .param("performedBy", performedBy.toString())
                        .param("status", "IN_PROGRESS")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("IN_PROGRESS"));
    }

    @Test
    void listRoundsWithInvalidStatusReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/inspection-rounds")
                        .param("status", "INVALID_STATUS")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
