package com.toir.dto;

import com.toir.dto.equipmentmaintenance.EquipmentMaintenanceRuleRequest;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationRequest;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceTriggerRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void regulationRequestRequiresMeterTypeAndIntervalTogether() {
        MaintenanceRegulationRequest request = regulationRequest(MeterType.CUSTOM, null, 0, 0, 10.0);

        assertThat(validator.validate(request))
                .anySatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("meterTriggerPairValid"));
    }

    @Test
    void regulationRequestRejectsInvalidMeterAndLeadRanges() {
        MaintenanceRegulationRequest request = regulationRequest(MeterType.CUSTOM, 0.0, -1, -1, 101.0);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("triggerMeterInterval", "toleranceDays", "leadTimeDays", "leadMeterPercent");
    }

    @Test
    void equipmentRuleRequestRequiresMeterTypeAndIntervalTogether() {
        EquipmentMaintenanceRuleRequest request = equipmentRuleRequest(null, 1000.0, 0);

        assertThat(validator.validate(request))
                .anySatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("meterTriggerPairValid"));
    }

    @Test
    void equipmentRuleRequestRejectsInvalidMeterAndToleranceRanges() {
        EquipmentMaintenanceRuleRequest request = equipmentRuleRequest(MeterType.CUSTOM, -1.0, -1);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("triggerMeterInterval", "toleranceDays");
    }

    private MaintenanceRegulationRequest regulationRequest(
            MeterType meterType,
            Double meterInterval,
            Integer toleranceDays,
            Integer leadTimeDays,
            Double leadMeterPercent
    ) {
        return new MaintenanceRegulationRequest(
                null,
                "Monthly regulation",
                null,
                UUID.randomUUID(),
                null,
                MaintenanceKind.PREVENTIVE,
                1.0,
                true,
                PeriodicityUnit.MONTH,
                1,
                toleranceDays,
                false,
                meterType,
                meterInterval,
                null,
                null,
                null,
                null,
                null,
                null,
                leadTimeDays,
                leadMeterPercent,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private EquipmentMaintenanceRuleRequest equipmentRuleRequest(
            MeterType meterType,
            Double meterInterval,
            Integer toleranceDays
    ) {
        return new EquipmentMaintenanceRuleRequest(
                null,
                null,
                "Equipment rule",
                null,
                MaintenanceKind.PREVENTIVE,
                1.0,
                true,
                PeriodicityUnit.MONTH,
                1,
                toleranceDays,
                false,
                meterType,
                meterInterval,
                null,
                null,
                false,
                null
        );
    }
}
