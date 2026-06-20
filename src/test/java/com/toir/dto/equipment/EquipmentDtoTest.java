package com.toir.dto.equipment;

import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.LifetimeStatus;
import com.toir.enums.MeterType;
import org.junit.jupiter.api.Test;

import com.toir.dto.equipmentpassport.ProductivityEntryDto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
        equipment.setProducedYear(2024);
        equipment.setAverageOperatingLifeHours(10_000L);
        equipment.setAverageDailyUsage(200.0);
        equipment.setExpectedLifetimeHours(18_000L);

        EquipmentDto dto = EquipmentDto.from(equipment);

        assertThat(dto.producedYear()).isEqualTo(2024);
        assertThat(dto.averageOperatingLifeHours()).isEqualTo(10_000L);
        assertThat(dto.averageDailyUsage()).isEqualTo(200.0);
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
                List.of(),
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
        assertThat(ref.productivity()).isEmpty();
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
        assertThat(ref.productivity()).isNull();
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

    @Test
    void fromIncludesMeterLifetimeSnapshot() {
        UUID meterId = UUID.randomUUID();
        Equipment equipment = new Equipment();
        equipment.setLifetimeCounterType(MeterType.CYCLES);
        equipment.setLifetimeMeterId(meterId);
        equipment.setLifetimeLimitValue(10_000.0);
        equipment.setLifetimeBaselineValue(100.0);
        equipment.setAverageDailyUsage(200.0);
        equipment.setLifetimeWarningPercent(10.0);

        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(meterId);
        meter.setMeterType(MeterType.CYCLES);
        meter.setUnit("cycle");
        meter.setCurrentValue(9_500);

        EquipmentDto dto = EquipmentDto.from(equipment, meter);

        assertThat(dto.lifetimeCounterType()).isEqualTo(MeterType.CYCLES);
        assertThat(dto.lifetimeMeterId()).isEqualTo(meterId);
        assertThat(dto.lifetimeLimitValue()).isEqualTo(10_000.0);
        assertThat(dto.lifetimeBaselineValue()).isEqualTo(100.0);
        assertThat(dto.averageDailyUsage()).isEqualTo(200.0);
        assertThat(dto.lifetimeWarningPercent()).isEqualTo(10.0);
        assertThat(dto.lifetimeCurrentValue()).isEqualTo(9_500.0);
        assertThat(dto.lifetimeTargetValue()).isEqualTo(10_100.0);
        assertThat(dto.lifetimeRemainingValue()).isEqualTo(600.0);
        assertThat(dto.lifetimeConsumedPercent()).isEqualTo(94.0);
        assertThat(dto.lifetimeUnit()).isEqualTo("cycle");
        assertThat(dto.lifetimeStatus()).isEqualTo(LifetimeStatus.EXPIRING_SOON);
    }

    @Test
    void fromComputesDaysOfResourceRemainingFromLifetimeLimitAndAverageDailyUsage() {
        Equipment equipment = new Equipment();
        equipment.setLifetimeLimitValue(10_000.0);
        equipment.setAverageDailyUsage(200.0);

        EquipmentDto dto = EquipmentDto.from(equipment);

        assertThat(dto.daysOfResourceRemaining()).isEqualTo(50L);
    }

    @Test
    void fromComputesDaysOfResourceRemainingFromExpectedLifetimeHoursFallback() {
        Equipment equipment = new Equipment();
        equipment.setExpectedLifetimeHours(18_000L);
        equipment.setAverageDailyUsage(200.0);

        EquipmentDto dto = EquipmentDto.from(equipment);

        assertThat(dto.lifetimeLimitValue()).isEqualTo(18_000.0);
        assertThat(dto.daysOfResourceRemaining()).isEqualTo(90L);
    }

    @Test
    void fromReturnsNullDaysOfResourceRemainingWhenAverageDailyUsageMissing() {
        Equipment equipment = new Equipment();
        equipment.setLifetimeLimitValue(10_000.0);

        EquipmentDto dto = EquipmentDto.from(equipment);

        assertThat(dto.daysOfResourceRemaining()).isNull();
    }

    @Test
    void fromReturnsNullDaysOfResourceRemainingWhenAverageDailyUsageNotPositive() {
        Equipment equipment = new Equipment();
        equipment.setLifetimeLimitValue(10_000.0);
        equipment.setAverageDailyUsage(0.0);

        EquipmentDto dto = EquipmentDto.from(equipment);

        assertThat(dto.daysOfResourceRemaining()).isNull();
    }

    @Test
    void fromRoundsDownDaysOfResourceRemaining() {
        Equipment equipment = new Equipment();
        equipment.setLifetimeLimitValue(10_001.0);
        equipment.setAverageDailyUsage(200.0);

        EquipmentDto dto = EquipmentDto.from(equipment);

        assertThat(dto.daysOfResourceRemaining()).isEqualTo(50L);
    }
}
