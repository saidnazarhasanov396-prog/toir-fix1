package com.toir.service;

import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.approval.ApprovalStepDto;
import com.toir.dto.approval.ApprovalHistoryDto;
import com.toir.dto.approval.ApprovalStatisticsDto;
import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.dto.approval.DecisionRequest;
import com.toir.dto.approval.ReturnApprovalRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.users.User;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.NotificationSeverity;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalDelegateRepository;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.approval.ApprovalActionExecutor;
import com.toir.service.approval.ApprovalGovernanceService;
import com.toir.service.approval.ApprovalRouteResolver;
import com.toir.service.approval.ApprovalSlaPolicyService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
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
            "MAINTENANCE_BUDGET",
            "MAINTENANCE_DUE_EVENT",
            "REPAIR_REQUEST",
            "MAINTENANCE_REGULATION",
            "REGULATION_CHANGE_PROPOSAL",
            "ACTUAL_COST",
            "DEFECT_LIST",
            "PLANNED_SHUTDOWN",
            "REPAIR_CAMPAIGN"
    );

    private final ApprovalRequestRepository requestRepository;
    private final ApprovalDelegateRepository delegateRepository;
    private final ApprovalActionExecutor approvalActionExecutor;
    private final ApprovalGovernanceService governanceService;
    private final ApprovalSlaPolicyService slaPolicyService;
    private final ApprovalRouteResolver routeResolver;
    private final JdbcTemplate jdbcTemplate;
    private final AuditBuilderService auditBuilderService;
    private final ApprovalScopeService approvalScopeService;
    private final ScopeAccessService scopeAccessService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;


    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> listByDocument(String documentType, UUID documentId) {
        return requestRepository.findAllByDocumentTypeAndDocumentIdAndIsDeletedFalse(normalizeDocumentType(documentType), documentId).stream()
                .filter(approvalScopeService::canReadApproval)
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> pending() {
        return requestRepository.findAllByStatusAndIsDeletedFalseOrderByCreatedAtDesc(ApprovalStatus.PENDING).stream()
                .filter(approvalScopeService::canReadApproval)
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> search(String documentType,
                                           UUID documentId,
                                           UUID requesterId,
                                           ApprovalStatus status,
                                           String search) {
        String normalizedDocumentType = StringUtils.hasText(documentType) ? normalizeDocumentType(documentType) : null;
        String normalizedSearch = StringUtils.hasText(search)
                ? search.trim().toLowerCase(Locale.ROOT)
                : null;

        return requestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(request -> normalizedDocumentType == null
                        || normalizedDocumentType.equals(request.getDocumentType()))
                .filter(request -> documentId == null || documentId.equals(request.getDocumentId()))
                .filter(request -> requesterId == null || requesterId.equals(request.getRequesterId()))
                .filter(request -> status == null || status == request.getStatus())
                .filter(request -> normalizedSearch == null || matchesSearch(request, normalizedSearch))
                .filter(approvalScopeService::canReadApproval)
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> listAll() {
        return requestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(approvalScopeService::canReadApproval)
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> overdue() {
        Instant now = Instant.now();
        return requestRepository.findOverdue(now).stream()
                .filter(approvalScopeService::canReadApproval)
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApprovalStatisticsDto statistics() {
        return new ApprovalStatisticsDto(
                requestRepository.countByStatus(ApprovalStatus.PENDING),
                requestRepository.countByStatus(ApprovalStatus.APPROVED),
                requestRepository.countByStatus(ApprovalStatus.REJECTED),
                requestRepository.countByStatus(ApprovalStatus.EXPIRED),
                requestRepository.countEscalated()
        );
    }

    @Transactional(readOnly = true)
    public List<ApprovalHistoryDto> history(UUID approvalId) {
        ApprovalRequest request = getOrThrow(approvalId);
        approvalScopeService.assertCanReadApproval(request);
        return governanceService.history(approvalId).stream()
                .map(ApprovalHistoryDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> byRequester(UUID requesterId) {
        return requestRepository.findAllByRequesterIdAndIsDeletedFalseOrderByCreatedAtDesc(requesterId).stream()
                .filter(approvalScopeService::canReadApproval)
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ApprovalRequestDto findById(UUID id) {
        ApprovalRequest request = getOrThrow(id);
        approvalScopeService.assertCanReadApproval(request);
        return toDto(request);
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
        return createOrReuseApprovalForDocument(
                documentType,
                documentId,
                ApprovalActionType.APPROVE,
                requesterId,
                approverId,
                approverRole,
                title,
                description
        );
    }

    @Transactional
    public ApprovalRequestDto createOrReuseApprovalForDocument(String documentType,
                                                               UUID documentId,
                                                               ApprovalActionType actionType,
                                                               UUID requesterId,
                                                               UUID approverId,
                                                               String approverRole,
                                                               String title,
                                                               String description) {
        return createOrReuseApprovalForDocument(
                documentType,
                documentId,
                actionType,
                requesterId,
                approverId,
                approverRole,
                title,
                description,
                true
        );
    }

    @Transactional
    public ApprovalRequestDto createOrReuseSystemApprovalForDocument(String documentType,
                                                                     UUID documentId,
                                                                     ApprovalActionType actionType,
                                                                     UUID requesterId,
                                                                     UUID approverId,
                                                                     String approverRole,
                                                                     String title,
                                                                     String description) {
        return createOrReuseApprovalForDocument(
                documentType,
                documentId,
                actionType,
                requesterId,
                approverId,
                approverRole,
                title,
                description,
                false
        );
    }

    private ApprovalRequestDto createOrReuseApprovalForDocument(String documentType,
                                                                UUID documentId,
                                                                ApprovalActionType actionType,
                                                                UUID requesterId,
                                                                UUID approverId,
                                                                String approverRole,
                                                                String title,
                                                                String description,
                                                                boolean validateScope) {
        String normalizedType = normalizeDocumentType(documentType);
        if (!INTEGRATED_DOCUMENT_TYPES.contains(normalizedType)) {
            throw RestException.badRequest("Unsupported approval-integrated document type: " + normalizedType);
        }
        if (documentId == null) {
            throw RestException.badRequest("documentId is required");
        }

        UUID effectiveRequesterId = requesterId != null ? requesterId : scopeAccessService.currentUserIdOrNull();
        UUID effectiveApproverId = approverId;
        if (effectiveRequesterId == null) {
            throw RestException.badRequest("requesterId is required to create approval request");
        }
        if (validateScope) {
            approvalScopeService.assertCanCreateApproval(new CreateApprovalRequest(
                    normalizedType,
                    documentId,
                    StringUtils.hasText(title) ? title : normalizedType + " approval",
                    effectiveRequesterId,
                    description,
                    effectiveApproverId == null
                            ? List.of()
                            : List.of(new CreateApprovalRequest.StepInput(effectiveApproverId, approverRole))
            ));
        }

        ApprovalActionType effectiveActionType = actionType == null ? ApprovalActionType.APPROVE : actionType;
        lockApprovalTargetAction(normalizedType, documentId, effectiveActionType);
        return findPendingApproval(normalizedType, documentId, effectiveActionType)
                .map(this::toDtoAfterReuse)
                .orElseGet(() -> createNewApproval(
                        normalizedType,
                        documentId,
                        StringUtils.hasText(title) ? title : normalizedType + " approval",
                        effectiveRequesterId,
                        description,
                        effectiveApproverId == null
                                ? List.of()
                                : List.of(new CreateApprovalRequest.StepInput(effectiveApproverId, approverRole)),
                        effectiveActionType,
                        true
                ));
    }

    private void lockApprovalTargetAction(String normalizedType, UUID documentId, ApprovalActionType actionType) {
        int lockNamespace = normalizedType.hashCode();
        int lockResource = (documentId + ":" + actionType.name()).hashCode();
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(?, ?)",
                ps -> {
                    ps.setInt(1, lockNamespace);
                    ps.setInt(2, lockResource);
                },
                rs -> null
        );
    }

    private java.util.Optional<ApprovalRequest> findPendingApproval(String normalizedType,
                                                                    UUID documentId,
                                                                    ApprovalActionType actionType) {
        return requestRepository.findFirstPendingByTargetAndAction(
                normalizedType,
                documentId,
                actionType.name(),
                ApprovalStatus.PENDING.name()
        );
    }

    private ApprovalRequestDto toDtoAfterReuse(ApprovalRequest existing) {
        notifyCurrentStep(existing);
        return toDto(existing);
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
    public ApprovalRequestDto returnToStep(UUID requestId, ReturnApprovalRequest returnRequest) {
        ApprovalRequest request = getOrThrow(requestId);
        expireIfNeeded(request);
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw RestException.conflict("Request is not pending: " + request.getStatus());
        }
        if (request.isExecuted()) {
            throw RestException.conflict("Executed approvals cannot be returned");
        }
        if (!StringUtils.hasText(returnRequest.comment())) {
            throw RestException.badRequest("comment is required");
        }

        ApprovalStep current = currentStepOrThrow(request);
        UUID delegatedForId = assertCanActOnCurrentStep(request, current, returnRequest.approverId());
        int currentStep = request.getCurrentStep();
        int returnToStep = returnRequest.returnToStep();
        if (returnToStep >= currentStep) {
            throw RestException.badRequest("returnToStep must be less than currentStep");
        }
        if (returnToStep < 1) {
            throw RestException.badRequest("returnToStep must be greater than or equal to 1");
        }
        boolean returnStepExists = request.getSteps().stream()
                .anyMatch(step -> step.getStepNumber() == returnToStep);
        if (!returnStepExists) {
            throw RestException.badRequest("returnToStep does not exist: " + returnToStep);
        }

        Instant returnedAt = Instant.now();
        request.setCurrentStep(returnToStep);
        request.setStatus(ApprovalStatus.PENDING);
        request.setLastReturnedAt(returnedAt);
        request.setLastReturnedBy(returnRequest.approverId());
        request.setLastReturnComment(returnRequest.comment());
        request.getSteps().stream()
                .filter(step -> step.getStepNumber() >= returnToStep)
                .forEach(step -> {
                    step.setDecision(ApprovalDecision.PENDING);
                    step.setDecidedById(null);
                    step.setDelegatedForId(null);
                    step.setDecidedAt(null);
                    step.setComment(null);
                });

        ApprovalRequest saved = requestRepository.save(request);
        governanceService.record(
                saved,
                ApprovalStatus.PENDING,
                ApprovalStatus.PENDING,
                returnRequest.approverId(),
                delegatedForId,
                "Returned from step " + currentStep + " to step " + returnToStep + ": " + returnRequest.comment(),
                ApprovalActionType.RETURNED_TO_STEP
        );
        notifyReturned(saved, currentStep, returnToStep);

        auditBuilderService.log(
                "approval_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.APPROVAL_REQUEST,
                "Р—Р°СЏРІРєР° РЅР° СЃРѕРіР»Р°СЃРѕРІР°РЅРёРµ РѕР±РЅРѕРІР»РµРЅР°",
                request,
                saved
        );

        return toDto(saved);
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
        if (request.getTargetType() == ApprovalTargetType.MAINTENANCE_DUE_EVENT
                || "MAINTENANCE_DUE_EVENT".equals(normalizeDocumentType(request.getDocumentType()))) {
            request.setResultJson(executeOnce(request));
            request.setFailureReason(null);
        }

        ApprovalRequest saved = requestRepository.save(request);
        governanceService.record(saved, ApprovalStatus.PENDING, ApprovalStatus.CANCELLED,
                scopeAccessService.currentUserIdOrNull(), "Approval cancelled");


        auditBuilderService.log(
                "approval_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.APPROVAL_REQUEST,
                "Заявка на согласование обновлена",
                request,
                saved
        );

        return toDto(request);
    }

    private ApprovalRequestDto applyDecision(UUID requestId, DecisionRequest decision, ApprovalDecision outcome) {
        ApprovalRequest request = getOrThrow(requestId);
        expireIfNeeded(request);
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw RestException.conflict("Request is not pending: " + request.getStatus());
        }
        ApprovalStep current = currentStepOrThrow(request);
        boolean delegateApprover = assertCanActOnCurrentStep(request, current, decision.approverId()) != null;
        current.setDecision(outcome);
        current.setDecidedById(decision.approverId());
        current.setDelegatedForId(delegateApprover ? current.getApproverId() : null);
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

        ApprovalStatus oldStatus = ApprovalStatus.PENDING;
        if (isTerminal) {
            executeTerminalAction(request, outcome);
        }

        ApprovalRequest saved = requestRepository.save(request);
        if (isTerminal) {
            governanceService.record(saved, oldStatus, saved.getStatus(), decision.approverId(), decision.comment());
        }
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


        return toDto(request);
    }

    private ApprovalStep currentStepOrThrow(ApprovalRequest request) {
        return request.getSteps().stream()
                .filter(s -> s.getStepNumber() == request.getCurrentStep())
                .findFirst()
                .orElseThrow(() -> RestException.conflict("No current step"));
    }

    private UUID assertCanActOnCurrentStep(ApprovalRequest request, ApprovalStep current, UUID actorId) {
        boolean designatedApprover = current.getApproverId().equals(actorId);
        boolean delegateApprover = isActiveDelegate(current.getApproverId(), actorId);
        if (designatedApprover) {
            approvalScopeService.assertCanDecideApproval(request, current);
            return null;
        }
        if (!delegateApprover || !matchesCurrentPrincipal(actorId)) {
            throw RestException.forbidden("Only designated approver can act on this step");
        }
        return current.getApproverId();
    }

    private void executeTerminalAction(ApprovalRequest request, ApprovalDecision outcome) {
        if (request.isExecuted()) {
            return;
        }
        ApprovalActionType originalActionType = request.getActionType();
        boolean isMaintenanceDueEvent = request.getTargetType() == ApprovalTargetType.MAINTENANCE_DUE_EVENT
                || "MAINTENANCE_DUE_EVENT".equals(normalizeDocumentType(request.getDocumentType()));
        boolean preserveActionType = isMaintenanceDueEvent
                && (originalActionType == ApprovalActionType.CREATE_TASK
                || originalActionType == ApprovalActionType.CREATE_WORK_ORDER);
        if (!preserveActionType) {
            request.setActionType(outcome == ApprovalDecision.APPROVED
                    ? ApprovalActionType.APPROVE
                    : ApprovalActionType.REJECT);
        }
        try {
            request.setResultJson(executeOnce(request));
            request.setFailureReason(null);
        } catch (RuntimeException ex) {
            request.setStatus(ApprovalStatus.FAILED);
            request.setCompletedAt(Instant.now());
            request.setFailureReason(ex.getMessage());
            request.setResultJson(null);
        }
    }

    private String executeOnce(ApprovalRequest request) {
        if (request.isExecuted()) {
            return request.getResultJson();
        }
        if (request.getExecutionId() == null) {
            request.setExecutionId(UUID.randomUUID());
        }
        String result = approvalActionExecutor.execute(request);
        request.setExecuted(true);
        request.setExecutedAt(Instant.now());
        return result;
    }

    private void expireIfNeeded(ApprovalRequest request) {
        if (request.getStatus() == ApprovalStatus.PENDING
                && request.getExpiresAt() != null
                && request.getExpiresAt().isBefore(Instant.now())) {
            governanceService.expire(request, null, "Approval expired before decision");
        }
    }

    private boolean isActiveDelegate(UUID approverId, UUID delegateId) {
        if (approverId == null || delegateId == null) {
            return false;
        }
        return delegateRepository.findActiveDelegation(approverId, delegateId, Instant.now()).isPresent();
    }

    private boolean matchesCurrentPrincipal(UUID candidateId) {
        if (candidateId == null) {
            return false;
        }
        UUID currentUserId = scopeAccessService.currentUserIdOrNull();
        if (candidateId.equals(currentUserId) || scopeAccessService.isScopeAdmin()) {
            return true;
        }
        return scopeAccessService.currentEmployeeId()
                .map(candidateId::equals)
                .orElse(false);
    }

    private ApprovalRequestDto createNewApproval(String normalizedDocumentType,
                                                 UUID documentId,
                                                 String title,
                                                 UUID requesterId,
                                                 String description,
                                                 List<CreateApprovalRequest.StepInput> steps) {
        return createNewApproval(normalizedDocumentType, documentId, title, requesterId, description, steps, null, false);
    }

    private ApprovalRequestDto createNewApproval(String normalizedDocumentType,
                                                 UUID documentId,
                                                 String title,
                                                 UUID requesterId,
                                                 String description,
                                                 List<CreateApprovalRequest.StepInput> steps,
                                                 ApprovalActionType actionType) {
        return createNewApproval(normalizedDocumentType, documentId, title, requesterId, description, steps, actionType, false);
    }

    private ApprovalRequestDto createNewApproval(String normalizedDocumentType,
                                                 UUID documentId,
                                                 String title,
                                                 UUID requesterId,
                                                 String description,
                                                 List<CreateApprovalRequest.StepInput> steps,
                                                 ApprovalActionType actionType,
                                                 boolean flushBeforeSideEffects) {
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
        ApprovalRequest request = new ApprovalRequest();
        request.setDocumentType(normalizedDocumentType);
        request.setDocumentId(documentId);
        request.setTitle(title.trim());
        request.setRequesterId(requesterId);
        request.setDescription(description);
        request.setStatus(ApprovalStatus.PENDING);
        request.setCurrentStep(1);
        request.setActionType(actionType);
        if (request.getExpiresAt() == null) {
            request.setExpiresAt(Instant.now().plus(slaPolicyService.slaFor(request)));
        }

        List<CreateApprovalRequest.StepInput> effectiveSteps = steps;
        if (effectiveSteps == null || effectiveSteps.isEmpty()) {
            effectiveSteps = routeResolver.resolveRoute(request);
        }
        if (effectiveSteps == null || effectiveSteps.isEmpty()) {
            throw RestException.badRequest("At least one approval step is required");
        }

        int idx = 1;
        for (CreateApprovalRequest.StepInput input : effectiveSteps) {
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

        ApprovalRequest saved = flushBeforeSideEffects
                ? requestRepository.saveAndFlush(request)
                : requestRepository.save(request);
        governanceService.record(saved, null, ApprovalStatus.PENDING, requesterId, "Approval created");
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

        return toDto(saved);
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
        String decisionText = switch (request.getStatus()) {
            case FAILED -> "failed";
            case EXPIRED -> "expired";
            case CANCELLED -> "cancelled";
            default -> outcome == ApprovalDecision.APPROVED ? "approved" : "rejected";
        };
        notificationService.notifyUser(
                request.getRequesterId(),
                "Approval " + decisionText + ": " + request.getTitle(),
                "Approval request " + request.getTitle() + " was " + decisionText + ".",
                request.getStatus() == ApprovalStatus.APPROVED ? NotificationSeverity.INFO : NotificationSeverity.WARNING,
                notificationEntityType(request.getDocumentType()),
                request.getDocumentId() == null ? null : request.getDocumentId().toString()
        );
    }

    private void notifyReturned(ApprovalRequest request, int fromStep, int returnToStep) {
        String message = "Approval was returned to step " + returnToStep + " for correction.";
        notificationService.notifyUser(
                request.getRequesterId(),
                "Approval returned: " + request.getTitle(),
                message,
                NotificationSeverity.WARNING,
                notificationEntityType(request.getDocumentType()),
                request.getDocumentId() == null ? null : request.getDocumentId().toString()
        );
        request.getSteps().stream()
                .filter(step -> step.getStepNumber() == returnToStep)
                .findFirst()
                .ifPresent(step -> notificationService.notifyUser(
                        step.getApproverId(),
                        "Approval returned to your step: " + request.getTitle(),
                        message,
                        NotificationSeverity.INFO,
                        "ApprovalRequest",
                        request.getId() == null ? null : request.getId().toString()
                ));
    }

    private String notificationEntityType(String documentType) {
        return switch (normalizeDocumentType(documentType)) {
            case "WORK_ORDER" -> "WorkOrder";
            case "PPR_PLAN" -> "PprPlan";
            case "PROCUREMENT_REQUEST" -> "ProcurementRequest";
            case "MAINTENANCE_BUDGET" -> "MaintenanceBudget";
            case "MAINTENANCE_DUE_EVENT" -> "MaintenanceDueEvent";
            case "REPAIR_REQUEST" -> "RepairRequest";
            case "MAINTENANCE_REGULATION" -> "MaintenanceRegulation";
            case "REGULATION_CHANGE_PROPOSAL" -> "RegulationChangeProposal";
            case "ACTUAL_COST" -> "ActualCost";
            case "DEFECT_LIST" -> "DefectList";
            case "PLANNED_SHUTDOWN" -> "PlannedShutdown";
            case "REPAIR_CAMPAIGN" -> "RepairCampaign";
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

    private boolean matchesSearch(ApprovalRequest request, String search) {
        return containsIgnoreCase(request.getDocumentType(), search)
                || containsIgnoreCase(request.getTitle(), search)
                || containsIgnoreCase(request.getDescription(), search)
                || containsIgnoreCase(request.getStatus().name(), search)
                || request.getSteps().stream().anyMatch(step ->
                        containsIgnoreCase(step.getComment(), search)
                                || containsIgnoreCase(step.getApproverRole(), search));
    }

    private boolean containsIgnoreCase(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }

    private ApprovalRequestDto toDto(ApprovalRequest request) {
        List<ApprovalStepDto> steps = request.getSteps().stream()
                .map(step -> new ApprovalStepDto(
                        step.getId(),
                        step.getStepNumber(),
                        step.getApproverId(),
                        step.getApproverRole(),
                        step.getDecision(),
                        step.getDecidedAt(),
                        step.getComment(),
                        step.getDecidedById(),
                        userName(step.getDecidedById()),
                        step.getDelegatedForId(),
                        userName(step.getDelegatedForId())))
                .toList();
        ApprovalStep currentStep = request.getSteps().stream()
                .filter(step -> step.getStepNumber() == request.getCurrentStep())
                .findFirst()
                .orElse(null);
        String targetTypeName = request.getTargetType() == null
                ? request.getDocumentType()
                : request.getTargetType().name();
        UUID targetId = request.getTargetId() == null ? request.getDocumentId() : request.getTargetId();
        String targetUrl = targetUrl(targetTypeName, targetId);
        boolean canDecide = request.getStatus() == ApprovalStatus.PENDING
                && currentStep != null
                && currentStep.getDecision() == ApprovalDecision.PENDING
                && (matchesCurrentPrincipal(currentStep.getApproverId())
                || isActiveDelegate(currentStep.getApproverId(), scopeAccessService.currentUserIdOrNull()));
        boolean canCancel = request.getStatus() == ApprovalStatus.PENDING
                && (scopeAccessService.isScopeAdmin() || matchesCurrentPrincipal(request.getRequesterId()));

        return new ApprovalRequestDto(
                request.getId(),
                request.getDocumentType(),
                request.getDocumentId(),
                request.getTitle(),
                request.getRequesterId(),
                request.getStatus(),
                request.getCurrentStep(),
                request.getCompletedAt(),
                request.getDescription(),
                request.getCreatedAt(),
                steps,
                request.getTargetType(),
                request.getTargetId(),
                request.getActionType(),
                userName(request.getRequesterId()),
                currentStep == null ? null : userName(currentStep.getApproverId()),
                steps.size(),
                request.getExpiresAt(),
                request.getEscalatedAt() != null,
                request.getStatus() == ApprovalStatus.PENDING
                        && request.getExpiresAt() != null
                        && request.getExpiresAt().isBefore(Instant.now()),
                request.getTitle(),
                targetUrl,
                request.getResultJson(),
                request.getFailureReason(),
                canDecide,
                canDecide,
                canCancel,
                new ApprovalRequestDto.TargetSummary(
                        targetId,
                        targetTypeName,
                        request.getTitle(),
                        null,
                        request.getStatus() == null ? null : request.getStatus().name(),
                        targetUrl),
                request.getLastReturnedAt() != null,
                request.getLastReturnedAt(),
                request.getLastReturnedBy(),
                request.getLastReturnComment());
    }

    private String userName(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findByIdAndIsDeletedFalse(userId)
                .map(User::getFullName)
                .filter(StringUtils::hasText)
                .orElse(null);
    }

    private String targetUrl(String targetType, UUID targetId) {
        if (targetType == null || targetId == null) {
            return null;
        }
        return switch (targetType) {
            case "WORK_ORDER" -> "/work-orders/" + targetId;
            case "PPR_PLAN" -> "/ppr-plans/" + targetId;
            case "PROCUREMENT_REQUEST" -> "/procurement-requests/" + targetId;
            case "MAINTENANCE_BUDGET", "BUDGET" -> "/budgets/" + targetId;
            case "MAINTENANCE_DUE_EVENT" -> "/maintenance-due-events/" + targetId;
            case "REPAIR_REQUEST" -> "/repair-requests/" + targetId;
            case "MAINTENANCE_REGULATION" -> "/maintenance-regulations/" + targetId;
            case "REGULATION_CHANGE_PROPOSAL" -> "/regulation-change-proposals/" + targetId;
            case "ACTUAL_COST" -> "/actual-costs/" + targetId;
            case "DEFECT_LIST" -> "/defect-lists/" + targetId;
            case "PLANNED_SHUTDOWN" -> "/planned-shutdowns/" + targetId;
            case "REPAIR_CAMPAIGN" -> "/repair-campaigns/" + targetId;
            default -> "/approvals/targets/" + targetType + "/" + targetId;
        };
    }

    private ApprovalRequest getOrThrow(UUID id) {
        return requestRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Approval request not found: " + id));
    }
}
