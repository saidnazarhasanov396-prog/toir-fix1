package com.toir.service;

import com.toir.dto.analytics.MetricExplanationDto;
import com.toir.dto.rcm.EquipmentRiskScore;
import com.toir.entity.RcmSnapshot;
import com.toir.exception.RestException;
import com.toir.repository.RcmSnapshotRepository;

import com.toir.entity.equipment.CriticalityClass;
import com.toir.repository.CriticalityClassRepository;
import com.toir.entity.defects.Defect;
import com.toir.repository.defects.DefectRepository;
import com.toir.enums.DefectStatus;
import com.toir.entity.equipment.Equipment;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.entity.ReliabilityMetric;
import com.toir.repository.ReliabilityMetricRepository;
import com.toir.service.MetricExplanationService.RcmProbabilityBasis;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RcmService {

    private final EquipmentRepository equipmentRepository;
    private final CriticalityClassRepository criticalityClassRepository;
    private final DefectRepository defectRepository;
    private final ReliabilityMetricRepository reliabilityMetricRepository;
    private final RcmSnapshotRepository snapshotRepository;
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
        Map<UUID, CriticalityClass> critById = criticalityClassRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(CriticalityClass::getId, c -> c));
        Map<UUID, Long> openDefectsByEq = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> d.getStatus() != DefectStatus.CLOSED)
                .collect(Collectors.groupingBy(Defect::getEquipmentId, Collectors.counting()));
        Map<UUID, ReliabilityMetric> metricByEq = reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(ReliabilityMetric::getEquipmentId, m -> m, (a, b) -> a));

        return equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .map(eq -> score(eq, critById, openDefectsByEq, metricByEq, lang))
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

    private EquipmentRiskScore score(Equipment eq,
                                     Map<UUID, CriticalityClass> critById,
                                     Map<UUID, Long> openDefectsByEq,
                                     Map<UUID, ReliabilityMetric> metricByEq,
                                     String lang) {
        CriticalityClass cls = eq.getCriticalityClassId() != null ? critById.get(eq.getCriticalityClassId()) : null;
        int consequence = 0;
        int safetyImpact = 0;
        int productionImpact = 0;
        int ecologicalImpact = 0;
        int energyImpact = 0;
        Integer repairPriority = null;
        String clsCode = null;
        String clsName = null;
        if (cls != null) {
            clsCode = cls.getCode();
            clsName = cls.getName();
            repairPriority = cls.getRepairPriority();
            safetyImpact = nz(cls.getSafetyImpact());
            productionImpact = nz(cls.getProductionImpact());
            ecologicalImpact = nz(cls.getEcologicalImpact());
            energyImpact = nz(cls.getEnergyImpact());
            consequence = safetyImpact + productionImpact + ecologicalImpact + energyImpact;
        }
        long openDefects = openDefectsByEq.getOrDefault(eq.getId(), 0L);
        ReliabilityMetric m = metricByEq.get(eq.getId());
        double mtbf = m != null && m.getMtbfHours() != null ? m.getMtbfHours() : 0.0;
        double mttr = m != null && m.getMttrHours() != null ? m.getMttrHours() : 0.0;

        int probability;
        RcmProbabilityBasis probabilityBasis;
        if (openDefects >= 5) {
            probability = 5;
            probabilityBasis = RcmProbabilityBasis.OPEN_DEFECTS;
        } else if (openDefects >= 3) {
            probability = 4;
            probabilityBasis = RcmProbabilityBasis.OPEN_DEFECTS;
        } else if (openDefects >= 1) {
            probability = 3;
            probabilityBasis = RcmProbabilityBasis.OPEN_DEFECTS;
        } else if (mtbf > 0 && mtbf < 2000) {
            probability = 3;
            probabilityBasis = RcmProbabilityBasis.MTBF;
        } else if (mtbf > 0 && mtbf < 4000) {
            probability = 2;
            probabilityBasis = RcmProbabilityBasis.MTBF;
        } else {
            probability = 1;
            probabilityBasis = RcmProbabilityBasis.BASELINE;
        }

        int risk = Math.min(100, consequence * probability);
        MetricExplanationDto explanation = metricExplanationService.rcmRisk(
                lang,
                safetyImpact,
                productionImpact,
                ecologicalImpact,
                energyImpact,
                consequence,
                probability,
                risk,
                openDefects,
                mtbf,
                probabilityBasis
        );
        return new EquipmentRiskScore(
                eq.getId(), eq.getCode(), eq.getName(), clsCode, clsName,
                consequence, probability, risk, repairPriority,
                openDefects, mtbf, mttr, explanation
        );
    }

    private int nz(Integer v) { return v == null ? 0 : v; }

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
