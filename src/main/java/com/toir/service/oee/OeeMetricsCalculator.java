package com.toir.service.oee;

import com.toir.entity.OeeRecord;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OeeMetricsCalculator {

    public OeeMetrics calculate(OeeRecord record) {
        double availability = record.getPlannedProductionMinutes() > 0
                ? record.getRunMinutes() / record.getPlannedProductionMinutes()
                : 0;
        double idealTimeMinutes = record.getIdealCycleSeconds() * record.getTotalCount() / 60.0;
        double performance = record.getRunMinutes() > 0 ? idealTimeMinutes / record.getRunMinutes() : 0;
        double quality = record.getTotalCount() > 0 ? record.getGoodCount() / record.getTotalCount() : 0;
        return new OeeMetrics(availability, performance, quality, availability * performance * quality);
    }

    public OeeMetrics aggregate(List<OeeRecord> records) {
        double totalPlanned = 0;
        double totalRun = 0;
        double totalIdeal = 0;
        double totalCount = 0;
        double totalGood = 0;

        for (OeeRecord record : records) {
            totalPlanned += record.getPlannedProductionMinutes();
            totalRun += record.getRunMinutes();
            totalIdeal += record.getIdealCycleSeconds() * record.getTotalCount() / 60.0;
            totalCount += record.getTotalCount();
            totalGood += record.getGoodCount();
        }

        double availability = totalPlanned > 0 ? totalRun / totalPlanned : 0;
        double performance = totalRun > 0 ? totalIdeal / totalRun : 0;
        double quality = totalCount > 0 ? totalGood / totalCount : 0;
        return new OeeMetrics(availability, performance, quality, availability * performance * quality);
    }
}
