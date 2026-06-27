package com.toir.service;

import com.toir.dto.rcm.RiskReasonCategory;
import com.toir.dto.rcm.RiskReasonCode;
import com.toir.dto.rcm.RiskSeverity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetricExplanationServiceTest {

    private final MetricExplanationService service = new MetricExplanationService();

    @Test
    void availabilitySummaryFormatsHourDurationsAsHoursAndMinutes() {
        var explanation = service.availability(
                "en",
                122.456,
                2.5,
                119.956,
                97.96
        );

        assertThat(explanation.summary())
                .contains("observed time was 122 hours 27 minutes")
                .contains("downtime was 2 hours 30 minutes");
        assertThat(explanation.steps()).anySatisfy(step -> {
            assertThat(step.label()).isEqualTo("Observed time");
            assertThat(step.value().doubleValue()).isEqualTo(122.46);
            assertThat(step.unit()).isEqualTo("hours");
            assertThat(step.displayValue()).isEqualTo("122 hours 27 minutes");
        });
    }

    @Test
    void availabilitySummaryFormatsLocalizedHourDurationsAsHoursAndMinutes() {
        assertThat(service.availability("uz", 122.456, 2.5, 119.956, 97.96).summary())
                .contains("kuzatilgan vaqt 122 soat 27 daq")
                .contains("to'xtash vaqti 2 soat 30 daq");

        assertThat(service.availability("ru", 122.456, 2.5, 119.956, 97.96).summary())
                .contains("наблюдаемое время 122 ч 27 мин")
                .contains("простой 2 ч 30 мин");
    }

    @Test
    void rcmRiskSummaryFormatsHourBasedReasonsAsHoursAndMinutes() {
        var primaryReason = new EquipmentRiskScoringReason(
                RiskReasonCode.LOW_MTBF_HIGH,
                RiskReasonCategory.PROBABILITY,
                122.456,
                3,
                4,
                RiskSeverity.HIGH
        );
        var result = new EquipmentRiskScoringResult(8, 4, 32, List.of(primaryReason), primaryReason);

        assertThat(service.rcmRisk("en", result).summary())
                .contains("MTBF is 122 hours 27 minutes, below the 2000 hour threshold");
    }
}
