package com.toir.dto.equipment;

import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import org.junit.jupiter.api.Test;

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

        EquipmentDto dto = EquipmentDto.from(equipment);

        assertThat(dto.averageOperatingLifeHours()).isEqualTo(10_000L);
    }
}
