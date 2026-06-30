package com.toir.service;

import com.toir.dto.actualcost.ActualCostDto;
import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideCreateRequest;
import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideResponseDto;
import com.toir.dto.budget.ActualCostHandoverSummary;
import com.toir.dto.budget.ActualCostRegisterSummary;
import com.toir.dto.budget.ActualCostReviewActivitySummary;
import com.toir.dto.budget.BudgetSummaryResponse;
import com.toir.dto.financialreview.*;
import com.toir.dto.notification.NotificationDto;
import com.toir.entity.Counteragent;
import com.toir.entity.Department;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.ActualCostReviewEvent;
import com.toir.entity.projects.ActualCostReviewRouteOverride;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.projects.FinancialApprovalRule;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.NotificationSeverity;
import com.toir.exception.RestException;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.actualCost.ActualCostReviewEventRepository;
import com.toir.repository.actualCost.ActualCostReviewRouteOverrideRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.projects.FinancialApprovalRuleRepository;
import com.toir.security.PermissionConstants;
import com.toir.util.CsvWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActualCostReviewFacadeService {

    private static final int DEFAULT_THRESHOLD_HOURS = 24;
    private static final int DEFAULT_REMINDER_WINDOW_HOURS = 4;

    private final ActualCostRepository actualCostRepository;
    private final ActualCostService actualCostService;
    private final FinanceScopeService financeScopeService;
    private final NotificationService notificationService;
    private final ActualCostReviewRouteOverrideService routeOverrideService;
    private final ActualCostReviewRouteOverrideRepository routeOverrideRepository;
    private final ActualCostReviewEventRepository eventRepository;
    private final WorkOrderRepository workOrderRepository;
    private final ContractorWorkRepository contractorWorkRepository;
    private final DepartmentRepository departmentRepository;
    private final CostCategoryRepository costCategoryRepository;
    private final FinancialApprovalRuleRepository financialApprovalRuleRepository;
    private final CounteragentService counteragentService;

    @Transactional(readOnly = true)
    public List<ActualCostReviewItem> reviewQueue(String search) {
        return financeScopeService.filterActualCosts(
                        actualCostRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING)
                ).stream()
                .filter(cost -> matchesSearch(cost, search))
                .map(this::toItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActualCostReviewItem> actualCostRegister(String search) {
        return financeScopeService.filterActualCosts(
                        actualCostRepository.findAllByFiltersOrderByUpdatedAtDesc(null, search)
                ).stream()
                .map(this::toItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public ActualCostRegisterSummary registerSummary(List<ActualCostReviewItem> items) {
        double totalAmount = items.stream().mapToDouble(ActualCostReviewItem::amount).sum();
        double approvedAmount = items.stream().filter(item -> "APPROVED".equals(item.status())).mapToDouble(ActualCostReviewItem::amount).sum();
        double pendingAmount = items.stream().filter(item -> "PENDING".equals(item.status())).mapToDouble(ActualCostReviewItem::amount).sum();
        double rejectedAmount = items.stream().filter(item -> "REJECTED".equals(item.status())).mapToDouble(ActualCostReviewItem::amount).sum();
        long approvedCount = items.stream().filter(item -> "APPROVED".equals(item.status())).count();
        long pendingCount = items.stream().filter(item -> "PENDING".equals(item.status())).count();
        long rejectedCount = items.stream().filter(item -> "REJECTED".equals(item.status())).count();
        return new ActualCostRegisterSummary(totalAmount, approvedAmount, pendingAmount, rejectedAmount,
                items.size(), approvedCount, pendingCount, rejectedCount);
    }

    @Transactional(readOnly = true)
    public List<ActualCostReviewActivityItem> activity(String search) {
        Map<UUID, ActualCost> actualCosts = actualCostsById();
        return eventRepository.findAllByIsDeletedFalseOrderByOccurredAtDesc().stream()
                .filter(event -> matchesSearch(event, search))
                .map(event -> toActivityItem(event, actualCosts.get(event.getActualCostId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ActualCostReviewActivitySummary activitySummary(List<ActualCostReviewActivityItem> items) {
        return new ActualCostReviewActivitySummary(
                items.size(),
                (int) items.stream().filter(item -> "ROUTE".equals(item.eventGroup())).count(),
                (int) items.stream().filter(item -> "SLA".equals(item.eventGroup())).count(),
                (int) items.stream().filter(item -> "REVIEW".equals(item.eventGroup())).count(),
                (int) items.stream().filter(item -> "SYSTEM".equals(item.eventGroup())).count(),
                (int) items.stream().filter(item -> "NOTIFICATION".equals(item.source())).count(),
                (int) items.stream().filter(item -> "AUDIT".equals(item.source())).count(),
                items.stream().map(ActualCostReviewActivityItem::actualCostId).distinct().count()
        );
    }

    @Transactional(readOnly = true)
    public List<ActualCostReviewHandoverItem> handovers(String search) {
        Map<UUID, ActualCost> actualCosts = actualCostsById();
        return eventRepository.findAllByEventCodeAndIsDeletedFalseOrderByOccurredAtDesc("HANDOVER").stream()
                .filter(event -> matchesSearch(event, search))
                .map(event -> toHandoverItem(event, actualCosts.get(event.getActualCostId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ActualCostHandoverSummary handoverSummary(List<ActualCostReviewHandoverItem> items) {
        List<ActualCostHandoverSummary.TargetRoleRow> byTargetRole = items.stream()
                .filter(item -> item.nextApprovalRoleCode() != null && !item.nextApprovalRoleCode().isBlank())
                .collect(Collectors.groupingBy(ActualCostReviewHandoverItem::nextApprovalRoleCode, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new ActualCostHandoverSummary.TargetRoleRow(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream().mapToDouble(ActualCostReviewHandoverItem::amount).sum()
                ))
                .toList();
        List<ActualCostHandoverSummary.DepartmentRow> byDepartment = items.stream()
                .filter(item -> objectId(item.department()) != null)
                .collect(Collectors.groupingBy(item -> objectId(item.department()), LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    Object department = entry.getValue().getFirst().department();
                    return new ActualCostHandoverSummary.DepartmentRow(
                            new BudgetSummaryResponse.CategoryRef(
                                    entry.getKey(),
                                    objectText(department, "code"),
                                    objectText(department, "name")
                            ),
                            entry.getValue().size(),
                            entry.getValue().stream().mapToDouble(ActualCostReviewHandoverItem::amount).sum()
                    );
                })
                .toList();
        return new ActualCostHandoverSummary(
                items.size(),
                (int) items.stream().map(ActualCostReviewHandoverItem::actualCostId).distinct().count(),
                byDepartment.size(),
                (int) items.stream().map(ActualCostReviewHandoverItem::nextApprovalRoleCode).filter(Objects::nonNull).distinct().count(),
                (int) items.stream().map(ActualCostReviewHandoverItem::actorName).filter(Objects::nonNull).distinct().count(),
                byTargetRole,
                byDepartment
        );
    }

    @Transactional
    public ActualCostDto approve(UUID id, UUID reviewerId, String reviewComment) {
        ActualCostDto result = actualCostService.review(
                id,
                true,
                reviewerId,
                hasText(reviewComment) ? reviewComment : "Approved from financial review"
        );
        recordEvent(id, null, null, reviewerId, "SYSTEM", "REVIEW", "APPROVED",
                "Actual cost approved", result.reviewComment(), null, result.status().name());
        return result;
    }

    @Transactional
    public ActualCostDto reject(UUID id, UUID reviewerId, String reviewComment) {
        if (!hasText(reviewComment)) {
            throw RestException.badRequest("Rejection comment is required");
        }
        ActualCostDto result = actualCostService.review(id, false, reviewerId, reviewComment);
        recordEvent(id, null, null, reviewerId, "SYSTEM", "REVIEW", "REJECTED",
                "Actual cost rejected", result.reviewComment(), null, result.status().name());
        return result;
    }

    @Transactional
    public BulkActualCostReviewResponse bulkReview(List<UUID> ids, String action, UUID reviewerId, String reviewComment) {
        List<UUID> safeIds = ids != null ? ids : List.of();
        List<BulkActualCostReviewResponse.Success> successes = new ArrayList<>();
        List<BulkActualCostReviewResponse.Failure> failures = new ArrayList<>();
        boolean approve = "APPROVE".equalsIgnoreCase(action);
        for (UUID id : safeIds) {
            try {
                ActualCostDto dto = approve ? approve(id, reviewerId, reviewComment) : reject(id, reviewerId, reviewComment);
                successes.add(new BulkActualCostReviewResponse.Success(id, dto.status().name()));
            } catch (RuntimeException ex) {
                failures.add(new BulkActualCostReviewResponse.Failure(id, ex.getMessage()));
            }
        }
        return new BulkActualCostReviewResponse(action, safeIds.size(), successes.size(), failures.size(), successes, failures);
    }

    @Transactional
    public EvaluateOverdueActualCostsResponse evaluateOverdue(Integer thresholdHours, Integer reminderWindowHours) {
        int threshold = thresholdHours != null ? thresholdHours : DEFAULT_THRESHOLD_HOURS;
        int reminderWindow = reminderWindowHours != null ? reminderWindowHours : DEFAULT_REMINDER_WINDOW_HOURS;
        List<ActualCostReviewItem> pending = reviewQueue(null);
        List<ActualCostReviewItem> overdueItems = pending.stream()
                .filter(item -> item.ageHours() >= threshold)
                .toList();
        List<ActualCostReviewItem> dueSoonItems = pending.stream()
                .filter(item -> item.ageHours() < threshold && threshold - item.ageHours() <= reminderWindow)
                .toList();
        NotificationCounts overdueNotifications = notifyReviewItems(
                overdueItems,
                NotificationSeverity.WARNING,
                "Actual cost overdue",
                "Actual cost is overdue for finance review."
        );
        NotificationCounts dueSoonNotifications = notifyReviewItems(
                dueSoonItems,
                NotificationSeverity.INFO,
                "Actual cost review due soon",
                "Actual cost is approaching its finance review SLA."
        );
        return new EvaluateOverdueActualCostsResponse(
                threshold,
                reminderWindow,
                pending.size(),
                overdueItems.size(),
                dueSoonItems.size(),
                overdueNotifications.created(),
                dueSoonNotifications.created(),
                overdueNotifications.skipped(),
                dueSoonNotifications.skipped()
        );
    }

    @Transactional
    public BulkActualCostSlaActionResponse bulkSlaAction(List<UUID> ids, String action, Integer thresholdHours,
                                                         Integer reminderWindowHours, UUID actorId, String comment) {
        List<UUID> safeIds = ids != null ? ids : List.of();
        int threshold = thresholdHours != null ? thresholdHours : DEFAULT_THRESHOLD_HOURS;
        int reminderWindow = reminderWindowHours != null ? reminderWindowHours : DEFAULT_REMINDER_WINDOW_HOURS;
        List<BulkActualCostSlaActionResponse.Success> successes = new ArrayList<>();
        List<BulkActualCostSlaActionResponse.Failure> failures = new ArrayList<>();
        for (UUID id : safeIds) {
            try {
                actualCostRepository.findByIdAndIsDeletedFalse(id)
                        .orElseThrow(() -> RestException.notFound("Actual cost not found: " + id));
                recordEvent(id, null, null, actorId, "SYSTEM", "SLA", action,
                        "Actual cost SLA " + action.toLowerCase(), comment, NotificationSeverity.WARNING.name(), "CREATED");
                successes.add(new BulkActualCostSlaActionResponse.Success(id, "CREATED"));
            } catch (RuntimeException ex) {
                failures.add(new BulkActualCostSlaActionResponse.Failure(id, ex.getMessage()));
            }
        }
        return new BulkActualCostSlaActionResponse(action, threshold, reminderWindow, safeIds.size(),
                successes.size(), 0, failures.size(), successes, failures);
    }

    @Transactional
    public ActualCostReviewRouteOverrideResponseDto applyRouteOverride(UUID actualCostId, UUID departmentId,
                                                                       String approvalRoleCode, String escalationRoleCode,
                                                                       Integer thresholdHours, String comment, UUID actorId) {
        ActualCostReviewRouteOverrideResponseDto response = routeOverrideService.apply(new ActualCostReviewRouteOverrideCreateRequest(
                actualCostId,
                departmentId,
                approvalRoleCode,
                escalationRoleCode,
                thresholdHours,
                comment
        ));
        recordEvent(actualCostId, null, response.id(), actorId, "SYSTEM", "ROUTE", "OVERRIDE_APPLIED",
                "Actual cost route override applied", comment, null, null);
        return response;
    }

    @Transactional
    public List<UUID> clearRouteOverride(UUID actualCostId, UUID actorId, String comment) {
        List<UUID> cleared = routeOverrideService.deactivateActiveForActualCost(actualCostId, actorId, comment);
        for (UUID overrideId : cleared) {
            recordEvent(actualCostId, null, overrideId, actorId, "SYSTEM", "ROUTE", "OVERRIDE_CLEARED",
                    "Actual cost route override cleared", comment, null, null);
        }
        return cleared;
    }

    @Transactional
    public ActualCostReviewInboxHandoverResponse handoverFromInbox(UUID actualCostId, UUID notificationId,
                                                                   UUID departmentId, String approvalRoleCode,
                                                                   String escalationRoleCode, Integer thresholdHours,
                                                                   String handoverComment, String acknowledgementComment,
                                                                   UUID actorId, boolean scopeAdmin) {
        ActualCostReviewRouteOverrideResponseDto override = applyRouteOverride(
                actualCostId,
                departmentId,
                approvalRoleCode,
                escalationRoleCode,
                thresholdHours,
                handoverComment,
                actorId
        );
        notificationService.acknowledge(notificationId, actorId, scopeAdmin, acknowledgementComment);
        recordHandoverEvent(actualCostId, notificationId, override.id(), actorId, null, approvalRoleCode,
                null, escalationRoleCode, null, thresholdHours, handoverComment, acknowledgementComment);
        return new ActualCostReviewInboxHandoverResponse(
                toItem(actualCostRepository.findByIdAndIsDeletedFalse(actualCostId).orElseThrow()),
                new ActualCostReviewInboxHandoverResponse.OverrideRef(override.id(), approvalRoleCode, escalationRoleCode,
                        thresholdHours != null ? thresholdHours : DEFAULT_THRESHOLD_HOURS),
                notificationId,
                acknowledgementComment
        );
    }

    @Transactional(readOnly = true)
    public UUID actualCostIdFromNotification(UUID notificationId, UUID currentUserId, boolean scopeAdmin) {
        NotificationDto notification = notificationService.findById(notificationId, currentUserId, scopeAdmin);
        if (!hasText(notification.entityId())) {
            throw RestException.badRequest("Financial review notification has no actual cost reference");
        }
        try {
            return UUID.fromString(notification.entityId());
        } catch (IllegalArgumentException ex) {
            throw RestException.badRequest("Financial review notification has invalid actual cost reference");
        }
    }

    @Transactional(readOnly = true)
    public String csv(String filename, List<ActualCostReviewItem> items) {
        return CsvWriter.build(
                List.of("id", "status", "allocationStatus", "unallocated", "budgetLineId", "amount", "costDate",
                        "approvalRoleCode", "routeSource", "isOverdue", "allocatedAt", "allocatedById", "notes"),
                items,
                List.of(
                        ActualCostReviewItem::id,
                        ActualCostReviewItem::status,
                        ActualCostReviewItem::allocationStatus,
                        ActualCostReviewItem::unallocated,
                        item -> item.budgetLine() instanceof ActualCostReviewItem.BudgetLineRef budgetLine
                                ? budgetLine.id()
                                : null,
                        ActualCostReviewItem::amount,
                        ActualCostReviewItem::costDate,
                        ActualCostReviewItem::approvalRoleCode,
                        ActualCostReviewItem::routeSource,
                        ActualCostReviewItem::isOverdue,
                        ActualCostReviewItem::allocatedAt,
                        ActualCostReviewItem::allocatedById,
                        ActualCostReviewItem::notes
                )
        );
    }

    @Transactional(readOnly = true)
    public String activityCsv(List<ActualCostReviewActivityItem> items) {
        return CsvWriter.build(
                List.of("id", "actualCostId", "eventGroup", "eventCode", "occurredAt", "status", "amount"),
                items,
                List.of(
                        ActualCostReviewActivityItem::id,
                        ActualCostReviewActivityItem::actualCostId,
                        ActualCostReviewActivityItem::eventGroup,
                        ActualCostReviewActivityItem::eventCode,
                        ActualCostReviewActivityItem::occurredAt,
                        ActualCostReviewActivityItem::actualCostStatus,
                        ActualCostReviewActivityItem::amount
                )
        );
    }

    @Transactional(readOnly = true)
    public String handoversCsv(List<ActualCostReviewHandoverItem> items) {
        return CsvWriter.build(
                List.of("id", "actualCostId", "occurredAt", "previousRole", "nextRole", "comment"),
                items,
                List.of(
                        ActualCostReviewHandoverItem::id,
                        ActualCostReviewHandoverItem::actualCostId,
                        ActualCostReviewHandoverItem::occurredAt,
                        ActualCostReviewHandoverItem::previousApprovalRoleCode,
                        ActualCostReviewHandoverItem::nextApprovalRoleCode,
                        ActualCostReviewHandoverItem::handoverComment
                )
        );
    }

    private ActualCostReviewItem toItem(ActualCost cost) {
        ActualCostReviewRouteOverride activeOverride = routeOverrideRepository
                .findFirstByActualCostIdAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(cost.getId())
                .orElse(null);
        ActualCostContext context = resolveContext(cost, activeOverride);
        FinancialApprovalRule matchingRule = activeOverride == null && context.department() != null
                ? financialApprovalRuleRepository
                .findFirstMatchingRule(context.department().getId(), cost.getAmount())
                .orElse(null)
                : null;
        int ageHours = ageHours(cost);
        int threshold = activeOverride != null
                ? activeOverride.getThresholdHours()
                : matchingRule != null && matchingRule.getThresholdHours() != null
                ? matchingRule.getThresholdHours()
                : DEFAULT_THRESHOLD_HOURS;
        boolean overdue = ageHours >= threshold;
        String approvalRole = activeOverride != null && hasText(activeOverride.getApprovalRoleCode())
                ? activeOverride.getApprovalRoleCode()
                : matchingRule != null && hasText(matchingRule.getRequiredRoleCode())
                ? matchingRule.getRequiredRoleCode()
                : "FINANCE_MANAGER";
        String escalationRole = activeOverride != null && hasText(activeOverride.getEscalationRoleCode())
                ? activeOverride.getEscalationRoleCode()
                : matchingRule != null
                ? matchingRule.getEscalateToRoleCode()
                : null;
        Object override = activeOverride != null ? routeOverrideService.toResponse(activeOverride, cost) : null;
        ActualCostReviewItem.ApprovalRuleRef approvalRule = matchingRule != null
                ? new ActualCostReviewItem.ApprovalRuleRef(
                matchingRule.getId(),
                matchingRule.getCode(),
                matchingRule.getThresholdHours()
        )
                : new ActualCostReviewItem.ApprovalRuleRef(null, "DEFAULT", threshold);
        return new ActualCostReviewItem(
                cost.getId(),
                cost.getWorkOrderId(),
                cost.getRepairRequestId(),
                cost.getContractorWorkId(),
                cost.getCostCategoryId(),
                cost.getStatus() != null ? cost.getStatus().name() : null,
                cost.getAmount(),
                cost.getCostDate(),
                cost.getNotes(),
                cost.getReviewedAt(),
                cost.getReviewedById(),
                null,
                cost.getReviewedById() != null ? new ActualCostReviewItem.UserRef(cost.getReviewedById(), null) : null,
                cost.getReviewComment(),
                toCounteragentWorkRef(context.counteragentWork(), context.workOrder()),
                toWorkOrderRef(context.workOrder(), context.department()),
                cost.getRepairRequestId() != null ? new ActualCostReviewItem.RepairRequestRef(cost.getRepairRequestId(), "") : null,
                cost.getBudgetLineId() != null ? new ActualCostReviewItem.BudgetLineRef(cost.getBudgetLineId(), null, null, null) : null,
                toDepartmentRef(context.department()),
                toCostCategoryRef(context.costCategory()),
                ageHours,
                overdue,
                "/financial-review/history/" + cost.getId(),
                approvalRule,
                approvalRole,
                escalationRole,
                Math.max(threshold - ageHours, 0),
                activeOverride != null ? "OVERRIDE" : "RULE",
                override,
                cost.getStatus() == ActualCostStatus.PENDING,
                null,
                approvalRole,
                contextType(cost),
                "/budgets?actualCostId=" + cost.getId(),
                "/financial-review?actualCostId=" + cost.getId(),
                sourceLink(cost),
                cost.getBudgetLineId() == null ? "UNALLOCATED" : "ALLOCATED",
                cost.getBudgetLineId() == null,
                cost.getAllocationComment(),
                cost.getAllocatedAt(),
                cost.getAllocatedById()
        );
    }

    private NotificationCounts notifyReviewItems(List<ActualCostReviewItem> items,
                                                 NotificationSeverity severity,
                                                 String title,
                                                 String message) {
        int created = 0;
        int skipped = 0;
        for (ActualCostReviewItem item : items) {
            UUID departmentId = objectId(item.department());
            List<NotificationDto> notifications = notificationService.notifyDepartmentByPermission(
                    departmentId,
                    PermissionConstants.ACTUAL_COST_APPROVE,
                    title,
                    message,
                    severity,
                    "ACTUAL_COST",
                    item.id().toString()
            );
            if (notifications == null || notifications.isEmpty()) {
                skipped++;
            } else {
                created += notifications.size();
            }
        }
        return new NotificationCounts(created, skipped);
    }

    private ActualCostReviewActivityItem toActivityItem(ActualCostReviewEvent event, ActualCost cost) {
        ActualCostContext context = cost != null ? resolveContext(cost, null) : ActualCostContext.empty();
        return new ActualCostReviewActivityItem(
                event.getId(),
                event.getSource(),
                event.getEventGroup(),
                event.getEventCode(),
                event.getOccurredAt(),
                event.getActualCostId(),
                cost != null && cost.getStatus() != null ? cost.getStatus().name() : event.getStatus(),
                cost != null ? cost.getAmount() : 0,
                event.getTitle(),
                event.getDescription(),
                null,
                event.getNextApprovalRoleCode(),
                event.getNextApprovalRoleCode(),
                event.getNextEscalationRoleCode(),
                event.getRouteOverrideId() != null ? "OVERRIDE" : "RULE",
                event.getNextThresholdHours(),
                null,
                null,
                event.getSeverity(),
                event.getStatus(),
                toDepartmentRef(context.department()),
                toCounteragentRef(context.counteragentWork()),
                toCounteragentWorkRef(context.counteragentWork(), context.workOrder()),
                toWorkOrderRef(context.workOrder(), context.department()),
                "/financial-review/history/" + event.getActualCostId(),
                "/financial-review?actualCostId=" + event.getActualCostId()
        );
    }

    private ActualCostReviewHandoverItem toHandoverItem(ActualCostReviewEvent event, ActualCost cost) {
        ActualCostContext context = cost != null ? resolveContext(cost, null) : ActualCostContext.empty();
        return new ActualCostReviewHandoverItem(
                event.getId(),
                event.getOccurredAt(),
                event.getActualCostId(),
                cost != null && cost.getStatus() != null ? cost.getStatus().name() : event.getStatus(),
                cost != null ? cost.getAmount() : 0,
                toDepartmentRef(context.department()),
                toCounteragentRef(context.counteragentWork()),
                toCounteragentWorkRef(context.counteragentWork(), context.workOrder()),
                toWorkOrderRef(context.workOrder(), context.department()),
                null,
                event.getNotificationId(),
                event.getPreviousApprovalRoleCode(),
                event.getNextApprovalRoleCode(),
                event.getPreviousEscalationRoleCode(),
                event.getNextEscalationRoleCode(),
                event.getPreviousThresholdHours(),
                event.getNextThresholdHours(),
                event.getHandoverComment(),
                event.getAcknowledgementComment(),
                "/financial-review/history/" + event.getActualCostId(),
                "/financial-review/activity?actualCostId=" + event.getActualCostId()
        );
    }

    private void recordHandoverEvent(UUID actualCostId, UUID notificationId, UUID routeOverrideId, UUID actorId,
                                     String previousRole, String nextRole, String previousEscalation,
                                     String nextEscalation, Integer previousThreshold, Integer nextThreshold,
                                     String handoverComment, String acknowledgementComment) {
        ActualCostReviewEvent event = baseEvent(actualCostId, notificationId, routeOverrideId, actorId,
                "SYSTEM", "ROUTE", "HANDOVER", "Actual cost review handed over", handoverComment, null, null);
        event.setPreviousApprovalRoleCode(previousRole);
        event.setNextApprovalRoleCode(nextRole);
        event.setPreviousEscalationRoleCode(previousEscalation);
        event.setNextEscalationRoleCode(nextEscalation);
        event.setPreviousThresholdHours(previousThreshold);
        event.setNextThresholdHours(nextThreshold);
        event.setHandoverComment(handoverComment);
        event.setAcknowledgementComment(acknowledgementComment);
        eventRepository.save(event);
    }

    private void recordEvent(UUID actualCostId, UUID notificationId, UUID routeOverrideId, UUID actorId, String source,
                             String eventGroup, String eventCode, String title, String description,
                             String severity, String status) {
        eventRepository.save(baseEvent(actualCostId, notificationId, routeOverrideId, actorId,
                source, eventGroup, eventCode, title, description, severity, status));
    }

    private ActualCostReviewEvent baseEvent(UUID actualCostId, UUID notificationId, UUID routeOverrideId, UUID actorId,
                                            String source, String eventGroup, String eventCode, String title,
                                            String description, String severity, String status) {
        ActualCostReviewEvent event = new ActualCostReviewEvent();
        event.setActualCostId(actualCostId);
        event.setNotificationId(notificationId);
        event.setRouteOverrideId(routeOverrideId);
        event.setActorUserId(actorId);
        event.setSource(source);
        event.setEventGroup(eventGroup);
        event.setEventCode(eventCode);
        event.setTitle(title);
        event.setDescription(description);
        event.setSeverity(severity);
        event.setStatus(status);
        event.setOccurredAt(Instant.now());
        return event;
    }

    private Map<UUID, ActualCost> actualCostsById() {
        return actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(ActualCost::getId, cost -> cost, (left, right) -> left));
    }

    private boolean matchesSearch(ActualCost cost, String search) {
        if (!hasText(search)) {
            return true;
        }
        String lower = search.toLowerCase();
        return contains(cost.getId(), lower)
                || contains(cost.getNotes(), lower)
                || contains(cost.getReviewComment(), lower)
                || contains(cost.getStatus(), lower)
                || contains(cost.getAmount(), lower);
    }

    private boolean matchesSearch(ActualCostReviewEvent event, String search) {
        if (!hasText(search)) {
            return true;
        }
        String lower = search.toLowerCase();
        return contains(event.getTitle(), lower)
                || contains(event.getDescription(), lower)
                || contains(event.getEventCode(), lower)
                || contains(event.getActualCostId(), lower);
    }

    private boolean contains(Object value, String lowerSearch) {
        return value != null && value.toString().toLowerCase().contains(lowerSearch);
    }

    private UUID objectId(Object value) {
        Object id = objectProperty(value, "id");
        if (id instanceof UUID uuid) {
            return uuid;
        }
        if (id instanceof String text && !text.isBlank()) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private String objectText(Object value, String property) {
        Object result = objectProperty(value, property);
        return result != null ? result.toString() : "";
    }

    private Object objectProperty(Object value, String name) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return map.get(name);
        }
        try {
            return value.getClass().getMethod(name).invoke(value);
        } catch (ReflectiveOperationException | SecurityException ex) {
            return null;
        }
    }

    private int ageHours(ActualCost cost) {
        Instant from = cost.getCreatedAt() != null ? cost.getCreatedAt() : cost.getCostDate();
        if (from == null) {
            return 0;
        }
        return Math.max(0, Math.toIntExact(Duration.between(from, Instant.now()).toHours()));
    }

    private String contextType(ActualCost cost) {
        if (cost.getContractorWorkId() != null) {
            return "COUNTERAGENT_WORK";
        }
        if (cost.getWorkOrderId() != null) {
            return "WORK_ORDER";
        }
        if (cost.getRepairRequestId() != null) {
            return "REPAIR_REQUEST";
        }
        return "GENERAL";
    }

    private String sourceLink(ActualCost cost) {
        if (cost.getContractorWorkId() != null) {
            return "/counteragent-works?workId=" + cost.getContractorWorkId();
        }
        if (cost.getWorkOrderId() != null) {
            return "/work-orders/" + cost.getWorkOrderId();
        }
        if (cost.getRepairRequestId() != null) {
            return "/repair-requests/" + cost.getRepairRequestId();
        }
        return "/financial-review?actualCostId=" + cost.getId();
    }

    private ActualCostContext resolveContext(ActualCost cost, ActualCostReviewRouteOverride activeOverride) {
        ContractorWork contractorWork = cost.getContractorWorkId() != null
                ? contractorWorkRepository.findByIdAndIsDeletedFalse(cost.getContractorWorkId()).orElse(null)
                : null;
        WorkOrder workOrder = resolveWorkOrder(cost, contractorWork);
        Department department = resolveDepartment(activeOverride, workOrder);
        CostCategory costCategory = cost.getCostCategoryId() != null
                ? costCategoryRepository.findByIdAndIsDeletedFalse(cost.getCostCategoryId()).orElse(null)
                : null;
        return new ActualCostContext(workOrder, contractorWork, department, costCategory);
    }

    private WorkOrder resolveWorkOrder(ActualCost cost, ContractorWork contractorWork) {
        if (cost.getWorkOrderId() != null) {
            return workOrderRepository.findByIdAndIsDeletedFalse(cost.getWorkOrderId()).orElse(null);
        }
        if (contractorWork != null && contractorWork.getWorkOrderId() != null) {
            return workOrderRepository.findByIdAndIsDeletedFalse(contractorWork.getWorkOrderId()).orElse(null);
        }
        return null;
    }

    private Department resolveDepartment(ActualCostReviewRouteOverride activeOverride, WorkOrder workOrder) {
        if (activeOverride != null && activeOverride.getDepartmentId() != null) {
            return departmentRepository.findByIdAndIsDeletedFalse(activeOverride.getDepartmentId()).orElse(null);
        }
        if (workOrder != null && workOrder.getDepartmentId() != null) {
            return departmentRepository.findByIdAndIsDeletedFalse(workOrder.getDepartmentId()).orElse(null);
        }
        return null;
    }

    private ActualCostReviewItem.Ref toDepartmentRef(Department department) {
        if (department == null) {
            return null;
        }
        return new ActualCostReviewItem.Ref(department.getId(), safeText(department.getCode()), safeText(department.getName()));
    }

    private ActualCostReviewItem.Ref toCostCategoryRef(CostCategory costCategory) {
        if (costCategory == null) {
            return null;
        }
        return new ActualCostReviewItem.Ref(costCategory.getId(), safeText(costCategory.getCode()), safeText(costCategory.getName()));
    }

    private ActualCostReviewItem.Ref toCounteragentRef(ContractorWork contractorWork) {
        if (contractorWork == null || contractorWork.getCounteragentId() == null) {
            return null;
        }
        Counteragent counteragent = counteragentService.load(contractorWork.getCounteragentId());
        return new ActualCostReviewItem.Ref(
                counteragent.getId(),
                safeText(counteragent.getCode()),
                safeText(counteragent.getName())
        );
    }

    private ActualCostReviewItem.WorkOrderRef toWorkOrderRef(WorkOrder workOrder, Department department) {
        if (workOrder == null) {
            return null;
        }
        return new ActualCostReviewItem.WorkOrderRef(
                workOrder.getId(),
                safeText(workOrder.getNumber()),
                safeText(workOrder.getTitle()),
                toDepartmentRef(department)
        );
    }

    private ActualCostReviewItem.CounteragentWorkRef toCounteragentWorkRef(ContractorWork contractorWork, WorkOrder workOrder) {
        if (contractorWork == null) {
            return null;
        }
        return new ActualCostReviewItem.CounteragentWorkRef(
                contractorWork.getId(),
                safeText(contractorWork.getDescription()),
                contractorWork.getStatus() != null ? contractorWork.getStatus().name() : null,
                contractorWork.getCost(),
                toCounteragentRef(contractorWork),
                toWorkOrderRef(workOrder, null)
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeText(String value) {
        return value != null ? value : "";
    }

    private record ActualCostContext(
            WorkOrder workOrder,
            ContractorWork counteragentWork,
            Department department,
            CostCategory costCategory
    ) {
        static ActualCostContext empty() {
            return new ActualCostContext(null, null, null, null);
        }
    }

    private record NotificationCounts(int created, int skipped) {
    }
}
