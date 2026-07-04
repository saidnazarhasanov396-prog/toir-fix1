package com.toir.service.oee;

import com.toir.entity.OeeRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OeeMetricsCalculatorTest {

    private final OeeMetricsCalculator calculator = new OeeMetricsCalculator();

    @Test
    void calculatesRowMetricsFromRawFacts() {
        OeeRecord record = record(60, 20, 100, 20, 15);

        OeeMetrics metrics = calculator.calculate(record);

        assertThat(metrics.availability()).isEqualTo(20.0 / 60.0);
        assertThat(metrics.performance()).isEqualTo((100.0 * 20.0 / 60.0) / 20.0);
        assertThat(metrics.quality()).isEqualTo(15.0 / 20.0);
        assertThat(metrics.oee()).isEqualTo((20.0 / 60.0) * ((100.0 * 20.0 / 60.0) / 20.0) * (15.0 / 20.0));
    }

    @Test
    void returnsZeroWhenDenominatorsAreZero() {
        OeeMetrics metrics = calculator.calculate(record(0, 0, 30, 0, 0));

        assertThat(metrics.availability()).isZero();
        assertThat(metrics.performance()).isZero();
        assertThat(metrics.quality()).isZero();
        assertThat(metrics.oee()).isZero();
    }

    @Test
    void aggregatesRawFactsBeforeCalculatingSummaryMetrics() {
        OeeMetrics metrics = calculator.aggregate(List.of(
                record(100, 80, 48, 100, 95),
                record(200, 120, 30, 120, 100)
        ));

        assertThat(metrics.availability()).isEqualTo(200.0 / 300.0);
        assertThat(metrics.quality()).isEqualTo(195.0 / 220.0);
    }

    private OeeRecord record(double planned, double run, double idealCycleSeconds, double total, double good) {
        OeeRecord record = new OeeRecord();
        record.setPlannedProductionMinutes(planned);
        record.setRunMinutes(run);
        record.setIdealCycleSeconds(idealCycleSeconds);
        record.setTotalCount(total);
        record.setGoodCount(good);
        return record;
    }
}
