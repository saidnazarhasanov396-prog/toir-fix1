package com.toir.service.equipment;

import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentLocationType;
import com.toir.exception.RestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquipmentRequiredFieldsTest {

    @Test
    void rejectsEveryMissingGlobalPassportField() {
        Equipment equipment = new Equipment();

        assertThatThrownBy(() -> EquipmentRequiredFields.validate(equipment))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("criticalityClassId")
                .hasMessageContaining("locationId")
                .hasMessageContaining("commissionedAt")
                .hasMessageContaining("responsibleId");
    }

    @Test
    void acceptsDepartmentPlacementAsAValidEquipmentLocation() {
        Equipment equipment = new Equipment();
        equipment.setCriticalityClassId(UUID.randomUUID());
        equipment.setCommissionedAt(LocalDate.of(2026, 7, 31));
        equipment.setResponsibleId(UUID.randomUUID());
        equipment.setCurrentLocationType(EquipmentLocationType.DEPARTMENT);
        equipment.setDepartmentId(UUID.randomUUID());

        assertThatCode(() -> EquipmentRequiredFields.validate(equipment)).doesNotThrowAnyException();
    }
}
