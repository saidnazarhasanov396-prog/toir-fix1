package com.toir.controller.equipment;

import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextPolicy;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextV1;
import com.toir.service.equipmentlifecycle.EquipmentLifecycleContextAssembler;
import com.toir.service.equipmentlifecycleexport.EquipmentLifecycleExportProfileResolver;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EquipmentLifecycleContextControllerTest {

    @Test
    void exposesTheAssemblerThroughTheEquipmentReadApi() {
        UUID equipmentId = UUID.randomUUID();
        Instant asOf = Instant.parse("2026-07-31T10:00:00Z");
        EquipmentLifecycleContextAssembler assembler = mock(EquipmentLifecycleContextAssembler.class);
        EquipmentLifecycleExportProfileResolver resolver = mock(EquipmentLifecycleExportProfileResolver.class);
        EquipmentLifecycleContextPolicy policy = new EquipmentLifecycleContextPolicy(
                Instant.EPOCH, Duration.ZERO, Set.of(), Map.of(),
                EquipmentLifecycleContextPolicy.MeasurementGranularity.RAW
        );
        EquipmentLifecycleContextV1 context = null;
        when(resolver.resolve(EquipmentLifecycleExportProfileResolver.STANDARD_V1, asOf))
                .thenReturn(new EquipmentLifecycleExportProfileResolver.ResolvedProfile(
                        EquipmentLifecycleExportProfileResolver.STANDARD_V1, policy, "{}", "fingerprint"
                ));
        when(assembler.assemble(equipmentId, asOf, policy)).thenReturn(context);
        EquipmentLifecycleContextController controller = new EquipmentLifecycleContextController(
                assembler, resolver, Clock.fixed(asOf, ZoneOffset.UTC)
        );

        assertThat(controller.get(equipmentId, null)).isNull();
        verify(assembler).assemble(equipmentId, asOf, policy);
    }
}
