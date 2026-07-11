package com.toir.service;

import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.approval.ApprovalStepDto;
import com.toir.dto.approval.ApprovalHistoryDto;
import com.toir.dto.approval.ApprovalStartRequest;
import com.toir.dto.approval.ApprovalStatisticsDto;
import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.dto.approval.DecisionRequest;
import com.toir.dto.approval.ReturnApprovalRequest;
import com.toir.dto.approval.UpdateApprovalRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.users.Role;
import com.toir.entity.users.User;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.UserStatus;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalDelegateRepository;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.maintanance.MaintenanceAutomationService;
import com.toir.service.maintanance.MaintenanceRegulationService;
import com.toir.service.approval.ApprovalActionExecutor;
import com.toir.service.approval.ApprovalGovernanceService;
import com.toir.service.approval.ApprovalOrchestrator;
import com.toir.service.approval.ApprovalRouteResolver;
import com.toir.service.approval.ApprovalSlaPolicyService;
import com.toir.service.repair.RepairRequestService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Transactional
@RequiredArgsConstructor
public class ApprovalService implements ApprovalOrchestrator {

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
            "REPAIR_CAMPAIGN",
            "EQUIPMENT_COMMISSIONING"
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
    private final ObjectProvider<WorkOrderService> workOrderServiceProvider;
    private final ObjectProvider<PprPlanService> pprPlanServiceProvider;
    private final ObjectProvider<ProcurementRequestService> procurementRequestServiceProvider;
    private final ObjectProvider<RepairRequestService> repairRequestServiceProvider;
    private final ObjectProvider<MaintenanceAutomationService> maintenanceAutomationServiceProvider;
    private final ObjectProvider<MaintenanceRegulationService> maintenanceRegulationServiceProvider;
    private final ObjectProvider<com.toir.service.equipment.EquipmentCommissioningActService> equipmentCommissioningActServiceProvider;


    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> listByDocument(String documentType, UUID documentId) {
        return requestRepository.findAllByTargetTypeAndTargetIdAndIsDeletedFalse(normalizeDocumentType(documentType), documentId).stream()
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
                        || normalizedDocumentType.equals(effectiveTargetTypeName(request)))
                .filter(request -> documentId == null || documentId.equals(effectiveTargetId(request)))
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
        ApprovalTargetType targetType = effectiveTargetType(r);
        UUID targetId = effectiveTargetId(r);
        CreateApprovalRequest effectiveRequest = new CreateApprovalRequest(
                targetType.name(),
                targetId,
                r.title(),
                r.requesterId(),
                r.description(),
                r.steps(),
                targetType,
                targetId,
                r.actionType()
        );
        approvalScopeService.assertCanCreateApproval(effectiveRequest);
        return createNewApproval(
                targetType,
                targetId,
                r.title(),
                r.requesterId(),
                r.description(),
                r.steps(),
                r.actionType()
        );
    }

    @Transactional
    public ApprovalRequestDto update(UUID id, UpdateApprovalRequest update) {
        ApprovalRequest request = getOrThrow(id);
        approvalScopeService.assertCanUpdateApproval(request);

        if (request.getStatus() != ApprovalStatus.PENDING
                && request.getStatus() != ApprovalStatus.DRAFT) {
            throw RestException.badRequest("Only pending or draft approval requests can be updated");
        }
        boolean processStarted = request.getCurrentStep() > 1
                || request.getLastReturnedAt() != null
                || request.isExecuted()
                || request.getSteps().stream()
                .anyMatch(step -> step.getDecision() != ApprovalDecision.PENDING
                        || step.getDecidedAt() != null
                        || step.getDecidedById() != null);
        if (processStarted) {
            throw RestException.badRequest("Approval request cannot be updated after the approval process has started");
        }

        List<CreateApprovalRequest.StepInput> steps = normalizeUpdateStepInputs(update.steps());
        request.setTitle(update.title().trim());
        request.setDescription(update.description());

        request.getSteps().clear();
        requestRepository.saveAndFlush(request);

        int stepNumber = 1;
        for (CreateApprovalRequest.StepInput input : steps) {
            ApprovalStep step = new ApprovalStep();
            step.setRequest(request);
            step.setStepNumber(stepNumber++);
            step.setApproverId(input.approverId());
            step.setApproverRole(input.approverRole());
            step.setDecision(ApprovalDecision.PENDING);
            request.getSteps().add(step);
        }
        request.setCurrentStep(1);

        ApprovalRequest saved = requestRepository.save(request);
        UUID actorId = scopeAccessService.currentUserIdOrNull();
        governanceService.record(
                saved,
                saved.getStatus(),
                saved.getStatus(),
                actorId,
                "Approval request updated",
                ApprovalActionType.UPDATED
        );
        auditBuilderService.log(
                "approval_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.APPROVAL_REQUEST,
                "Approval request updated",
                null,
                saved
        );
        return toDto(saved);
    }

    private ApprovalTargetType effectiveTargetType(CreateApprovalRequest request) {
        if (request.targetType() != null) {
            return request.targetType();
        }
        ApprovalTargetType targetType = ApprovalTargetType.fromDocumentType(request.documentType());
        if (targetType != null) {
            return targetType;
        }
        return ApprovalTargetType.OTHER;
    }

    private UUID effectiveTargetId(CreateApprovalRequest request) {
        if (request.targetId() != null) {
            return request.targetId();
        }
        if (request.documentId() != null) {
            return request.documentId();
        }
        return UUID.randomUUID();
    }

    @Transactional
    @Override
    public ApprovalRequestDto requestApproval(CreateApprovalRequest request) {
        return create(request);
    }

    @Transactional
    public ApprovalRequestDto requestApproval(ApprovalStartRequest request) {
        return requestApproval(request, null, null);
    }

    @Transactional
    public ApprovalRequestDto requestApproval(ApprovalStartRequest request,
                                              UUID legacyApproverId,
                                              String legacyApproverRole) {
        if (request == null) {
            throw RestException.badRequest("Approval request body is required");
        }
        if (request.targetType() == null) {
            throw RestException.badRequest("targetType is required");
        }
        if (request.targetId() == null) {
            throw RestException.badRequest("targetId is required");
        }
        ApprovalActionType effectiveActionType = request.actionType() == null
                ? ApprovalActionType.APPROVE
                : request.actionType();
        String normalizedType = normalizeDocumentType(request.targetType().name());
        if (!INTEGRATED_DOCUMENT_TYPES.contains(normalizedType)) {
            throw RestException.badRequest("Unsupported approval target type: " + normalizedType);
        }

        UUID requesterId = scopeAccessService.currentUserIdOrNull();
        if (requesterId == null) {
            throw RestException.badRequest("Authenticated requester is required to create approval request");
        }

        if (request.targetType() == ApprovalTargetType.MAINTENANCE_DUE_EVENT) {
            return maintenanceAutomationServiceProvider.getObject()
                    .approveDueEvent(request.targetId(), requesterId, effectiveActionType);
        }

        TargetMetadata target = loadTargetMetadataOrThrow(request.targetType(), request.targetId());
        validateStartPreconditions(request.targetType(), request.targetId());
        approvalScopeService.assertCanCreateApproval(new CreateApprovalRequest(
                normalizedType,
                request.targetId(),
                target.title(),
                requesterId,
                request.comment(),
                legacyApproverId == null
                        ? List.of()
                        : List.of(new CreateApprovalRequest.StepInput(legacyApproverId, legacyApproverRole))
        ));

        return createOrReuseApprovalForDocument(
                normalizedType,
                request.targetId(),
                effectiveActionType,
                requesterId,
                legacyApproverId,
                legacyApproverRole,
                target.title(),
                StringUtils.hasText(request.comment()) ? request.comment().trim() : target.description(),
                false
        );
    }

    private TargetMetadata loadTargetMetadataOrThrow(ApprovalTargetType targetType, UUID targetId) {
        String sql = targetMetadataSql(targetType);
        if (!StringUtils.hasText(sql)) {
            throw RestException.badRequest("Unsupported approval target type: " + targetType);
        }
        TargetMetadata metadata = jdbcTemplate.query(
                sql,
                ps -> ps.setObject(1, targetId),
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    String code = rs.getString("code");
                    String title = rs.getString("title");
                    String display = StringUtils.hasText(code)
                            ? targetType.name() + " approval: " + code
                            : targetType.name() + " approval";
                    return new TargetMetadata(
                            StringUtils.hasText(title) ? display + " - " + title : display,
                            "Approval request for " + targetType.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                                    + (StringUtils.hasText(code) ? " " + code : ""));
                }
        );
        if (metadata == null) {
            throw RestException.notFound("Approval target not found: " + targetType + " " + targetId);
        }
        return metadata;
    }

    private String targetMetadataSql(ApprovalTargetType targetType) {
        return switch (targetType) {
            case WORK_ORDER -> "select number as code, title as title from work_orders where id = ? and is_deleted = false";
            case PPR_PLAN -> "select code as code, name as title from ppr_plans where id = ? and is_deleted = false";
            case PROCUREMENT_REQUEST, PROCUREMENT -> "select number as code, title as title from procurement_requests where id = ? and is_deleted = false";
            case MAINTENANCE_BUDGET, BUDGET -> "select code as code, name as title from maintenance_budgets where id = ? and is_deleted = false";
            case REPAIR_REQUEST -> "select number as code, title as title from repair_requests where id = ? and is_deleted = false";
            case MAINTENANCE_REGULATION -> "select code as code, name as title from maintenance_regulations where id = ? and is_deleted = false";
            case REGULATION_CHANGE_PROPOSAL -> "select code as code, title as title from regulation_change_proposals where id = ? and is_deleted = false";
            case ACTUAL_COST -> "select null as code, concat('Actual cost ', amount) as title from actual_costs where id = ? and is_deleted = false";
            case DEFECT_LIST -> "select code as code, title as title from defect_lists where id = ? and is_deleted = false";
            case PLANNED_SHUTDOWN -> "select null as code, name as title from planned_shutdowns where id = ? and is_deleted = false";
            case REPAIR_CAMPAIGN -> "select code as code, name as title from repair_campaigns where id = ? and is_deleted = false";
            case EQUIPMENT_COMMISSIONING -> "select act_number as code, concat('Equipment ', equipment_id) as title from equipment_commissioning_acts where id = ? and is_deleted = false";
            default -> null;
        };
    }

    private void validateStartPreconditions(ApprovalTargetType targetType, UUID targetId) {
        switch (targetType) {
            case WORK_ORDER -> workOrderServiceProvider.getObject().validateCanApprove(targetId);
            case PPR_PLAN -> pprPlanServiceProvider.getObject().validateCanApprove(targetId);
            case PROCUREMENT_REQUEST, PROCUREMENT -> procurementRequestServiceProvider.getObject().validateCanApprove(targetId);
            case REPAIR_REQUEST -> repairRequestServiceProvider.getObject().assertMeterReadingsReadyForApproval(targetId);
            case MAINTENANCE_REGULATION -> maintenanceRegulationServiceProvider.getObject().validateCanApprove(targetId);
            case EQUIPMENT_COMMISSIONING -> {
                if (equipmentCommissioningActServiceProvider == null) {
                    throw RestException.conflict("Equipment commissioning approval service is unavailable");
                }
                equipmentCommissioningActServiceProvider.getObject().validateCanApprove(targetId);
            }
            default -> {
            }
        }
    }

    private record TargetMetadata(String title, String description) {
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
        ApprovalTargetType targetType = ApprovalTargetType.fromDocumentType(documentType);
        String normalizedType = targetType == null ? "" : targetType.name();
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
                        targetType,
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
    @Override
    public ApprovalRequestDto approve(UUID requestId, DecisionRequest decision) {
        return applyDecision(requestId, decision, ApprovalDecision.APPROVED);
    }

    @Transactional
    public ApprovalRequestDto approveStep(UUID requestId, UUID stepId, DecisionRequest decision) {
        return applyDecision(requestId, stepId, decision, ApprovalDecision.APPROVED);
    }

    @Transactional
    @Override
    public ApprovalRequestDto reject(UUID requestId, DecisionRequest decision) {
        return applyDecision(requestId, decision, ApprovalDecision.REJECTED);
    }

    @Transactional
    public ApprovalRequestDto rejectStep(UUID requestId, UUID stepId, DecisionRequest decision) {
        return applyDecision(requestId, stepId, decision, ApprovalDecision.REJECTED);
    }

    @Transactional
    public ApprovalRequestDto returnToStep(UUID requestId, ReturnApprovalRequest returnRequest) {
        ApprovalRequest request = getOrThrow(requestId);
        UUID actorId = effectiveDecisionActor(returnRequest == null ? null : returnRequest.approverId());
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
        UUID delegatedForId = assertCanActOnCurrentStep(request, current, actorId);
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
        request.setLastReturnedBy(actorId);
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
                actorId,
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
        if (effectiveTargetType(request) == ApprovalTargetType.MAINTENANCE_DUE_EVENT) {
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
        return applyDecision(requestId, null, decision, outcome);
    }

    private ApprovalRequestDto applyDecision(UUID requestId, UUID expectedStepId, DecisionRequest decision, ApprovalDecision outcome) {
        ApprovalRequest request = getOrThrow(requestId);
        UUID actorId = effectiveDecisionActor(decision);
        if (request.getStatus() == ApprovalStatus.FAILED) {
            if (expectedStepId != null && !expectedStepId.equals(currentStepOrThrow(request).getId())) {
                throw RestException.conflict("Only current pending step can be acted on");
            }
            return retryFailedFinalization(request, decision, outcome, actorId);
        }
        expireIfNeeded(request);
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw RestException.conflict("Request is not pending: " + request.getStatus());
        }
        ApprovalStep current = currentStepOrThrow(request);
        if (expectedStepId != null && !expectedStepId.equals(current.getId())) {
            throw RestException.conflict("Only current pending step can be acted on");
        }
        boolean delegateApprover = assertCanActOnCurrentStep(request, current, actorId) != null;
        current.setDecision(outcome);
        current.setDecidedById(actorId);
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
            governanceService.record(saved, oldStatus, saved.getStatus(), actorId, decision.comment());
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

    private ApprovalRequestDto retryFailedFinalization(ApprovalRequest request,
                                                       DecisionRequest decision,
                                                       ApprovalDecision outcome,
                                                       UUID actorId) {
        ApprovalStep current = currentStepOrThrow(request);
        if (current.getDecision() != outcome) {
            throw RestException.conflict(
                    "Failed approval can only retry its original decision: " + current.getDecision()
            );
        }
        assertCanActOnCurrentStep(request, current, actorId);

        ApprovalStatus retriedStatus = outcome == ApprovalDecision.APPROVED
                ? ApprovalStatus.APPROVED
                : ApprovalStatus.REJECTED;
        request.setStatus(retriedStatus);
        request.setCompletedAt(Instant.now());
        request.setFailureReason(null);
        request.setResultJson(null);
        executeTerminalAction(request, outcome);

        ApprovalRequest saved = requestRepository.save(request);
        governanceService.record(saved, ApprovalStatus.FAILED, saved.getStatus(), actorId, decision.comment());
        if (saved.getStatus() != ApprovalStatus.FAILED) {
            notifyFinalDecision(saved, outcome);
        }
        return toDto(saved);
    }

    private UUID effectiveDecisionActor(DecisionRequest decision) {
        return effectiveDecisionActor(decision == null ? null : decision.approverId());
    }

    private UUID effectiveDecisionActor(UUID requestedActorId) {
        UUID actorId = requestedActorId;
        if (actorId != null) {
            return actorId;
        }
        actorId = scopeAccessService.currentUserIdOrNull();
        if (actorId != null) {
            return actorId;
        }
        java.util.Optional<UUID> employeeId = scopeAccessService.currentEmployeeId();
        if (employeeId != null && employeeId.isPresent()) {
            return employeeId.get();
        }
        throw RestException.badRequest("approverId is required when no authenticated user is available");
    }

    private ApprovalStep currentStepOrThrow(ApprovalRequest request) {
        return request.getSteps().stream()
                .filter(s -> s.getStepNumber() == request.getCurrentStep())
                .findFirst()
                .orElseThrow(() -> RestException.conflict("No current step"));
    }

    private UUID assertCanActOnCurrentStep(ApprovalRequest request, ApprovalStep current, UUID actorId) {
        if (current.getApproverId() != null) {
            boolean designatedApprover = current.getApproverId().equals(actorId);
            boolean delegateApprover = isActiveDelegate(current.getApproverId(), actorId);
            if (designatedApprover && canActorActOnStep(current, actorId)) {
                approvalScopeService.assertCanDecideApproval(request, current);
                return null;
            }
            if (!delegateApprover || !canActorActOnStep(current, actorId)) {
                throw RestException.forbidden("Only designated approver can act on this step");
            }
            return current.getApproverId();
        }
        if (canActorActOnStep(current, actorId)) {
            approvalScopeService.assertCanDecideApproval(request, current);
            return null;
        }
        throw RestException.forbidden("Only designated approver can act on this step");
    }

    private void executeTerminalAction(ApprovalRequest request, ApprovalDecision outcome) {
        if (request.isExecuted()) {
            return;
        }
        ApprovalActionType originalActionType = request.getActionType();
        boolean isMaintenanceDueEvent = effectiveTargetType(request) == ApprovalTargetType.MAINTENANCE_DUE_EVENT;
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

    private boolean matchesAuthenticatedPrincipal(UUID candidateId) {
        if (candidateId == null) {
            return false;
        }
        UUID currentUserId = scopeAccessService.currentUserIdOrNull();
        if (candidateId.equals(currentUserId)) {
            return true;
        }
        return scopeAccessService.currentEmployeeId()
                .map(candidateId::equals)
                .orElse(false);
    }

    private boolean canActorApproveRoleStep(UUID actorId, String approverRole) {
        if (!matchesAuthenticatedPrincipal(actorId)) {
            return false;
        }
        return isActiveUserWithRole(actorId, approverRole);
    }

    private boolean canCurrentPrincipalActOnStep(ApprovalStep step) {
        if (step == null) {
            return false;
        }
        UUID actorId = scopeAccessService.currentUserIdOrNull();
        if (actorId == null) {
            actorId = scopeAccessService.currentEmployeeId().orElse(null);
        }
        return canActorActOnStep(step, actorId);
    }

    private boolean canActorActOnStep(ApprovalStep step, UUID actorId) {
        if (step == null || actorId == null) {
            return false;
        }
        if (step.getApproverId() != null) {
            if (step.getApproverId().equals(actorId)) {
                return true;
            }
            return matchesAuthenticatedPrincipal(actorId)
                    && isActiveDelegate(step.getApproverId(), actorId);
        }
        return canActorApproveRoleStep(actorId, step.getApproverRole());
    }

    private boolean isActiveUserWithRole(UUID userId, String roleCode) {
        if (userId == null || !StringUtils.hasText(roleCode)) {
            return false;
        }
        return userRepository.findByIdAndIsDeletedFalse(userId)
                .filter(this::isActiveUser)
                .filter(user -> hasRole(user, roleCode) || currentAuthenticationHasRole(roleCode))
                .isPresent();
    }

    private List<User> activeUsersWithRole(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return List.of();
        }
        List<User> users = userRepository.findAllWithRolesAndIsDeletedFalse();
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        return users.stream()
                .filter(this::isActiveUser)
                .filter(user -> hasRole(user, roleCode))
                .toList();
    }

    private boolean isActiveUser(User user) {
        return user != null && (user.getStatus() == null || user.getStatus() == UserStatus.ACTIVE);
    }

    private boolean hasRole(User user, String roleCode) {
        String expectedRole = normalizeRole(roleCode);
        if (expectedRole == null) {
            return false;
        }
        return roleStream(user)
                .map(Role::getCode)
                .map(this::normalizeRole)
                .filter(Objects::nonNull)
                .anyMatch(expectedRole::equals);
    }

    private boolean currentAuthenticationHasRole(String roleCode) {
        String expectedRole = normalizeRole(roleCode);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (expectedRole == null || authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(this::normalizeRole)
                .filter(Objects::nonNull)
                .anyMatch(expectedRole::equals);
    }

    private String normalizeRole(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return null;
        }
        String normalized = roleCode.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized.substring("ROLE_".length()) : normalized;
    }

    private Stream<Role> roleStream(User user) {
        if (user == null) {
            return Stream.empty();
        }
        Stream<Role> primary = user.getPrimaryRole() == null ? Stream.empty() : Stream.of(user.getPrimaryRole());
        Stream<Role> additional = user.getRoles() == null ? Stream.empty() : user.getRoles().stream();
        return Stream.concat(primary, additional).filter(Objects::nonNull);
    }

    private ApprovalRequestDto createNewApproval(ApprovalTargetType targetType,
                                                 UUID targetId,
                                                 String title,
                                                 UUID requesterId,
                                                 String description,
                                                 List<CreateApprovalRequest.StepInput> steps) {
        return createNewApproval(targetType, targetId, title, requesterId, description, steps, null, false);
    }

    private ApprovalRequestDto createNewApproval(ApprovalTargetType targetType,
                                                 UUID targetId,
                                                 String title,
                                                 UUID requesterId,
                                                 String description,
                                                 List<CreateApprovalRequest.StepInput> steps,
                                                 ApprovalActionType actionType) {
        return createNewApproval(targetType, targetId, title, requesterId, description, steps, actionType, false);
    }

    private ApprovalRequestDto createNewApproval(ApprovalTargetType targetType,
                                                 UUID targetId,
                                                 String title,
                                                 UUID requesterId,
                                                 String description,
                                                 List<CreateApprovalRequest.StepInput> steps,
                                                 ApprovalActionType actionType,
                                                 boolean flushBeforeSideEffects) {
        if (targetType == null) {
            throw RestException.badRequest("targetType is required");
        }
        if (targetId == null) {
            throw RestException.badRequest("targetId is required");
        }
        if (requesterId == null) {
            throw RestException.badRequest("requesterId is required");
        }
        if (!StringUtils.hasText(title)) {
            throw RestException.badRequest("title is required");
        }
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(targetType);
        request.setTargetId(targetId);
        request.setTitle(title.trim());
        request.setRequesterId(requesterId);
        request.setDescription(description);
        request.setStatus(ApprovalStatus.PENDING);
        request.setCurrentStep(1);
        request.setActionType(actionType);
        if (targetType == ApprovalTargetType.PLANNED_SHUTDOWN) {
            Long scopeVersion = jdbcTemplate.queryForObject(
                    "select scope_version from planned_shutdowns where id = ? and is_deleted = false",
                    Long.class, targetId);
            if (scopeVersion == null) {
                throw RestException.conflict("Planned shutdown approval scope is unavailable");
            }
            request.setPayloadJson("{\"scopeVersion\":" + scopeVersion + "}");
        }
        if (request.getExpiresAt() == null) {
            request.setExpiresAt(Instant.now().plus(slaPolicyService.slaFor(request)));
        }

        List<CreateApprovalRequest.StepInput> effectiveSteps = normalizeStepInputs(steps);
        if (effectiveSteps.isEmpty()) {
            effectiveSteps = normalizeStepInputs(routeResolver.resolveRoute(request));
        }
        if (effectiveSteps.isEmpty()) {
            throw RestException.badRequest("At least one approval step is required");
        }

        int idx = 1;
        for (CreateApprovalRequest.StepInput input : effectiveSteps) {
            if (input.approverId() == null && !StringUtils.hasText(input.approverRole())) {
                throw RestException.badRequest("approverId or approverRole is required for each step");
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

    private List<CreateApprovalRequest.StepInput> normalizeStepInputs(List<CreateApprovalRequest.StepInput> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<CreateApprovalRequest.StepInput> normalized = new ArrayList<>();
        for (CreateApprovalRequest.StepInput input : steps) {
            if (input == null) {
                continue;
            }
            String approverRole = StringUtils.hasText(input.approverRole())
                    ? input.approverRole().trim()
                    : null;
            if (input.approverId() != null) {
                normalized.add(new CreateApprovalRequest.StepInput(input.approverId(), approverRole));
                continue;
            }
            if (!StringUtils.hasText(approverRole)) {
                continue;
            }
            normalized.add(new CreateApprovalRequest.StepInput(null, approverRole));
        }
        return normalized;
    }

    private List<CreateApprovalRequest.StepInput> normalizeUpdateStepInputs(
            List<CreateApprovalRequest.StepInput> steps
    ) {
        if (steps == null || steps.isEmpty()) {
            throw RestException.badRequest("At least one approval step is required");
        }
        List<CreateApprovalRequest.StepInput> normalized = new ArrayList<>();
        for (CreateApprovalRequest.StepInput input : steps) {
            if (input == null) {
                throw RestException.badRequest("approverId or approverRole is required for each step");
            }
            String approverRole = StringUtils.hasText(input.approverRole())
                    ? input.approverRole().trim()
                    : null;
            if (input.approverId() == null && approverRole == null) {
                throw RestException.badRequest("approverId or approverRole is required for each step");
            }
            normalized.add(new CreateApprovalRequest.StepInput(input.approverId(), approverRole));
        }
        return normalized;
    }

    private void notifyCurrentStep(ApprovalRequest request) {
        request.getSteps().stream()
                .filter(step -> step.getStepNumber() == request.getCurrentStep())
                .filter(step -> step.getDecision() == ApprovalDecision.PENDING)
                .findFirst()
                .ifPresent(step -> notifyStepApprovers(
                        step,
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
                notificationEntityType(effectiveTargetType(request)),
                effectiveTargetId(request) == null ? null : effectiveTargetId(request).toString()
        );
    }

    private void notifyReturned(ApprovalRequest request, int fromStep, int returnToStep) {
        String message = "Approval was returned to step " + returnToStep + " for correction.";
        notificationService.notifyUser(
                request.getRequesterId(),
                "Approval returned: " + request.getTitle(),
                message,
                NotificationSeverity.WARNING,
                notificationEntityType(effectiveTargetType(request)),
                effectiveTargetId(request) == null ? null : effectiveTargetId(request).toString()
        );
        request.getSteps().stream()
                .filter(step -> step.getStepNumber() == returnToStep)
                .findFirst()
                .ifPresent(step -> notifyStepApprovers(
                        step,
                        "Approval returned to your step: " + request.getTitle(),
                        message,
                        NotificationSeverity.INFO,
                        "ApprovalRequest",
                        request.getId() == null ? null : request.getId().toString()
                ));
    }

    private void notifyStepApprovers(ApprovalStep step,
                                     String title,
                                     String message,
                                     NotificationSeverity severity,
                                     String entityType,
                                     String entityId) {
        if (step.getApproverId() != null) {
            notificationService.notifyUser(step.getApproverId(), title, message, severity, entityType, entityId);
            return;
        }
        activeUsersWithRole(step.getApproverRole())
                .forEach(user -> notificationService.notifyUser(user.getId(), title, message, severity, entityType, entityId));
    }

    private String notificationEntityType(ApprovalTargetType targetType) {
        if (targetType == null) {
            return "ApprovalRequest";
        }
        return switch (targetType) {
            case WORK_ORDER -> "WorkOrder";
            case PPR_PLAN -> "PprPlan";
            case PROCUREMENT_REQUEST, PROCUREMENT -> "ProcurementRequest";
            case MAINTENANCE_BUDGET, BUDGET -> "MaintenanceBudget";
            case MAINTENANCE_DUE_EVENT -> "MaintenanceDueEvent";
            case REPAIR_REQUEST -> "RepairRequest";
            case MAINTENANCE_REGULATION -> "MaintenanceRegulation";
            case REGULATION_CHANGE_PROPOSAL -> "RegulationChangeProposal";
            case ACTUAL_COST -> "ActualCost";
            case DEFECT_LIST -> "DefectList";
            case PLANNED_SHUTDOWN -> "PlannedShutdown";
            case REPAIR_CAMPAIGN -> "RepairCampaign";
            case EQUIPMENT_COMMISSIONING -> "EquipmentCommissioningAct";
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

    private ApprovalTargetType effectiveTargetType(ApprovalRequest request) {
        if (request.getTargetType() != null) {
            return request.getTargetType();
        }
        return ApprovalTargetType.fromDocumentType(request.getDocumentType());
    }

    private String effectiveTargetTypeName(ApprovalRequest request) {
        ApprovalTargetType targetType = effectiveTargetType(request);
        if (targetType != null) {
            return targetType.name();
        }
        return normalizeDocumentType(request.getDocumentType());
    }

    private UUID effectiveTargetId(ApprovalRequest request) {
        return request.getTargetId() == null ? request.getDocumentId() : request.getTargetId();
    }

    private boolean matchesSearch(ApprovalRequest request, String search) {
        return containsIgnoreCase(effectiveTargetTypeName(request), search)
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
        String targetTypeName = effectiveTargetTypeName(request);
        UUID targetId = effectiveTargetId(request);
        String targetUrl = targetUrl(targetTypeName, targetId);
        boolean canDecide = request.getStatus() == ApprovalStatus.PENDING
                && currentStep != null
                && currentStep.getDecision() == ApprovalDecision.PENDING
                && canCurrentPrincipalActOnStep(currentStep);
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
                effectiveTargetType(request),
                targetId,
                request.getActionType(),
                userName(request.getRequesterId()),
                currentApproverLabel(currentStep),
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

    private String currentApproverLabel(ApprovalStep currentStep) {
        if (currentStep == null) {
            return null;
        }
        if (currentStep.getApproverId() != null) {
            return userName(currentStep.getApproverId());
        }
        return currentStep.getApproverRole();
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
