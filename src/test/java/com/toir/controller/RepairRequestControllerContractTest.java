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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
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

    @Test
    void detailIncludesEquipmentDepartmentReporterIds() throws Exception {
        UUID requestId = UUID.randomUUID();
        RepairRequestDto response = dtoWithLinks(requestId);
        when(service.findById(requestId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/repair-requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentId").value(response.equipmentId().toString()))
                .andExpect(jsonPath("$.departmentId").value(response.departmentId().toString()))
                .andExpect(jsonPath("$.reporterId").value(response.reporterId().toString()));
    }

    @Test
    void listIncludesEquipmentDepartmentReporterIds() throws Exception {
        RepairRequestDto response = dtoWithLinks(UUID.randomUUID());
        when(securityScope.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, null, 0, 20, null))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/repair-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].equipmentId").value(response.equipmentId().toString()))
                .andExpect(jsonPath("$.content[0].departmentId").value(response.departmentId().toString()))
                .andExpect(jsonPath("$.content[0].reporterId").value(response.reporterId().toString()));
    }

    @Test
    void detailWithNullableIdsReturnsNullsNot500() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(service.findById(requestId)).thenReturn(dtoWithNullableIds(requestId));

        mockMvc.perform(get("/api/v1/repair-requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentId").value(nullValue()))
                .andExpect(jsonPath("$.departmentId").value(nullValue()))
                .andExpect(jsonPath("$.reporterId").value(nullValue()));
    }

    private RepairRequestDto dtoWithLinks(UUID requestId) {
        return new RepairRequestDto(
                requestId,
                "RR-2026-1001",
                "Repair request",
                "Description",
                UUID.randomUUID(),
                "Pump #1",
                UUID.randomUUID(),
                "Maintenance",
                null,
                UUID.randomUUID(),
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
                UUID.randomUUID(),
                "Pump #2",
                UUID.randomUUID(),
                "Maintenance",
                null,
                UUID.randomUUID(),
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

    private RepairRequestDto dtoWithNullableIds(UUID requestId) {
        return new RepairRequestDto(
                requestId,
                "RR-2026-1003",
                "Repair request",
                "Description",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
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
