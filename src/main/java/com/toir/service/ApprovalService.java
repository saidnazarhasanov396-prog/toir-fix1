package com.toir.service;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.ApprovalDecision;
import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalStatus;
import com.toir.entity.ApprovalStep;
import com.toir.repository.ApprovalRequestRepository;

import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.dto.approval.DecisionRequest;
import com.toir.exception.RestException;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
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
    private final AuditSerializationService auditSerializationService;

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> listByDocument(String documentType, UUID documentId) {
        return requestRepository.findAllByDocumentTypeAndDocumentIdAndIsDeletedFalse(documentType, documentId).stream()
                .map(ApprovalRequestDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> pending() {
        return requestRepository.findAllByStatusAndIsDeletedFalseOrderByCreatedAtDesc(ApprovalStatus.PENDING).stream()
                .map(ApprovalRequestDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> byRequester(UUID requesterId) {
        return requestRepository.findAllByRequesterIdAndIsDeletedFalseOrderByCreatedAtDesc(requesterId).stream()
                .map(ApprovalRequestDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ApprovalRequestDto findById(UUID id) {
        return ApprovalRequestDto.from(getOrThrow(id));
    }

    @Transactional
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
        ApprovalRequest saved = requestRepository.save(request);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
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
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw RestException.conflict("Request is not pending: " + request.getStatus());
        }
        String oldJson = auditSerializationService.toJson(request);
        request.setStatus(ApprovalStatus.CANCELLED);
        request.setCompletedAt(Instant.now());
        audit(AuditAction.UPDATE, request.getId(), oldJson, request);
        return ApprovalRequestDto.from(request);
    }

    private ApprovalRequestDto applyDecision(UUID requestId, DecisionRequest decision, ApprovalDecision outcome) {
        ApprovalRequest request = getOrThrow(requestId);
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw RestException.conflict("Request is not pending: " + request.getStatus());
        }
        String oldJson = auditSerializationService.toJson(request);
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
        audit(AuditAction.UPDATE, request.getId(), oldJson, request);
        return ApprovalRequestDto.from(request);
    }

    private ApprovalRequest getOrThrow(UUID id) {
        return requestRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Approval request not found: " + id));
    }

    private void audit(AuditAction action, UUID id, String oldJson, ApprovalRequest current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "approval_request",
                id != null ? id.toString() : null,
                action,
                AuditModule.APPROVAL_REQUEST,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Заявка на согласование создана";
            case UPDATE -> "Заявка на согласование обновлена";
            case DELETE -> "Заявка на согласование удалена";
            default -> "Действие выполнено над заявкой на согласование";
        };
    }
}
