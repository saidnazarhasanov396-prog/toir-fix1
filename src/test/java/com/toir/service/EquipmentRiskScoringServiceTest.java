package com.toir.service;

import com.toir.dto.rcm.RiskReasonCategory;
import com.toir.dto.rcm.RiskReasonCode;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.EquipmentStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentRiskScoringServiceTest {

    private final EquipmentRiskScoringService service = new EquipmentRiskScoringService();

    @Test
    void scoresDynamicSignalsAndMarksOpenDefectsAsPrimaryReason() {
        EquipmentRiskEvidence evidence = new EquipmentRiskEvidence(
                UUID.randomUUID(),
                "HIGH",
                "High criticality",
                CriticalityLevel.HIGH,
                2,
                EquipmentStatus.ACTIVE,
                0,
                0,
                0,
                0,
                3,
                0,
                3500,
                10,
                12,
                0,
                0
        );

        EquipmentRiskScoringResult result = service.score(evidence);

        assertThat(result.probabilityScore()).isEqualTo(4);
        assertThat(result.consequenceScore()).isEqualTo(7);
        assertThat(result.riskScore()).isEqualTo(28);
        assertThat(result.primaryReason().code()).isEqualTo(RiskReasonCode.OPEN_DEFECTS_HIGH);
        assertThat(result.reasons()).extracting(EquipmentRiskScoringReason::code)
                .containsExactly(
                        RiskReasonCode.OPEN_DEFECTS_HIGH,
                        RiskReasonCode.LOW_MTBF_MEDIUM,
                        RiskReasonCode.HIGH_CRITICALITY,
                        RiskReasonCode.RECENT_DOWNTIME,
                        RiskReasonCode.HIGH_MTTR
                );
        assertThat(result.reasons()).extracting(EquipmentRiskScoringReason::category)
                .contains(RiskReasonCategory.PROBABILITY, RiskReasonCategory.CONSEQUENCE);
    }

    @Test
    void usesBaselineProbabilityWhenNoActiveProbabilitySignalExists() {
        EquipmentRiskEvidence evidence = new EquipmentRiskEvidence(
                UUID.randomUUID(),
                "LOW",
                "Low criticality",
                CriticalityLevel.LOW,
                5,
                EquipmentStatus.ACTIVE,
                0,
                0,
                0,
                0,
                0,
                0,
                5000,
                2,
                0,
                0,
                0
        );

        EquipmentRiskScoringResult result = service.score(evidence);

        assertThat(result.probabilityScore()).isEqualTo(1);
        assertThat(result.consequenceScore()).isEqualTo(1);
        assertThat(result.riskScore()).isEqualTo(1);
        assertThat(result.primaryReason().code()).isEqualTo(RiskReasonCode.BASELINE_PROBABILITY);
        assertThat(result.reasons()).extracting(EquipmentRiskScoringReason::code)
                .containsExactly(RiskReasonCode.BASELINE_PROBABILITY, RiskReasonCode.LOW_CRITICALITY);
    }

    @Test
    void recurringDefectsAndOverdueMaintenanceRaiseProbabilityButCapAtFive() {
        EquipmentRiskEvidence evidence = new EquipmentRiskEvidence(
                UUID.randomUUID(),
                "CRITICAL",
                "Critical equipment",
                CriticalityLevel.CRITICAL,
                1,
                EquipmentStatus.OUT_OF_SERVICE,
                0,
                0,
                0,
                0,
                5,
                2,
                1500,
                30,
                30,
                3,
                1
        );

        EquipmentRiskScoringResult result = service.score(evidence);

        assertThat(result.probabilityScore()).isEqualTo(5);
        assertThat(result.consequenceScore()).isEqualTo(14);
        assertThat(result.riskScore()).isEqualTo(70);
        assertThat(result.reasons()).extracting(EquipmentRiskScoringReason::code)
                .contains(
                        RiskReasonCode.OPEN_DEFECTS_CRITICAL,
                        RiskReasonCode.LOW_MTBF_HIGH,
                        RiskReasonCode.RECURRING_DEFECTS,
                        RiskReasonCode.OVERDUE_MAINTENANCE,
                        RiskReasonCode.CRITICAL_EQUIPMENT,
                        RiskReasonCode.RECENT_DOWNTIME,
                        RiskReasonCode.HIGH_MTTR,
                        RiskReasonCode.OPEN_HIGH_REPAIR_REQUEST,
                        RiskReasonCode.EQUIPMENT_UNAVAILABLE
                );
    }
}
