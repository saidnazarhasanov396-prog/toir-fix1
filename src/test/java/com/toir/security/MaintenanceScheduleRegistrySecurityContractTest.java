package com.toir.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.toir.controller.maintenance.MaintenanceScheduleCalculationController;
import com.toir.controller.maintenance.MaintenanceScheduleCalculationDashboardController;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

class MaintenanceScheduleRegistrySecurityContractTest {

    private static final String REGISTRY_READ_AUTH =
            "hasAnyAuthority('PPR_PLAN_READ','PPR_PLAN_CREATE','PPR_PLAN_UPDATE','PPR_PLAN_DELETE',"
                    + "'PPR_PLAN_APPROVE','PPR_PLAN_GENERATE','SYSTEM_ADMIN','*')";

    @Test
    void calculationRegistryReadsAllowPlanReadOrPlanActions() {
        assertGetMappingsRequireRegistryRead(
                MaintenanceScheduleCalculationController.class,
                Set.of("list", "get"));
        assertGetMappingsRequireRegistryRead(
                MaintenanceScheduleCalculationDashboardController.class,
                Set.of("stats", "listByLifecycle"));
    }

    private void assertGetMappingsRequireRegistryRead(
            Class<?> controllerType,
            Set<String> methodNames) {
        Method[] endpoints = Arrays.stream(controllerType.getDeclaredMethods())
                .filter(method -> methodNames.contains(method.getName()))
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .toArray(Method[]::new);

        assertThat(endpoints).hasSize(methodNames.size());
        assertThat(endpoints)
                .allSatisfy(method -> assertThat(method.getAnnotation(PreAuthorize.class).value())
                        .isEqualTo(REGISTRY_READ_AUTH));
    }
}
