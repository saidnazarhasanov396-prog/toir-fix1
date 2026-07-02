package com.toir.controller;

import com.toir.dto.procurement.ProcurementRequestDto;
import com.toir.dto.procurement.ProcurementRequestLineDto;
import com.toir.enums.PriorityLevel;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.enums.ProcurementRequestType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.ActualCostService;
import com.toir.service.finance.ProcurementBudgetAllocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FinanceReviewControllerContractTest {

    @Mock
    ProcurementBudgetAllocationService procurementBudgetAllocationService;

    @Mock
    ActualCostService actualCostService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FinanceReviewController controller = new FinanceReviewController(
                procurementBudgetAllocationService,
                actualCostService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void procurementReviewQueueReturnsSubmittedUnallocatedRequestsWithLines() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        ProcurementRequestLineDto line = new ProcurementRequestLineDto(
                lineId,
                requestId,
                UUID.randomUUID(),
                2,
                0,
                2,
                "pcs",
                null,
                120,
                null
        );
        ProcurementRequestDto dto = new ProcurementRequestDto(
                requestId,
                "PR-1",
                "Pump procurement",
                null,
                departmentId,
                null,
                null,
                null,
                null,
                null,
                null,
                PriorityLevel.MEDIUM,
                null,
                null,
                ProcurementRequestType.SPARE_PART,
                null,
                null,
                null,
                null,
                ProcurementRequestStatus.SUBMITTED,
                "MANUAL",
                null,
                120,
                null,
                null,
                null,
                null,
                null,
                List.of(line),
                null,
                "UNALLOCATED",
                null,
                null
        );

        when(procurementBudgetAllocationService.reviewQueue(
                departmentId,
                ProcurementRequestStatus.SUBMITTED,
                true
        )).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/finance-review/procurement-requests")
                        .param("departmentId", departmentId.toString())
                        .param("status", "SUBMITTED")
                        .param("unallocatedOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requestId.toString()))
                .andExpect(jsonPath("$[0].lines.length()").value(1))
                .andExpect(jsonPath("$[0].lines[0].id").value(lineId.toString()));

        verify(procurementBudgetAllocationService).reviewQueue(
                departmentId,
                ProcurementRequestStatus.SUBMITTED,
                true
        );
    }
}
