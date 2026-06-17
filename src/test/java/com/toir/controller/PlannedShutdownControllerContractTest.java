package com.toir.controller;

import com.toir.dto.plannedshutdown.PlannedShutdownDto;
import com.toir.enums.PlanStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.ApprovalService;
import com.toir.service.PlannedShutdownService;
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
class PlannedShutdownControllerContractTest {

    @Mock
    private PlannedShutdownService service;

    @Mock
    private ApprovalService approvalService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PlannedShutdownController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listShutdownsWithFiltersReturnsFilteredPage() throws Exception {
        UUID shutdownId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        
        PlannedShutdownDto dto = new PlannedShutdownDto(
                shutdownId,
                "Annual Maintenance",
                departmentId,
                Instant.parse("2026-05-19T10:00:00Z"),
                Instant.parse("2026-05-19T18:00:00Z"),
                "Routine check",
                PlanStatus.DRAFT
        );

        when(service.findAllFiltered(eq(departmentId), eq(PlanStatus.DRAFT), eq("annual"))).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/planned-shutdowns")
                        .param("departmentId", departmentId.toString())
                        .param("status", "DRAFT")
                        .param("search", "annual"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(shutdownId.toString()))
                .andExpect(jsonPath("$.content[0].name").value("Annual Maintenance"))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.content[0].departmentId").value(departmentId.toString()));
    }
}
