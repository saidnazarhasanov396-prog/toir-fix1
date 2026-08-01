package com.toir.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.toir.dto.pprplanning.PprPlanningSelectionRequest;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

class PprPlanningSessionControllerContractTest {

    @Test
    void exposesPlanningWorkspaceUnderStableRouteAndUsesPermissions() throws Exception {
        RequestMapping mapping = PprPlanningSessionController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping.value()).containsExactly("/api/ppr-planning-sessions");

        Method select = PprPlanningSessionController.class.getMethod(
                "select", UUID.class, PprPlanningSelectionRequest.class, String.class);
        assertThat(select.getAnnotation(PreAuthorize.class).value())
                .contains("PPR_PLAN_UPDATE")
                .doesNotContain("CHIEF_MECHANIC");
    }
}
