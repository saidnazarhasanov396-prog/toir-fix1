package com.toir.service;

import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalStatus;
import com.toir.repository.ApprovalDelegateRepository;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.approval.ApprovalActionExecutor;
import com.toir.service.approval.ApprovalGovernanceService;
import com.toir.service.approval.ApprovalRouteResolver;
import com.toir.service.approval.ApprovalSlaPolicyService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    ApprovalRequestRepository requestRepository;

    @Mock
    ApprovalDelegateRepository delegateRepository;

    @Mock
    ApprovalActionExecutor approvalActionExecutor;

    @Mock
    ApprovalGovernanceService governanceService;

    @Mock
    ApprovalSlaPolicyService slaPolicyService;

    @Mock
    ApprovalRouteResolver routeResolver;

    @Mock
    JdbcTemplate jdbcTemplate;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    ApprovalScopeService approvalScopeService;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    NotificationService notificationService;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    ApprovalService service;

    @Test
    void createOrReuseApprovalForDocumentLocksAndReturnsExistingPendingApproval() {
        UUID workOrderId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalRequest existing = pendingApproval(UUID.randomUUID(), workOrderId, requesterId);

        when(requestRepository.findFirstPendingByTargetAndAction(
                "WORK_ORDER",
                workOrderId,
                ApprovalActionType.APPROVE.name(),
                ApprovalStatus.PENDING.name()
        )).thenReturn(Optional.of(existing));

        ApprovalRequestDto result = service.createOrReuseApprovalForDocument(
                "WORK_ORDER",
                workOrderId,
                ApprovalActionType.APPROVE,
                requesterId,
                approverId,
                "WORK_ORDER_APPROVER",
                "Work order approval",
                "Approval request for work order"
        );

        assertThat(result.id()).isEqualTo(existing.getId());
        assertThat(result.documentType()).isEqualTo("WORK_ORDER");
        assertThat(result.documentId()).isEqualTo(workOrderId);
        verify(jdbcTemplate).query(
                eq("SELECT pg_advisory_xact_lock(?, ?)"),
                any(PreparedStatementSetter.class),
                any(ResultSetExtractor.class)
        );
        verify(requestRepository, never()).saveAndFlush(any(ApprovalRequest.class));
        verify(governanceService, never()).record(any(), any(), any(), any(), any());
    }

    private ApprovalRequest pendingApproval(UUID id, UUID workOrderId, UUID requesterId) {
        ApprovalRequest approval = new ApprovalRequest();
        ReflectionTestUtils.setField(approval, "id", id);
        ReflectionTestUtils.setField(approval, "createdAt", Instant.now());
        ReflectionTestUtils.setField(approval, "updatedAt", Instant.now());
        approval.setDocumentType("WORK_ORDER");
        approval.setDocumentId(workOrderId);
        approval.setActionType(ApprovalActionType.APPROVE);
        approval.setRequesterId(requesterId);
        approval.setTitle("Work order approval");
        approval.setDescription("Approval request for work order");
        approval.setStatus(ApprovalStatus.PENDING);
        approval.setCurrentStep(1);
        approval.setExpiresAt(Instant.now().plus(Duration.ofHours(24)));
        return approval;
    }
}
