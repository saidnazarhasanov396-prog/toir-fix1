package com.toir.service;

import com.toir.dto.conditionreading.ConditionReadingDto;
import com.toir.dto.conditionreading.ConditionReadingRequest;
import com.toir.entity.ConditionReading;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.ConditionParameter;
import com.toir.exception.RestException;
import com.toir.repository.ConditionReadingRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConditionReadingServiceTest {

    @Mock
    ConditionReadingRepository repo;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    DefectRepository defectRepository;

    @Mock
    WebhookService webhookService;

    @Mock
    UnitOfMeasurementService unitOfMeasurementService;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    ConditionReadingService service;

    @Test
    void recordWithKnownDictionaryUnitSucceeds() {
        UUID equipmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(unitOfMeasurementService.normalizeRequiredUnitOrThrow(" BAR ", "condition reading unit")).thenReturn("bar");
        when(repo.save(any(ConditionReading.class))).thenAnswer(invocation -> {
            ConditionReading reading = invocation.getArgument(0);
            reading.setId(UUID.randomUUID());
            return reading;
        });

        ConditionReadingDto dto = service.record(
                equipmentId,
                new ConditionReadingRequest(
                        ConditionParameter.TEMPERATURE,
                        78.5,
                        " BAR ",
                        null,
                        90.0,
                        100.0,
                        50.0,
                        40.0,
                        "ok"
                ),
                userId
        );

        assertThat(dto.equipmentId()).isEqualTo(equipmentId);
        assertThat(dto.unit()).isEqualTo("bar");
        verify(repo).save(any(ConditionReading.class));
    }

    @Test
    void recordWithUnknownDictionaryUnitReturnsBadRequest() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(unitOfMeasurementService.normalizeRequiredUnitOrThrow("mystery", "condition reading unit"))
                .thenThrow(RestException.badRequest("Unknown condition reading unit"));

        assertThatThrownBy(() -> service.record(
                equipmentId,
                new ConditionReadingRequest(
                        ConditionParameter.PRESSURE,
                        10.0,
                        "mystery",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                null
        )).isInstanceOf(RestException.class)
                .hasMessageContaining("Unknown");

        verify(repo, never()).save(any(ConditionReading.class));
    }
}
