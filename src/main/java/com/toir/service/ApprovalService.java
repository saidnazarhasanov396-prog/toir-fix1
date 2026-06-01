package com.toir.service;

import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.dto.approval.DecisionRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.BudgetStatus;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.exception.RestException;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ApprovalService {

    private static final Set<String> INTEGRATED_DOCUMENT_TYPES = Set.of(
            "WORK_ORDER",
            "PPR_PLAN",
            "PROCUREMENT_REQUEST",
            "MAINTENANCE_BUDGET"
    );

    private final ApprovalRequestRepository requestRepository;
    private final ProcurementRequestRepository procurementRequestRepository;
    private final MaintenanceBudgetRepository maintenanceBudgetRepository;
    private final WorkOrderService workOrderService;
    private final PprPlanService pprPlanService;
    private final AuditBuilderService auditBuilderService;
    private final ApprovalScopeService approvalScopeService;
    private final ScopeAccessService scopeAccessService;
    private final NotificationService notificationService;


    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> listByDocument(String documentType, UUID documentId) {
        return requestRepository.findAllByDocumentTypeAndDocumentIdAndIsDeletedFalse(normalizeDocumentType(documentType), documentId).stream()
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
    public List<ApprovalRequestDto> listAll() {
        return requestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
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
        return createNewApproval(
                normalizeDocumentType(r.documentType()),
                r.documentId(),
                r.title(),
                r.requesterId(),
                r.description(),
                r.steps()
        );
    }

    @Transactional
    public ApprovalRequestDto createOrReuseApprovalForDocument(String documentType,
                                                               UUID documentId,
                                                               UUID requesterId,
                                                               UUID approverId,
                                                               String approverRole,
                                                               String title,
                                                               String description) {
        String normalizedType = normalizeDocumentType(documentType);
        if (!INTEGRATED_DOCUMENT_TYPES.contains(normalizedType)) {
            throw RestException.badRequest("Unsupported approval-integrated document type: " + normalizedType);
        }
        if (documentId == null) {
            throw RestException.badRequest("documentId is required");
        }

        UUID effectiveRequesterId = requesterId != null ? requesterId : scopeAccessService.currentUserIdOrNull();
        UUID effectiveApproverId = approverId != null ? approverId : effectiveRequesterId;
        if (effectiveRequesterId == null || effectiveApproverId == null) {
            throw RestException.badRequest("requesterId/approverId is required to create approval request");
        }
        approvalScopeService.assertCanCreateApproval(new CreateApprovalRequest(
                normalizedType,
                documentId,
                StringUtils.hasText(title) ? title : normalizedType + " approval",
                effectiveRequesterId,
                description,
                List.of(new CreateApprovalRequest.StepInput(effectiveApproverId, approverRole))
        ));

        return requestRepository
                .findFirstByDocumentTypeAndDocumentIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(
                        normalizedType,
                        documentId,
                        ApprovalStatus.PENDING
                )
                .map(existing -> {
                    notifyCurrentStep(existing);
                    return ApprovalRequestDto.from(existing);
                })
                .orElseGet(() -> createNewApproval(
                        normalizedType,
                        documentId,
                        StringUtils.hasText(title) ? title : normalizedType + " approval",
                        effectiveRequesterId,
                        description,
                        List.of(new CreateApprovalRequest.StepInput(effectiveApproverId, approverRole))
                ));
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

        boolean isTerminal = false;
        if (outcome == ApprovalDecision.REJECTED) {
            request.setStatus(ApprovalStatus.REJECTED);
            request.setCompletedAt(Instant.now());
            isTerminal = true;
        } else {
            int next = request.getCurrentStep() + 1;
            boolean hasNext = request.getSteps().stream().anyMatch(s -> s.getStepNumber() == next);
            if (hasNext) {
                request.setCurrentStep(next);
            } else {
                request.setStatus(ApprovalStatus.APPROVED);
                request.setCompletedAt(Instant.now());
                isTerminal = true;
            }
        }

        if (isTerminal) {
            completeApprovalAndApplyBusinessDecision(request, decision, outcome);
        }

        ApprovalRequest saved = requestRepository.save(request);
        if (isTerminal) {
            notifyFinalDecision(saved, outcome);
        } else {
            notifyCurrentStep(saved);
        }

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

    private void completeApprovalAndApplyBusinessDecision(ApprovalRequest request,
                                                          DecisionRequest decision,
                                                          ApprovalDecision outcome) {
        String type = normalizeDocumentType(request.getDocumentType());
        UUID documentId = request.getDocumentId();
        switch (type) {
            case "WORK_ORDER" -> {
                if (outcome == ApprovalDecision.APPROVED) {
                    applyWorkOrderApproval(documentId, decision.approverId());
                }
            }
            case "PPR_PLAN" -> {
                if (outcome == ApprovalDecision.APPROVED) {
                    applyPprPlanApproval(documentId, decision.approverId());
                }
            }
            case "PROCUREMENT_REQUEST" -> applyProcurementDecision(documentId, outcome, decision.comment());
            case "MAINTENANCE_BUDGET" -> {
                if (outcome == ApprovalDecision.APPROVED) {
                    applyMaintenanceBudgetApproval(documentId);
                }
            }
            default -> {
                // Not an integrated document type in this phase.
            }
        }
    }

    private void applyWorkOrderApproval(UUID workOrderId, UUID approverId) {
        workOrderService.approve(workOrderId, approverId);
    }

    private void applyPprPlanApproval(UUID planId, UUID approverId) {
        pprPlanService.approve(planId, approverId);
    }

    private void applyProcurementDecision(UUID requestId, ApprovalDecision outcome, String comment) {
        ProcurementRequest request = procurementRequestRepository.findByIdAndIsDeletedFalse(requestId)
                .orElseThrow(() -> RestException.notFound("Procurement request not found: " + requestId));
        if (outcome == ApprovalDecision.APPROVED) {
            if (request.getStatus() != ProcurementRequestStatus.SUBMITTED) {
                throw RestException.badRequest("Procurement request approval can be finalized only from SUBMITTED status");
            }
            request.setStatus(ProcurementRequestStatus.APPROVED);
            request.setApprovedAt(Instant.now());
            request.setRejectionReason(null);
            procurementRequestRepository.save(request);
            return;
        }
        if (request.getStatus() == ProcurementRequestStatus.RECEIVED
                || request.getStatus() == ProcurementRequestStatus.CANCELLED) {
            throw RestException.badRequest("Cannot reject completed procurement request");
        }
        request.setStatus(ProcurementRequestStatus.REJECTED);
        request.setRejectionReason(StringUtils.hasText(comment) ? comment.trim() : "Rejected by approval workflow");
        procurementRequestRepository.save(request);
    }

    private void applyMaintenanceBudgetApproval(UUID budgetId) {
        MaintenanceBudget budget = maintenanceBudgetRepository.findByIdAndIsDeletedFalse(budgetId)
                .orElseThrow(() -> RestException.notFound("Budget not found: " + budgetId));
        if (budget.getStatus() != BudgetStatus.DRAFT) {
            throw RestException.badRequest("Budget approval request can be finalized only from DRAFT status");
        }
        budget.setStatus(BudgetStatus.APPROVED);
        maintenanceBudgetRepository.save(budget);
    }

    private ApprovalRequestDto createNewApproval(String normalizedDocumentType,
                                                 UUID documentId,
                                                 String title,
                                                 UUID requesterId,
                                                 String description,
                                                 List<CreateApprovalRequest.StepInput> steps) {
        if (!StringUtils.hasText(normalizedDocumentType)) {
            throw RestException.badRequest("documentType is required");
        }
        if (documentId == null) {
            throw RestException.badRequest("documentId is required");
        }
        if (requesterId == null) {
            throw RestException.badRequest("requesterId is required");
        }
        if (!StringUtils.hasText(title)) {
            throw RestException.badRequest("title is required");
        }
        if (steps == null || steps.isEmpty()) {
            throw RestException.badRequest("At least one approval step is required");
        }

        ApprovalRequest request = new ApprovalRequest();
        request.setDocumentType(normalizedDocumentType);
        request.setDocumentId(documentId);
        request.setTitle(title.trim());
        request.setRequesterId(requesterId);
        request.setDescription(description);
        request.setStatus(ApprovalStatus.PENDING);
        request.setCurrentStep(1);

        int idx = 1;
        for (CreateApprovalRequest.StepInput input : steps) {
            if (input.approverId() == null) {
                throw RestException.badRequest("approverId is required for each step");
            }
            ApprovalStep step = new ApprovalStep();
            step.setRequest(request);
            step.setStepNumber(idx++);
            step.setApproverId(input.approverId());
            step.setApproverRole(input.approverRole());
            step.setDecision(ApprovalDecision.PENDING);
            request.getSteps().add(step);
        }

        ApprovalRequest saved = requestRepository.save(request);
        notifyCurrentStep(saved);

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

    private void notifyCurrentStep(ApprovalRequest request) {
        request.getSteps().stream()
                .filter(step -> step.getStepNumber() == request.getCurrentStep())
                .filter(step -> step.getDecision() == ApprovalDecision.PENDING)
                .findFirst()
                .ifPresent(step -> notificationService.notifyUser(
                        step.getApproverId(),
                        "Approval requested: " + request.getTitle(),
                        "Approval request " + request.getTitle() + " requires your decision.",
                        NotificationSeverity.INFO,
                        "ApprovalRequest",
                        request.getId() == null ? null : request.getId().toString()
                ));
    }

    private void notifyFinalDecision(ApprovalRequest request, ApprovalDecision outcome) {
        String decisionText = outcome == ApprovalDecision.APPROVED ? "approved" : "rejected";
        notificationService.notifyUser(
                request.getRequesterId(),
                "Approval " + decisionText + ": " + request.getTitle(),
                "Approval request " + request.getTitle() + " was " + decisionText + ".",
                outcome == ApprovalDecision.APPROVED ? NotificationSeverity.INFO : NotificationSeverity.WARNING,
                notificationEntityType(request.getDocumentType()),
                request.getDocumentId() == null ? null : request.getDocumentId().toString()
        );
    }

    private String notificationEntityType(String documentType) {
        return switch (normalizeDocumentType(documentType)) {
            case "WORK_ORDER" -> "WorkOrder";
            case "PPR_PLAN" -> "PprPlan";
            case "PROCUREMENT_REQUEST" -> "ProcurementRequest";
            case "MAINTENANCE_BUDGET" -> "MaintenanceBudget";
            default -> "ApprovalRequest";
        };
    }

    private String normalizeDocumentType(String documentType) {
        if (!StringUtils.hasText(documentType)) {
            return "";
        }
        return documentType.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }

    private ApprovalRequest getOrThrow(UUID id) {
        return requestRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Approval request not found: " + id));
    }
}
