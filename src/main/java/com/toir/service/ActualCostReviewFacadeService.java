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
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.ActualCostReviewEvent;
import com.toir.entity.projects.ActualCostReviewRouteOverride;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.NotificationSeverity;
import com.toir.exception.RestException;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.actualCost.ActualCostReviewEventRepository;
import com.toir.repository.actualCost.ActualCostReviewRouteOverrideRepository;
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
        return new ActualCostHandoverSummary(
                items.size(),
                (int) items.stream().map(ActualCostReviewHandoverItem::actualCostId).distinct().count(),
                0,
                (int) items.stream().map(ActualCostReviewHandoverItem::nextApprovalRoleCode).filter(Objects::nonNull).distinct().count(),
                (int) items.stream().map(ActualCostReviewHandoverItem::actorName).filter(Objects::nonNull).distinct().count(),
                byTargetRole,
                List.of()
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
        int overdue = (int) pending.stream().filter(item -> item.ageHours() >= threshold).count();
        int dueSoon = (int) pending.stream()
                .filter(item -> item.ageHours() < threshold && threshold - item.ageHours() <= reminderWindow)
                .count();
        return new EvaluateOverdueActualCostsResponse(threshold, reminderWindow, pending.size(), overdue, dueSoon, 0, 0, 0, 0);
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
                List.of("id", "status", "amount", "costDate", "approvalRoleCode", "routeSource", "isOverdue", "notes"),
                items,
                List.of(
                        ActualCostReviewItem::id,
                        ActualCostReviewItem::status,
                        ActualCostReviewItem::amount,
                        ActualCostReviewItem::costDate,
                        ActualCostReviewItem::approvalRoleCode,
                        ActualCostReviewItem::routeSource,
                        ActualCostReviewItem::isOverdue,
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
        int ageHours = ageHours(cost);
        int threshold = activeOverride != null ? activeOverride.getThresholdHours() : DEFAULT_THRESHOLD_HOURS;
        boolean overdue = ageHours >= threshold;
        String approvalRole = activeOverride != null ? activeOverride.getApprovalRoleCode() : "FINANCE_MANAGER";
        String escalationRole = activeOverride != null ? activeOverride.getEscalationRoleCode() : null;
        Object override = activeOverride != null ? routeOverrideService.toResponse(activeOverride, cost) : null;
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
                null,
                cost.getWorkOrderId() != null ? new ActualCostReviewItem.WorkOrderRef(cost.getWorkOrderId(), "", "", null) : null,
                cost.getRepairRequestId() != null ? new ActualCostReviewItem.RepairRequestRef(cost.getRepairRequestId(), "") : null,
                cost.getBudgetLineId() != null ? new ActualCostReviewItem.BudgetLineRef(cost.getBudgetLineId(), null, null, null) : null,
                null,
                cost.getCostCategoryId() != null ? new ActualCostReviewItem.Ref(cost.getCostCategoryId(), "", "") : null,
                ageHours,
                overdue,
                "/financial-review/history/" + cost.getId(),
                new ActualCostReviewItem.ApprovalRuleRef(null, "DEFAULT", threshold),
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
                sourceLink(cost)
        );
    }

    private ActualCostReviewActivityItem toActivityItem(ActualCostReviewEvent event, ActualCost cost) {
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
                null,
                null,
                null,
                null,
                "/financial-review/history/" + event.getActualCostId(),
                "/financial-review?actualCostId=" + event.getActualCostId()
        );
    }

    private ActualCostReviewHandoverItem toHandoverItem(ActualCostReviewEvent event, ActualCost cost) {
        return new ActualCostReviewHandoverItem(
                event.getId(),
                event.getOccurredAt(),
                event.getActualCostId(),
                cost != null && cost.getStatus() != null ? cost.getStatus().name() : event.getStatus(),
                cost != null ? cost.getAmount() : 0,
                null,
                null,
                null,
                null,
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

    private int ageHours(ActualCost cost) {
        Instant from = cost.getCreatedAt() != null ? cost.getCreatedAt() : cost.getCostDate();
        if (from == null) {
            return 0;
        }
        return Math.max(0, Math.toIntExact(Duration.between(from, Instant.now()).toHours()));
    }

    private String contextType(ActualCost cost) {
        if (cost.getContractorWorkId() != null) {
            return "CONTRACTOR";
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
            return "/contractors?contractorWorkId=" + cost.getContractorWorkId();
        }
        if (cost.getWorkOrderId() != null) {
            return "/work-orders/" + cost.getWorkOrderId();
        }
        if (cost.getRepairRequestId() != null) {
            return "/repair-requests/" + cost.getRepairRequestId();
        }
        return "/financial-review?actualCostId=" + cost.getId();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
