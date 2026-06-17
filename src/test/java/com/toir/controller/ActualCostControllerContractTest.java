package com.toir.controller;

import com.toir.dto.actualcost.ActualCostDto;
import com.toir.enums.ActualCostStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.service.ApprovalService;
import com.toir.service.ActualCostService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ActualCostControllerContractTest {

    @Mock
    ActualCostService service;

    @Mock
    ApprovalService approvalService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ActualCostController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rejectWithoutCommentShouldReturnBadRequest() throws Exception {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/actual-costs/{id}/reject", id)
                        .param("reviewerId", reviewerId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Rejection comment is required"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectWithBlankCommentShouldReturnBadRequest() throws Exception {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/actual-costs/{id}/reject", id)
                        .param("reviewerId", reviewerId.toString())
                        .param("comment", "   "))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Use /api/v1/approvals/{id}/reject to reject approval requests"));
    }

    @Test
    void rejectWithValidCommentShouldReturnSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        ActualCostDto dto = new ActualCostDto(
                id,
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                ActualCostStatus.REJECTED,
                reviewerId,
                Instant.now(),
                "Reason",
                100,
                Instant.now(),
                null
        );

        mockMvc.perform(post("/api/v1/actual-costs/{id}/reject", id)
                        .param("reviewerId", reviewerId.toString())
                        .param("comment", "Reason"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Use /api/v1/approvals/{id}/reject to reject approval requests"));
    }

    @Test
    void listShouldSupportBusinessSearchWithoutWorkOrderId() throws Exception {
        ActualCostDto dto = new ActualCostDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                null,
                UUID.randomUUID(),
                ActualCostStatus.PENDING,
                null,
                null,
                null,
                250.0,
                Instant.now(),
                "WO-2026-10"
        );
        when(service.findByFilters(null, "WO-2026-10")).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/actual-costs").param("search", "WO-2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(dto.id().toString()));

        verify(service).findByFilters(null, "WO-2026-10");
    }

    @Test
    void listShouldKeepExistingWorkOrderIdFilterBehavior() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(service.findByFilters(workOrderId, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/actual-costs").param("workOrderId", workOrderId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(service).findByFilters(workOrderId, null);
    }

    @Test
    void listWithInvalidWorkOrderIdShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/actual-costs").param("workOrderId", "invalid-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'workOrderId': invalid-uuid. Expected UUID."));

        verifyNoInteractions(service);
    }
}
