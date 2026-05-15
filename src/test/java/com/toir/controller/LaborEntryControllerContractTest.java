package com.toir.controller;

import com.toir.dto.laborentry.LaborEntryDto;
import com.toir.enums.UserStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.LaborEntryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LaborEntryControllerContractTest {

    @Mock
    LaborEntryService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LaborEntryController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listLaborReturnsUserObjectForInternalUser() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(service.findByWorkOrder(workOrderId)).thenReturn(List.of(internalLabor(workOrderId, userId)));

        mockMvc.perform(get("/api/v1/work-orders/{workOrderId}/labor", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].user.id").value(userId.toString()))
                .andExpect(jsonPath("$.content[0].user.fullName").value("Ali Worker"))
                .andExpect(jsonPath("$.content[0].user.username").value("ali.worker"))
                .andExpect(jsonPath("$.content[0].user.email").value("ali@example.com"))
                .andExpect(jsonPath("$.content[0].user.phone").value("+998901112233"))
                .andExpect(jsonPath("$.content[0].user.status").value("ACTIVE"));

        verify(service).findByWorkOrder(workOrderId);
    }

    @Test
    void listLaborKeepsUserIdForBackwardCompatibility() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(service.findByWorkOrder(workOrderId)).thenReturn(List.of(internalLabor(workOrderId, userId)));

        mockMvc.perform(get("/api/v1/work-orders/{workOrderId}/labor", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(userId.toString()));
    }

    @Test
    void listLaborContractorOnlyReturnsUserNull() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(service.findByWorkOrder(workOrderId)).thenReturn(List.of(contractorLabor(workOrderId)));

        mockMvc.perform(get("/api/v1/work-orders/{workOrderId}/labor", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].user", nullValue()))
                .andExpect(jsonPath("$.content[0].userId", nullValue()))
                .andExpect(jsonPath("$.content[0].contractorName").value("Outside Contractor"));
    }

    @Test
    void listLaborMissingUserReturnsUserNull() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID missingUserId = UUID.randomUUID();
        when(service.findByWorkOrder(workOrderId)).thenReturn(List.of(missingUserLabor(workOrderId, missingUserId)));

        mockMvc.perform(get("/api/v1/work-orders/{workOrderId}/labor", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(missingUserId.toString()))
                .andExpect(jsonPath("$.content[0].user", nullValue()));
    }

    private LaborEntryDto internalLabor(UUID workOrderId, UUID userId) {
        return new LaborEntryDto(
                UUID.randomUUID(),
                workOrderId,
                userId,
                new LaborEntryDto.UserRef(
                        userId,
                        "Ali Worker",
                        "ali.worker",
                        "ali@example.com",
                        "+998901112233",
                        UserStatus.ACTIVE
                ),
                null,
                LocalDate.of(2026, 5, 15),
                2.5,
                150000.0,
                "Routine work"
        );
    }

    private LaborEntryDto contractorLabor(UUID workOrderId) {
        return new LaborEntryDto(
                UUID.randomUUID(),
                workOrderId,
                null,
                null,
                "Outside Contractor",
                LocalDate.of(2026, 5, 15),
                3.0,
                125000.0,
                "Contractor activity"
        );
    }

    private LaborEntryDto missingUserLabor(UUID workOrderId, UUID userId) {
        return new LaborEntryDto(
                UUID.randomUUID(),
                workOrderId,
                userId,
                null,
                null,
                LocalDate.of(2026, 5, 15),
                1.0,
                100000.0,
                "Legacy row"
        );
    }
}
