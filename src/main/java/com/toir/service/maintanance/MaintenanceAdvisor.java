package com.toir.service.maintanance;

import com.toir.entity.equipment.CalibrationRecord;
import com.toir.repository.CalibrationRecordRepository;
import com.toir.entity.ConditionReading;
import com.toir.repository.ConditionReadingRepository;
import com.toir.entity.defects.Defect;
import com.toir.repository.defects.DefectRepository;
import com.toir.enums.DefectStatus;
import com.toir.entity.equipment.Equipment;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.dto.rcm.EquipmentRiskScore;
import com.toir.service.RcmService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Консолидированный советник по обслуживанию: по каждой единице оборудования
 * собирает рекомендации (срочный ремонт, поверка, повторная диагностика)
 * на основании RCM-риска, открытых дефектов, condition-показаний и поверок.
 */
@Service
@RequiredArgsConstructor
public class MaintenanceAdvisor {

    private final RcmService rcmService;
    private final EquipmentRepository equipmentRepository;
    private final DefectRepository defectRepository;
    private final ConditionReadingRepository conditionReadingRepository;
    private final CalibrationRecordRepository calibrationRecordRepository;



    public List<EquipmentAdvice> adviceAll() {
        Map<UUID, EquipmentRiskScore> scoreByEq = rcmService.computeAll().stream()
                .collect(Collectors.toMap(EquipmentRiskScore::equipmentId, s -> s));
        Map<UUID, Long> openDefectsByEq = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> d.getStatus() != DefectStatus.CLOSED)
                .collect(Collectors.groupingBy(Defect::getEquipmentId, Collectors.counting()));
        LocalDate today = LocalDate.now();
        List<EquipmentAdvice> out = new ArrayList<>();
        for (Equipment eq : equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            EquipmentRiskScore score = scoreByEq.get(eq.getId());
            List<String> actions = new ArrayList<>();
            String urgency = "LOW";

            if (score != null && score.riskScore() >= 60) {
                actions.add("Запланировать внеочередной осмотр / диагностику");
                urgency = "HIGH";
            } else if (score != null && score.riskScore() >= 30) {
                actions.add("Усилить мониторинг по графику ТО");
                urgency = "MEDIUM";
            }

            long openDefects = openDefectsByEq.getOrDefault(eq.getId(), 0L);
            if (openDefects >= 3) {
                actions.add("Разобрать накопленные дефекты (>= 3 открытых)");
                urgency = upgrade(urgency, "HIGH");
            }

            List<ConditionReading> alarms = conditionReadingRepository
                    .findAllByEquipmentIdAndIsDeletedFalseOrderByRecordedAtDesc(eq.getId()).stream()
                    .limit(5)
                    .filter(r -> "ALARM".equals(r.getSeverity()) || "WARN".equals(r.getSeverity()))
                    .toList();
            if (!alarms.isEmpty()) {
                actions.add("Проверить параметры: "
                        + alarms.stream().map(r -> r.getParameter() + "=" + r.getValue()).limit(3)
                        .collect(Collectors.joining(", ")));
                if (alarms.stream().anyMatch(r -> "ALARM".equals(r.getSeverity()))) {
                    urgency = upgrade(urgency, "HIGH");
                } else {
                    urgency = upgrade(urgency, "MEDIUM");
                }
            }

            List<CalibrationRecord> calibs = calibrationRecordRepository
                    .findAllByEquipmentIdAndIsDeletedFalseOrderByPerformedAtDesc(eq.getId());
            if (!calibs.isEmpty()) {
                CalibrationRecord latest = calibs.get(0);
                if (latest.getNextDueAt() != null) {
                    long daysToDue = java.time.temporal.ChronoUnit.DAYS.between(today, latest.getNextDueAt());
                    if (daysToDue < 0) {
                        actions.add("Просрочена поверка на " + Math.abs(daysToDue) + " дн.");
                        urgency = upgrade(urgency, "HIGH");
                    } else if (daysToDue <= 30) {
                        actions.add("Запланировать поверку (осталось " + daysToDue + " дн.)");
                        urgency = upgrade(urgency, "MEDIUM");
                    }
                }
            }

            if (actions.isEmpty()) continue;
            out.add(new EquipmentAdvice(
                    eq.getId(), eq.getCode(), eq.getName(),
                    score != null ? score.riskScore() : 0,
                    openDefects, alarms.size(), urgency, actions
            ));
        }
        out.sort((a, b) -> Integer.compare(urgencyRank(b.urgency()), urgencyRank(a.urgency())));
        return out;
    }

    private String upgrade(String current, String candidate) {
        return urgencyRank(candidate) > urgencyRank(current) ? candidate : current;
    }

    private int urgencyRank(String u) {
        return switch (u) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    public record EquipmentAdvice(
            UUID equipmentId,
            String equipmentCode,
            String equipmentName,
            int riskScore,
            long openDefects,
            int recentAlarms,
            String urgency,
            List<String> actions
    ) {}
}
