package com.toir.service.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.planning.PprPlanningSession;
import com.toir.entity.planning.PprPlanningVariant;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.planning.PprPlanningSessionStatus;
import com.toir.enums.planning.PprPlanningVariantStatus;
import com.toir.exception.RestException;
import com.toir.repository.planning.PprPlanningSessionRepository;
import com.toir.repository.planning.PprPlanningVariantItemRepository;
import com.toir.repository.planning.PprPlanningVariantRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PprPlanningApprovalBindingServiceTest {

    @Mock PprPlanningSessionRepository sessions;
    @Mock PprPlanningVariantRepository variants;
    @Mock PprPlanningVariantItemRepository items;

    private PprPlanningApprovalBindingService service;
    private UUID sessionId;
    private UUID variantId;
    private PprPlanningSession session;
    private PprPlanningVariant variant;

    @BeforeEach
    void setUp() {
        service = new PprPlanningApprovalBindingService(sessions, variants, items);
        sessionId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        session = new PprPlanningSession();
        session.setId(sessionId);
        session.setStatus(PprPlanningSessionStatus.SELECTED);
        session.setSelectedVariantId(variantId);
        variant = new PprPlanningVariant();
        variant.setId(variantId);
        variant.setSession(session);
        variant.setStatus(PprPlanningVariantStatus.SELECTED);
        variant.setRevision(3L);
        variant.setContentHash("a".repeat(64));
        variant.setHashVersion(1);
    }

    @Test
    void submissionRequiresSelectedVariant() {
        session.setSelectedVariantId(null);
        when(sessions.findByIdAndIsDeletedFalseForUpdate(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.resolveForSubmission(sessionId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("PPR_PLANNING_SELECTION_REQUIRED");
    }

    @Test
    void submissionBindsExactImmutableRevisionAndMarksSessionPending() {
        when(sessions.findByIdAndIsDeletedFalseForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(variants.findByIdAndSessionIdAndIsDeletedFalse(variantId, sessionId)).thenReturn(Optional.of(variant));
        when(items.countByVariantIdAndRevision(variantId, 3L)).thenReturn(4L);
        ApprovalRequest request = new ApprovalRequest();
        request.setId(UUID.randomUUID());
        request.setTargetType(ApprovalTargetType.PPR_PLANNING_SESSION);
        request.setTargetId(sessionId);

        var binding = service.resolveForSubmission(sessionId);
        service.bind(request, binding);
        service.markSubmitted(request, binding);

        assertThat(request.getPprPlanningVariantId()).isEqualTo(variantId);
        assertThat(request.getCalculationRevision()).isEqualTo(3L);
        assertThat(request.getCalculationContentHash()).isEqualTo("a".repeat(64));
        assertThat(session.getStatus()).isEqualTo(PprPlanningSessionStatus.PENDING_APPROVAL);
        assertThat(session.getApprovalRequestId()).isEqualTo(request.getId());
        verify(sessions).save(session);
    }

    @Test
    void approvalCommandRejectsChangedSelection() {
        UUID changed = UUID.randomUUID();
        session.setStatus(PprPlanningSessionStatus.PENDING_APPROVAL);
        session.setSelectedVariantId(changed);
        when(sessions.findByIdAndIsDeletedFalseForUpdate(sessionId)).thenReturn(Optional.of(session));
        ApprovalRequest request = boundRequest();

        assertThatThrownBy(() -> service.validateApprovalCommand(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("PPR_PLANNING_APPROVAL_STALE");
    }

    @Test
    void rejectionUnlocksTheUnchangedSelectionForResubmission() {
        session.setStatus(PprPlanningSessionStatus.PENDING_APPROVAL);
        session.setApprovalRequestId(UUID.randomUUID());
        session.setSelectedVariantRevision(3L);
        session.setSelectedVariantHash("a".repeat(64));
        session.setSelectedVariantHashVersion(1);
        when(sessions.findByIdAndIsDeletedFalseForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(variants.findByIdAndSessionIdAndIsDeletedFalse(variantId, sessionId)).thenReturn(Optional.of(variant));
        ApprovalRequest request = boundRequest();
        request.setId(session.getApprovalRequestId());

        service.markRejected(request);

        assertThat(session.getStatus()).isEqualTo(PprPlanningSessionStatus.REJECTED);
        assertThat(session.getApprovalRequestId()).isNull();
        verify(sessions).save(session);
    }

    private ApprovalRequest boundRequest() {
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(ApprovalTargetType.PPR_PLANNING_SESSION);
        request.setTargetId(sessionId);
        request.setPprPlanningVariantId(variantId);
        request.setCalculationRevision(3L);
        request.setCalculationContentHash("a".repeat(64));
        request.setCalculationContentHashVersion(1);
        return request;
    }
}
