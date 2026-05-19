package com.toir.service;

import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.dto.approval.DecisionRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRequestRepository requestRepository;
    private final AuditBuilderService auditBuilderService;
    private final ApprovalScopeService approvalScopeService;


    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> listByDocument(String documentType, UUID documentId) {
        return requestRepository.findAllByDocumentTypeAndDocumentIdAndIsDeletedFalse(documentType, documentId).stream()
                .filter(approvalScopeService::canReadApproval)
                .map(ApprovalRequestDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> pending() {
        return requestRepository.findAllByStatusAndIsDeletedFalseOrderByCreatedAtDesc(ApprovalStatus.PENDING).stream()
                .filter(approvalScopeService::canReadApproval)
                .map(ApprovalRequestDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> byRequester(UUID requesterId) {
        return requestRepository.findAllByRequesterIdAndIsDeletedFalseOrderByCreatedAtDesc(requesterId).stream()
                .filter(approvalScopeService::canReadApproval)
                .map(ApprovalRequestDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ApprovalRequestDto findById(UUID id) {
        ApprovalRequest request = getOrThrow(id);
        approvalScopeService.assertCanReadApproval(request);
        return ApprovalRequestDto.from(request);
    }

    @Transactional
    public ApprovalRequestDto create(CreateApprovalRequest r) {
        approvalScopeService.assertCanCreateApproval(r);
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
        ApprovalRequest saved = requestRepository.save(request);

        auditBuilderService.log(
                "approval_request",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.APPROVAL_REQUEST,
                "Заявка на согласование создана",
                null,
                saved
        );

        return ApprovalRequestDto.from(saved);
    }

    @Transactional
    public ApprovalRequestDto approve(UUID requestId, DecisionRequest decision) {
        return applyDecision(requestId, decision, ApprovalDecision.APPROVED);
    }

    @Transactional
    public ApprovalRequestDto reject(UUID requestId, DecisionRequest decision) {
        return applyDecision(requestId, decision, ApprovalDecision.REJECTED);
    }

    @Transactional
    public ApprovalRequestDto cancel(UUID requestId) {
        ApprovalRequest request = getOrThrow(requestId);
        approvalScopeService.assertCanCancelApproval(request);
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw RestException.conflict("Request is not pending: " + request.getStatus());
        }
        request.setStatus(ApprovalStatus.CANCELLED);
        request.setCompletedAt(Instant.now());

        ApprovalRequest saved = requestRepository.save(request);


        auditBuilderService.log(
                "approval_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.APPROVAL_REQUEST,
                "Заявка на согласование обновлена",
                request,
                saved
        );

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
        approvalScopeService.assertCanDecideApproval(request, current);
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

        ApprovalRequest saved = requestRepository.save(request);

        auditBuilderService.log(
                "approval_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.APPROVAL_REQUEST,
                "Заявка на согласование обновлена",
                request,
                saved
        );


        return ApprovalRequestDto.from(request);
    }

    private ApprovalRequest getOrThrow(UUID id) {
        return requestRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Approval request not found: " + id));
    }
}
