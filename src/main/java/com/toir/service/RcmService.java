package com.toir.service;

import com.toir.dto.rcm.EquipmentRiskScore;
import com.toir.dto.rcm.RiskExplanationDto;
import com.toir.entity.RcmSnapshot;
import com.toir.exception.RestException;
import com.toir.repository.RcmSnapshotRepository;

import com.toir.entity.equipment.Equipment;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.util.SortUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RcmService {

    private final EquipmentRepository equipmentRepository;
    private final RcmSnapshotRepository snapshotRepository;
    private final EquipmentRiskEvidenceService equipmentRiskEvidenceService;
    private final EquipmentRiskScoringService equipmentRiskScoringService;
    private final MetricExplanationService metricExplanationService;



    public List<EquipmentRiskScore> computeAll() {
        return computeAll("riskScore", "desc", null);
    }

    public List<EquipmentRiskScore> computeAll(String lang) {
        return computeAll("riskScore", "desc", lang);
    }

    public List<EquipmentRiskScore> computeAll(String sortBy, String sortDir) {
        return computeAll(sortBy, sortDir, null);
    }

    public List<EquipmentRiskScore> computeAll(String sortBy, String sortDir, String lang) {
        List<Equipment> equipment = equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        Map<UUID, EquipmentRiskEvidence> evidenceByEquipment = equipmentRiskEvidenceService.collect(equipment);

        return equipment.stream()
                .map(eq -> score(eq, evidenceByEquipment.get(eq.getId()), lang))
                .sorted(riskScoreComparator(sortBy, sortDir))
                .toList();
    }

    public List<EquipmentRiskScore> topN(int n) {
        return topN(n, "riskScore", "desc", null);
    }

    public List<EquipmentRiskScore> topN(int n, String lang) {
        return topN(n, "riskScore", "desc", lang);
    }

    public List<EquipmentRiskScore> topN(int n, String sortBy, String sortDir) {
        return topN(n, sortBy, sortDir, null);
    }

    public List<EquipmentRiskScore> topN(int n, String sortBy, String sortDir, String lang) {
        return computeAll(sortBy, sortDir, lang).stream().limit(n).toList();
    }

    /** Рассчитывает RCM и сохраняет snapshot-строки на текущий момент. */
    @Transactional
    public List<RcmSnapshot> captureSnapshot() {
        Instant now = Instant.now();
        List<EquipmentRiskScore> scores = computeAll();
        List<RcmSnapshot> saved = new ArrayList<>();
        for (EquipmentRiskScore s : scores) {
            RcmSnapshot snap = new RcmSnapshot();
            snap.setEquipmentId(s.equipmentId());
            snap.setEquipmentCode(s.equipmentCode());
            snap.setEquipmentName(s.equipmentName());
            snap.setCriticalityClass(s.criticalityClass());
            snap.setConsequence(s.consequence());
            snap.setProbability(s.probability());
            snap.setRiskScore(s.riskScore());
            snap.setRepairPriority(s.repairPriority());
            snap.setOpenDefects(s.openDefects());
            snap.setMtbfHours(s.mtbfHours());
            snap.setMttrHours(s.mttrHours());
            snap.setCapturedAt(now);
            saved.add(snapshotRepository.save(snap));
        }
        return saved;
    }

    public List<RcmSnapshot> historyFor(UUID equipmentId) {
        equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        return snapshotRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByCapturedAtDesc(equipmentId);
    }

    private EquipmentRiskScore score(Equipment eq, EquipmentRiskEvidence evidence, String lang) {
        EquipmentRiskEvidence resolvedEvidence = evidence == null ? defaultEvidence(eq) : evidence;
        EquipmentRiskScoringResult result = equipmentRiskScoringService.score(resolvedEvidence);
        RiskExplanationDto explanation = metricExplanationService.rcmRisk(lang, result);
        return new EquipmentRiskScore(
                eq.getId(), eq.getCode(), eq.getName(),
                resolvedEvidence.criticalityCode(),
                resolvedEvidence.criticalityName(),
                result.consequenceScore(),
                result.probabilityScore(),
                result.riskScore(),
                resolvedEvidence.repairPriority(),
                resolvedEvidence.openDefects(),
                resolvedEvidence.mtbfHours(),
                resolvedEvidence.mttrHours(),
                explanation
        );
    }

    private EquipmentRiskEvidence defaultEvidence(Equipment eq) {
        return new EquipmentRiskEvidence(
                eq.getId(),
                null,
                null,
                null,
                null,
                eq.getStatus(),
                0,
                0,
                0,
                0,
                0,
                0,
                0
        );
    }

    private Comparator<EquipmentRiskScore> riskScoreComparator(String sortBy, String sortDir) {
        String requestedSort = sortBy == null || sortBy.isBlank() ? "riskScore" : sortBy.trim();
        Comparator<EquipmentRiskScore> comparator = switch (requestedSort) {
            case "consequence" -> Comparator.comparingInt(EquipmentRiskScore::consequence);
            case "probability", "probabilityPercent" -> Comparator.comparingInt(EquipmentRiskScore::probability);
            case "riskScore" -> Comparator.comparingInt(EquipmentRiskScore::riskScore);
            case "openDefects" -> Comparator.comparingLong(EquipmentRiskScore::openDefects);
            case "mtbfHours" -> Comparator.comparingDouble(EquipmentRiskScore::mtbfHours);
            case "mttrHours" -> Comparator.comparingDouble(EquipmentRiskScore::mttrHours);
            default -> Comparator.comparingInt(EquipmentRiskScore::riskScore);
        };
        if (SortUtils.direction(sortDir, Sort.Direction.DESC).isDescending()) {
            return comparator.reversed();
        }
        return comparator;
    }
}
