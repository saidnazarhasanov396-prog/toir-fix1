package com.toir.service.plannedshutdown;

import com.toir.dto.plannedshutdown.PlannedShutdownBlocker;
import com.toir.dto.plannedshutdown.PlannedShutdownReadinessAssessment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class PlannedShutdownReadinessPolicy {

    private static final Comparator<PlannedShutdownBlocker> ORDER = Comparator
            .comparing(PlannedShutdownBlocker::code)
            .thenComparing(PlannedShutdownBlocker::entityType)
            .thenComparing(blocker -> blocker.entityId() == null ? "" : blocker.entityId().toString());

    public PlannedShutdownReadinessAssessment evaluateReadiness(Facts facts) {
        List<PlannedShutdownBlocker> blockers = new ArrayList<>();
        addUnless(blockers, facts.hasBoundary(), "BOUNDARY_MISSING", "Shutdown boundary is missing",
                "PLANNED_SHUTDOWN", facts.shutdownId());
        addUnless(blockers, facts.hasResponsibleOwner(), "OWNER_MISSING", "Responsible owner is missing",
                "PLANNED_SHUTDOWN", facts.shutdownId());
        addUnless(blockers, facts.hasWork(), "WORK_MISSING", "Shutdown has no work items",
                "PLANNED_SHUTDOWN", facts.shutdownId());
        facts.work().stream().filter(WorkFact::critical).filter(work -> !work.materialReserved())
                .forEach(work -> add(blockers, "MATERIAL_RESERVATION_MISSING",
                        "Critical work has no current material reservation", "WORK_ITEM", work.id()));
        facts.work().stream().filter(work -> !work.performerOrContractorAssigned())
                .forEach(work -> add(blockers, "PERFORMER_OR_CONTRACTOR_MISSING",
                        "Work has no eligible performer or contractor", "WORK_ITEM", work.id()));
        if (!facts.approvalScopeCurrent()) {
            add(blockers, "APPROVAL_SCOPE_STALE", "Approval does not match the current shutdown scope",
                    "PLANNED_SHUTDOWN", facts.shutdownId());
        } else {
            addUnless(blockers, facts.approvalComplete(), "PLANNED_SHUTDOWN_APPROVAL_ROUTE_STALE",
                    "Persisted runtime approval route is incomplete or malformed",
                    "PLANNED_SHUTDOWN", facts.shutdownId());
        }
        facts.isolation().stream().filter(point -> !point.configured())
                .forEach(point -> add(blockers, "ISOLATION_POINT_MISSING",
                        "Required isolation point is missing", "ISOLATION_POINT", point.id()));
        boolean requiresIsolation = facts.work().stream().anyMatch(WorkFact::requiresIsolation);
        if (requiresIsolation && facts.isolation().isEmpty()) {
            add(blockers, "ISOLATION_POINT_MISSING", "Required isolation point is missing",
                    "PLANNED_SHUTDOWN", facts.shutdownId());
        }
        addUnless(blockers, facts.permitsActive(), "PERMIT_INACTIVE", "Required safety permit is not active",
                "PLANNED_SHUTDOWN", facts.shutdownId());
        addUnless(blockers, facts.criticalReadinessComplete(), "READINESS_CRITICAL_INCOMPLETE",
                "A critical readiness item is incomplete", "PLANNED_SHUTDOWN", facts.shutdownId());
        boolean inWindow = facts.approvedWindowPresent() && facts.approvedStartAt() != null
                && facts.approvedEndAt() != null && facts.evaluatedAt() != null
                && !facts.evaluatedAt().isBefore(facts.approvedStartAt())
                && !facts.evaluatedAt().isAfter(facts.approvedEndAt());
        addUnless(blockers, inWindow, "WINDOW_OUTSIDE_APPROVED", "Current time is outside the approved window",
                "PLANNED_SHUTDOWN", facts.shutdownId());
        return assessment(blockers);
    }

    public PlannedShutdownReadinessAssessment evaluateSafeState(Facts facts) {
        List<PlannedShutdownBlocker> blockers = new ArrayList<>(evaluateReadiness(facts).blockers());
        facts.isolation().stream().filter(IsolationFact::configured).filter(point -> !point.applied())
                .forEach(point -> add(blockers, "ISOLATION_NOT_APPLIED", "Isolation point is not applied",
                        "ISOLATION_POINT", point.id()));
        facts.isolation().stream().filter(IsolationFact::configured).filter(IsolationFact::applied)
                .filter(point -> !point.verified())
                .forEach(point -> add(blockers, "ISOLATION_NOT_VERIFIED", "Isolation point is not verified",
                        "ISOLATION_POINT", point.id()));
        return assessment(blockers);
    }

    private static PlannedShutdownReadinessAssessment assessment(List<PlannedShutdownBlocker> blockers) {
        List<PlannedShutdownBlocker> ordered = blockers.stream().distinct().sorted(ORDER).toList();
        return new PlannedShutdownReadinessAssessment(ordered.isEmpty(), ordered);
    }

    private static void addUnless(List<PlannedShutdownBlocker> blockers, boolean condition, String code,
            String message, String entityType, UUID entityId) {
        if (!condition) add(blockers, code, message, entityType, entityId);
    }

    private static void add(List<PlannedShutdownBlocker> blockers, String code, String message,
            String entityType, UUID entityId) {
        blockers.add(new PlannedShutdownBlocker(code, message, entityType, entityId));
    }

    public record WorkFact(UUID id, boolean critical, boolean requiresIsolation, boolean materialReserved,
                           boolean performerOrContractorAssigned) {
    }

    public record IsolationFact(UUID id, boolean configured, boolean applied, boolean verified) {
    }

    public record Facts(UUID shutdownId, boolean hasBoundary, boolean hasResponsibleOwner, boolean hasWork,
                        List<WorkFact> work, boolean approvalComplete, boolean approvalScopeCurrent,
                        boolean criticalReadinessComplete,
                        List<IsolationFact> isolation, boolean permitsActive, boolean approvedWindowPresent,
                        Instant approvedStartAt, Instant approvedEndAt, Instant evaluatedAt) {
        public Facts {
            work = work == null ? List.of() : List.copyOf(work);
            isolation = isolation == null ? List.of() : List.copyOf(isolation);
        }
    }
}
