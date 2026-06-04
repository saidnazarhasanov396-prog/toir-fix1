package com.toir.model;

import com.toir.dto.equipment.EquipmentCreateRequest;
import com.toir.dto.equipment.EquipmentDto;
import com.toir.dto.equipment.EquipmentUpdateRequest;
import com.toir.entity.equipment.Equipment;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentArrivalDateModelContractTest {

    @Test
    void equipmentEntityExposesArrivalDate() {
        assertThat(Arrays.stream(Equipment.class.getDeclaredFields()).map(field -> field.getName()))
                .contains("arrivalDate");
    }

    @Test
    void equipmentRequestsAndDtoExposeArrivalDate() {
        assertThat(recordComponentNames(EquipmentCreateRequest.class)).contains("arrivalDate");
        assertThat(recordComponentNames(EquipmentUpdateRequest.class)).contains("arrivalDate");
        assertThat(recordComponentNames(EquipmentDto.class)).contains("arrivalDate");
    }

    private static String[] recordComponentNames(Class<? extends Record> recordClass) {
        return Arrays.stream(recordClass.getRecordComponents())
                .map(RecordComponent::getName)
                .toArray(String[]::new);
    }
}
