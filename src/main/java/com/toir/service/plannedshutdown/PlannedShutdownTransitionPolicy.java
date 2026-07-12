package com.toir.service.plannedshutdown;

import com.toir.enums.PlannedShutdownStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class PlannedShutdownTransitionPolicy {
    private static final Map<PlannedShutdownStatus, Set<PlannedShutdownStatus>> FORWARD =
            new EnumMap<>(PlannedShutdownStatus.class);
    private static final Set<PlannedShutdownStatus> CANCELLABLE = EnumSet.range(
            PlannedShutdownStatus.DRAFT, PlannedShutdownStatus.PREPARATION);
    private static final Set<PlannedShutdownStatus> RESCHEDULABLE = EnumSet.range(
            PlannedShutdownStatus.DRAFT, PlannedShutdownStatus.PREPARATION);
    private static final Set<PlannedShutdownStatus> EXTENDABLE = EnumSet.of(
            PlannedShutdownStatus.SHUTDOWN_STARTED, PlannedShutdownStatus.SAFE_STATE,
            PlannedShutdownStatus.REPAIR_IN_PROGRESS, PlannedShutdownStatus.TESTING,
            PlannedShutdownStatus.STARTUP, PlannedShutdownStatus.EMERGENCY_EXTENDED);

    static {
        edge(PlannedShutdownStatus.DRAFT, PlannedShutdownStatus.SCOPE_FORMATION);
        edge(PlannedShutdownStatus.SCOPE_FORMATION, PlannedShutdownStatus.READINESS_CHECK);
        edge(PlannedShutdownStatus.READINESS_CHECK, PlannedShutdownStatus.PENDING_APPROVAL);
        edge(PlannedShutdownStatus.PENDING_APPROVAL, PlannedShutdownStatus.APPROVED);
        edge(PlannedShutdownStatus.APPROVED, PlannedShutdownStatus.PREPARATION);
        edge(PlannedShutdownStatus.PREPARATION, PlannedShutdownStatus.SHUTDOWN_STARTED);
        edge(PlannedShutdownStatus.SHUTDOWN_STARTED, PlannedShutdownStatus.SAFE_STATE);
        edge(PlannedShutdownStatus.SAFE_STATE, PlannedShutdownStatus.REPAIR_IN_PROGRESS);
        edge(PlannedShutdownStatus.REPAIR_IN_PROGRESS, PlannedShutdownStatus.TESTING);
        edge(PlannedShutdownStatus.TESTING, PlannedShutdownStatus.STARTUP);
        edge(PlannedShutdownStatus.STARTUP, PlannedShutdownStatus.COMPLETED);
        edge(PlannedShutdownStatus.COMPLETED, PlannedShutdownStatus.CLOSED);
    }

    public boolean canTransition(PlannedShutdownStatus from, PlannedShutdownStatus to) {
        return from != null && to != null && FORWARD.getOrDefault(from, Set.of()).contains(to);
    }

    public boolean canCancel(PlannedShutdownStatus status) { return CANCELLABLE.contains(status); }
    public boolean canReschedule(PlannedShutdownStatus status) { return RESCHEDULABLE.contains(status); }
    public boolean canExtend(PlannedShutdownStatus status) { return EXTENDABLE.contains(status); }

    private static void edge(PlannedShutdownStatus from, PlannedShutdownStatus to) {
        FORWARD.computeIfAbsent(from, ignored -> EnumSet.noneOf(PlannedShutdownStatus.class)).add(to);
    }
}
