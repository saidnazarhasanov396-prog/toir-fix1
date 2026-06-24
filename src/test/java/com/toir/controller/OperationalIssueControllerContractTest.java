package com.toir.controller;

import com.toir.dto.operationalissue.OperationalIssueDto;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueStatus;
import com.toir.enums.OperationalIssueType;
import com.toir.service.OperationalIssueService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OperationalIssueControllerContractTest {

    @Mock
    OperationalIssueService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OperationalIssueController(service))
                .build();
    }

    @Test
    void listForwardsSearchAndFilters() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        when(service.search(
                eq(OperationalIssueStatus.OPEN),
                eq(NotificationSeverity.WARNING),
                eq(OperationalIssueType.INSPECTION_DEFECT),
                eq(departmentId),
                eq(equipmentId),
                eq("cobalt"),
                eq(2),
                eq(50),
                eq("severity"),
                eq("asc")
        )).thenReturn(new PageImpl<>(List.<OperationalIssueDto>of(), PageRequest.of(2, 50), 0));

        mockMvc.perform(get("/api/v1/operational-issues")
                        .param("status", "OPEN")
                        .param("severity", "WARNING")
                        .param("type", "INSPECTION_DEFECT")
                        .param("departmentId", departmentId.toString())
                        .param("equipmentId", equipmentId.toString())
                        .param("search", "cobalt")
                        .param("page", "2")
                        .param("size", "50")
                        .param("sort", "severity")
                        .param("direction", "asc"))
                .andExpect(status().isOk());

        verify(service).search(
                OperationalIssueStatus.OPEN,
                NotificationSeverity.WARNING,
                OperationalIssueType.INSPECTION_DEFECT,
                departmentId,
                equipmentId,
                "cobalt",
                2,
                50,
                "severity",
                "asc"
        );
    }
}
