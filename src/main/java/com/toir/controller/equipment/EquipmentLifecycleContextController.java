package com.toir.controller.equipment;

import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextV1;
import com.toir.service.equipmentlifecycle.EquipmentLifecycleContextAssembler;
import com.toir.service.equipmentlifecycleexport.EquipmentLifecycleExportProfileResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/equipment/{equipmentId}/lifecycle-context")
@PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
public class EquipmentLifecycleContextController {

    private final EquipmentLifecycleContextAssembler assembler;
    private final EquipmentLifecycleExportProfileResolver profileResolver;
    private final Clock clock;

    public EquipmentLifecycleContextController(
            EquipmentLifecycleContextAssembler assembler,
            EquipmentLifecycleExportProfileResolver profileResolver,
            @Qualifier("equipmentLifecycleClock") Clock clock
    ) {
        this.assembler = assembler;
        this.profileResolver = profileResolver;
        this.clock = clock;
    }

    @GetMapping
    public EquipmentLifecycleContextV1 get(
            @PathVariable UUID equipmentId,
            @RequestParam(required = false) Instant asOf
    ) {
        Instant effectiveAsOf = asOf == null ? clock.instant() : asOf;
        var resolved = profileResolver.resolve(
                EquipmentLifecycleExportProfileResolver.STANDARD_V1,
                effectiveAsOf
        );
        return assembler.assemble(equipmentId, effectiveAsOf, resolved.policy());
    }
}
