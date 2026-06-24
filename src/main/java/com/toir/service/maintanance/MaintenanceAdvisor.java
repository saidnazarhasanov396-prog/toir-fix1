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
import java.util.LinkedHashMap;
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
        return advice(null, null);
    }

    public List<EquipmentAdvice> advice(UUID equipmentId, String urgencyFilter) {
        String normalizedUrgency = normalizeUrgency(urgencyFilter);
        Map<UUID, EquipmentRiskScore> scoreByEq = rcmService.computeAll().stream()
                .collect(Collectors.toMap(EquipmentRiskScore::equipmentId, s -> s));
        Map<UUID, Long> openDefectsByEq = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> d.getStatus() != DefectStatus.CLOSED)
                .collect(Collectors.groupingBy(Defect::getEquipmentId, Collectors.counting()));
        LocalDate today = LocalDate.now();
        List<EquipmentAdvice> out = new ArrayList<>();
        for (Equipment eq : equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (equipmentId != null && !equipmentId.equals(eq.getId())) {
                continue;
            }
            EquipmentRiskScore score = scoreByEq.get(eq.getId());
            List<String> actions = new ArrayList<>();
            List<AdviceAction> actionItems = new ArrayList<>();
            String urgency = "LOW";

            if (score != null && score.riskScore() >= 60) {
                String label = "Запланировать внеочередной осмотр / диагностику";
                actions.add(label);
                actionItems.add(adviceAction(
                        "SCHEDULE_INSPECTION",
                        label,
                        "Создать маршрут внеочередного осмотра для оборудования",
                        "/inspection",
                        orderedQuery(
                                "equipmentId", eq.getId().toString(),
                                "mode", "create-route",
                                "source", "advisor",
                                "reason", "high-risk"
                        ),
                        "HIGH",
                        true
                ));
                urgency = "HIGH";
            } else if (score != null && score.riskScore() >= 30) {
                String label = "Усилить мониторинг по графику ТО";
                actions.add(label);
                actionItems.add(adviceAction(
                        "OPEN_MONITORING",
                        label,
                        "Открыть мониторинг оборудования и проверить счетчики/показания",
                        "/meters",
                        orderedQuery(
                                "equipmentId", eq.getId().toString(),
                                "source", "advisor",
                                "reason", "medium-risk"
                        ),
                        "MEDIUM",
                        true
                ));
                urgency = "MEDIUM";
            }

            long openDefects = openDefectsByEq.getOrDefault(eq.getId(), 0L);
            if (openDefects >= 3) {
                String label = "Разобрать накопленные дефекты (>= 3 открытых)";
                actions.add(label);
                actionItems.add(adviceAction(
                        "OPEN_DEFECTS",
                        label,
                        "Открыть дефекты оборудования, кроме закрытых",
                        "/defects",
                        orderedQuery(
                                "equipmentId", eq.getId().toString(),
                                "statusScope", "open",
                                "source", "advisor"
                        ),
                        "HIGH",
                        true
                ));
                urgency = upgrade(urgency, "HIGH");
            }

            List<ConditionReading> alarms = conditionReadingRepository
                    .findAllByEquipmentIdAndIsDeletedFalseOrderByRecordedAtDesc(eq.getId()).stream()
                    .limit(5)
                    .filter(r -> "ALARM".equals(r.getSeverity()) || "WARN".equals(r.getSeverity()))
                    .toList();
            if (!alarms.isEmpty()) {
                String label = "Проверить параметры: "
                        + alarms.stream().map(r -> r.getParameter() + "=" + r.getValue()).limit(3)
                        .collect(Collectors.joining(", "));
                boolean hasAlarm = alarms.stream().anyMatch(r -> "ALARM".equals(r.getSeverity()));
                actions.add(label);
                actionItems.add(adviceAction(
                        "OPEN_MONITORING",
                        label,
                        "Открыть мониторинг оборудования с проблемными параметрами",
                        "/meters",
                        orderedQuery(
                                "equipmentId", eq.getId().toString(),
                                "source", "advisor",
                                "reason", "condition-alert"
                        ),
                        hasAlarm ? "HIGH" : "MEDIUM",
                        false
                ));
                if (hasAlarm) {
                    urgency = upgrade(urgency, "HIGH");
                } else {
                    urgency = upgrade(urgency, "MEDIUM");
                }
            }

            List<CalibrationRecord> calibs = calibrationRecordRepository
                    .findAllByEquipmentIdAndIsDeletedFalseOrderByPerformedAtDesc(eq.getId());
            if (!calibs.isEmpty()) {
                CalibrationRecord latest = calibs.getFirst();
                if (latest.getNextDueAt() != null) {
                    long daysToDue = java.time.temporal.ChronoUnit.DAYS.between(today, latest.getNextDueAt());
                    if (daysToDue < 0) {
                        String label = "Просрочена поверка на " + Math.abs(daysToDue) + " дн.";
                        actions.add(label);
                        actionItems.add(calibrationAction(eq.getId(), label, "calibration-overdue", "HIGH"));
                        urgency = upgrade(urgency, "HIGH");
                    } else if (daysToDue <= 30) {
                        String label = "Запланировать поверку (осталось " + daysToDue + " дн.)";
                        actions.add(label);
                        actionItems.add(calibrationAction(eq.getId(), label, "calibration-due", "MEDIUM"));
                        urgency = upgrade(urgency, "MEDIUM");
                    }
                }
            }

            if (actions.isEmpty()) continue;
            if (normalizedUrgency != null && !normalizedUrgency.equals(urgency)) {
                continue;
            }
            out.add(new EquipmentAdvice(
                    eq.getId(), eq.getCode(), eq.getName(),
                    score != null ? score.riskScore() : 0,
                    openDefects, alarms.size(), urgency, actions, actionItems
            ));
        }
        out.sort((a, b) -> Integer.compare(urgencyRank(b.urgency()), urgencyRank(a.urgency())));
        return out;
    }

    public MaintenanceAdviceStats stats(UUID equipmentId, String urgency) {
        List<EquipmentAdvice> advice = advice(equipmentId, urgency);
        int total = advice.size();
        long high = advice.stream().filter(a -> "HIGH".equals(a.urgency())).count();
        long medium = advice.stream().filter(a -> "MEDIUM".equals(a.urgency())).count();
        long low = advice.stream().filter(a -> "LOW".equals(a.urgency())).count();
        long openDefects = advice.stream().mapToLong(EquipmentAdvice::openDefects).sum();
        long recentAlarms = advice.stream().mapToLong(EquipmentAdvice::recentAlarms).sum();
        double averageRiskScore = advice.stream()
                .mapToInt(EquipmentAdvice::riskScore)
                .average()
                .orElse(0.0);
        return new MaintenanceAdviceStats(total, high, medium, low, openDefects, recentAlarms, averageRiskScore);
    }

    private String upgrade(String current, String candidate) {
        return urgencyRank(candidate) > urgencyRank(current) ? candidate : current;
    }

    private String normalizeUrgency(String urgency) {
        if (urgency == null || urgency.isBlank()) {
            return null;
        }
        return urgency.trim().toUpperCase();
    }

    private int urgencyRank(String u) {
        return switch (u) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private AdviceAction calibrationAction(UUID equipmentId, String label, String reason, String urgency) {
        return adviceAction(
                "SCHEDULE_CALIBRATION",
                label,
                "Открыть регистрацию поверки для оборудования",
                "/calibrations",
                orderedQuery(
                        "equipmentId", equipmentId.toString(),
                        "mode", "create",
                        "source", "advisor",
                        "reason", reason
                ),
                urgency,
                true
        );
    }

    private AdviceAction adviceAction(
            String type,
            String label,
            String description,
            String targetPath,
            Map<String, String> query,
            String urgency,
            boolean primary
    ) {
        return new AdviceAction(type, label, description, targetPath, query, urgency, primary);
    }

    private Map<String, String> orderedQuery(String... entries) {
        Map<String, String> query = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            query.put(entries[i], entries[i + 1]);
        }
        return query;
    }

    public record EquipmentAdvice(
            UUID equipmentId,
            String equipmentCode,
            String equipmentName,
            int riskScore,
            long openDefects,
            int recentAlarms,
            String urgency,
            List<String> actions,
            List<AdviceAction> actionItems
    ) {}

    public record AdviceAction(
            String type,
            String label,
            String description,
            String targetPath,
            Map<String, String> query,
            String urgency,
            boolean primary
    ) {}

    public record MaintenanceAdviceStats(
            int totalAdvice,
            long highUrgency,
            long mediumUrgency,
            long lowUrgency,
            long openDefects,
            long recentAlarms,
            double averageRiskScore
    ) {}
}
