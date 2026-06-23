package com.toir.service;

import com.toir.dto.dashboard.EquipmentLifecycleSummaryResponse;
import com.toir.dto.dashboard.EquipmentLifecycleSummaryResponse.EquipmentLifecycleItem;
import com.toir.dto.rcm.EquipmentRiskScore;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentLifecycleStage;
import com.toir.enums.EquipmentStatus;
import com.toir.repository.equipment.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardLifecycleService {

    private final EquipmentRepository equipmentRepository;
    private final RcmService          rcmService;

    @Transactional(readOnly = true)
    public EquipmentLifecycleSummaryResponse getLifecycleSummary() {

        List<Equipment> equipments =
                equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();

        Map<UUID, EquipmentRiskScore> riskMap = rcmService.computeAll().stream()
                .collect(Collectors.toMap(EquipmentRiskScore::equipmentId, Function.identity()));

        int lowRisk      = 0;
        int mediumRisk   = 0;
        int highRisk     = 0;
        int inRepair     = 0;
        int decommissioned = 0;

        List<EquipmentLifecycleItem> highRiskItems = new java.util.ArrayList<>();

        for (Equipment eq : equipments) {
            EquipmentRiskScore risk = riskMap.get(eq.getId());
            EquipmentLifecycleStage stage = classify(eq, risk);

            switch (stage) {
                case LOW_RISK      -> lowRisk++;
                case MEDIUM_RISK   -> mediumRisk++;
                case HIGH_RISK     -> {
                    highRisk++;
                    highRiskItems.add(new EquipmentLifecycleItem(
                            eq.getId(),
                            eq.getCode(),
                            eq.getName(),
                            stage,
                            risk != null ? risk.riskScore() : 0,
                            risk != null ? risk.openDefects() : 0
                    ));
                }
                case IN_REPAIR     -> inRepair++;
                case DECOMMISSIONED -> decommissioned++;
            }
        }

        // HIGH_RISK uskunalarni risk score bo'yicha tartiblash
        highRiskItems.sort((a, b) -> Integer.compare(b.riskScore(), a.riskScore()));

        return new EquipmentLifecycleSummaryResponse(
                lowRisk, mediumRisk, highRisk, inRepair, decommissioned,
                equipments.size(),
                highRiskItems
        );
    }

    /**
     * Status va RCM score asosida lifecycle stage aniqlanadi.
     * Status ustuvor — keyin RCM score.
     */
    private EquipmentLifecycleStage classify(Equipment eq, EquipmentRiskScore risk) {
        int score = risk != null ? risk.riskScore() : 0;

        if (eq.getStatus() == EquipmentStatus.DECOMMISSIONED) {
            return EquipmentLifecycleStage.DECOMMISSIONED;
        }
        if (eq.getStatus() == EquipmentStatus.IN_REPAIR) {
            if (score >= 60) {
                return EquipmentLifecycleStage.HIGH_RISK;
            }
            return EquipmentLifecycleStage.IN_REPAIR;
        }
        if (score >= 60) return EquipmentLifecycleStage.HIGH_RISK;
        if (score >= 30) return EquipmentLifecycleStage.MEDIUM_RISK;
        return EquipmentLifecycleStage.LOW_RISK;
    }
}
