package com.toir.controller;

import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalStatus;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApprovalControllerTest {

    @Mock
    ApprovalService service;

    @Mock
    ApprovalAnalyticsService analyticsService;

    @Mock
    ApprovalRuleService ruleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ApprovalController(service, analyticsService, ruleService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void putUpdatesOnlyEditableApprovalFields() throws Exception {
        UUID approvalId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ApprovalRequestDto response = response(approvalId, requesterId, targetId);
        when(service.update(eq(approvalId), org.mockito.ArgumentMatchers.any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/approvals/{id}", approvalId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Updated approval",
                                  "description": "Updated description",
                                  "steps": [
                                    {"approverId": null, "approverRole": "APPROVAL_MANAGER"}
                                  ],
                                  "targetType": "PROCUREMENT_REQUEST",
                                  "targetId": "%s",
                                  "actionType": "DELETE",
                                  "requesterId": "%s"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(approvalId.toString()))
                .andExpect(jsonPath("$.targetType").value("WORK_ORDER"))
                .andExpect(jsonPath("$.targetId").value(targetId.toString()))
                .andExpect(jsonPath("$.actionType").value("APPROVE"))
                .andExpect(jsonPath("$.requesterId").value(requesterId.toString()));

        ArgumentCaptor<com.toir.dto.approval.UpdateApprovalRequest> captor =
                ArgumentCaptor.forClass(com.toir.dto.approval.UpdateApprovalRequest.class);
        verify(service).update(eq(approvalId), captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("Updated approval");
        assertThat(captor.getValue().steps().getFirst().approverId()).isNull();
        assertThat(captor.getValue().steps().getFirst().approverRole()).isEqualTo("APPROVAL_MANAGER");
    }

    @Test
    void putRequiresAtLeastOneStep() throws Exception {
        mockMvc.perform(put("/api/v1/approvals/{id}", UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Updated approval",
                                  "steps": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("steps")));
    }

    @Test
    void startCreatesApprovalThroughCanonicalEndpoint() throws Exception {
        UUID approvalId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ApprovalRequestDto response = response(approvalId, requesterId, targetId);
        when(service.requestApproval(org.mockito.ArgumentMatchers.any(com.toir.dto.approval.ApprovalStartRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/approvals/start")
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetType": "WORK_ORDER",
                                  "targetId": "%s",
                                  "actionType": "APPROVE",
                                  "comment": "Please approve"
                                }
                                """.formatted(targetId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(approvalId.toString()));

        ArgumentCaptor<com.toir.dto.approval.ApprovalStartRequest> captor =
                ArgumentCaptor.forClass(com.toir.dto.approval.ApprovalStartRequest.class);
        verify(service).requestApproval(captor.capture());
        assertThat(captor.getValue().targetType()).isEqualTo(ApprovalTargetType.WORK_ORDER);
        assertThat(captor.getValue().targetId()).isEqualTo(targetId);
    }

    @Test
    void stepApproveIgnoresClientApproverAndUsesCurrentAuthenticatedActor() throws Exception {
        UUID approvalId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ApprovalRequestDto response = response(approvalId, requesterId, targetId);
        when(service.approveStep(eq(approvalId), eq(stepId), org.mockito.ArgumentMatchers.any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/approvals/{id}/steps/{stepId}/approve", approvalId, stepId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "approverId": "%s",
                                  "comment": "Looks good"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(approvalId.toString()));

        ArgumentCaptor<com.toir.dto.approval.DecisionRequest> captor =
                ArgumentCaptor.forClass(com.toir.dto.approval.DecisionRequest.class);
        verify(service).approveStep(eq(approvalId), eq(stepId), captor.capture());
        assertThat(captor.getValue().approverId()).isNull();
        assertThat(captor.getValue().comment()).isEqualTo("Looks good");
    }

    @Test
    void requesterApproveDecisionFailureReturnsForbidden() throws Exception {
        UUID approvalId = UUID.randomUUID();
        when(service.approve(eq(approvalId), org.mockito.ArgumentMatchers.any()))
                .thenThrow(RestException.forbidden("Requester cannot decide lifecycle approval"));

        mockMvc.perform(post("/api/v1/approvals/{id}/approve", approvalId)
                        .contentType("application/json")
                        .content("""
                                {"comment": "approve"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void requesterRejectDecisionFailureReturnsForbidden() throws Exception {
        UUID approvalId = UUID.randomUUID();
        when(service.reject(eq(approvalId), org.mockito.ArgumentMatchers.any()))
                .thenThrow(RestException.forbidden("Requester cannot decide lifecycle approval"));

        mockMvc.perform(post("/api/v1/approvals/{id}/reject", approvalId)
                        .contentType("application/json")
                        .content("""
                                {"comment": "reject"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPreservesIndependentAuthoritativeActionFlags() throws Exception {
        UUID approvalId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ApprovalRequestDto response = responseWithFlags(
                approvalId, requesterId, targetId, false, true, false);
        when(service.findById(approvalId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/approvals/{id}", approvalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canApprove").value(false))
                .andExpect(jsonPath("$.canReject").value(true))
                .andExpect(jsonPath("$.canCancel").value(false));
    }

    private ApprovalRequestDto response(UUID id, UUID requesterId, UUID targetId) {
        ApprovalRequest approval = new ApprovalRequest();
        ReflectionTestUtils.setField(approval, "id", id);
        ReflectionTestUtils.setField(approval, "createdAt", Instant.now());
        ReflectionTestUtils.setField(approval, "updatedAt", Instant.now());
        approval.setTargetType(ApprovalTargetType.WORK_ORDER);
        approval.setTargetId(targetId);
        approval.setTitle("Updated approval");
        approval.setRequesterId(requesterId);
        approval.setStatus(ApprovalStatus.PENDING);
        approval.setCurrentStep(1);
        approval.setActionType(ApprovalActionType.APPROVE);
        return ApprovalRequestDto.from(approval);
    }

    private ApprovalRequestDto responseWithFlags(UUID id,
                                                 UUID requesterId,
                                                 UUID targetId,
                                                 boolean canApprove,
                                                 boolean canReject,
                                                 boolean canCancel) {
        return new ApprovalRequestDto(
                id,
                ApprovalTargetType.PLANNED_SHUTDOWN.name(),
                targetId,
                "Lifecycle approval",
                requesterId,
                ApprovalStatus.PENDING,
                1,
                null,
                null,
                Instant.now(),
                List.of(),
                ApprovalTargetType.PLANNED_SHUTDOWN,
                targetId,
                ApprovalActionType.APPROVE,
                null,
                "SYSTEM_ADMIN",
                1,
                null,
                false,
                false,
                "Lifecycle approval",
                "/planned-shutdowns/" + targetId,
                null,
                null,
                canApprove,
                canReject,
                canCancel,
                null);
    }
}
