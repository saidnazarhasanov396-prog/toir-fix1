package com.toir.controller;

import com.toir.controller.repair.RepairRequestController;
import com.toir.dto.repairrequest.RepairRequestDto;
import com.toir.dto.triad.DefectBriefDto;
import com.toir.dto.triad.WorkOrderBriefDto;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.DefectStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestSource;
import com.toir.enums.RequestStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.security.SecurityScope;
import com.toir.service.repair.RepairRequestService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RepairRequestControllerContractTest {

    @Mock
    RepairRequestService service;

    @Mock
    SecurityScope securityScope;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RepairRequestController(service, securityScope))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void detailIncludesLinkedDefects() throws Exception {
        UUID requestId = UUID.randomUUID();
        RepairRequestDto response = dtoWithLinks(requestId);
        when(service.findById(requestId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/repair-requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedDefects[0].id").value(response.linkedDefects().getFirst().id().toString()))
                .andExpect(jsonPath("$.linkedDefects[0].code").value("DEF-2026-1001"));
    }

    @Test
    void detailIncludesLinkedWorkOrders() throws Exception {
        UUID requestId = UUID.randomUUID();
        RepairRequestDto response = dtoWithLinks(requestId);
        when(service.findById(requestId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/repair-requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedWorkOrders[0].id").value(response.linkedWorkOrders().getFirst().id().toString()))
                .andExpect(jsonPath("$.linkedWorkOrders[0].number").value("WO-2026-1001"));
    }

    @Test
    void detailWithNoLinksReturnsEmptyArrays() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(service.findById(requestId)).thenReturn(dtoWithoutLinks(requestId));

        mockMvc.perform(get("/api/v1/repair-requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedDefects").isArray())
                .andExpect(jsonPath("$.linkedDefects").isEmpty())
                .andExpect(jsonPath("$.linkedWorkOrders").isArray())
                .andExpect(jsonPath("$.linkedWorkOrders").isEmpty());
    }

    private RepairRequestDto dtoWithLinks(UUID requestId) {
        return new RepairRequestDto(
                requestId,
                "RR-2026-1001",
                "Repair request",
                "Description",
                "Pump #1",
                "Maintenance",
                null,
                "Reporter",
                null,
                PriorityLevel.MEDIUM,
                CriticalityLevel.MEDIUM,
                RequestStatus.OPEN,
                RequestSource.MANUAL,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                null,
                null,
                null,
                null,
                List.of(new DefectBriefDto(
                        UUID.randomUUID(),
                        "DEF-2026-1001",
                        "Leak",
                        DefectStatus.OPEN,
                        "HIGH",
                        Instant.now()
                )),
                List.of(new WorkOrderBriefDto(
                        UUID.randomUUID(),
                        "WO-2026-1001",
                        WorkOrderStatus.APPROVED,
                        WorkType.REPAIR,
                        PriorityLevel.MEDIUM,
                        Instant.now(),
                        Instant.now().plusSeconds(3600)
                ))
        );
    }

    private RepairRequestDto dtoWithoutLinks(UUID requestId) {
        return new RepairRequestDto(
                requestId,
                "RR-2026-1002",
                "Repair request",
                "Description",
                "Pump #2",
                "Maintenance",
                null,
                "Reporter",
                null,
                PriorityLevel.MEDIUM,
                CriticalityLevel.MEDIUM,
                RequestStatus.OPEN,
                RequestSource.MANUAL,
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }
}
