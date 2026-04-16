package com.toir.approval;

import com.toir.approval.dto.ApprovalRequestDto;
import com.toir.approval.dto.CreateApprovalRequest;
import com.toir.approval.dto.DecisionRequest;
import com.toir.common.exception.RestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ApprovalService {

    private final ApprovalRequestRepository requestRepository;

    public ApprovalService(ApprovalRequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> listByDocument(String documentType, UUID documentId) {
        return requestRepository.findAllByDocumentTypeAndDocumentId(documentType, documentId).stream()
                .map(ApprovalRequestDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> pending() {
        return requestRepository.findAllByStatusOrderByCreatedAtDesc(ApprovalStatus.PENDING).stream()
                .map(ApprovalRequestDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> byRequester(UUID requesterId) {
        return requestRepository.findAllByRequesterIdOrderByCreatedAtDesc(requesterId).stream()
                .map(ApprovalRequestDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ApprovalRequestDto findById(UUID id) {
        return ApprovalRequestDto.from(getOrThrow(id));
    }

    public ApprovalRequestDto create(CreateApprovalRequest r) {
        ApprovalRequest request = new ApprovalRequest();
        request.setDocumentType(r.documentType());
        request.setDocumentId(r.documentId());
        request.setTitle(r.title());
        request.setRequesterId(r.requesterId());
        request.setDescription(r.description());
        request.setStatus(ApprovalStatus.PENDING);
        request.setCurrentStep(1);

        int idx = 1;
        for (CreateApprovalRequest.StepInput input : r.steps()) {
            ApprovalStep step = new ApprovalStep();
            step.setRequest(request);
            step.setStepNumber(idx++);
            step.setApproverId(input.approverId());
            step.setApproverRole(input.approverRole());
            step.setDecision(ApprovalDecision.PENDING);
            request.getSteps().add(step);
        }
        return ApprovalRequestDto.from(requestRepository.save(request));
    }

    public ApprovalRequestDto approve(UUID requestId, DecisionRequest decision) {
        return applyDecision(requestId, decision, ApprovalDecision.APPROVED);
    }

    public ApprovalRequestDto reject(UUID requestId, DecisionRequest decision) {
        return applyDecision(requestId, decision, ApprovalDecision.REJECTED);
    }

    public ApprovalRequestDto cancel(UUID requestId) {
        ApprovalRequest request = getOrThrow(requestId);
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw RestException.conflict("Request is not pending: " + request.getStatus());
        }
        request.setStatus(ApprovalStatus.CANCELLED);
        request.setCompletedAt(Instant.now());
        return ApprovalRequestDto.from(request);
    }

    private ApprovalRequestDto applyDecision(UUID requestId, DecisionRequest decision, ApprovalDecision outcome) {
        ApprovalRequest request = getOrThrow(requestId);
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw RestException.conflict("Request is not pending: " + request.getStatus());
        }
        ApprovalStep current = request.getSteps().stream()
                .filter(s -> s.getStepNumber() == request.getCurrentStep())
                .findFirst()
                .orElseThrow(() -> RestException.conflict("No current step"));
        if (!current.getApproverId().equals(decision.approverId())) {
            throw RestException.forbidden("Only designated approver can act on this step");
        }
        current.setDecision(outcome);
        current.setDecidedAt(Instant.now());
        current.setComment(decision.comment());

        if (outcome == ApprovalDecision.REJECTED) {
            request.setStatus(ApprovalStatus.REJECTED);
            request.setCompletedAt(Instant.now());
        } else {
            int next = request.getCurrentStep() + 1;
            boolean hasNext = request.getSteps().stream().anyMatch(s -> s.getStepNumber() == next);
            if (hasNext) {
                request.setCurrentStep(next);
            } else {
                request.setStatus(ApprovalStatus.APPROVED);
                request.setCompletedAt(Instant.now());
            }
        }
        return ApprovalRequestDto.from(request);
    }

    private ApprovalRequest getOrThrow(UUID id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> RestException.notFound("Approval request not found: " + id));
    }
}
