package com.toir.service.equipment;

import com.toir.dto.equipmentattribute.EquipmentAttributeDefinitionRequest;
import com.toir.dto.equipmentattribute.EquipmentAttributeOptionDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentAttributeDefinition;
import com.toir.entity.equipment.EquipmentAttributeRequiredCriticality;
import com.toir.entity.equipment.EquipmentAttributeValue;
import com.toir.entity.equipment.EquipmentAttributeValueHistory;
import com.toir.entity.equipment.EquipmentType;
import com.toir.enums.EquipmentAttributeDataType;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentAttributeDefinitionRepository;
import com.toir.repository.equipment.EquipmentAttributeRequiredCriticalityRepository;
import com.toir.repository.equipment.EquipmentAttributeValueHistoryRepository;
import com.toir.repository.equipment.EquipmentAttributeValueRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentAttributeServiceTest {

    @Mock
    EquipmentAttributeDefinitionRepository definitionRepository;

    @Mock
    EquipmentAttributeValueRepository valueRepository;

    @Mock
    EquipmentTypeRepository equipmentTypeRepository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    com.toir.repository.equipment.EquipmentAttributeOptionSourceRepository optionSourceRepository;

    @Mock
    com.toir.repository.equipment.EquipmentAttributeOptionItemRepository optionItemRepository;

    @Mock
    EquipmentAttributeRequiredCriticalityRepository requiredCriticalityRepository;

    @Mock
    EquipmentAttributeValueHistoryRepository valueHistoryRepository;

    @Mock
    com.toir.repository.CriticalityClassRepository criticalityClassRepository;

    @InjectMocks
    EquipmentAttributeService service;

    @Test
    void createDefinitionPersistsPumpAttribute() {
        UUID equipmentTypeId = UUID.randomUUID();
        stubEquipmentType(equipmentTypeId);
        when(definitionRepository.existsActiveByEquipmentTypeIdAndKey(equipmentTypeId, "motor_power")).thenReturn(false);
        when(definitionRepository.save(any(EquipmentAttributeDefinition.class))).thenAnswer(invocation -> {
            EquipmentAttributeDefinition definition = invocation.getArgument(0);
            definition.setId(UUID.randomUUID());
            return definition;
        });

        var dto = service.createDefinition(equipmentTypeId, new EquipmentAttributeDefinitionRequest(
                "Motor_Power",
                "Motor Power",
                "Мощность двигателя",
                "Dvigatel quvvati",
                EquipmentAttributeDataType.NUMBER,
                "kW",
                true,
                0.0,
                500.0,
                null,
                List.of(),
                "Motor",
                10
        ));

        assertThat(dto.key()).isEqualTo("motor_power");
        assertThat(dto.dataType()).isEqualTo(EquipmentAttributeDataType.NUMBER);
        assertThat(dto.required()).isTrue();
        assertThat(dto.unit()).isEqualTo("kW");

        ArgumentCaptor<EquipmentAttributeDefinition> captor =
                ArgumentCaptor.forClass(EquipmentAttributeDefinition.class);
        verify(definitionRepository).save(captor.capture());
        assertThat(captor.getValue().getEquipmentTypeId()).isEqualTo(equipmentTypeId);
        assertThat(captor.getValue().getMinValue()).isZero();
        assertThat(captor.getValue().getMaxValue()).isEqualTo(500.0);
    }

    @Test
    void updateDefinitionRequiresAttributeToBelongToType() {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID otherTypeId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        stubEquipmentType(equipmentTypeId);
        EquipmentAttributeDefinition definition = definition(definitionId, otherTypeId, "motor_power",
                EquipmentAttributeDataType.NUMBER, true);
        when(definitionRepository.findByIdAndIsDeletedFalse(definitionId)).thenReturn(Optional.of(definition));

        assertThatThrownBy(() -> service.updateDefinition(equipmentTypeId, definitionId, definitionRequest("motor_power")))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("does not belong");
                });
    }

    @Test
    void deleteDefinitionSoftDeletes() {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        stubEquipmentType(equipmentTypeId);
        EquipmentAttributeDefinition definition = definition(definitionId, equipmentTypeId, "seal_type",
                EquipmentAttributeDataType.SELECT, false);
        when(definitionRepository.findByIdAndIsDeletedFalse(definitionId)).thenReturn(Optional.of(definition));

        service.deleteDefinition(equipmentTypeId, definitionId);

        assertThat(definition.isDeleted()).isTrue();
        verify(definitionRepository).save(definition);
    }

    @Test
    void upsertValuesByKeyPersistsPumpAttributes() {
        UUID typeId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        EquipmentAttributeDefinition motorPower = definition(UUID.randomUUID(), typeId, "motor_power",
                EquipmentAttributeDataType.NUMBER, true);
        motorPower.setMinValue(0.0);
        motorPower.setMaxValue(500.0);
        EquipmentAttributeDefinition sealType = definition(UUID.randomUUID(), typeId, "seal_type",
                EquipmentAttributeDataType.SELECT, false);
        sealType.setOptions(List.of(option("mechanical_seal", "Mechanical seal"), option("packing", "Packing")));
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(motorPower, sealType));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of());

        service.upsertValues(equipment, List.of(
                new EquipmentAttributeValueRequest(null, "motor_power", null, 75.0, null, null, null, null),
                new EquipmentAttributeValueRequest(null, "seal_type", null, null, null, null, "mechanical_seal", null)
        ));

        ArgumentCaptor<Iterable<EquipmentAttributeValue>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(valueRepository).saveAll(captor.capture());
        List<EquipmentAttributeValue> saved = toList(captor.getValue());
        assertThat(saved).hasSize(2);
        assertThat(saved).anySatisfy(value -> {
            assertThat(value.getAttributeDefinitionId()).isEqualTo(motorPower.getId());
            assertThat(value.getValueNumber()).isEqualTo(75.0);
        });
        assertThat(saved).anySatisfy(value -> {
            assertThat(value.getAttributeDefinitionId()).isEqualTo(sealType.getId());
            assertThat(value.getValueOption()).isEqualTo("mechanical_seal");
        });
    }

    @Test
    void upsertValuesByAttributeDefinitionIdPersistsValue() {
        UUID typeId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        EquipmentAttributeDefinition flowRate = definition(UUID.randomUUID(), typeId, "flow_rate",
                EquipmentAttributeDataType.NUMBER, false);
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(flowRate));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of());

        service.upsertValues(equipment, List.of(
                new EquipmentAttributeValueRequest(flowRate.getId(), null, null, 250.0, null, null, null, null)
        ));

        ArgumentCaptor<Iterable<EquipmentAttributeValue>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(valueRepository).saveAll(captor.capture());
        EquipmentAttributeValue saved = toList(captor.getValue()).getFirst();
        assertThat(saved.getAttributeDefinitionId()).isEqualTo(flowRate.getId());
        assertThat(saved.getValueNumber()).isEqualTo(250.0);
    }

    @ParameterizedTest
    @MethodSource("validValueFields")
    void upsertValuesAcceptsCorrectValueFieldByDataType(EquipmentAttributeDataType dataType,
                                                        EquipmentAttributeValueRequest request,
                                                        String expectedKey) {
        UUID typeId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        EquipmentAttributeDefinition definition = definition(UUID.randomUUID(), typeId, expectedKey, dataType, false);
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(definition));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of());

        service.upsertValues(equipment, List.of(request));

        verify(valueRepository).saveAll(any());
    }

    @ParameterizedTest
    @MethodSource("invalidValueFields")
    void upsertValuesRejectsWrongValueFieldByDataType(EquipmentAttributeDataType dataType,
                                                      EquipmentAttributeValueRequest request,
                                                      String expectedKey,
                                                      String expectedField) {
        UUID typeId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        EquipmentAttributeDefinition definition = definition(UUID.randomUUID(), typeId, expectedKey, dataType, false);
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(definition));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.upsertValues(equipment, List.of(request)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains(expectedField);
                });
        verify(valueRepository, never()).saveAll(any());
    }

    @Test
    void upsertValuesRejectsUnknownKey() {
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID(), typeId);
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of());
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipment.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.upsertValues(equipment, List.of(
                new EquipmentAttributeValueRequest(null, "unknown_key", "value", null, null, null, null, null)
        ))).isInstanceOfSatisfying(RestException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getMessage()).contains("Unknown equipment attribute key");
        });
    }

    @Test
    void upsertValuesRejectsDefinitionFromAnotherType() {
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID(), typeId);
        UUID foreignDefinitionId = UUID.randomUUID();
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of());
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipment.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.upsertValues(equipment, List.of(
                new EquipmentAttributeValueRequest(foreignDefinitionId, null, null, 75.0, null, null, null, null)
        ))).isInstanceOfSatisfying(RestException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getMessage()).contains("not allowed for this equipment type");
        });
    }

    @Test
    void upsertValuesRejectsDuplicateValues() {
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID(), typeId);
        EquipmentAttributeDefinition motorPower = definition(UUID.randomUUID(), typeId, "motor_power",
                EquipmentAttributeDataType.NUMBER, false);
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(motorPower));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipment.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.upsertValues(equipment, List.of(
                new EquipmentAttributeValueRequest(null, "motor_power", null, 75.0, null, null, null, null),
                new EquipmentAttributeValueRequest(motorPower.getId(), null, null, 90.0, null, null, null, null)
        ))).isInstanceOfSatisfying(RestException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getMessage()).contains("Duplicate");
        });
    }

    @Test
    void upsertValuesRejectsMissingRequiredAttribute() {
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID(), typeId);
        EquipmentAttributeDefinition motorPower = definition(UUID.randomUUID(), typeId, "motor_power",
                EquipmentAttributeDataType.NUMBER, true);
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(motorPower));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipment.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.upsertValues(equipment, List.of()))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Missing required equipment attributes");
                });
    }

    @Test
    void createDefinition_withRequiredCriticalityClasses_persistsPolicy() {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID criticalityId = UUID.randomUUID();
        stubEquipmentType(equipmentTypeId);
        when(definitionRepository.existsActiveByEquipmentTypeIdAndKey(equipmentTypeId, "vibration_limit")).thenReturn(false);
        when(criticalityClassRepository.findAllByIdInAndIsDeletedFalse(List.of(criticalityId))).thenReturn(List.of(criticalityClass(criticalityId)));
        when(definitionRepository.save(any(EquipmentAttributeDefinition.class))).thenAnswer(invocation -> {
            EquipmentAttributeDefinition definition = invocation.getArgument(0);
            definition.setId(UUID.randomUUID());
            return definition;
        });
        var dto = service.createDefinition(equipmentTypeId, new EquipmentAttributeDefinitionRequest(
                "vibration_limit",
                "Vibration Limit",
                null,
                null,
                EquipmentAttributeDataType.NUMBER,
                "mm/s",
                false,
                null,
                null,
                null,
                List.of(),
                "Monitoring",
                10,
                List.of(criticalityId)
        ));

        assertThat(dto.requiredForCriticalityClassIds()).containsExactly(criticalityId);
        ArgumentCaptor<Iterable<EquipmentAttributeRequiredCriticality>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(requiredCriticalityRepository).saveAll(captor.capture());
        assertThat(toRequiredCriticalityList(captor.getValue()))
                .extracting(EquipmentAttributeRequiredCriticality::getCriticalityClassId)
                .containsExactly(criticalityId);
    }

    @Test
    void updateDefinition_replacesRequiredCriticalityClasses() {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        UUID oldCriticalityId = UUID.randomUUID();
        UUID newCriticalityId = UUID.randomUUID();
        stubEquipmentType(equipmentTypeId);
        EquipmentAttributeDefinition definition = definition(definitionId, equipmentTypeId, "vibration_limit",
                EquipmentAttributeDataType.NUMBER, false);
        EquipmentAttributeRequiredCriticality oldPolicy = requiredCriticality(definitionId, oldCriticalityId);
        when(definitionRepository.findByIdAndIsDeletedFalse(definitionId)).thenReturn(Optional.of(definition));
        when(criticalityClassRepository.findAllByIdInAndIsDeletedFalse(List.of(newCriticalityId))).thenReturn(List.of(criticalityClass(newCriticalityId)));
        when(requiredCriticalityRepository.findAllByAttributeDefinitionIdAndIsDeletedFalse(definitionId)).thenReturn(List.of(oldPolicy));
        when(definitionRepository.save(definition)).thenReturn(definition);
        var dto = service.updateDefinition(equipmentTypeId, definitionId, new EquipmentAttributeDefinitionRequest(
                "vibration_limit",
                "Vibration Limit",
                null,
                null,
                EquipmentAttributeDataType.NUMBER,
                "mm/s",
                false,
                null,
                null,
                null,
                List.of(),
                "Monitoring",
                10,
                List.of(newCriticalityId)
        ));

        assertThat(oldPolicy.isDeleted()).isTrue();
        verify(requiredCriticalityRepository).saveAll(List.of(oldPolicy));
        assertThat(dto.requiredForCriticalityClassIds()).containsExactly(newCriticalityId);
    }

    @Test
    void upsertValues_missingAlwaysRequiredAttribute_returnsBadRequest() {
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID(), typeId);
        EquipmentAttributeDefinition motorPower = definition(UUID.randomUUID(), typeId, "motor_power",
                EquipmentAttributeDataType.NUMBER, true);
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(motorPower));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipment.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.upsertValues(equipment, List.of()))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("motor_power").contains("required by equipment type");
                });
    }

    @Test
    void upsertValues_missingCriticalityRequiredAttribute_returnsBadRequest() {
        UUID typeId = UUID.randomUUID();
        UUID criticalityId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID(), typeId);
        equipment.setCriticalityClassId(criticalityId);
        EquipmentAttributeDefinition vibrationLimit = definition(UUID.randomUUID(), typeId, "vibration_limit",
                EquipmentAttributeDataType.NUMBER, false);
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(vibrationLimit));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipment.getId())).thenReturn(List.of());
        when(requiredCriticalityRepository.findAllByAttributeDefinitionIdInAndIsDeletedFalse(List.of(vibrationLimit.getId())))
                .thenReturn(List.of(requiredCriticality(vibrationLimit.getId(), criticalityId)));

        assertThatThrownBy(() -> service.upsertValues(equipment, List.of()))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("vibration_limit").contains("required by criticality");
                });
    }

    @Test
    void upsertValues_criticalityRequiredAttributePresent_passes() {
        UUID typeId = UUID.randomUUID();
        UUID criticalityId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID(), typeId);
        equipment.setCriticalityClassId(criticalityId);
        EquipmentAttributeDefinition vibrationLimit = definition(UUID.randomUUID(), typeId, "vibration_limit",
                EquipmentAttributeDataType.NUMBER, false);
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(vibrationLimit));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipment.getId())).thenReturn(List.of());
        when(requiredCriticalityRepository.findAllByAttributeDefinitionIdInAndIsDeletedFalse(List.of(vibrationLimit.getId())))
                .thenReturn(List.of(requiredCriticality(vibrationLimit.getId(), criticalityId)));

        service.upsertValues(equipment, List.of(
                new EquipmentAttributeValueRequest(null, "vibration_limit", null, 4.2, null, null, null, null)
        ));

        verify(valueRepository).saveAll(any());
    }

    @Test
    void upsertValues_noEquipmentCriticality_ignoresCriticalityRequiredPolicy() {
        UUID typeId = UUID.randomUUID();
        UUID criticalityId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID(), typeId);
        EquipmentAttributeDefinition vibrationLimit = definition(UUID.randomUUID(), typeId, "vibration_limit",
                EquipmentAttributeDataType.NUMBER, false);
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(vibrationLimit));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipment.getId())).thenReturn(List.of());
        when(requiredCriticalityRepository.findAllByAttributeDefinitionIdInAndIsDeletedFalse(List.of(vibrationLimit.getId())))
                .thenReturn(List.of(requiredCriticality(vibrationLimit.getId(), criticalityId)));

        service.upsertValues(equipment, List.of());

        verify(valueRepository, never()).saveAll(any());
    }

    @Test
    void createDefinition_unknownCriticalityClass_returnsNotFoundOrBadRequest() {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID missingCriticalityId = UUID.randomUUID();
        stubEquipmentType(equipmentTypeId);
        when(definitionRepository.existsActiveByEquipmentTypeIdAndKey(equipmentTypeId, "vibration_limit")).thenReturn(false);
        when(criticalityClassRepository.findAllByIdInAndIsDeletedFalse(List.of(missingCriticalityId))).thenReturn(List.of());

        assertThatThrownBy(() -> service.createDefinition(equipmentTypeId, new EquipmentAttributeDefinitionRequest(
                "vibration_limit",
                "Vibration Limit",
                null,
                null,
                EquipmentAttributeDataType.NUMBER,
                "mm/s",
                false,
                null,
                null,
                null,
                List.of(),
                "Monitoring",
                10,
                List.of(missingCriticalityId)
        ))).isInstanceOfSatisfying(RestException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(ex.getMessage()).contains("Criticality class not found");
        });
        verify(definitionRepository, never()).save(any());
    }

    @Test
    void upsertValuesRejectsNumberOutsideMinMax() {
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID(), typeId);
        EquipmentAttributeDefinition motorPower = definition(UUID.randomUUID(), typeId, "motor_power",
                EquipmentAttributeDataType.NUMBER, true);
        motorPower.setMinValue(0.0);
        motorPower.setMaxValue(500.0);
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(motorPower));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipment.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.upsertValues(equipment, List.of(
                new EquipmentAttributeValueRequest(null, "motor_power", null, 501.0, null, null, null, null)
        ))).isInstanceOfSatisfying(RestException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getMessage()).contains("above maxValue");
        });
    }

    @Test
    void upsertValuesRejectsRangeOutsideMinMax() {
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID(), typeId);
        EquipmentAttributeDefinition pressureRange = definition(UUID.randomUUID(), typeId, "pressure_range",
                EquipmentAttributeDataType.RANGE, false);
        pressureRange.setMinValue(8.0);
        pressureRange.setMaxValue(12.0);
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(pressureRange));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipment.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.upsertValues(equipment, List.of(
                new EquipmentAttributeValueRequest(null, "pressure_range", null, 7.5, null, null, null, null)
        ))).isInstanceOfSatisfying(RestException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getMessage()).contains("below minValue");
        });
    }

    @Test
    void upsertValuesRejectsSelectOutsideAllowedOptions() {
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID(), typeId);
        EquipmentAttributeDefinition sealType = definition(UUID.randomUUID(), typeId, "seal_type",
                EquipmentAttributeDataType.SELECT, false);
        sealType.setOptions(List.of(option("mechanical_seal", "Mechanical seal"), option("packing", "Packing")));
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(sealType));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipment.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.upsertValues(equipment, List.of(
                new EquipmentAttributeValueRequest(null, "seal_type", null, null, null, null, "Invalid seal", null)
        ))).isInstanceOfSatisfying(RestException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getMessage()).contains("option is not allowed");
        });
    }

    @Test
    void upsertValuesRejectsInactiveSelectOptionId() {
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID(), typeId);
        EquipmentAttributeDefinition sealType = definition(UUID.randomUUID(), typeId, "seal_type",
                EquipmentAttributeDataType.SELECT, false);
        sealType.setOptions(List.of(
                option("mechanical_seal", "Mechanical seal"),
                new EquipmentAttributeOptionDto("obsolete_seal", "Obsolete seal", null, null, 20, false)
        ));
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(sealType));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipment.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.upsertValues(equipment, List.of(
                new EquipmentAttributeValueRequest(null, "seal_type", null, null, null, null, "obsolete_seal", null)
        ))).isInstanceOfSatisfying(RestException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getMessage()).contains("option is not allowed");
        });
    }

    @Test
    void replaceValuesReturnsDefinitionsWithPersistedValues() {
        UUID typeId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        EquipmentAttributeDefinition motorPower = definition(UUID.randomUUID(), typeId, "motor_power",
                EquipmentAttributeDataType.NUMBER, true);
        EquipmentAttributeValue value = value(equipmentId, motorPower.getId());
        value.setValueNumber(90.0);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(motorPower));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId))
                .thenReturn(List.of())
                .thenReturn(List.of(value));

        List<EquipmentAttributeValueDto> result = service.replaceValues(equipmentId, List.of(
                new EquipmentAttributeValueRequest(null, "motor_power", null, 90.0, null, null, null, null)
        ));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().equipmentId()).isEqualTo(equipmentId);
        assertThat(result.getFirst().key()).isEqualTo("motor_power");
        assertThat(result.getFirst().valueNumber()).isEqualTo(90.0);
    }

    @Test
    void upsertValues_createsHistoryForNewValue() {
        UUID typeId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        EquipmentAttributeDefinition motorPower = definition(UUID.randomUUID(), typeId, "motor_power",
                EquipmentAttributeDataType.NUMBER, false);
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(motorPower));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of());

        service.upsertValues(equipment, List.of(
                new EquipmentAttributeValueRequest(null, "motor_power", null, 75.0, null, null, null, null)
        ));

        ArgumentCaptor<Iterable<EquipmentAttributeValueHistory>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(valueHistoryRepository).saveAll(captor.capture());
        EquipmentAttributeValueHistory history = toHistoryList(captor.getValue()).getFirst();
        assertThat(history.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(history.getAttributeDefinitionId()).isEqualTo(motorPower.getId());
        assertThat(history.getAttributeKey()).isEqualTo("motor_power");
        assertThat(history.getOldValue()).isNull();
        assertThat(history.getNewValue()).isEqualTo("75");
        assertThat(history.getSource().name()).isEqualTo("API");
        assertThat(history.getChangedAt()).isNotNull();
    }

    @Test
    void upsertValues_createsHistoryForChangedValue() {
        UUID typeId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        EquipmentAttributeDefinition motorPower = definition(UUID.randomUUID(), typeId, "motor_power",
                EquipmentAttributeDataType.NUMBER, false);
        EquipmentAttributeValue existing = value(equipmentId, motorPower.getId());
        existing.setValueNumber(75.0);
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(motorPower));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of(existing));

        service.upsertValues(equipment, List.of(
                new EquipmentAttributeValueRequest(null, "motor_power", null, 90.0, null, null, null, null)
        ));

        ArgumentCaptor<Iterable<EquipmentAttributeValueHistory>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(valueHistoryRepository).saveAll(captor.capture());
        EquipmentAttributeValueHistory history = toHistoryList(captor.getValue()).getFirst();
        assertThat(history.getOldValue()).isEqualTo("75");
        assertThat(history.getNewValue()).isEqualTo("90");
    }

    @Test
    void upsertValues_doesNotCreateHistoryForUnchangedValue() {
        UUID typeId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        EquipmentAttributeDefinition motorPower = definition(UUID.randomUUID(), typeId, "motor_power",
                EquipmentAttributeDataType.NUMBER, false);
        EquipmentAttributeValue existing = value(equipmentId, motorPower.getId());
        existing.setValueNumber(75.0);
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(motorPower));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of(existing));

        service.upsertValues(equipment, List.of(
                new EquipmentAttributeValueRequest(null, "motor_power", null, 75.0, null, null, null, null)
        ));

        verify(valueHistoryRepository, never()).saveAll(any());
    }

    @Test
    void upsertValues_multipleChangedValues_createMultipleHistoryRows() {
        UUID typeId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        EquipmentAttributeDefinition motorPower = definition(UUID.randomUUID(), typeId, "motor_power",
                EquipmentAttributeDataType.NUMBER, false);
        EquipmentAttributeDefinition sealType = definition(UUID.randomUUID(), typeId, "seal_type",
                EquipmentAttributeDataType.SELECT, false);
        sealType.setOptions(List.of(option("mechanical", "Mechanical"), option("packing", "Packing")));
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(motorPower, sealType));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of());

        service.upsertValues(equipment, List.of(
                new EquipmentAttributeValueRequest(null, "motor_power", null, 75.0, null, null, null, null),
                new EquipmentAttributeValueRequest(null, "seal_type", null, null, null, null, "mechanical", null)
        ));

        ArgumentCaptor<Iterable<EquipmentAttributeValueHistory>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(valueHistoryRepository).saveAll(captor.capture());
        assertThat(toHistoryList(captor.getValue())).hasSize(2);
    }

    @Test
    void upsertValues_preservesRequiredPolicyValidation() {
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID(), typeId);
        EquipmentAttributeDefinition motorPower = definition(UUID.randomUUID(), typeId, "motor_power",
                EquipmentAttributeDataType.NUMBER, true);
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(motorPower));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipment.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.upsertValues(equipment, List.of()))
                .isInstanceOf(RestException.class);
        verify(valueHistoryRepository, never()).saveAll(any());
    }

    @Test
    void upsertValues_numericEquivalentValue_doesNotCreateDuplicateHistory() {
        UUID typeId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        EquipmentAttributeDefinition motorPower = definition(UUID.randomUUID(), typeId, "motor_power",
                EquipmentAttributeDataType.NUMBER, false);
        EquipmentAttributeValue existing = value(equipmentId, motorPower.getId());
        existing.setValueNumber(10.0);
        when(definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(typeId)).thenReturn(List.of(motorPower));
        when(valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of(existing));

        service.upsertValues(equipment, List.of(
                new EquipmentAttributeValueRequest(null, "motor_power", null, 10.00, null, null, null, null)
        ));

        verify(valueHistoryRepository, never()).saveAll(any());
    }

    private static Stream<Object[]> validValueFields() {
        return Stream.of(
                new Object[]{EquipmentAttributeDataType.TEXT, new EquipmentAttributeValueRequest(null, "text_attr", "text", null, null, null, null, null), "text_attr"},
                new Object[]{EquipmentAttributeDataType.NUMBER, new EquipmentAttributeValueRequest(null, "number_attr", null, 1.0, null, null, null, null), "number_attr"},
                new Object[]{EquipmentAttributeDataType.RANGE, new EquipmentAttributeValueRequest(null, "range_attr", null, 10.0, null, null, null, null), "range_attr"},
                new Object[]{EquipmentAttributeDataType.DATE, new EquipmentAttributeValueRequest(null, "date_attr", null, null, LocalDate.of(2026, 5, 21), null, null, null), "date_attr"},
                new Object[]{EquipmentAttributeDataType.BOOLEAN, new EquipmentAttributeValueRequest(null, "bool_attr", null, null, null, true, null, null), "bool_attr"},
                new Object[]{EquipmentAttributeDataType.SELECT, new EquipmentAttributeValueRequest(null, "select_attr", null, null, null, null, "A", null), "select_attr"},
                new Object[]{EquipmentAttributeDataType.MULTI_SELECT, new EquipmentAttributeValueRequest(null, "multi_attr", null, null, null, null, null, "[\"A\"]"), "multi_attr"},
                new Object[]{EquipmentAttributeDataType.JSON, new EquipmentAttributeValueRequest(null, "json_attr", null, null, null, null, null, "{\"a\":1}"), "json_attr"},
                new Object[]{EquipmentAttributeDataType.FILE, new EquipmentAttributeValueRequest(null, "file_attr", "file-id", null, null, null, null, null), "file_attr"},
                new Object[]{EquipmentAttributeDataType.REFERENCE, new EquipmentAttributeValueRequest(null, "reference_attr", "ref-id", null, null, null, null, null), "reference_attr"}
        );
    }

    private static Stream<Object[]> invalidValueFields() {
        return Stream.of(
                new Object[]{EquipmentAttributeDataType.TEXT, new EquipmentAttributeValueRequest(null, "text_attr", null, 1.0, null, null, null, null), "text_attr", "valueText"},
                new Object[]{EquipmentAttributeDataType.NUMBER, new EquipmentAttributeValueRequest(null, "number_attr", "1", null, null, null, null, null), "number_attr", "valueNumber"},
                new Object[]{EquipmentAttributeDataType.RANGE, new EquipmentAttributeValueRequest(null, "range_attr", "10", null, null, null, null, null), "range_attr", "valueNumber"},
                new Object[]{EquipmentAttributeDataType.DATE, new EquipmentAttributeValueRequest(null, "date_attr", "2026-05-21", null, null, null, null, null), "date_attr", "valueDate"},
                new Object[]{EquipmentAttributeDataType.BOOLEAN, new EquipmentAttributeValueRequest(null, "bool_attr", "true", null, null, null, null, null), "bool_attr", "valueBoolean"},
                new Object[]{EquipmentAttributeDataType.SELECT, new EquipmentAttributeValueRequest(null, "select_attr", "A", null, null, null, null, null), "select_attr", "valueOption"},
                new Object[]{EquipmentAttributeDataType.MULTI_SELECT, new EquipmentAttributeValueRequest(null, "multi_attr", "A", null, null, null, null, null), "multi_attr", "valueJson"},
                new Object[]{EquipmentAttributeDataType.JSON, new EquipmentAttributeValueRequest(null, "json_attr", "{}", null, null, null, null, null), "json_attr", "valueJson"},
                new Object[]{EquipmentAttributeDataType.FILE, new EquipmentAttributeValueRequest(null, "file_attr", null, 1.0, null, null, null, null), "file_attr", "valueText"},
                new Object[]{EquipmentAttributeDataType.REFERENCE, new EquipmentAttributeValueRequest(null, "reference_attr", null, 1.0, null, null, null, null), "reference_attr", "valueText"}
        );
    }

    private void stubEquipmentType(UUID id) {
        EquipmentType type = new EquipmentType();
        type.setId(id);
        type.setCode("ET-2026-0001");
        type.setName("Pump");
        type.setCategory("PRODUCTION_EQUIPMENT");
        when(equipmentTypeRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(type));
    }

    private EquipmentAttributeDefinitionRequest definitionRequest(String key) {
        return new EquipmentAttributeDefinitionRequest(
                key,
                "Motor Power",
                null,
                null,
                EquipmentAttributeDataType.NUMBER,
                "kW",
                true,
                0.0,
                500.0,
                null,
                List.of(),
                "Motor",
                10
        );
    }

    private Equipment equipment(UUID id, UUID equipmentTypeId) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("P-101");
        equipment.setName("Pump P-101");
        equipment.setInventoryNumber("INV-P-101");
        equipment.setEquipmentTypeId(equipmentTypeId);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }

    private EquipmentAttributeDefinition definition(UUID id,
                                                    UUID equipmentTypeId,
                                                    String key,
                                                    EquipmentAttributeDataType dataType,
                                                    boolean required) {
        EquipmentAttributeDefinition definition = new EquipmentAttributeDefinition();
        definition.setId(id);
        definition.setEquipmentTypeId(equipmentTypeId);
        definition.setKey(key);
        definition.setLabel(key);
        definition.setDataType(dataType);
        definition.setRequired(required);
        definition.setSortOrder(0);
        definition.setOptions(List.of());
        return definition;
    }

    private EquipmentAttributeOptionDto option(String id, String label) {
        return new EquipmentAttributeOptionDto(id, label, null, null, null, true);
    }

    private EquipmentAttributeValue value(UUID equipmentId, UUID definitionId) {
        EquipmentAttributeValue value = new EquipmentAttributeValue();
        value.setId(UUID.randomUUID());
        value.setEquipmentId(equipmentId);
        value.setAttributeDefinitionId(definitionId);
        return value;
    }

    private com.toir.entity.equipment.CriticalityClass criticalityClass(UUID id) {
        com.toir.entity.equipment.CriticalityClass criticality = new com.toir.entity.equipment.CriticalityClass();
        criticality.setId(id);
        criticality.setCode("CRIT-HIGH");
        criticality.setName("High");
        return criticality;
    }

    private EquipmentAttributeRequiredCriticality requiredCriticality(UUID definitionId, UUID criticalityId) {
        EquipmentAttributeRequiredCriticality policy = new EquipmentAttributeRequiredCriticality();
        policy.setId(UUID.randomUUID());
        policy.setAttributeDefinitionId(definitionId);
        policy.setCriticalityClassId(criticalityId);
        return policy;
    }

    private List<EquipmentAttributeValue> toList(Iterable<EquipmentAttributeValue> values) {
        return ((List<EquipmentAttributeValue>) values);
    }

    private List<EquipmentAttributeRequiredCriticality> toRequiredCriticalityList(
            Iterable<EquipmentAttributeRequiredCriticality> values
    ) {
        return ((List<EquipmentAttributeRequiredCriticality>) values);
    }

    private List<EquipmentAttributeValueHistory> toHistoryList(Iterable<EquipmentAttributeValueHistory> values) {
        return ((List<EquipmentAttributeValueHistory>) values);
    }
}
