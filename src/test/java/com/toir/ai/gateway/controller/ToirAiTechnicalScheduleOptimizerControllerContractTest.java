package com.toir.ai.gateway.controller;

import com.toir.security.PermissionConstants;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

class ToirAiTechnicalScheduleOptimizerControllerContractTest {

    @Test
    void endpointIsGetSecuredAndProxiesWeibullIntervals() throws Exception {
        RequestMapping requestMapping = ToirAiTechnicalScheduleOptimizerController.class
                .getAnnotation(RequestMapping.class);
        PreAuthorize authorization = ToirAiTechnicalScheduleOptimizerController.class
                .getAnnotation(PreAuthorize.class);
        GetMapping getMapping = ToirAiTechnicalScheduleOptimizerController.class
                .getMethod("maintenanceKindIntervals", java.util.UUID.class)
                .getAnnotation(GetMapping.class);

        assertThat(requestMapping.value()).containsExactly("/api/v1/ai/technical-schedule-optimizer");
        assertThat(getMapping.value()).containsExactly("/equipment/{equipmentId}/maintenance-kind-intervals");
        assertThat(authorization.value()).contains(PermissionConstants.AI_GATEWAY_EXECUTE);
    }
}
