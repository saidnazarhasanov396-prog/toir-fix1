package com.toir.controller.maintenance;

import com.toir.dto.maintenanceembedding.MaintenanceTemplateSemanticSearchRequest;
import com.toir.dto.maintenanceembedding.MaintenanceTemplateSemanticSearchResponse;
import com.toir.security.PermissionConstants;
import com.toir.service.maintenanceembedding.MaintenanceTemplateSemanticSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/maintenance-templates")
@Tag(name = "maintenance-template-semantic-search")
@PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('"
        + PermissionConstants.MAINTENANCE_TEMPLATE_SEMANTIC_SEARCH + "')")
@ConditionalOnProperty(
        prefix = "toir.ai.maintenance-action-semantic-search",
        name = {"enabled", "semantic-search-enabled"},
        havingValue = "true")
public class MaintenanceTemplateSemanticSearchController {

    private final MaintenanceTemplateSemanticSearchService service;

    public MaintenanceTemplateSemanticSearchController(MaintenanceTemplateSemanticSearchService service) {
        this.service = service;
    }

    @PostMapping("/semantic-search")
    @Operation(summary = "Find the top 10 unique Maintenance Templates by Maintenance Action meaning")
    public ResponseEntity<MaintenanceTemplateSemanticSearchResponse> search(
            @Valid @RequestBody MaintenanceTemplateSemanticSearchRequest request
    ) {
        return ResponseEntity.ok(service.search(request.query()));
    }
}
