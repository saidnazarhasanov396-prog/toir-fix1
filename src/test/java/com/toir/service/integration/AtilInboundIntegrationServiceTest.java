package com.toir.service.integration;

import com.toir.dto.integration.atil.AtilMeterReadingImportRequest;
import com.toir.entity.ExternalEntityLink;
import com.toir.enums.MeterType;
import com.toir.repository.ExternalEntityLinkRepository;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.service.MeterService;
import com.toir.service.VehicleService;
import com.toir.service.repair.RepairRequestService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AtilInboundIntegrationServiceTest {

    @Test
    void repeatedMeterReadingExternalIdIsSkippedWithoutCreatingAnotherReading() {
        MeterService meterService = mock(MeterService.class);
        ExternalEntityLinkRepository linkRepository = mock(ExternalEntityLinkRepository.class);
        AtilInboundIntegrationService service = new AtilInboundIntegrationService(
                mock(VehicleService.class),
                meterService,
                mock(RepairRequestService.class),
                mock(VehicleDetailsRepository.class),
                mock(EquipmentRepository.class),
                mock(EquipmentMeterRepository.class),
                linkRepository
        );
        UUID readingId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        ExternalEntityLink existingLink = new ExternalEntityLink();
        existingLink.setTargetEntityId(UUID.randomUUID());
        when(linkRepository.findBySourceSystemAndSourceEntityTypeAndSourceEntityIdAndIsDeletedFalse(
                "ATIL", "METER_READING", readingId.toString()))
                .thenReturn(Optional.of(existingLink));
        AtilMeterReadingImportRequest repeated = new AtilMeterReadingImportRequest(
                readingId,
                vehicleId,
                null,
                MeterType.MILEAGE_KM,
                1250.0,
                Instant.parse("2026-07-10T06:00:00Z"),
                "test-device",
                null
        );

        var result = service.importMeterReadings(List.of(repeated));

        assertThat(result.created()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.items()).singleElement()
                .extracting(item -> item.action() + ":" + item.status())
                .isEqualTo("SKIPPED_EXISTING:OK");
        verifyNoInteractions(meterService);
    }
}
