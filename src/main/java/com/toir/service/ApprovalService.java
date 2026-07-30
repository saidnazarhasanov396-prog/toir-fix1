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
import com.toir.entity.ApprovalTemplate;
import com.toir.entity.ApprovalTemplateStep;
import com.toir.entity.users.Role;
import com.toir.entity.users.User;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalFlowType;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.ApprovalResolutionCode;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.NotificationEventType;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.UserStatus;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalDelegateRepository;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.maintanance.MaintenanceAutomationService;
import com.toir.service.maintanance.MaintenanceRegulationService;
import com.toir.service.maintanance.MaintenanceScheduleApprovalBinding;
import com.toir.service.maintanance.MaintenanceScheduleApprovalBindingService;
import com.toir.service.approval.ApprovalActionExecutor;
import com.toir.service.approval.ApprovalGovernanceService;
import com.toir.service.approval.ApprovalOrchestrator;
import com.toir.service.approval.ApprovalRouteResolver;
import com.toir.service.approval.ApprovalRouteSnapshot;
import com.toir.service.approval.ApprovalSlaPolicyService;
import com.toir.service.approval.LifecycleApprovalRoutePolicy;
import com.toir.service.approval.LifecycleApprovalStartPlan;
import com.toir.service.approval.LifecycleRouteResolution;
import com.toir.service.repair.RepairCampaignApprovalPolicy;
import com.toir.service.repair.RepairRequestService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.PersistenceException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

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
    @Autowired
    private LifecycleApprovalRoutePolicy lifecycleApprovalRoutePolicy = new LifecycleApprovalRoutePolicy();
    @Autowired
    private MaintenanceScheduleApprovalBindingService maintenanceScheduleApprovalBindingService;
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
    public List<ApprovalRequestDto> myTasks() {
        return requestRepository.findAllByStatusAndIsDeletedFalseOrderByCreatedAtDesc(ApprovalStatus.PENDING).stream()
                .filter(approvalScopeService::canReadApproval)
                .map(this::toDto)
                .filter(dto -> dto.currentUserTaskId() != null)
                .filter(dto -> dto.allowedActions().contains("APPROVE")
                        || dto.allowedActions().contains("REJECT"))
                .toList();
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
        if (isBoundApprovalFirstRequest(request)) {
            throw RestException.conflict(
                    "PPR approval route and exact calculation binding are immutable");
        }

        if (request.getStatus() != ApprovalStatus.PENDING
                && request.getStatus() != ApprovalStatus.DRAFT) {
            throw RestException.badRequest("Only pending or draft approval requests can be updated");
        }
        boolean processStarted = (request.getStatus() == ApprovalStatus.PENDING
                && effectiveFlowType(request) == ApprovalFlowType.PARALLEL_ALL)
                || request.getCurrentStep() > 1
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
            case PLANNED_SHUTDOWN -> loadPlannedShutdownApprovalSnapshot(targetId);
            case REPAIR_CAMPAIGN -> loadRepairCampaignApprovalSnapshot(targetId);
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

    private PlannedShutdownApprovalSnapshot loadPlannedShutdownApprovalSnapshot(UUID targetId) {
        String status = jdbcTemplate.queryForObject(
                "select status from planned_shutdowns where id = ? and is_deleted = false", String.class, targetId);
        Long scopeVersion = jdbcTemplate.queryForObject(
                "select scope_version from planned_shutdowns where id = ? and is_deleted = false", Long.class, targetId);
        Long approvalVersion = jdbcTemplate.queryForObject(
                "select approval_scope_version from planned_shutdowns where id = ? and is_deleted = false",
                Long.class, targetId);
        String scopeHash = jdbcTemplate.queryForObject(
                "select approval_scope_hash from planned_shutdowns where id = ? and is_deleted = false",
                String.class, targetId);
        if (!"PENDING_APPROVAL".equals(status)) {
            throw RestException.conflict("PLANNED_SHUTDOWN_NOT_PENDING_APPROVAL");
        }
        if (scopeVersion == null || !Objects.equals(scopeVersion, approvalVersion)
                || scopeHash == null || !scopeHash.matches("[0-9a-f]{64}")) {
            throw RestException.conflict("PLANNED_SHUTDOWN_APPROVAL_SCOPE_STALE");
        }
        return new PlannedShutdownApprovalSnapshot(scopeVersion, scopeHash);
    }

    private record PlannedShutdownApprovalSnapshot(Long scopeVersion, String scopeHash) {}

    private static String plannedShutdownApprovalPayload(PlannedShutdownApprovalSnapshot snapshot) {
        return "{\"scopeVersion\":" + snapshot.scopeVersion()
                + ",\"scopeHash\":\"" + snapshot.scopeHash() + "\"}";
    }

    private record RepairCampaignApprovalSnapshot(Long scopeVersion, String scopeHash, Long campaignVersion) {}

    private RepairCampaignApprovalSnapshot loadRepairCampaignApprovalSnapshot(UUID targetId) {
        return jdbcTemplate.queryForObject("""
                select status, scope_version, approval_scope_version, approval_scope_hash, version
                from repair_campaigns where id = ? and is_deleted = false
                """, (rs, rowNum) -> {
            Long scopeVersion = rs.getLong("scope_version");
            Long approvalVersion = rs.getLong("approval_scope_version");
            String scopeHash = rs.getString("approval_scope_hash");
            if (!"PENDING_APPROVAL".equals(rs.getString("status"))) {
                throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_CAMPAIGN_STATUS_MISMATCH");
            }
            if (!Objects.equals(scopeVersion, approvalVersion)) {
                throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_SCOPE_VERSION_MISMATCH");
            }
            if (scopeHash == null || !scopeHash.matches("[0-9a-f]{64}")) {
                throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_SCOPE_HASH_MISMATCH");
            }
            return new RepairCampaignApprovalSnapshot(scopeVersion, scopeHash, rs.getLong("version"));
        }, targetId);
    }

    private static String repairCampaignApprovalPayload(RepairCampaignApprovalSnapshot snapshot) {
        return "{\"scopeVersion\":" + snapshot.scopeVersion()
                + ",\"scopeHash\":\"" + snapshot.scopeHash()
                + "\",\"campaignVersion\":" + snapshot.campaignVersion() + "}";
    }

    @Transactional
    public LifecycleApprovalStartPlan planLifecycleApproval(ApprovalTargetType targetType,
                                                            UUID targetId,
                                                            ApprovalActionType actionType,
                                                            boolean domainPending,
                                                            String currentPayload) {
        ApprovalActionType effectiveAction = actionType == null
                ? ApprovalActionType.APPROVE
                : actionType;
        if (!lifecycleApprovalRoutePolicy.supports(targetType, effectiveAction)) {
            return lifecyclePlan(
                    targetType,
                    targetId,
                    effectiveAction,
                    null,
                    List.of(),
                    LifecycleApprovalRoutePolicy.Reason.UNSUPPORTED_TARGET_ACTION);
        }
        if (targetId == null) {
            throw RestException.badRequest("targetId is required");
        }

        lockApprovalTargetAction(targetType.name(), targetId, effectiveAction);
        List<ApprovalRequest> pending = requestRepository.findAllPendingByTargetAndAction(
                targetType.name(),
                targetId,
                effectiveAction.name(),
                ApprovalStatus.PENDING.name());
        if (pending == null) {
            pending = List.of();
        } else {
            pending = pending.stream()
                    .filter(Objects::nonNull)
                    .toList();
            pending.stream()
                    .map(ApprovalRequest::getSteps)
                    .filter(Objects::nonNull)
                    .forEach(List::size);
        }
        if (!pending.isEmpty() && !domainPending) {
            return lifecyclePlan(
                    targetType,
                    targetId,
                    effectiveAction,
                    null,
                    List.of(),
                    LifecycleApprovalRoutePolicy.Reason.REQUEST_STATUS_INCONSISTENT);
        }

        ApprovalRequest reusable = null;
        LifecycleApprovalRoutePolicy.Reason firstRuntimeFailure = null;
        for (ApprovalRequest candidate : pending) {
            if (!Objects.equals(candidate.getPayloadJson(), currentPayload)) {
                continue;
            }
            LifecycleApprovalRoutePolicy.Reason compatibilityFailure = lifecycleReuseFailure(
                    candidate, targetType, targetId, effectiveAction);
            if (compatibilityFailure == LifecycleApprovalRoutePolicy.Reason.VALID) {
                if (reusable == null) {
                    reusable = candidate;
                }
            } else if (firstRuntimeFailure == null) {
                firstRuntimeFailure = compatibilityFailure;
            }
        }

        if (reusable != null) {
            UUID changedBy = scopeAccessService.currentUserIdOrNull();
            for (ApprovalRequest candidate : pending) {
                if (candidate == reusable) {
                    continue;
                }
                String reason = Objects.equals(candidate.getPayloadJson(), currentPayload)
                        ? "DUPLICATE_PENDING_APPROVAL"
                        : supersededScopeMessage(targetType);
                cancelLifecyclePending(candidate, changedBy, reason);
            }
            return lifecyclePlan(
                    targetType,
                    targetId,
                    effectiveAction,
                    reusable,
                    List.of(),
                    LifecycleApprovalRoutePolicy.Reason.VALID);
        }

        UUID changedBy = scopeAccessService.currentUserIdOrNull();
        for (ApprovalRequest candidate : pending) {
            if (!Objects.equals(candidate.getPayloadJson(), currentPayload)) {
                cancelLifecyclePending(candidate, changedBy, supersededScopeMessage(targetType));
            }
        }
        if (firstRuntimeFailure != null) {
            return lifecyclePlan(
                    targetType,
                    targetId,
                    effectiveAction,
                    null,
                    List.of(),
                    firstRuntimeFailure);
        }

        LifecycleRouteResolution resolution = routeResolver.resolveLifecycleRoute(targetType, effectiveAction);
        return lifecyclePlan(
                targetType,
                targetId,
                effectiveAction,
                null,
                resolution.steps(),
                resolution.reason(),
                resolution.flowType(),
                resolution.templateId(),
                resolution.templateVersion());
    }

    @Transactional
    public ApprovalRequestDto materializeLifecycleApproval(LifecycleApprovalStartPlan plan,
                                                           UUID requesterId,
                                                           String title,
                                                           String description,
                                                           String payloadAfterFlush) {
        requireValidLifecyclePlanIdentity(plan);
        ApprovalActionType effectivePlanAction = effectiveActionType(plan.actionType());
        if (plan.reusable()) {
            if (plan.failure() != LifecycleApprovalRoutePolicy.Reason.VALID
                    || !plan.frozenSteps().isEmpty()
                    || lifecycleReuseFailure(
                    plan.reusableRequest(), plan.targetType(), plan.targetId(), effectivePlanAction)
                    != LifecycleApprovalRoutePolicy.Reason.VALID) {
                throw RestException.conflict("Invalid reusable lifecycle approval plan");
            }
            return toDto(plan.reusableRequest());
        }
        if (!plan.creatable()) {
            String reason = plan.failure() == null ? "UNKNOWN" : plan.failure().name();
            throw RestException.conflict("Lifecycle approval plan is not creatable: " + reason);
        }
        if (requesterId == null) {
            throw RestException.badRequest("requesterId is required");
        }
        if (!StringUtils.hasText(title)) {
            throw RestException.badRequest("title is required");
        }

        List<CreateApprovalRequest.StepInput> frozenSteps = normalizeLifecyclePlanSteps(plan.frozenSteps());
        if (plan.flowType() == ApprovalFlowType.PARALLEL_ALL
                && frozenSteps.stream()
                .anyMatch(step -> requesterId.equals(step.approverId()))) {
            throw RestException.conflict("LIFECYCLE_APPROVAL_REQUESTER_ASSIGNEE_CONFLICT");
        }
        ApprovalRequest request = new ApprovalRequest();
        request.setTargetType(plan.targetType());
        request.setTargetId(plan.targetId());
        request.setActionType(effectivePlanAction);
        request.setTitle(title.trim());
        request.setRequesterId(requesterId);
        request.setDescription(description);
        request.setPayloadJson(payloadAfterFlush);
        request.setFlowType(plan.flowType());
        request.setTemplateId(plan.templateId());
        request.setTemplateVersion(plan.templateVersion());
        request.setApprovalRound(nextApprovalRound(plan.targetType(), plan.targetId(), effectivePlanAction));
        request.setStatus(ApprovalStatus.PENDING);
        request.setCurrentStep(plan.flowType() == ApprovalFlowType.PARALLEL_ALL ? 0 : 1);
        request.setExpiresAt(Instant.now().plus(slaPolicyService.slaFor(request)));

        int stepNumber = 1;
        for (CreateApprovalRequest.StepInput input : frozenSteps) {
            ApprovalStep step = new ApprovalStep();
            step.setRequest(request);
            step.setStepNumber(stepNumber++);
            step.setApprovalRound(request.getApprovalRound());
            step.setFlowType(effectiveFlowType(request));
            step.setApproverId(input.approverId());
            step.setApproverRole(input.approverRole());
            step.setDecision(ApprovalDecision.PENDING);
            request.getSteps().add(step);
        }

        ApprovalRequest saved = requestRepository.saveAndFlush(request);
        governanceService.record(saved, null, ApprovalStatus.PENDING, requesterId, "Approval created");
        notifyCurrentStep(saved);
        auditBuilderService.log(
                "approval_request",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.APPROVAL_REQUEST,
                "Заявка на согласование создана",
                null,
                saved);
        return toDto(saved);
    }

    private LifecycleApprovalStartPlan lifecyclePlan(
            ApprovalTargetType targetType,
            UUID targetId,
            ApprovalActionType actionType,
            ApprovalRequest reusableRequest,
            List<CreateApprovalRequest.StepInput> frozenSteps,
            LifecycleApprovalRoutePolicy.Reason failure) {
        return new LifecycleApprovalStartPlan(
                targetType,
                targetId,
                actionType,
                reusableRequest,
                frozenSteps,
                failure);
    }

    private LifecycleApprovalStartPlan lifecyclePlan(
            ApprovalTargetType targetType,
            UUID targetId,
            ApprovalActionType actionType,
            ApprovalRequest reusableRequest,
            List<CreateApprovalRequest.StepInput> frozenSteps,
            LifecycleApprovalRoutePolicy.Reason failure,
            ApprovalFlowType flowType,
            UUID templateId,
            Long templateVersion) {
        return new LifecycleApprovalStartPlan(
                targetType, targetId, actionType, reusableRequest, frozenSteps, failure,
                flowType, templateId, templateVersion);
    }

    private LifecycleApprovalRoutePolicy.Reason lifecycleReuseFailure(
            ApprovalRequest request,
            ApprovalTargetType targetType,
            UUID targetId,
            ApprovalActionType actionType) {
        if (request == null
                || request.isDeleted()
                || request.getStatus() != ApprovalStatus.PENDING
                || request.isExecuted()
                || request.getCompletedAt() != null
                || (request.getExpiresAt() != null && !request.getExpiresAt().isAfter(Instant.now()))
                || effectiveTargetType(request) != targetType
                || !Objects.equals(effectiveTargetId(request), targetId)
                || effectiveActionType(request.getActionType()) != actionType) {
            return LifecycleApprovalRoutePolicy.Reason.REQUEST_STATUS_INCONSISTENT;
        }
        LifecycleApprovalRoutePolicy.ValidationResult runtime =
                lifecycleApprovalRoutePolicy.validateRuntime(normalizedLifecycleRuntimeView(request));
        return runtime.valid() ? LifecycleApprovalRoutePolicy.Reason.VALID : runtime.reason();
    }

    private ApprovalRequest normalizedLifecycleRuntimeView(ApprovalRequest request) {
        if (request.getTargetType() != null
                && request.getTargetId() != null
                && request.getActionType() != null) {
            return request;
        }
        ApprovalRequest normalized = new ApprovalRequest();
        normalized.setTargetType(effectiveTargetType(request));
        normalized.setTargetId(effectiveTargetId(request));
        normalized.setActionType(effectiveActionType(request.getActionType()));
        normalized.setRequesterId(request.getRequesterId());
        normalized.setStatus(request.getStatus());
        normalized.setCurrentStep(request.getCurrentStep());
        normalized.setFlowType(effectiveFlowType(request));
        normalized.setApprovalRound(request.getApprovalRound());
        normalized.setSteps(request.getSteps() == null
                ? new ArrayList<>()
                : new ArrayList<>(request.getSteps()));
        return normalized;
    }

    private void cancelLifecyclePending(ApprovalRequest request, UUID changedBy, String reason) {
        request.setStatus(ApprovalStatus.CANCELLED);
        request.setCompletedAt(Instant.now());
        request.setFailureReason(reason);
        requestRepository.saveAndFlush(request);
        governanceService.record(
                request,
                ApprovalStatus.PENDING,
                ApprovalStatus.CANCELLED,
                changedBy,
                reason);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void cancelPendingLifecycleApproval(
            ApprovalTargetType targetType, UUID targetId, String reason) {
        if (targetId == null
                || !lifecycleApprovalRoutePolicy.supports(targetType, ApprovalActionType.APPROVE)) {
            throw RestException.badRequest("Unsupported lifecycle approval target/action");
        }
        lockApprovalTargetAction(targetType.name(), targetId, ApprovalActionType.APPROVE);
        UUID changedBy = scopeAccessService.currentUserIdOrNull();
        requestRepository.findAllPendingByTargetAndAction(
                        targetType.name(), targetId,
                        ApprovalActionType.APPROVE.name(), ApprovalStatus.PENDING.name())
                .forEach(request -> cancelLifecyclePending(request, changedBy, reason));
    }

    private void requireValidLifecyclePlanIdentity(LifecycleApprovalStartPlan plan) {
        if (plan == null) {
            throw RestException.badRequest("Lifecycle approval plan is required");
        }
        if (plan.targetId() == null
                || !lifecycleApprovalRoutePolicy.supports(
                plan.targetType(), effectiveActionType(plan.actionType()))) {
            throw RestException.badRequest("Unsupported lifecycle approval plan");
        }
    }

    private List<CreateApprovalRequest.StepInput> normalizeLifecyclePlanSteps(
            List<CreateApprovalRequest.StepInput> frozenSteps) {
        if (frozenSteps == null || frozenSteps.isEmpty()) {
            throw RestException.conflict("APPROVAL_TEMPLATE_STEPS_INVALID");
        }
        List<CreateApprovalRequest.StepInput> normalized = new ArrayList<>(frozenSteps.size());
        Set<UUID> explicitApprovers = new java.util.HashSet<>();
        for (CreateApprovalRequest.StepInput input : frozenSteps) {
            if (input == null) {
                throw RestException.conflict("APPROVAL_TEMPLATE_STEPS_INVALID");
            }
            String role = input.approverRole();
            if (role != null && !StringUtils.hasText(role)) {
                throw RestException.conflict("APPROVAL_TEMPLATE_STEPS_INVALID");
            }
            if ((input.approverId() == null) == (role == null)) {
                throw RestException.conflict("APPROVAL_TEMPLATE_STEPS_INVALID");
            }
            if (input.approverId() != null && !explicitApprovers.add(input.approverId())) {
                throw RestException.conflict("APPROVAL_TEMPLATE_STEPS_INVALID");
            }
            normalized.add(new CreateApprovalRequest.StepInput(input.approverId(), role));
        }
        return List.copyOf(normalized);
    }

    private ApprovalActionType effectiveActionType(ApprovalActionType actionType) {
        return actionType == null ? ApprovalActionType.APPROVE : actionType;
    }

    private ApprovalFlowType effectiveFlowType(ApprovalRequest request) {
        return request.getFlowType() == null
                ? ApprovalFlowType.SEQUENTIAL
                : request.getFlowType();
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
        MaintenanceScheduleApprovalBinding calculationBinding =
                resolveCalculationBinding(
                        targetType,
                        documentId,
                        effectiveActionType,
                        effectiveRequesterId);
        List<ApprovalRequest> pendingApprovals = findPendingApprovals(
                targetType, normalizedType, documentId, effectiveActionType);
        ApprovalRequest reusable = null;
        if (!pendingApprovals.isEmpty()) {
            String currentPayload = currentScopePayload(targetType, documentId);
            for (ApprovalRequest candidate : pendingApprovals) {
                if (calculationBinding != null) {
                    ApprovalRouteSnapshot currentRoute =
                            routeResolver.resolveRouteSnapshot(candidate);
                    boolean tupleMatches =
                            maintenanceScheduleApprovalBindingService.matches(
                                    candidate, calculationBinding);
                    if (tupleMatches
                            && maintenanceScheduleApprovalBindingService
                            .routeMatches(candidate, currentRoute)
                            && reusable == null) {
                        reusable = candidate;
                        continue;
                    }
                    ApprovalResolutionCode resolution = tupleMatches
                            ? ApprovalResolutionCode.ROUTE_CHANGED
                            : ApprovalResolutionCode.NEW_REVISION;
                    maintenanceScheduleApprovalBindingService.supersede(
                            candidate,
                            resolution,
                            resolution == ApprovalResolutionCode.ROUTE_CHANGED
                                    ? "PPR_APPROVAL_SUPERSEDED_ROUTE_CHANGED"
                                    : "PPR_APPROVAL_SUPERSEDED_NEW_REVISION",
                            effectiveRequesterId);
                    requestRepository.saveAndFlush(candidate);
                    governanceService.record(
                            candidate,
                            ApprovalStatus.PENDING,
                            ApprovalStatus.SUPERSEDED,
                            effectiveRequesterId,
                            candidate.getFailureReason());
                    continue;
                }
                boolean payloadStale = currentPayload != null
                        && !Objects.equals(candidate.getPayloadJson(), currentPayload);
                boolean routeStale = routeStale(targetType, candidate);
                boolean duplicate = reusable != null;
                if (!payloadStale && !routeStale && !duplicate) {
                    reusable = candidate;
                    continue;
                }

                candidate.setStatus(ApprovalStatus.CANCELLED);
                candidate.setCompletedAt(Instant.now());
                String cancellationReason;
                if (routeStale) {
                    cancellationReason = targetType == ApprovalTargetType.PLANNED_SHUTDOWN
                            ? "NONCANONICAL_PLANNED_SHUTDOWN_ROUTE"
                            : "NONCANONICAL_REPAIR_CAMPAIGN_ROUTE";
                    candidate.setFailureReason(cancellationReason);
                    log.warn("Cancelling noncanonical approval id={} targetType={} reason={}",
                            candidate.getId(), targetType, cancellationReason);
                } else if (payloadStale) {
                    cancellationReason = supersededScopeMessage(targetType);
                } else {
                    cancellationReason = "DUPLICATE_PENDING_APPROVAL";
                    candidate.setFailureReason(cancellationReason);
                }
                requestRepository.saveAndFlush(candidate);
                governanceService.record(candidate, ApprovalStatus.PENDING, ApprovalStatus.CANCELLED,
                        effectiveRequesterId, cancellationReason);
            }
        }
        if (reusable != null) {
            return toDtoAfterReuse(reusable);
        }
        return createNewApproval(
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
        );
    }

    private String currentScopePayload(ApprovalTargetType targetType, UUID documentId) {
        if (targetType == ApprovalTargetType.PLANNED_SHUTDOWN) {
            return plannedShutdownApprovalPayload(loadPlannedShutdownApprovalSnapshot(documentId));
        }
        if (targetType == ApprovalTargetType.REPAIR_CAMPAIGN) {
            return repairCampaignApprovalPayload(loadRepairCampaignApprovalSnapshot(documentId));
        }
        return null;
    }

    private boolean routeStale(ApprovalTargetType targetType, ApprovalRequest request) {
        if (request == null) {
            return false;
        }
        return lifecycleRouteStale(request);
    }

    private boolean lifecycleRouteStale(ApprovalRequest request) {
        return lifecycleApprovalRoutePolicy.supports(
                effectiveTargetType(request), request.getActionType())
                && !lifecycleApprovalRoutePolicy
                .validateRuntime(normalizedLifecycleRuntimeView(request))
                .valid();
    }

    private boolean hasActionableRoute(ApprovalRequest request) {
        return !routeStale(effectiveTargetType(request), request)
                && (!isBoundApprovalFirstRequest(request)
                    || maintenanceScheduleApprovalBindingService.routeMatches(
                            request, routeResolver.resolveRouteSnapshot(request)));
    }

    private void assertActionableRoute(ApprovalRequest request) {
        ApprovalTargetType targetType = effectiveTargetType(request);
        if (isBoundApprovalFirstRequest(request)
                && !maintenanceScheduleApprovalBindingService.routeMatches(
                        request, routeResolver.resolveRouteSnapshot(request))) {
            throw RestException.conflict("PPR_APPROVAL_ROUTE_STALE");
        }
        if (routeStale(targetType, request)) {
            if (targetType == ApprovalTargetType.PLANNED_SHUTDOWN) {
                throw RestException.conflict("PLANNED_SHUTDOWN_APPROVAL_ROUTE_STALE");
            }
            throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_ROUTE_STALE");
        }
    }

    private String supersededScopeMessage(ApprovalTargetType targetType) {
        if (targetType == ApprovalTargetType.REPAIR_CAMPAIGN) {
            return "Superseded by a newer repair campaign scope snapshot";
        }
        return "Superseded by a newer planned shutdown scope snapshot";
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

    private List<ApprovalRequest> findPendingApprovals(ApprovalTargetType targetType,
                                                       String normalizedType,
                                                       UUID documentId,
                                                       ApprovalActionType actionType) {
        if (targetType == ApprovalTargetType.REPAIR_CAMPAIGN) {
            return requestRepository.findAllPendingByTargetAndAction(
                    normalizedType, documentId, actionType.name(), ApprovalStatus.PENDING.name());
        }
        return requestRepository.findFirstPendingByTargetAndAction(
                        normalizedType, documentId, actionType.name(), ApprovalStatus.PENDING.name())
                .stream()
                .toList();
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
        ApprovalRequest request = lockLifecycleMutationRequest(requestId);
        if (effectiveFlowType(request) == ApprovalFlowType.PARALLEL_ALL) {
            throw RestException.conflict("PARALLEL_APPROVAL_RETURN_NOT_SUPPORTED");
        }
        UUID actorId = effectiveDecisionActor(returnRequest == null ? null : returnRequest.approverId());
        expireIfNeeded(request);
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw RestException.conflict("Request is not pending: " + request.getStatus());
        }
        assertActionableRoute(request);
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
        ApprovalRequest request = lockLifecycleMutationRequest(requestId);
        approvalScopeService.assertCanCancelApproval(request);
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw RestException.conflict("Request is not pending: " + request.getStatus());
        }
        assertActionableRoute(request);
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
        ApprovalRequest request = lockLifecycleMutationRequest(requestId);
        UUID actorId = effectiveDecisionActor(decision);
        String decisionComment = decision == null ? null : decision.comment();
        if (isIdempotentApprovalFirstRetry(request, outcome, actorId)) {
            return toDto(request);
        }
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
        assertActionableRoute(request);
        if (effectiveFlowType(request) == ApprovalFlowType.PARALLEL_ALL) {
            return applyParallelDecision(request, expectedStepId, decision, outcome, actorId);
        }
        if (outcome == ApprovalDecision.REJECTED && !StringUtils.hasText(decisionComment)) {
            throw RestException.badRequest("comment is required for rejection");
        }
        ApprovalStep current = currentStepOrThrow(request);
        if (expectedStepId != null && !expectedStepId.equals(current.getId())) {
            throw RestException.conflict("Only current pending step can be acted on");
        }
        boolean delegateApprover = assertCanActOnCurrentStep(request, current, actorId, outcome) != null;
        current.setDecision(outcome);
        current.setDecidedById(actorId);
        current.setDelegatedForId(delegateApprover ? current.getApproverId() : null);
        current.setDecidedAt(Instant.now());
        current.setComment(decisionComment);

        boolean isTerminal = false;
        if (outcome == ApprovalDecision.REJECTED) {
            request.setStatus(ApprovalStatus.REJECTED);
            request.setCompletedAt(Instant.now());
            isTerminal = true;
        } else {
            int next = request.getCurrentStep() + 1;
            boolean hasNext = request.getSteps().stream()
                    .anyMatch(step -> !step.isDeleted() && step.getStepNumber() == next);
            if (hasNext) {
                request.setCurrentStep(next);
            } else {
                validatePersistedLifecycleCompletion(request);
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
            governanceService.record(saved, oldStatus, saved.getStatus(), actorId, decisionComment);
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

    private boolean isIdempotentApprovalFirstRetry(
            ApprovalRequest request,
            ApprovalDecision outcome,
            UUID actorId) {
        if (!isBoundApprovalFirstRequest(request)
                || request.getStatus() != ApprovalStatus.APPROVED
                || outcome != ApprovalDecision.APPROVED
                || !request.isExecuted()) {
            return false;
        }
        boolean participated = request.getSteps().stream()
                .filter(step -> !step.isDeleted())
                .filter(step ->
                        step.getDecision() == ApprovalDecision.APPROVED)
                .anyMatch(step ->
                        Objects.equals(step.getDecidedById(), actorId));
        if (!participated) {
            throw RestException.forbidden(
                    "Only an approver from the completed PPR route "
                            + "can retry this approval");
        }
        return true;
    }

    private ApprovalRequestDto applyParallelDecision(
            ApprovalRequest request,
            UUID expectedStepId,
            DecisionRequest decision,
            ApprovalDecision outcome,
            UUID actorId) {
        String decisionComment = decision == null ? null : decision.comment();
        if (outcome == ApprovalDecision.REJECTED && !StringUtils.hasText(decisionComment)) {
            throw RestException.badRequest("comment is required for rejection");
        }
        ApprovalStep task = currentRoundSteps(request).stream()
                .filter(step -> expectedStepId == null
                        ? Objects.equals(step.getApproverId(), actorId)
                        : Objects.equals(step.getId(), expectedStepId))
                .findFirst()
                .orElseThrow(() -> RestException.conflict("Parallel approval task not found in current round"));
        if (!Objects.equals(task.getApproverId(), actorId)) {
            throw RestException.forbidden("Only assigned approver can act on this parallel task");
        }
        if (task.getDecision() != ApprovalDecision.PENDING) {
            throw RestException.conflict("Approval task is already completed: " + task.getDecision());
        }
        approvalScopeService.assertCanDecideApproval(request, task);

        Instant actedAt = Instant.now();
        task.setDecision(outcome);
        task.setDecidedById(actorId);
        task.setDelegatedForId(null);
        task.setDecidedAt(actedAt);
        task.setComment(decisionComment);

        ApprovalStatus oldStatus = request.getStatus();
        boolean terminal = false;
        if (outcome == ApprovalDecision.REJECTED) {
            currentRoundSteps(request).stream()
                    .filter(step -> step != task && step.getDecision() == ApprovalDecision.PENDING)
                    .forEach(step -> {
                        step.setDecision(ApprovalDecision.CANCELLED);
                        step.setDecidedAt(actedAt);
                        step.setComment("Cancelled after rejection by another approver");
                    });
            request.setStatus(ApprovalStatus.REJECTED);
            request.setCompletedAt(actedAt);
            terminal = true;
        } else {
            boolean allApproved = currentRoundSteps(request).stream()
                    .allMatch(step -> step.getDecision() == ApprovalDecision.APPROVED);
            if (allApproved) {
                validatePersistedLifecycleCompletion(request);
                request.setStatus(ApprovalStatus.APPROVED);
                request.setCompletedAt(actedAt);
                terminal = true;
            }
        }

        if (terminal) {
            executeTerminalAction(request, outcome);
        }
        ApprovalRequest saved = requestRepository.save(request);
        governanceService.record(
                saved,
                oldStatus,
                saved.getStatus(),
                actorId,
                decisionComment,
                outcome == ApprovalDecision.APPROVED
                        ? ApprovalActionType.APPROVE
                        : ApprovalActionType.REJECT);
        if (terminal) {
            notifyFinalDecision(saved, outcome);
        }
        auditBuilderService.log(
                "approval_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.APPROVAL_REQUEST,
                "Parallel approval task decided",
                null,
                saved);
        return toDto(saved);
    }

    private List<ApprovalStep> currentRoundSteps(ApprovalRequest request) {
        return request.getSteps().stream()
                .filter(step -> !step.isDeleted())
                .filter(step -> step.getApprovalRound() == request.getApprovalRound())
                .toList();
    }

    private ApprovalRequest lockLifecycleMutationRequest(UUID requestId) {
        List<LifecycleDecisionIdentity> identities = jdbcTemplate.query(
                """
                SELECT COALESCE(target_type, document_type) AS target_type,
                       COALESCE(target_id, document_id) AS target_id,
                       COALESCE(action_type, 'APPROVE') AS action_type
                FROM approval_requests
                WHERE id = ? AND is_deleted = false
                """,
                (rs, rowNum) -> new LifecycleDecisionIdentity(
                        ApprovalTargetType.fromDocumentType(rs.getString("target_type")),
                        rs.getObject("target_id", UUID.class),
                        ApprovalActionType.valueOf(rs.getString("action_type"))),
                requestId);
        if (identities.isEmpty()) {
            return lockRequestForUpdate(requestId);
        }
        LifecycleDecisionIdentity identity = identities.getFirst();
        if (!lifecycleApprovalRoutePolicy.supports(identity.targetType(), identity.actionType())
                || identity.targetId() == null) {
            return lockRequestForUpdate(requestId);
        }

        lockLifecycleDomainRow(identity.targetType(), identity.targetId());
        lockApprovalTargetAction(identity.targetType().name(), identity.targetId(), identity.actionType());
        return requestRepository.findByIdAndIsDeletedFalseForUpdate(requestId)
                .orElseThrow(() -> RestException.notFound("Approval request not found: " + requestId));
    }

    private ApprovalRequest lockRequestForUpdate(UUID requestId) {
        return requestRepository.findByIdAndIsDeletedFalseForUpdate(requestId)
                .or(() -> requestRepository.findByIdAndIsDeletedFalse(requestId))
                .orElseThrow(() -> RestException.notFound("Approval request not found: " + requestId));
    }

    private void lockLifecycleDomainRow(ApprovalTargetType targetType, UUID targetId) {
        String sql = switch (targetType) {
            case REPAIR_CAMPAIGN ->
                    "SELECT id FROM repair_campaigns WHERE id = ? AND is_deleted = false FOR UPDATE";
            case PLANNED_SHUTDOWN ->
                    "SELECT id FROM planned_shutdowns WHERE id = ? AND is_deleted = false FOR UPDATE";
            default -> throw RestException.badRequest("Unsupported lifecycle approval target/action");
        };
        List<UUID> locked = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                targetId);
        if (locked.isEmpty()) {
            throw RestException.notFound("Lifecycle approval target not found: " + targetId);
        }
    }

    private record LifecycleDecisionIdentity(
            ApprovalTargetType targetType, UUID targetId, ApprovalActionType actionType) {}

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
            approvalScopeService.assertCanDecideApproval(request, current, current.getApproverId());
            return current.getApproverId();
        }
        if (canActorActOnStep(current, actorId)) {
            approvalScopeService.assertCanDecideApproval(request, current);
            return null;
        }
        throw RestException.forbidden("Only designated approver can act on this step");
    }

    private UUID assertCanActOnCurrentStep(ApprovalRequest request,
                                           ApprovalStep current,
                                           UUID actorId,
                                           ApprovalDecision outcome) {
        if (isBoundApprovalFirstRequest(request)) {
            if (Objects.equals(request.getRequesterId(), actorId)) {
                throw RestException.forbidden(
                        "Requester cannot approve or reject this PPR calculation");
            }
            boolean repeatedActor = request.getSteps().stream()
                    .filter(step -> step != current)
                    .filter(step -> step.getDecision() == ApprovalDecision.APPROVED)
                    .anyMatch(step -> Objects.equals(step.getDecidedById(), actorId));
            if (repeatedActor) {
                throw RestException.forbidden(
                        "An actor cannot approve more than one PPR approval step");
            }
            return assertCanActOnCurrentStep(request, current, actorId);
        }
        if (!isLifecycleApproval(request)) {
            return assertCanActOnCurrentStep(request, current, actorId);
        }
        if (!matchesAuthenticatedPrincipal(actorId)) {
            throw RestException.forbidden(
                    "Lifecycle decision actor must match the authenticated principal");
        }
        boolean assignmentSatisfied = lifecycleAssignmentSatisfied(current, actorId);
        LifecycleApprovalRoutePolicy.ValidationResult decision = lifecycleApprovalRoutePolicy.validateDecision(
                normalizedLifecycleRuntimeView(request),
                current,
                actorId,
                outcome,
                assignmentSatisfied);
        if (!decision.valid()) {
            throw mapDecisionFailure(decision.reason());
        }
        approvalScopeService.assertCanDecideApproval(request, current);
        return null;
    }

    private RestException mapDecisionFailure(LifecycleApprovalRoutePolicy.Reason reason) {
        return switch (reason) {
            case REQUESTER_DECISION -> RestException.forbidden(
                    "Requester cannot approve or reject this lifecycle approval");
            case REPEATED_APPROVING_ACTOR -> RestException.forbidden(
                    "An actor cannot approve more than one lifecycle approval step");
            case ACTOR_INELIGIBLE -> RestException.forbidden(
                    "Only the persisted lifecycle step assignee can act on this step");
            case DECISION_ACTOR_MISSING -> RestException.badRequest(
                    "Authenticated decision actor is required");
            case CURRENT_STEP_ONLY -> RestException.conflict(
                    "Only current pending step can be acted on");
            default -> RestException.conflict(
                    "Lifecycle approval decision is not actionable: " + reason);
        };
    }

    private void validatePersistedLifecycleCompletion(ApprovalRequest request) {
        if (!isLifecycleApproval(request)) {
            return;
        }
        LifecycleApprovalRoutePolicy.ValidationResult completion = lifecycleApprovalRoutePolicy
                .validateCompletion(normalizedLifecycleRuntimeView(request));
        if (!completion.valid()) {
            throw mapLifecycleCompletionFailure(request, completion.reason());
        }
    }

    private RestException mapLifecycleCompletionFailure(ApprovalRequest request,
                                                        LifecycleApprovalRoutePolicy.Reason reason) {
        if (effectiveTargetType(request) == ApprovalTargetType.REPAIR_CAMPAIGN) {
            if (reason == LifecycleApprovalRoutePolicy.Reason.RUNTIME_INCOMPLETE
                    || reason == LifecycleApprovalRoutePolicy.Reason.DECISION_ACTOR_MISSING) {
                return RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_INCOMPLETE_DISCIPLINE_ROUTE");
            }
            if (reason == LifecycleApprovalRoutePolicy.Reason.REQUESTER_DECISION
                    || reason == LifecycleApprovalRoutePolicy.Reason.REPEATED_APPROVING_ACTOR) {
                return RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_SEPARATION_OF_DUTY_FAILURE");
            }
            return RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_ROUTE_STALE");
        }
        return RestException.conflict("PLANNED_SHUTDOWN_APPROVAL_ROUTE_STALE");
    }

    private void executeTerminalAction(ApprovalRequest request, ApprovalDecision outcome) {
        if (request.isExecuted()) {
            return;
        }
        boolean lifecycleFinalization = isLifecycleApproval(request);
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
            if (lifecycleFinalization
                    || isBoundApprovalFirstRequest(request)
                    || shouldPropagateFinalizerFailure(ex)) {
                throw ex;
            }
            request.setStatus(ApprovalStatus.FAILED);
            request.setCompletedAt(Instant.now());
            request.setFailureReason(ex.getMessage());
            request.setResultJson(null);
        }
    }

    private boolean shouldPropagateFinalizerFailure(RuntimeException ex) {
        if (ex instanceof TransactionException
                || ex instanceof DataAccessException
                || ex instanceof PersistenceException) {
            return true;
        }
        try {
            return TransactionAspectSupport.currentTransactionStatus().isRollbackOnly();
        } catch (NoTransactionException ignored) {
            return false;
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
        return userRepository.findByIdAndIsDeletedFalse(actorId)
                .filter(this::isActiveUser)
                .filter(user -> hasRole(user, approverRole)
                        || currentAuthenticationHasRole(approverRole))
                .isPresent();
    }

    private boolean isLifecycleApproval(ApprovalRequest request) {
        return request != null && lifecycleApprovalRoutePolicy.supports(
                effectiveTargetType(request), effectiveActionType(request.getActionType()));
    }

    private MaintenanceScheduleApprovalBinding resolveCalculationBinding(
            ApprovalTargetType targetType,
            UUID targetId,
            ApprovalActionType actionType,
            UUID requesterId) {
        if (maintenanceScheduleApprovalBindingService == null) {
            return null;
        }
        return maintenanceScheduleApprovalBindingService.resolveForSubmission(
                targetType, targetId, actionType, requesterId);
    }

    private void bindCalculation(
            ApprovalRequest request,
            MaintenanceScheduleApprovalBinding binding) {
        if (maintenanceScheduleApprovalBindingService != null) {
            maintenanceScheduleApprovalBindingService.bind(request, binding);
        }
    }

    private boolean isBoundApprovalFirstRequest(ApprovalRequest request) {
        return maintenanceScheduleApprovalBindingService != null
                && maintenanceScheduleApprovalBindingService
                .isBoundApprovalFirstRequest(request);
    }

    private boolean lifecycleAssignmentSatisfied(ApprovalStep step, UUID actorId) {
        if (step == null || actorId == null) {
            return false;
        }
        if (step.getApproverId() != null) {
            return step.getApproverId().equals(actorId);
        }
        return lifecycleRoleSatisfied(actorId, step.getApproverRole());
    }

    private boolean lifecycleRoleSatisfied(UUID actorId, String configuredRole) {
        return isActiveUserWithRole(actorId, configuredRole)
                || (matchesAuthenticatedPrincipal(actorId)
                && currentAuthenticationHasRole(configuredRole));
    }

    private boolean canCurrentPrincipalActOnStep(ApprovalStep step) {
        if (step == null) {
            return false;
        }
        return canActorActOnStep(step, currentAuthenticatedActorId());
    }

    private UUID currentAuthenticatedActorId() {
        UUID actorId = scopeAccessService.currentUserIdOrNull();
        return actorId != null
                ? actorId
                : scopeAccessService.currentEmployeeId().orElse(null);
    }

    private boolean lifecycleDecisionIsActionable(ApprovalRequest request,
                                                  ApprovalStep current,
                                                  UUID actorId,
                                                  ApprovalDecision outcome) {
        if (actorId == null
                || current == null
                || !matchesAuthenticatedPrincipal(actorId)
                || lifecycleDecisionExpired(request)) {
            return false;
        }
        boolean assignmentSatisfied = lifecycleAssignmentSatisfied(current, actorId);
        LifecycleApprovalRoutePolicy.ValidationResult decision = lifecycleApprovalRoutePolicy.validateDecision(
                normalizedLifecycleRuntimeView(request),
                current,
                actorId,
                outcome,
                assignmentSatisfied);
        return decision.valid() && hasDecisionScope(request, current);
    }

    private boolean lifecycleDecisionExpired(ApprovalRequest request) {
        return request != null
                && request.getExpiresAt() != null
                && request.getExpiresAt().isBefore(Instant.now());
    }

    private boolean hasDecisionScope(ApprovalRequest request, ApprovalStep current) {
        try {
            approvalScopeService.assertCanDecideApproval(request, current);
            return true;
        } catch (AccessDeniedException ignored) {
            return false;
        }
    }

    private boolean hasCancellationPermission(ApprovalRequest request) {
        try {
            approvalScopeService.assertCanCancelApproval(request);
            return true;
        } catch (AccessDeniedException ignored) {
            return false;
        }
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
                .filter(user -> hasRole(user, roleCode))
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
        request.setActionType(actionType);
        MaintenanceScheduleApprovalBinding calculationBinding =
                resolveCalculationBinding(
                        targetType,
                        targetId,
                        effectiveActionType(actionType),
                        requesterId);
        bindCalculation(request, calculationBinding);
        if (targetType == ApprovalTargetType.PLANNED_SHUTDOWN) {
            PlannedShutdownApprovalSnapshot snapshot = loadPlannedShutdownApprovalSnapshot(targetId);
            request.setPayloadJson(plannedShutdownApprovalPayload(snapshot));
        }
        if (targetType == ApprovalTargetType.REPAIR_CAMPAIGN) {
            request.setPayloadJson(repairCampaignApprovalPayload(loadRepairCampaignApprovalSnapshot(targetId)));
        }
        if (request.getExpiresAt() == null) {
            request.setExpiresAt(Instant.now().plus(slaPolicyService.slaFor(request)));
        }

        boolean repairCampaignApproval = targetType == ApprovalTargetType.REPAIR_CAMPAIGN
                && (actionType == null || actionType == ApprovalActionType.APPROVE);
        boolean plannedShutdownApproval = targetType == ApprovalTargetType.PLANNED_SHUTDOWN
                && (actionType == null || actionType == ApprovalActionType.APPROVE);
        boolean domainValidatedApproval = repairCampaignApproval || plannedShutdownApproval;
        List<CreateApprovalRequest.StepInput> suppliedSteps =
                domainValidatedApproval || calculationBinding != null
                ? List.of()
                : normalizeStepInputs(steps);
        ApprovalRouteSnapshot routeSnapshot = suppliedSteps.isEmpty()
                ? routeResolver.resolveRouteSnapshot(request)
                : ApprovalRouteSnapshot.sequential(suppliedSteps);
        if (routeSnapshot == null) {
            routeSnapshot = ApprovalRouteSnapshot.sequential(routeResolver.resolveRoute(request));
        }
        List<CreateApprovalRequest.StepInput> effectiveSteps = normalizeStepInputs(routeSnapshot.steps());
        if (effectiveSteps.isEmpty()) {
            if (calculationBinding != null) {
                throw new RestException(
                        "Active PPR_PLAN_APPROVAL template with at least one "
                                + "configured approver step is required",
                        org.springframework.http.HttpStatus.CONFLICT,
                        "PPR_APPROVAL_TEMPLATE_ROUTE_NOT_CONFIGURED");
            }
            if (repairCampaignApproval) {
                throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_ROUTE_NOT_CONFIGURED");
            }
            if (plannedShutdownApproval) {
                throw RestException.conflict("PLANNED_SHUTDOWN_APPROVAL_ROUTE_NOT_CONFIGURED");
            }
            throw RestException.badRequest("At least one approval step is required");
        }
        request.setFlowType(routeSnapshot.flowType());
        request.setTemplateId(routeSnapshot.templateId());
        request.setTemplateVersion(routeSnapshot.templateVersion());
        if (calculationBinding != null) {
            maintenanceScheduleApprovalBindingService.bindResolvedRoute(
                    request, routeSnapshot, effectiveSteps);
        }
        ApprovalActionType roundAction = effectiveActionType(actionType);
        lockApprovalTargetAction(targetType.name(), targetId, roundAction);
        request.setApprovalRound(nextApprovalRound(targetType, targetId, roundAction));
        request.setCurrentStep(routeSnapshot.flowType() == ApprovalFlowType.PARALLEL_ALL ? 0 : 1);
        if (domainValidatedApproval) {
            LifecycleApprovalRoutePolicy.ValidationResult validation = validateLifecycleInputs(
                    targetType, actionType, routeSnapshot.flowType(), effectiveSteps);
            if (!validation.valid()) {
                log.warn("Lifecycle approval route is not configured: targetType={} targetId={} reason={}",
                        targetType, targetId, validation.reason());
                throw RestException.conflict(repairCampaignApproval
                        ? "REPAIR_CAMPAIGN_APPROVAL_ROUTE_NOT_CONFIGURED"
                        : "PLANNED_SHUTDOWN_APPROVAL_ROUTE_NOT_CONFIGURED");
            }
        }

        int idx = 1;
        for (CreateApprovalRequest.StepInput input : effectiveSteps) {
            if (input.approverId() == null && !StringUtils.hasText(input.approverRole())) {
                throw RestException.badRequest("approverId or approverRole is required for each step");
            }
            ApprovalStep step = new ApprovalStep();
            step.setRequest(request);
            step.setStepNumber(idx++);
            step.setApprovalRound(request.getApprovalRound());
            step.setFlowType(effectiveFlowType(request));
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

    private int nextApprovalRound(
            ApprovalTargetType targetType,
            UUID targetId,
            ApprovalActionType actionType) {
        Integer current = requestRepository.findMaxApprovalRound(
                targetType.name(), targetId, effectiveActionType(actionType).name());
        return current == null ? 1 : Math.addExact(current, 1);
    }

    private LifecycleApprovalRoutePolicy.ValidationResult validateLifecycleInputs(
            ApprovalTargetType targetType,
            ApprovalActionType actionType,
            ApprovalFlowType flowType,
            List<CreateApprovalRequest.StepInput> inputs) {
        ApprovalTemplate candidate = new ApprovalTemplate();
        candidate.setTargetType(targetType);
        candidate.setActionType(effectiveActionType(actionType));
        candidate.setFlowType(flowType);
        int order = 1;
        for (CreateApprovalRequest.StepInput input : inputs) {
            ApprovalTemplateStep step = new ApprovalTemplateStep();
            step.setTemplate(candidate);
            step.setStepOrder(order++);
            step.setApproverId(input.approverId());
            step.setApproverRole(input.approverRole());
            candidate.getSteps().add(step);
        }
        return lifecycleApprovalRoutePolicy.validateTemplate(candidate);
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
        if (effectiveFlowType(request) == ApprovalFlowType.PARALLEL_ALL) {
            currentRoundSteps(request).stream()
                    .filter(step -> step.getDecision() == ApprovalDecision.PENDING)
                    .forEach(step -> notifyStepApprovers(
                            step,
                            "Approval requested: " + request.getTitle(),
                            "Approval request " + request.getTitle() + " requires your decision.",
                            NotificationSeverity.INFO,
                            NotificationEventType.APPROVAL_REQUESTED,
                            NotificationEntityTypes.APPROVAL_REQUEST,
                            request.getId() == null ? null : request.getId().toString()));
            return;
        }
        request.getSteps().stream()
                .filter(step -> step.getStepNumber() == request.getCurrentStep())
                .filter(step -> step.getDecision() == ApprovalDecision.PENDING)
                .findFirst()
                .ifPresent(step -> notifyStepApprovers(
                        step,
                        "Approval requested: " + request.getTitle(),
                        "Approval request " + request.getTitle() + " requires your decision.",
                        NotificationSeverity.INFO,
                        NotificationEventType.APPROVAL_REQUESTED,
                        NotificationEntityTypes.APPROVAL_REQUEST,
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
        notificationService.notifyApprovalResult(
                request.getRequesterId(),
                "Approval " + decisionText + ": " + request.getTitle(),
                "Approval request " + request.getTitle() + " was " + decisionText + ".",
                request.getStatus() == ApprovalStatus.APPROVED ? NotificationSeverity.INFO : NotificationSeverity.WARNING,
                finalDecisionEvent(request, outcome),
                notificationEntityType(effectiveTargetType(request)),
                effectiveTargetId(request),
                request.getId()
        );
    }

    private void notifyReturned(ApprovalRequest request, int fromStep, int returnToStep) {
        String message = "Approval was returned to step " + returnToStep + " for correction.";
        notificationService.notifyApprovalResult(
                request.getRequesterId(),
                "Approval returned: " + request.getTitle(),
                message,
                NotificationSeverity.WARNING,
                NotificationEventType.APPROVAL_RETURNED_TO_REQUESTER,
                notificationEntityType(effectiveTargetType(request)),
                effectiveTargetId(request),
                request.getId()
        );
        request.getSteps().stream()
                .filter(step -> step.getStepNumber() == returnToStep)
                .findFirst()
                .ifPresent(step -> notifyStepApprovers(
                        step,
                        "Approval returned to your step: " + request.getTitle(),
                        message,
                        NotificationSeverity.INFO,
                        NotificationEventType.APPROVAL_RETURNED_TO_APPROVER,
                        NotificationEntityTypes.APPROVAL_REQUEST,
                        request.getId() == null ? null : request.getId().toString()
                ));
    }

    private void notifyStepApprovers(ApprovalStep step,
                                     String title,
                                     String message,
                                     NotificationSeverity severity,
                                     NotificationEventType eventType,
                                     String entityType,
                                     String entityId) {
        if (step.getApproverId() != null) {
            notificationService.notifyUser(step.getApproverId(), title, message, severity, eventType, entityType, entityId);
            return;
        }
        activeUsersWithRole(step.getApproverRole())
                .forEach(user -> notificationService.notifyUser(user.getId(), title, message, severity, eventType, entityType, entityId));
    }

    private NotificationEventType finalDecisionEvent(ApprovalRequest request, ApprovalDecision outcome) {
        return switch (request.getStatus()) {
            case FAILED -> NotificationEventType.APPROVAL_FAILED;
            case EXPIRED -> NotificationEventType.APPROVAL_EXPIRED;
            case CANCELLED -> NotificationEventType.APPROVAL_CANCELLED;
            case APPROVED -> NotificationEventType.APPROVAL_APPROVED;
            default -> outcome == ApprovalDecision.APPROVED
                    ? NotificationEventType.APPROVAL_APPROVED
                    : NotificationEventType.APPROVAL_REJECTED;
        };
    }

    private String notificationEntityType(ApprovalTargetType targetType) {
        if (targetType == null) {
            return NotificationEntityTypes.APPROVAL_REQUEST;
        }
        return switch (targetType) {
            case WORK_ORDER -> NotificationEntityTypes.WORK_ORDER;
            case PPR_PLAN -> NotificationEntityTypes.PPR_PLAN;
            case PROCUREMENT_REQUEST, PROCUREMENT -> "PROCUREMENT_REQUEST";
            case MAINTENANCE_BUDGET, BUDGET -> NotificationEntityTypes.MAINTENANCE_BUDGET;
            case MAINTENANCE_DUE_EVENT -> NotificationEntityTypes.MAINTENANCE_DUE_EVENT;
            case REPAIR_REQUEST -> NotificationEntityTypes.REPAIR_REQUEST;
            case MAINTENANCE_REGULATION -> "MAINTENANCE_REGULATION";
            case REGULATION_CHANGE_PROPOSAL -> "REGULATION_CHANGE_PROPOSAL";
            case ACTUAL_COST -> NotificationEntityTypes.ACTUAL_COST;
            case DEFECT_LIST -> "DEFECT_LIST";
            case PLANNED_SHUTDOWN -> NotificationEntityTypes.PLANNED_SHUTDOWN;
            case REPAIR_CAMPAIGN -> NotificationEntityTypes.REPAIR_CAMPAIGN;
            case EQUIPMENT_COMMISSIONING -> "EQUIPMENT_COMMISSIONING_ACT";
            default -> NotificationEntityTypes.APPROVAL_REQUEST;
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
                        userName(step.getDelegatedForId()),
                        step.getApprovalRound(),
                        userName(step.getApproverId())))
                .toList();
        ApprovalFlowType flowType = effectiveFlowType(request);
        UUID actorId = currentAuthenticatedActorId();
        ApprovalStep currentStep = request.getSteps().stream()
                .filter(step -> flowType == ApprovalFlowType.PARALLEL_ALL
                        ? step.getApprovalRound() == request.getApprovalRound()
                                && step.getDecision() == ApprovalDecision.PENDING
                                && Objects.equals(step.getApproverId(), actorId)
                        : step.getStepNumber() == request.getCurrentStep())
                .findFirst()
                .orElse(null);
        String targetTypeName = effectiveTargetTypeName(request);
        UUID targetId = effectiveTargetId(request);
        String targetUrl = targetUrl(targetTypeName, targetId);
        boolean staleRoute = routeStale(effectiveTargetType(request), request);
        boolean actionableRoute = !staleRoute;
        boolean lifecycleApproval = isLifecycleApproval(request);
        boolean canDecide = !lifecycleApproval
                && request.getStatus() == ApprovalStatus.PENDING
                && currentStep != null
                && currentStep.getDecision() == ApprovalDecision.PENDING
                && actionableRoute
                && canCurrentPrincipalActOnStep(currentStep);
        boolean canApprove = lifecycleApproval
                ? actionableRoute && lifecycleDecisionIsActionable(
                request, currentStep, actorId, ApprovalDecision.APPROVED)
                : canDecide;
        boolean canReject = lifecycleApproval
                ? actionableRoute && lifecycleDecisionIsActionable(
                request, currentStep, actorId, ApprovalDecision.REJECTED)
                : canDecide;
        boolean canCancel = request.getStatus() == ApprovalStatus.PENDING
                && actionableRoute
                && actorId != null
                && hasCancellationPermission(request);
        int approvedCount = (int) steps.stream()
                .filter(step -> step.decision() == ApprovalDecision.APPROVED).count();
        int pendingCount = (int) steps.stream()
                .filter(step -> step.decision() == ApprovalDecision.PENDING).count();
        int rejectedCount = (int) steps.stream()
                .filter(step -> step.decision() == ApprovalDecision.REJECTED).count();
        int cancelledCount = (int) steps.stream()
                .filter(step -> step.decision() == ApprovalDecision.CANCELLED).count();
        Set<String> allowedActions = new LinkedHashSet<>();
        if (canApprove) {
            allowedActions.add("APPROVE");
        }
        if (canReject) {
            allowedActions.add("REJECT");
        }
        if (canCancel) {
            allowedActions.add("CANCEL");
        }

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
                canApprove,
                canReject,
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
                request.getLastReturnComment(),
                request.getStatus() == ApprovalStatus.PENDING && actionableRoute,
                staleRoute,
                staleRoute ? "NONCANONICAL_ROUTE" : null,
                flowType,
                request.getApprovalRound(),
                request.getTemplateId(),
                request.getTemplateVersion(),
                steps.size(),
                approvedCount,
                pendingCount,
                rejectedCount,
                cancelledCount,
                currentStep == null ? null : currentStep.getId(),
                Set.copyOf(allowedActions));
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
