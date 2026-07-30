package com.toir.service.planning;

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
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PprPlanningApprovalBindingService {

    private final PprPlanningSessionRepository sessionRepository;
    private final PprPlanningVariantRepository variantRepository;
    private final PprPlanningVariantItemRepository itemRepository;

    @Transactional
    public PprPlanningApprovalBinding resolveForSubmission(UUID sessionId) {
        PprPlanningSession session = lock(sessionId);
        if (session.getStatus() != PprPlanningSessionStatus.SELECTED
                && session.getStatus() != PprPlanningSessionStatus.REJECTED
                && session.getStatus() != PprPlanningSessionStatus.PENDING_APPROVAL) {
            throw conflict("PPR_PLANNING_SESSION_NOT_SUBMITTABLE");
        }
        UUID variantId = session.getSelectedVariantId();
        if (variantId == null) {
            throw conflict("PPR_PLANNING_SELECTION_REQUIRED");
        }
        PprPlanningVariant variant = variantRepository
                .findByIdAndSessionIdAndIsDeletedFalse(variantId, sessionId)
                .orElseThrow(() -> conflict("PPR_PLANNING_APPROVAL_STALE"));
        if (variant.getStatus() != PprPlanningVariantStatus.SELECTED
                || variant.getRevision() < 1
                || variant.getContentHash() == null
                || variant.getContentHash().length() != 64
                || variant.getHashVersion() < 1
                || itemRepository.countByVariantIdAndRevision(variantId, variant.getRevision()) <= 0) {
            throw conflict("PPR_PLANNING_APPROVAL_STALE");
        }
        return new PprPlanningApprovalBinding(
                sessionId,
                variantId,
                variant.getRevision(),
                variant.getContentHash(),
                variant.getHashVersion());
    }

    public void bind(ApprovalRequest request, PprPlanningApprovalBinding binding) {
        if (binding == null) {
            return;
        }
        request.setPprPlanningVariantId(binding.variantId());
        request.setCalculationRevision(binding.revision());
        request.setCalculationContentHash(binding.contentHash());
        request.setCalculationContentHashVersion(binding.hashVersion());
    }

    @Transactional
    public void markSubmitted(ApprovalRequest request, PprPlanningApprovalBinding binding) {
        if (binding == null || request == null || request.getId() == null) {
            throw conflict("PPR_PLANNING_APPROVAL_BINDING_REQUIRED");
        }
        PprPlanningSession session = lock(binding.sessionId());
        assertCurrentSelection(session, request);
        session.setSelectedVariantRevision(binding.revision());
        session.setSelectedVariantHash(binding.contentHash());
        session.setSelectedVariantHashVersion(binding.hashVersion());
        session.setApprovalRequestId(request.getId());
        session.setStatus(PprPlanningSessionStatus.PENDING_APPROVAL);
        sessionRepository.save(session);
    }

    @Transactional
    public void validateApprovalCommand(ApprovalRequest request) {
        if (!isPlanningRequest(request)) {
            return;
        }
        PprPlanningSession session = lock(request.getTargetId());
        assertCurrentSelection(session, request);
        if (session.getStatus() != PprPlanningSessionStatus.PENDING_APPROVAL
                || !Objects.equals(session.getApprovalRequestId(), request.getId())
                || !Objects.equals(session.getSelectedVariantRevision(), request.getCalculationRevision())
                || !Objects.equals(session.getSelectedVariantHash(), request.getCalculationContentHash())
                || !Objects.equals(session.getSelectedVariantHashVersion(), request.getCalculationContentHashVersion())) {
            throw conflict("PPR_PLANNING_APPROVAL_STALE");
        }
    }

    @Transactional
    public void markRejected(ApprovalRequest request) {
        validateApprovalCommand(request);
        PprPlanningSession session = lock(request.getTargetId());
        session.setStatus(PprPlanningSessionStatus.REJECTED);
        session.setApprovalRequestId(null);
        sessionRepository.save(session);
    }

    public boolean matches(ApprovalRequest request, PprPlanningApprovalBinding binding) {
        return binding != null && isPlanningRequest(request)
                && Objects.equals(request.getTargetId(), binding.sessionId())
                && Objects.equals(request.getPprPlanningVariantId(), binding.variantId())
                && Objects.equals(request.getCalculationRevision(), binding.revision())
                && Objects.equals(request.getCalculationContentHash(), binding.contentHash())
                && Objects.equals(request.getCalculationContentHashVersion(), binding.hashVersion());
    }

    public boolean isPlanningRequest(ApprovalRequest request) {
        return request != null && request.getTargetType() == ApprovalTargetType.PPR_PLANNING_SESSION;
    }

    private void assertCurrentSelection(PprPlanningSession session, ApprovalRequest request) {
        if (!Objects.equals(session.getSelectedVariantId(), request.getPprPlanningVariantId())) {
            throw conflict("PPR_PLANNING_APPROVAL_STALE");
        }
        PprPlanningVariant variant = variantRepository
                .findByIdAndSessionIdAndIsDeletedFalse(
                        request.getPprPlanningVariantId(), session.getId())
                .orElseThrow(() -> conflict("PPR_PLANNING_APPROVAL_STALE"));
        if (variant.getRevision() != request.getCalculationRevision()
                || !Objects.equals(variant.getContentHash(), request.getCalculationContentHash())
                || variant.getHashVersion() != request.getCalculationContentHashVersion()) {
            throw conflict("PPR_PLANNING_APPROVAL_STALE");
        }
    }

    private PprPlanningSession lock(UUID sessionId) {
        return sessionRepository.findByIdAndIsDeletedFalseForUpdate(sessionId)
                .orElseThrow(() -> RestException.notFound(
                        "PPR planning session not found: " + sessionId));
    }

    private static RestException conflict(String code) {
        return new RestException(code, HttpStatus.CONFLICT, code);
    }
}
