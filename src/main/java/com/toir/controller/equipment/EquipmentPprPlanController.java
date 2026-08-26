package com.toir.controller.equipment;

import com.toir.dto.pprplanning.EquipmentPprPlannedWorksResponse;
import com.toir.dto.pprplanning.EquipmentPprPlansResponse;
import com.toir.service.PprPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Equipment-scoped PPR plans for AI / integrations.
 */
@RestController
@RequestMapping("/api/v1/equipment/{equipmentId}/ppr-plans")
@RequiredArgsConstructor
@Tag(name = "equipment-ppr-plans")
public class EquipmentPprPlanController {

    private static final String READ_AUTH =
            "hasAnyAuthority('PPR_CALENDAR_READ','EQUIPMENT_READ','SYSTEM_ADMIN','*')";

    private final PprPlanService pprPlanService;

    @GetMapping
    @PreAuthorize(READ_AUTH)
    @Operation(
            summary = "List PPR plans linked to equipment (includes type-level)",
            description = "Returns all non-deleted plans for this equipment. Each plan includes unique "
                    + "planned works (regulation/rule), not every yearly generated task. "
                    + "Pass includeTasks=true only if full task instances are needed."
    )
    public ResponseEntity<EquipmentPprPlansResponse> list(
            @PathVariable UUID equipmentId,
            @RequestParam(defaultValue = "false") boolean includeTasks
    ) {
        return ResponseEntity.ok(pprPlanService.findLinkedToEquipment(equipmentId, includeTasks));
    }

    @GetMapping("/direct")
    @PreAuthorize(READ_AUTH)
    @Operation(
            summary = "List unique planned works for this equipment only",
            description = "Returns one planned work per unique work type (oil change, motor repair, "
                    + "general, and so on) for this concrete equipment only. Repeated yearly "
                    + "tasks collapse into the latest occurrence. Does not include type-only plans "
                    + "and does not return full PPR plan payloads."
    )
    public ResponseEntity<EquipmentPprPlannedWorksResponse> listDirect(
            @PathVariable UUID equipmentId
    ) {
        return ResponseEntity.ok(pprPlanService.findDirectPlannedWorks(equipmentId));
    }
}
