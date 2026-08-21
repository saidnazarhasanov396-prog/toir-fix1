package com.toir.controller.equipment;

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
 * Equipment-scoped PPR plans — primary read API for AI context loading.
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
            summary = "List PPR plans linked to equipment",
            description = "Returns plans attached via equipment target, equipment-type target, "
                    + "or existing PPR tasks for this equipment. Intended for AI / integrations. "
                    + "By default returns plan summaries (tasks empty, taskCount set). "
                    + "Pass includeTasks=true to include this equipment's tasks only."
    )
    public ResponseEntity<EquipmentPprPlansResponse> list(
            @PathVariable UUID equipmentId,
            @RequestParam(defaultValue = "false") boolean includeTasks
    ) {
        return ResponseEntity.ok(pprPlanService.findLinkedToEquipment(equipmentId, includeTasks));
    }
}
