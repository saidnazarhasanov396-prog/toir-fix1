package com.toir.controller;

import com.toir.controller.maintenance.MaintenanceTemplateSemanticSearchController;
import com.toir.dto.maintenanceembedding.MaintenanceTemplateSemanticSearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceTemplateSemanticSearchControllerContractTest {

    @Test
    void endpointIsPostSecuredAndResponseCannotExposeRawVectors() throws Exception {
        RequestMapping requestMapping = MaintenanceTemplateSemanticSearchController.class
                .getAnnotation(RequestMapping.class);
        PreAuthorize authorization = MaintenanceTemplateSemanticSearchController.class
                .getAnnotation(PreAuthorize.class);
        PostMapping postMapping = MaintenanceTemplateSemanticSearchController.class
                .getMethod("search", com.toir.dto.maintenanceembedding.MaintenanceTemplateSemanticSearchRequest.class)
                .getAnnotation(PostMapping.class);

        assertThat(requestMapping.value()).containsExactly("/api/v1/ai/maintenance-templates");
        assertThat(postMapping.value()).containsExactly("/semantic-search");
        assertThat(authorization.value()).contains("MAINTENANCE_TEMPLATE_SEMANTIC_SEARCH");
        assertThat(Arrays.stream(MaintenanceTemplateSemanticSearchResponse.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase()).toList())
                .doesNotContain("vector", "embedding", "query");
    }
}
