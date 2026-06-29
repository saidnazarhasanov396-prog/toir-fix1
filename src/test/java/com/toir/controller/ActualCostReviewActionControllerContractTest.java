package com.toir.controller;

import com.toir.dto.financialreview.ActualCostReviewItem;
import com.toir.dto.financialreview.ActualCostReviewActivityItem;
import com.toir.dto.financialreview.ActualCostReviewHandoverItem;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.ActualCostReviewFacadeService;
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
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ActualCostReviewActionControllerContractTest {

    @Mock
    ActualCostReviewFacadeService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ActualCostReviewActionController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void exportReviewQueueAppliesFrontendFiltersBeforeBuildingCsv() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID contractorId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        ActualCostReviewItem matching = reviewItem(
                UUID.randomUUID(), departmentId, contractorId, categoryId, "PENDING",
                "FINANCE_MANAGER", Instant.parse("2026-06-15T09:00:00Z"), false, 2, false);
        ActualCostReviewItem wrongDepartment = reviewItem(
                UUID.randomUUID(), UUID.randomUUID(), contractorId, categoryId, "PENDING",
                "FINANCE_MANAGER", Instant.parse("2026-06-15T09:00:00Z"), false, 2, false);
        ActualCostReviewItem wrongRole = reviewItem(
                UUID.randomUUID(), departmentId, contractorId, categoryId, "PENDING",
                "ACCOUNTANT", Instant.parse("2026-06-15T09:00:00Z"), false, 2, false);
        ActualCostReviewItem overdue = reviewItem(
                UUID.randomUUID(), departmentId, contractorId, categoryId, "PENDING",
                "FINANCE_MANAGER", Instant.parse("2026-06-15T09:00:00Z"), true, 0, false);
        ActualCostReviewItem allocated = reviewItem(
                UUID.randomUUID(), departmentId, contractorId, categoryId, "PENDING",
                "FINANCE_MANAGER", Instant.parse("2026-06-15T09:00:00Z"), false, 2, true);

        when(service.reviewQueue("pump"))
                .thenReturn(List.of(wrongDepartment, wrongRole, overdue, allocated, matching));
        when(service.csv(eq("actual-cost-review-queue.csv"), any()))
                .thenAnswer(invocation -> idsCsv(invocation.getArgument(1)));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-queue/export")
                        .param("search", "pump")
                        .param("status", "PENDING")
                        .param("departmentId", departmentId.toString())
                        .param("contractorId", contractorId.toString())
                        .param("approvalRoleCode", "FINANCE_MANAGER")
                        .param("attentionMode", "DUE_SOON")
                        .param("reminderWindowHours", "4")
                        .param("allocationStatus", "UNALLOCATED"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(matching.id().toString())))
                .andExpect(content().string(not(containsString(wrongDepartment.id().toString()))))
                .andExpect(content().string(not(containsString(wrongRole.id().toString()))))
                .andExpect(content().string(not(containsString(overdue.id().toString()))))
                .andExpect(content().string(not(containsString(allocated.id().toString()))));
    }

    @Test
    void exportActualCostsAppliesRegisterFiltersBeforeBuildingCsv() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID contractorId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        ActualCostReviewItem matching = reviewItem(
                UUID.randomUUID(), departmentId, contractorId, categoryId, "APPROVED",
                "FINANCE_MANAGER", Instant.parse("2026-06-15T09:00:00Z"), false, 12, true);
        ActualCostReviewItem wrongStatus = reviewItem(
                UUID.randomUUID(), departmentId, contractorId, categoryId, "PENDING",
                "FINANCE_MANAGER", Instant.parse("2026-06-15T09:00:00Z"), false, 12, true);
        ActualCostReviewItem wrongDate = reviewItem(
                UUID.randomUUID(), departmentId, contractorId, categoryId, "APPROVED",
                "FINANCE_MANAGER", Instant.parse("2026-05-31T23:00:00Z"), false, 12, true);
        ActualCostReviewItem wrongCategory = reviewItem(
                UUID.randomUUID(), departmentId, contractorId, UUID.randomUUID(), "APPROVED",
                "FINANCE_MANAGER", Instant.parse("2026-06-15T09:00:00Z"), false, 12, true);
        ActualCostReviewItem unallocated = reviewItem(
                UUID.randomUUID(), departmentId, contractorId, categoryId, "APPROVED",
                "FINANCE_MANAGER", Instant.parse("2026-06-15T09:00:00Z"), false, 12, false);

        when(service.actualCostRegister("valve"))
                .thenReturn(List.of(wrongStatus, wrongDate, wrongCategory, unallocated, matching));
        when(service.csv(eq("actual-costs.csv"), any()))
                .thenAnswer(invocation -> idsCsv(invocation.getArgument(1)));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/export")
                        .param("search", "valve")
                        .param("status", "APPROVED")
                        .param("departmentId", departmentId.toString())
                        .param("contractorId", contractorId.toString())
                        .param("costCategoryId", categoryId.toString())
                        .param("dateFrom", "2026-06-01")
                        .param("dateTo", "2026-06-30")
                        .param("allocationStatus", "ALLOCATED"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(matching.id().toString())))
                .andExpect(content().string(not(containsString(wrongStatus.id().toString()))))
                .andExpect(content().string(not(containsString(wrongDate.id().toString()))))
                .andExpect(content().string(not(containsString(wrongCategory.id().toString()))))
                .andExpect(content().string(not(containsString(unallocated.id().toString()))));
    }

    @Test
    void exportReviewActivityAppliesFrontendFiltersBeforeBuildingCsv() throws Exception {
        UUID actualCostId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        ActualCostReviewActivityItem matching = activityItem(
                actualCostId, departmentId, "SLA", "FINANCE_MANAGER");
        ActualCostReviewActivityItem wrongGroup = activityItem(
                actualCostId, departmentId, "ROUTE", "FINANCE_MANAGER");
        ActualCostReviewActivityItem wrongDepartment = activityItem(
                actualCostId, UUID.randomUUID(), "SLA", "FINANCE_MANAGER");
        ActualCostReviewActivityItem wrongRole = activityItem(
                actualCostId, departmentId, "SLA", "ACCOUNTANT");

        when(service.activity("pump")).thenReturn(List.of(wrongGroup, wrongDepartment, wrongRole, matching));
        when(service.activityCsv(any())).thenAnswer(invocation -> activityIdsCsv(invocation.getArgument(0)));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/review-activity/export")
                        .param("search", "pump")
                        .param("eventGroup", "SLA")
                        .param("departmentId", departmentId.toString())
                        .param("roleCode", "FINANCE_MANAGER")
                        .param("actualCostId", actualCostId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(matching.id().toString())))
                .andExpect(content().string(not(containsString(wrongGroup.id().toString()))))
                .andExpect(content().string(not(containsString(wrongDepartment.id().toString()))))
                .andExpect(content().string(not(containsString(wrongRole.id().toString()))));
    }

    @Test
    void exportHandoversAppliesFrontendFiltersBeforeBuildingCsv() throws Exception {
        UUID actualCostId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        ActualCostReviewHandoverItem matching = handoverItem(
                actualCostId, departmentId, "FINANCE_MANAGER");
        ActualCostReviewHandoverItem wrongDepartment = handoverItem(
                actualCostId, UUID.randomUUID(), "FINANCE_MANAGER");
        ActualCostReviewHandoverItem wrongRole = handoverItem(
                actualCostId, departmentId, "ACCOUNTANT");
        ActualCostReviewHandoverItem wrongActualCost = handoverItem(
                UUID.randomUUID(), departmentId, "FINANCE_MANAGER");

        when(service.handovers("pump")).thenReturn(List.of(wrongDepartment, wrongRole, wrongActualCost, matching));
        when(service.handoversCsv(any())).thenAnswer(invocation -> handoverIdsCsv(invocation.getArgument(0)));

        mockMvc.perform(get("/api/v1/budgets/actual-costs/handovers/export")
                        .param("search", "pump")
                        .param("departmentId", departmentId.toString())
                        .param("approvalRoleCode", "FINANCE_MANAGER")
                        .param("actualCostId", actualCostId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(matching.id().toString())))
                .andExpect(content().string(not(containsString(wrongDepartment.id().toString()))))
                .andExpect(content().string(not(containsString(wrongRole.id().toString()))))
                .andExpect(content().string(not(containsString(wrongActualCost.id().toString()))));
    }

    private String idsCsv(List<ActualCostReviewItem> items) {
        return items.stream()
                .map(item -> item.id().toString())
                .collect(Collectors.joining("\n"));
    }

    private String activityIdsCsv(List<ActualCostReviewActivityItem> items) {
        return items.stream()
                .map(item -> item.id().toString())
                .collect(Collectors.joining("\n"));
    }

    private String handoverIdsCsv(List<ActualCostReviewHandoverItem> items) {
        return items.stream()
                .map(item -> item.id().toString())
                .collect(Collectors.joining("\n"));
    }

    private ActualCostReviewItem reviewItem(UUID id,
                                            UUID departmentId,
                                            UUID contractorId,
                                            UUID costCategoryId,
                                            String status,
                                            String approvalRoleCode,
                                            Instant costDate,
                                            boolean overdue,
                                            int hoursToOverdue,
                                            boolean allocated) {
        UUID contractorWorkId = UUID.randomUUID();
        UUID budgetLineId = allocated ? UUID.randomUUID() : null;
        return new ActualCostReviewItem(
                id,
                UUID.randomUUID(),
                null,
                contractorWorkId,
                costCategoryId,
                status,
                100.0,
                costDate,
                "pump",
                null,
                null,
                null,
                null,
                null,
                new ActualCostReviewItem.ContractorWorkRef(
                        contractorWorkId,
                        "Contractor work",
                        "IN_PROGRESS",
                        100.0,
                        new ActualCostReviewItem.Ref(contractorId, "C-1", "Contractor"),
                        null
                ),
                new ActualCostReviewItem.WorkOrderRef(UUID.randomUUID(), "WO-1", "Pump", null),
                null,
                budgetLineId != null
                        ? new ActualCostReviewItem.BudgetLineRef(budgetLineId, "Budget line", null, null)
                        : null,
                new ActualCostReviewItem.Ref(departmentId, "D-1", "Department"),
                new ActualCostReviewItem.Ref(costCategoryId, "CC-1", "Category"),
                12,
                overdue,
                "/financial-review/history/" + id,
                null,
                approvalRoleCode,
                null,
                hoursToOverdue,
                "RULE",
                null,
                "PENDING".equals(status),
                null,
                approvalRoleCode,
                "CONTRACTOR",
                "/budgets?actualCostId=" + id,
                "/financial-review?actualCostId=" + id,
                "/financial-review?actualCostId=" + id
        );
    }

    private ActualCostReviewActivityItem activityItem(UUID actualCostId,
                                                      UUID departmentId,
                                                      String eventGroup,
                                                      String roleCode) {
        return new ActualCostReviewActivityItem(
                UUID.randomUUID(),
                "SYSTEM",
                eventGroup,
                "REMINDER",
                Instant.parse("2026-06-15T09:00:00Z"),
                actualCostId,
                "PENDING",
                100.0,
                "pump",
                "pump",
                "Actor",
                roleCode,
                roleCode,
                null,
                "RULE",
                24,
                4,
                2,
                "WARNING",
                "CREATED",
                new ActualCostReviewItem.Ref(departmentId, "D-1", "Department"),
                null,
                null,
                null,
                "/financial-review/history/" + actualCostId,
                "/financial-review?actualCostId=" + actualCostId
        );
    }

    private ActualCostReviewHandoverItem handoverItem(UUID actualCostId,
                                                      UUID departmentId,
                                                      String nextRoleCode) {
        return new ActualCostReviewHandoverItem(
                UUID.randomUUID(),
                Instant.parse("2026-06-15T09:00:00Z"),
                actualCostId,
                "PENDING",
                100.0,
                new ActualCostReviewItem.Ref(departmentId, "D-1", "Department"),
                null,
                null,
                null,
                "Actor",
                UUID.randomUUID(),
                "ACCOUNTANT",
                nextRoleCode,
                null,
                null,
                12,
                24,
                "pump",
                "ack",
                "/financial-review/history/" + actualCostId,
                "/financial-review/activity?actualCostId=" + actualCostId
        );
    }
}
