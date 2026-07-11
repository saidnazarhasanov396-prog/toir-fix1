package com.toir.security;

import com.toir.controller.PlannedShutdownController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlannedShutdownLifecyclePermissionContractTest {
    @Test
    void everyLifecycleEndpointUsesItsExactOperationPermission() {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("formScope", "PLANNED_SHUTDOWN_UPDATE"),
                Map.entry("beginReadiness", "PLANNED_SHUTDOWN_UPDATE"),
                Map.entry("requestApproval", "PLANNED_SHUTDOWN_REQUEST_APPROVAL"),
                Map.entry("prepare", "PLANNED_SHUTDOWN_PREPARE"),
                Map.entry("startShutdown", "PLANNED_SHUTDOWN_PREPARE"),
                Map.entry("confirmSafeState", "PLANNED_SHUTDOWN_CONFIRM_SAFE_STATE"),
                Map.entry("startRepair", "PLANNED_SHUTDOWN_START_REPAIR"),
                Map.entry("startTesting", "PLANNED_SHUTDOWN_TEST"),
                Map.entry("createStartupTest", "PLANNED_SHUTDOWN_TEST"),
                Map.entry("recordStartupTestResult", "PLANNED_SHUTDOWN_TEST"),
                Map.entry("startStartup", "PLANNED_SHUTDOWN_STARTUP"),
                Map.entry("approveProductionReturn", "PLANNED_SHUTDOWN_STARTUP"),
                Map.entry("complete", "PLANNED_SHUTDOWN_STARTUP"),
                Map.entry("close", "PLANNED_SHUTDOWN_CLOSE"),
                Map.entry("startupTests", "PLANNED_SHUTDOWN_READ"),
                Map.entry("productionReturn", "PLANNED_SHUTDOWN_READ"),
                Map.entry("closureReport", "PLANNED_SHUTDOWN_READ"),
                Map.entry("cancel", "PLANNED_SHUTDOWN_CANCEL"),
                Map.entry("reschedule", "PLANNED_SHUTDOWN_RESCHEDULE"),
                Map.entry("extend", "PLANNED_SHUTDOWN_EXTEND"));

        expected.forEach((methodName, permission) -> {
            var method = java.util.Arrays.stream(PlannedShutdownController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
            String expression = method.getAnnotation(PreAuthorize.class).value();
            assertThat(expression).contains("hasAuthority('" + permission + "')");
            expected.values().stream().filter(other -> !other.equals(permission))
                    .forEach(other -> assertThat(expression).doesNotContain("hasAuthority('" + other + "')"));
        });
    }
}
