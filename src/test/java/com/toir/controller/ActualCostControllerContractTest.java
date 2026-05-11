package com.toir.controller;

import com.toir.dto.actualcost.ActualCostDto;
import com.toir.enums.ActualCostStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.service.ActualCostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ActualCostControllerContractTest {

    @Mock
    ActualCostService service;

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
        when(service.review(id, false, reviewerId, "   "))
                .thenThrow(RestException.badRequest("Rejection comment is required"));

        mockMvc.perform(post("/api/v1/actual-costs/{id}/reject", id)
                        .param("reviewerId", reviewerId.toString())
                        .param("comment", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Rejection comment is required"));
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
        when(service.review(id, false, reviewerId, "Reason")).thenReturn(dto);

        mockMvc.perform(post("/api/v1/actual-costs/{id}/reject", id)
                        .param("reviewerId", reviewerId.toString())
                        .param("comment", "Reason"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewComment").value("Reason"));
    }
}

