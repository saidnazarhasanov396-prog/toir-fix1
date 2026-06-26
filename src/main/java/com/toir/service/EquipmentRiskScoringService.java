package com.toir.service;

import com.toir.dto.rcm.RiskReasonCategory;
import com.toir.dto.rcm.RiskReasonCode;
import com.toir.dto.rcm.RiskSeverity;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.EquipmentStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class EquipmentRiskScoringService {

    public EquipmentRiskScoringResult score(EquipmentRiskEvidence evidence) {
        List<EquipmentRiskScoringReason> probabilityReasons = new ArrayList<>();
        int probability = 1;

        long openDefects = evidence.openDefects();
        if (openDefects >= 5) {
            probability = 5;
            probabilityReasons.add(probabilityReason(
                    RiskReasonCode.OPEN_DEFECTS_CRITICAL, openDefects, 5, RiskSeverity.CRITICAL));
        } else if (openDefects >= 3) {
            probability = 4;
            probabilityReasons.add(probabilityReason(
                    RiskReasonCode.OPEN_DEFECTS_HIGH, openDefects, 4, RiskSeverity.HIGH));
        } else if (openDefects >= 1) {
            probability = 3;
            probabilityReasons.add(probabilityReason(
                    RiskReasonCode.OPEN_DEFECTS_MEDIUM, openDefects, 3, RiskSeverity.MEDIUM));
        }

        double mtbf = evidence.mtbfHours();
        if (mtbf > 0 && mtbf < 2000) {
            probability = Math.max(probability, 3);
            probabilityReasons.add(probabilityReason(
                    RiskReasonCode.LOW_MTBF_HIGH, round2(mtbf), 3, RiskSeverity.HIGH));
        } else if (mtbf > 0 && mtbf < 4000) {
            probability = Math.max(probability, 2);
            probabilityReasons.add(probabilityReason(
                    RiskReasonCode.LOW_MTBF_MEDIUM, round2(mtbf), 2, RiskSeverity.MEDIUM));
        }

        if (evidence.recurringDefects() > 0) {
            probability = Math.min(5, probability + 1);
            probabilityReasons.add(new EquipmentRiskScoringReason(
                    RiskReasonCode.RECURRING_DEFECTS,
                    RiskReasonCategory.PROBABILITY,
                    evidence.recurringDefects(),
                    1,
                    probability,
                    RiskSeverity.MEDIUM
            ));
        }

        if (evidence.overdueMaintenanceCount() > 0) {
            probability = Math.min(5, probability + 1);
            probabilityReasons.add(new EquipmentRiskScoringReason(
                    RiskReasonCode.OVERDUE_MAINTENANCE,
                    RiskReasonCategory.PROBABILITY,
                    evidence.overdueMaintenanceCount(),
                    1,
                    probability,
                    RiskSeverity.HIGH
            ));
        }

        if (probabilityReasons.isEmpty()) {
            probabilityReasons.add(new EquipmentRiskScoringReason(
                    RiskReasonCode.BASELINE_PROBABILITY,
                    RiskReasonCategory.PROBABILITY,
                    0,
                    0,
                    1,
                    RiskSeverity.LOW
            ));
        }

        List<EquipmentRiskScoringReason> consequenceReasons = new ArrayList<>();
        int consequence = 0;

        CriticalityLevel level = resolveCriticalityLevel(evidence);
        if (level != null) {
            EquipmentRiskScoringReason criticalityReason = criticalityReason(level);
            consequence += criticalityReason.scoreImpact();
            consequenceReasons.add(criticalityReason);
        }

        int downtimeImpact = downtimeImpact(evidence.recentDowntimeHours());
        if (downtimeImpact > 0) {
            consequence += downtimeImpact;
            consequenceReasons.add(new EquipmentRiskScoringReason(
                    RiskReasonCode.RECENT_DOWNTIME,
                    RiskReasonCategory.CONSEQUENCE,
                    round2(evidence.recentDowntimeHours()),
                    downtimeImpact,
                    downtimeImpact,
                    downtimeImpact >= 3 ? RiskSeverity.HIGH : RiskSeverity.MEDIUM
            ));
        }

        int mttrImpact = mttrImpact(evidence.mttrHours());
        if (mttrImpact > 0) {
            consequence += mttrImpact;
            consequenceReasons.add(new EquipmentRiskScoringReason(
                    RiskReasonCode.HIGH_MTTR,
                    RiskReasonCategory.CONSEQUENCE,
                    round2(evidence.mttrHours()),
                    mttrImpact,
                    mttrImpact,
                    mttrImpact >= 2 ? RiskSeverity.HIGH : RiskSeverity.MEDIUM
            ));
        }

        if (evidence.openHighRepairRequestCount() > 0) {
            consequence += 2;
            consequenceReasons.add(new EquipmentRiskScoringReason(
                    RiskReasonCode.OPEN_HIGH_REPAIR_REQUEST,
                    RiskReasonCategory.CONSEQUENCE,
                    evidence.openHighRepairRequestCount(),
                    2,
                    2,
                    RiskSeverity.HIGH
            ));
        }

        if (isUnavailable(evidence.equipmentStatus())) {
            consequence += 2;
            consequenceReasons.add(new EquipmentRiskScoringReason(
                    RiskReasonCode.EQUIPMENT_UNAVAILABLE,
                    RiskReasonCategory.CONSEQUENCE,
                    evidence.equipmentStatus().name(),
                    2,
                    2,
                    RiskSeverity.HIGH
            ));
        }

        List<EquipmentRiskScoringReason> reasons = new ArrayList<>();
        reasons.addAll(probabilityReasons);
        reasons.addAll(consequenceReasons);

        int risk = Math.min(100, consequence * probability);
        EquipmentRiskScoringReason primaryReason = probabilityReasons.stream()
                .max(Comparator.comparingInt(this::primaryWeight))
                .orElse(probabilityReasons.getFirst());

        return new EquipmentRiskScoringResult(consequence, probability, risk, reasons, primaryReason);
    }

    private EquipmentRiskScoringReason probabilityReason(RiskReasonCode code,
                                                         Object value,
                                                         int targetScore,
                                                         RiskSeverity severity) {
        return new EquipmentRiskScoringReason(
                code,
                RiskReasonCategory.PROBABILITY,
                value,
                targetScore,
                targetScore,
                severity
        );
    }

    private EquipmentRiskScoringReason criticalityReason(CriticalityLevel level) {
        return switch (level) {
            case CRITICAL -> new EquipmentRiskScoringReason(
                    RiskReasonCode.CRITICAL_EQUIPMENT,
                    RiskReasonCategory.CONSEQUENCE,
                    level.name(),
                    5,
                    5,
                    RiskSeverity.CRITICAL);
            case HIGH -> new EquipmentRiskScoringReason(
                    RiskReasonCode.HIGH_CRITICALITY,
                    RiskReasonCategory.CONSEQUENCE,
                    level.name(),
                    4,
                    4,
                    RiskSeverity.HIGH);
            case MEDIUM -> new EquipmentRiskScoringReason(
                    RiskReasonCode.MEDIUM_CRITICALITY,
                    RiskReasonCategory.CONSEQUENCE,
                    level.name(),
                    2,
                    2,
                    RiskSeverity.MEDIUM);
            case LOW -> new EquipmentRiskScoringReason(
                    RiskReasonCode.LOW_CRITICALITY,
                    RiskReasonCategory.CONSEQUENCE,
                    level.name(),
                    1,
                    1,
                    RiskSeverity.LOW);
        };
    }

    private CriticalityLevel resolveCriticalityLevel(EquipmentRiskEvidence evidence) {
        if (evidence.criticalityLevel() != null) {
            return evidence.criticalityLevel();
        }
        String value = ((evidence.criticalityCode() == null ? "" : evidence.criticalityCode()) + " "
                + (evidence.criticalityName() == null ? "" : evidence.criticalityName()))
                .toUpperCase(Locale.ROOT);
        if (value.contains("CRITICAL")) {
            return CriticalityLevel.CRITICAL;
        }
        if (value.contains("HIGH")) {
            return CriticalityLevel.HIGH;
        }
        if (value.contains("MEDIUM") || value.contains("MED")) {
            return CriticalityLevel.MEDIUM;
        }
        if (value.contains("LOW")) {
            return CriticalityLevel.LOW;
        }
        return null;
    }

    private int downtimeImpact(double downtimeHours) {
        if (downtimeHours >= 24) {
            return 3;
        }
        if (downtimeHours >= 8) {
            return 2;
        }
        if (downtimeHours > 0) {
            return 1;
        }
        return 0;
    }

    private int mttrImpact(double mttrHours) {
        if (mttrHours >= 24) {
            return 2;
        }
        if (mttrHours >= 8) {
            return 1;
        }
        return 0;
    }

    private boolean isUnavailable(EquipmentStatus status) {
        return status == EquipmentStatus.IN_REPAIR || status == EquipmentStatus.OUT_OF_SERVICE;
    }

    private int primaryWeight(EquipmentRiskScoringReason reason) {
        return switch (reason.code()) {
            case OPEN_DEFECTS_CRITICAL -> 100;
            case OPEN_DEFECTS_HIGH -> 90;
            case OPEN_DEFECTS_MEDIUM -> 80;
            case LOW_MTBF_HIGH -> 70;
            case LOW_MTBF_MEDIUM -> 60;
            case OVERDUE_MAINTENANCE -> 50;
            case RECURRING_DEFECTS -> 40;
            case BASELINE_PROBABILITY -> 10;
            default -> 0;
        };
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
