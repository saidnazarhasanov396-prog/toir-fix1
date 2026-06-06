package com.toir.dto.equipment;

import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.LifetimeStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentDtoTest {

    @Test
    void fromIncludesEquipmentCategory() {
        Equipment equipment = new Equipment();
        equipment.setCategory(EquipmentCategory.VEHICLE);

        EquipmentDto dto = EquipmentDto.from(equipment);

        assertThat(dto.category()).isEqualTo(EquipmentCategory.VEHICLE);
    }

    @Test
    void fromIncludesAverageOperatingLifeHours() {
        Equipment equipment = new Equipment();
        equipment.setAverageOperatingLifeHours(10_000L);
        equipment.setExpectedLifetimeHours(18_000L);

        EquipmentDto dto = EquipmentDto.from(equipment);

        assertThat(dto.averageOperatingLifeHours()).isEqualTo(10_000L);
        assertThat(dto.expectedLifetimeHours()).isEqualTo(18_000L);
    }

    @Test
    void fromIncludesCalculatedLifetimeFields() {
        Equipment equipment = new Equipment();
        equipment.setOperationStartDate(LocalDate.now().minusMonths(6));
        equipment.setExpectedLifetimeMonths(24);

        EquipmentDto dto = EquipmentDto.from(equipment);

        assertThat(dto.operationStartDate()).isEqualTo(equipment.getOperationStartDate());
        assertThat(dto.expectedLifetimeMonths()).isEqualTo(24);
        assertThat(dto.expectedEndDate()).isEqualTo(equipment.getOperationStartDate().plusMonths(24));
        assertThat(dto.remainingLifetime()).isNotBlank();
        assertThat(dto.operatingDuration()).isNotBlank();
        assertThat(dto.lifetimeStatus()).isEqualTo(LifetimeStatus.NORMAL);
    }
}
