package com.toir.service.plannedshutdown;

import com.toir.enums.PlannedShutdownStatus;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class PlannedShutdownReadinessLifecyclePolicy {
    private static final Set<PlannedShutdownStatus> READINESS_DEFINITION = EnumSet.of(
            PlannedShutdownStatus.DRAFT, PlannedShutdownStatus.SCOPE_FORMATION, PlannedShutdownStatus.READINESS_CHECK);
    private static final Set<PlannedShutdownStatus> READINESS_ACTION = EnumSet.of(
            PlannedShutdownStatus.READINESS_CHECK, PlannedShutdownStatus.PREPARATION);
    private static final Set<PlannedShutdownStatus> ISOLATION_DEFINITION = EnumSet.of(
            PlannedShutdownStatus.DRAFT, PlannedShutdownStatus.SCOPE_FORMATION, PlannedShutdownStatus.READINESS_CHECK,
            PlannedShutdownStatus.PENDING_APPROVAL, PlannedShutdownStatus.APPROVED,
            PlannedShutdownStatus.PREPARATION, PlannedShutdownStatus.SHUTDOWN_STARTED);
    private static final Set<PlannedShutdownStatus> ISOLATION_APPLY = EnumSet.of(
            PlannedShutdownStatus.PREPARATION, PlannedShutdownStatus.SHUTDOWN_STARTED);
    private static final Set<PlannedShutdownStatus> ISOLATION_RELEASE = EnumSet.of(
            PlannedShutdownStatus.STARTUP, PlannedShutdownStatus.COMPLETED);

    public boolean canEditReadinessDefinition(PlannedShutdownStatus status) { return READINESS_DEFINITION.contains(status); }
    public boolean canActOnReadiness(PlannedShutdownStatus status) { return READINESS_ACTION.contains(status); }
    public boolean canEditIsolationDefinition(PlannedShutdownStatus status) { return ISOLATION_DEFINITION.contains(status); }
    public boolean canApplyIsolation(PlannedShutdownStatus status) { return ISOLATION_APPLY.contains(status); }
    public boolean canVerifyIsolation(PlannedShutdownStatus status) { return status == PlannedShutdownStatus.SHUTDOWN_STARTED; }
    public boolean canReleaseIsolation(PlannedShutdownStatus status) { return ISOLATION_RELEASE.contains(status); }
}
