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
    void passportRefContainsAllFields() {
        LocalDate installDate = LocalDate.of(2022, 3, 15);
        LocalDate lastInspectionDate = LocalDate.of(2024, 6, 1);

        EquipmentDto.PassportRef ref = new EquipmentDto.PassportRef(
                "PP-001",
                55.5,
                380.0,
                6.0,
                "FAC-123",
                "SER-456",
                120.0,
                installDate,
                lastInspectionDate,
                "Some notes"
        );

        assertThat(ref.passportNumber()).isEqualTo("PP-001");
        assertThat(ref.powerKw()).isEqualTo(55.5);
        assertThat(ref.voltageV()).isEqualTo(380.0);
        assertThat(ref.pressureBar()).isEqualTo(6.0);
        assertThat(ref.factoryNumber()).isEqualTo("FAC-123");
        assertThat(ref.manufacturerSerial()).isEqualTo("SER-456");
        assertThat(ref.throughput()).isEqualTo(120.0);
        assertThat(ref.installDate()).isEqualTo(installDate);
        assertThat(ref.lastInspectionDate()).isEqualTo(lastInspectionDate);
        assertThat(ref.notes()).isEqualTo("Some notes");
    }

    @Test
    void passportRefAllowsNullOptionalFields() {
        EquipmentDto.PassportRef ref = new EquipmentDto.PassportRef(
                "PP-002", null, null, null,
                null, null, null, null, null, null
        );

        assertThat(ref.passportNumber()).isEqualTo("PP-002");
        assertThat(ref.factoryNumber()).isNull();
        assertThat(ref.manufacturerSerial()).isNull();
        assertThat(ref.throughput()).isNull();
        assertThat(ref.installDate()).isNull();
        assertThat(ref.lastInspectionDate()).isNull();
        assertThat(ref.notes()).isNull();
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
