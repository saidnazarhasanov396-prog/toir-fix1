package com.toir.service.maintanance;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.PprPlan;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalResolutionCode;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.MaterializationMode;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprPlanOrigin;
import com.toir.enums.TaskMaterializationStatus;
import com.toir.exception.MaintenanceScheduleCalculationConflictException;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.maintenance.MaintenanceScheduleCalculationItemRepository;
import com.toir.service.approval.ApprovalRouteSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaintenanceScheduleApprovalBindingService {

    private static final String TARGET_TYPE = ApprovalTargetType.PPR_PLAN.name();
    private static final String ACTION_TYPE = ApprovalActionType.APPROVE.name();

    private final PprPlanRepository planRepository;
    private final ApprovalRequestRepository requestRepository;
    private final MaintenanceScheduleCalculationItemRepository itemRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public boolean isApprovalFirstTarget(
            ApprovalTargetType targetType, UUID targetId) {
        if (targetType != ApprovalTargetType.PPR_PLAN || targetId == null) {
            return false;
        }
        return planRepository.findByIdAndIsDeletedFalse(targetId)
                .map(this::isApprovalFirst)
                .orElse(false);
    }

    @Transactional
    public MaintenanceScheduleApprovalBinding resolveForSubmission(
            ApprovalTargetType targetType,
            UUID targetId,
            ApprovalActionType actionType,
            UUID requesterId) {
        if (targetType != ApprovalTargetType.PPR_PLAN) {
            return null;
        }
        PprPlan plan = planRepository.findByIdAndIsDeletedFalseForUpdate(targetId)
                .orElseThrow(() -> RestException.notFound("PPR plan not found"));
        if (!isApprovalFirst(plan)) {
            return null;
        }
        if (actionType != null && actionType != ApprovalActionType.APPROVE) {
            throw RestException.conflict(
                    "PPR_APPROVAL_FIRST_ONLY_SUPPORTS_APPROVE");
        }
        requireApprovalCandidate(plan);
        long revision = plan.getCalculationRevision();
        if (itemRepository.countByPlanIdAndCalculationRevision(
                targetId, revision) <= 0) {
            throw new RestException(
                    "Immutable calculation snapshot is missing",
                    org.springframework.http.HttpStatus.CONFLICT,
                    "PPR_CALCULATION_SNAPSHOT_MISSING");
        }
        MaintenanceScheduleContentHashVersionGuard.validate(
                plan.getCalculationContentHashVersion(),
                plan.getCalculationContentHash());
        return new MaintenanceScheduleApprovalBinding(
                targetId,
                revision,
                plan.getCalculationContentHash(),
                plan.getCalculationContentHashVersion(),
                requesterFingerprint(requesterId, plan));
    }

    public void bind(
            ApprovalRequest request,
            MaintenanceScheduleApprovalBinding binding) {
        if (binding == null) {
            return;
        }
        request.setCalculationRevision(binding.calculationRevision());
        request.setCalculationContentHash(binding.calculationContentHash());
        request.setCalculationContentHashVersion(
                binding.calculationContentHashVersion());
        request.setRequesterContextFingerprint(
                binding.requesterContextFingerprint());
    }

    public boolean matches(
            ApprovalRequest request,
            MaintenanceScheduleApprovalBinding binding) {
        return binding != null
                && request != null
                && Objects.equals(
                        effectiveTargetId(request),
                        binding.planId())
                && Objects.equals(
                        request.getCalculationRevision(),
                        binding.calculationRevision())
                && Objects.equals(
                        request.getCalculationContentHash(),
                        binding.calculationContentHash())
                && Objects.equals(
                        request.getCalculationContentHashVersion(),
                        binding.calculationContentHashVersion());
    }

    public boolean isBoundApprovalFirstRequest(ApprovalRequest request) {
        return request != null
                && effectiveTargetType(request) == ApprovalTargetType.PPR_PLAN
                && request.getCalculationRevision() != null;
    }

    public void bindResolvedRoute(
            ApprovalRequest request,
            ApprovalRouteSnapshot route,
            List<CreateApprovalRequest.StepInput> normalizedSteps) {
        if (!isBoundApprovalFirstRequest(request)) {
            return;
        }
        if (route == null || route.templateId() == null) {
            throw new RestException(
                    "Approval Template route is not configured for PPR plan",
                    org.springframework.http.HttpStatus.CONFLICT,
                    "PPR_APPROVAL_TEMPLATE_ROUTE_NOT_CONFIGURED");
        }
        request.setTemplateId(route.templateId());
        request.setTemplateVersion(route.templateVersion());
        request.setResolvedRouteFingerprint(
                routeFingerprint(route, normalizedSteps));
        if (request.getRequesterContextFingerprint() == null) {
            request.setRequesterContextFingerprint(sha256(
                    "requester=" + Objects.toString(request.getRequesterId(), "N")));
        }
    }

    public boolean routeMatches(
            ApprovalRequest request,
            ApprovalRouteSnapshot currentRoute) {
        if (!isBoundApprovalFirstRequest(request)) {
            return true;
        }
        if (currentRoute == null || currentRoute.templateId() == null) {
            return false;
        }
        return Objects.equals(
                        request.getResolvedRouteFingerprint(),
                        routeFingerprint(currentRoute, currentRoute.steps()))
                && Objects.equals(request.getTemplateId(), currentRoute.templateId())
                && Objects.equals(
                        request.getTemplateVersion(), currentRoute.templateVersion());
    }

    @Transactional
    public void supersedeForNewRevision(
            UUID planId,
            long currentRevision,
            String currentHash,
            int currentHashVersion,
            UUID actorId) {
        lockTargetAction(planId);
        List<ApprovalRequest> pending =
                requestRepository.findAllPendingByTargetAndAction(
                        TARGET_TYPE, planId, ACTION_TYPE,
                        ApprovalStatus.PENDING.name());
        List<ApprovalRequest> stale = pending.stream()
                .filter(request -> !Objects.equals(
                        request.getCalculationRevision(), currentRevision)
                        || !Objects.equals(
                                request.getCalculationContentHash(), currentHash)
                        || !Objects.equals(
                                request.getCalculationContentHashVersion(),
                                currentHashVersion))
                .toList();
        if (stale.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (ApprovalRequest request : stale) {
            request.setStatus(ApprovalStatus.SUPERSEDED);
            request.setResolutionCode(ApprovalResolutionCode.NEW_REVISION);
            request.setFailureReason(
                    "PPR_APPROVAL_SUPERSEDED_NEW_REVISION");
            request.setCompletedAt(now);
            for (ApprovalStep step : request.getSteps()) {
                if (!step.isDeleted()
                        && step.getDecision() == ApprovalDecision.PENDING) {
                    step.setDecision(ApprovalDecision.CANCELLED);
                    step.setDecidedAt(now);
                    step.setDecidedById(actorId);
                    step.setComment("NEW_REVISION");
                }
            }
        }
        requestRepository.saveAllAndFlush(stale);
    }

    public void supersede(
            ApprovalRequest request,
            ApprovalResolutionCode resolutionCode,
            String failureReason,
            UUID actorId) {
        Instant now = Instant.now();
        request.setStatus(ApprovalStatus.SUPERSEDED);
        request.setResolutionCode(resolutionCode);
        request.setFailureReason(failureReason);
        request.setCompletedAt(now);
        for (ApprovalStep step : request.getSteps()) {
            if (!step.isDeleted()
                    && step.getDecision() == ApprovalDecision.PENDING) {
                step.setDecision(ApprovalDecision.CANCELLED);
                step.setDecidedAt(now);
                step.setDecidedById(actorId);
                step.setComment(resolutionCode.name());
            }
        }
    }

    public void lockTargetAction(UUID planId) {
        int namespace = TARGET_TYPE.hashCode();
        int resource = (planId + ":" + ACTION_TYPE).hashCode();
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(?, ?)",
                ps -> {
                    ps.setInt(1, namespace);
                    ps.setInt(2, resource);
                },
                rs -> null);
    }

    private void requireApprovalCandidate(PprPlan plan) {
        if (plan.getOrigin() != PprPlanOrigin.MAINTENANCE_SCHEDULE
                || plan.getStatus() != PlanStatus.CALCULATED
                || plan.getTaskMaterializationStatus()
                != TaskMaterializationStatus.NOT_MATERIALIZED
                || plan.getCalculationRevision() == null
                || plan.getCalculationRevision() < 1
                || plan.getCalculationContentHashVersion() == null
                || plan.getCalculationContentHash() == null) {
            throw new RestException(
                    "Approval-first calculation is not ready for approval",
                    org.springframework.http.HttpStatus.CONFLICT,
                    "PPR_CALCULATION_NOT_READY_FOR_APPROVAL");
        }
    }

    private boolean isApprovalFirst(PprPlan plan) {
        return plan.getOrigin() == PprPlanOrigin.MAINTENANCE_SCHEDULE
                && plan.getMaterializationMode()
                == MaterializationMode.APPROVAL_FIRST;
    }

    private static ApprovalTargetType effectiveTargetType(
            ApprovalRequest request) {
        return request.getTargetType() != null
                ? request.getTargetType()
                : ApprovalTargetType.fromDocumentType(request.getDocumentType());
    }

    private static UUID effectiveTargetId(ApprovalRequest request) {
        return request.getTargetId() != null
                ? request.getTargetId()
                : request.getDocumentId();
    }

    private static String requesterFingerprint(
            UUID requesterId, PprPlan plan) {
        if (requesterId == null) {
            throw RestException.badRequest(
                    "Authenticated requester is required");
        }
        return sha256(
                "v1\nrequester=" + requesterId
                        + "\nplan=" + plan.getId()
                        + "\ndepartment="
                        + Objects.toString(plan.getDepartmentId(), "N")
                        + "\nscope="
                        + Objects.toString(plan.getScopeType(), "N"));
    }

    private static String routeFingerprint(
            ApprovalRouteSnapshot route,
            List<CreateApprovalRequest.StepInput> steps) {
        StringBuilder value = new StringBuilder("v1");
        value.append("\ntemplate=").append(route.templateId());
        value.append("\ntemplateVersion=")
                .append(Objects.toString(route.templateVersion(), "N"));
        value.append("\nflow=").append(route.flowType());
        List<CreateApprovalRequest.StepInput> safeSteps =
                steps == null ? List.of() : steps;
        value.append("\nsteps=").append(safeSteps.size());
        for (int index = 0; index < safeSteps.size(); index++) {
            CreateApprovalRequest.StepInput step = safeSteps.get(index);
            value.append('\n').append(index).append(".id=")
                    .append(Objects.toString(step.approverId(), "N"));
            value.append('\n').append(index).append(".role=")
                    .append(Objects.toString(step.approverRole(), "N"));
        }
        return sha256(value.toString());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available", exception);
        }
    }

    private static final class MaintenanceScheduleContentHashVersionGuard {
        private MaintenanceScheduleContentHashVersionGuard() {
        }

        static void validate(Integer version, String hash) {
            try {
                com.toir.enums.MaintenanceScheduleContentHashVersion
                        .fromPersistedValue(version);
            } catch (MaintenanceScheduleCalculationConflictException error) {
                throw error;
            }
            if (hash == null || !hash.matches("^[0-9a-f]{64}$")) {
                throw new MaintenanceScheduleCalculationConflictException(
                        MaintenanceScheduleCalculationConflictException.Reason
                                .INVALID_HASH);
            }
        }
    }
}
