package com.toir.controller;

import com.toir.dto.approval.ApprovalRuleDto;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.service.ApprovalService;
import com.toir.service.approval.ApprovalAnalyticsService;
import com.toir.service.approval.ApprovalRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApprovalRulesControllerTest {

    @Mock
    ApprovalService approvalService;

    @Mock
    ApprovalAnalyticsService analyticsService;

    @Mock
    ApprovalRuleService ruleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ApprovalController(approvalService, analyticsService, ruleService)
        ).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void rulesEndpointReturnsConfigurationInsteadOfRuntimeApprovalRequests() throws Exception {
        ApprovalRuleDto rule = new ApprovalRuleDto(
                ApprovalTargetType.WORK_ORDER,
                ApprovalActionType.APPROVE,
                "Work Order",
                1,
                List.of(new ApprovalRuleDto.Step(
                        1,
                        null,
                        null,
                        "DEPARTMENT_HEAD",
                        ApprovalRuleDto.ApproverType.ROLE
                )),
                true
        );
        when(ruleService.listRules()).thenReturn(List.of(rule));

        mockMvc.perform(get("/api/v1/approvals/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].targetType").value("WORK_ORDER"))
                .andExpect(jsonPath("$[0].stepsCount").value(1))
                .andExpect(jsonPath("$[0].steps[0].approverId").isEmpty())
                .andExpect(jsonPath("$[0].steps[0].approverRole").value("DEPARTMENT_HEAD"))
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].requesterId").doesNotExist())
                .andExpect(jsonPath("$[0].targetId").doesNotExist());

        verifyNoInteractions(approvalService);
    }

    @Test
    void putRulesSavesRoleOnlyTemplateConfiguration() throws Exception {
        ApprovalRuleDto saved = new ApprovalRuleDto(
                ApprovalTargetType.REPAIR_REQUEST,
                ApprovalActionType.APPROVE,
                "Repair Request",
                2,
                List.of(
                        new ApprovalRuleDto.Step(1, null, null, "MANAGER", ApprovalRuleDto.ApproverType.ROLE),
                        new ApprovalRuleDto.Step(2, null, null, "SYSTEM_ADMIN", ApprovalRuleDto.ApproverType.ROLE)
                ),
                true
        );
        when(ruleService.saveRule(any())).thenReturn(saved);

        mockMvc.perform(put("/api/v1/approvals/rules")
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetType": "REPAIR_REQUEST",
                                  "actionType": "APPROVE",
                                  "documentName": "Repair Request",
                                  "active": true,
                                  "steps": [
                                    {"order": 1, "approverRole": "MANAGER", "approverType": "ROLE"},
                                    {"order": 2, "approverRole": "SYSTEM_ADMIN", "approverType": "ROLE"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetType").value("REPAIR_REQUEST"))
                .andExpect(jsonPath("$.steps[0].approverId").isEmpty())
                .andExpect(jsonPath("$.steps[0].approverRole").value("MANAGER"));
    }

    @Test
    void putRulesRetainsExplicitUserAssignment() throws Exception {
        UUID approverId = UUID.randomUUID();
        when(ruleService.saveRule(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/v1/approvals/rules")
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetType": "REPAIR_CAMPAIGN",
                                  "actionType": "APPROVE",
                                  "documentName": "Repair Campaign",
                                  "stepsCount": 1,
                                  "active": true,
                                  "steps": [
                                    {
                                      "order": 1,
                                      "approverId": "%s",
                                      "approverType": "USER"
                                    }
                                  ]
                                }
                                """.formatted(approverId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepsCount").value(1))
                .andExpect(jsonPath("$.steps[0].approverType").value("USER"))
                .andExpect(jsonPath("$.steps[0].approverId").value(approverId.toString()))
                .andExpect(jsonPath("$.steps[0].approverRole").isEmpty());

        ArgumentCaptor<ApprovalRuleDto> captor = ArgumentCaptor.forClass(ApprovalRuleDto.class);
        verify(ruleService).saveRule(captor.capture());
        assertThat(captor.getValue().steps().getFirst().approverId()).isEqualTo(approverId);
        assertThat(captor.getValue().steps().getFirst().approverRole()).isNull();
        assertThat(captor.getValue().steps().getFirst().approverType())
                .isEqualTo(ApprovalRuleDto.ApproverType.USER);
    }

    @Test
    void invalidLifecycleStepsReturnControlledBadRequest() throws Exception {
        when(ruleService.saveRule(any()))
                .thenThrow(RestException.badRequest("APPROVAL_TEMPLATE_STEPS_INVALID"));

        mockMvc.perform(put("/api/v1/approvals/rules")
                        .contentType("application/json")
                        .content(lifecycleRoleRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("APPROVAL_TEMPLATE_STEPS_INVALID"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void multipleActiveLifecycleTemplatesReturnControlledConflict() throws Exception {
        when(ruleService.saveRule(any()))
                .thenThrow(RestException.conflict("MULTIPLE_ACTIVE_TEMPLATES"));

        mockMvc.perform(put("/api/v1/approvals/rules")
                        .contentType("application/json")
                        .content(lifecycleRoleRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("MULTIPLE_ACTIVE_TEMPLATES"))
                .andExpect(jsonPath("$.code").value(409));
    }

    private String lifecycleRoleRequest() {
        return """
                {
                  "targetType": "PLANNED_SHUTDOWN",
                  "actionType": "APPROVE",
                  "documentName": "Planned Shutdown",
                  "active": true,
                  "steps": [
                    {"order": 1, "approverRole": "SYSTEM_ADMIN", "approverType": "ROLE"}
                  ]
                }
                """;
    }
}
