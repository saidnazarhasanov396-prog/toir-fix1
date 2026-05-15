package com.toir.service;
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
import lombok.RequiredArgsConstructor;
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



    public List<EquipmentRiskScore> computeAll() {
        Map<UUID, CriticalityClass> critById = criticalityClassRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(CriticalityClass::getId, c -> c));
        Map<UUID, Long> openDefectsByEq = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> d.getStatus() != DefectStatus.CLOSED)
                .collect(Collectors.groupingBy(Defect::getEquipmentId, Collectors.counting()));
        Map<UUID, ReliabilityMetric> metricByEq = reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(ReliabilityMetric::getEquipmentId, m -> m, (a, b) -> a));

        return equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .map(eq -> score(eq, critById, openDefectsByEq, metricByEq))
                .sorted(Comparator.comparingInt(EquipmentRiskScore::riskScore).reversed())
                .toList();
    }

    public List<EquipmentRiskScore> topN(int n) {
        return computeAll().stream().limit(n).toList();
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
                                     Map<UUID, ReliabilityMetric> metricByEq) {
        CriticalityClass cls = eq.getCriticalityClassId() != null ? critById.get(eq.getCriticalityClassId()) : null;
        int consequence = 0;
        Integer repairPriority = null;
        String clsCode = null;
        if (cls != null) {
            clsCode = cls.getCode();
            repairPriority = cls.getRepairPriority();
            consequence += nz(cls.getSafetyImpact());
            consequence += nz(cls.getProductionImpact());
            consequence += nz(cls.getEcologicalImpact());
            consequence += nz(cls.getEnergyImpact());
        }
        long openDefects = openDefectsByEq.getOrDefault(eq.getId(), 0L);
        ReliabilityMetric m = metricByEq.get(eq.getId());
        double mtbf = m != null && m.getMtbfHours() != null ? m.getMtbfHours() : 0.0;
        double mttr = m != null && m.getMttrHours() != null ? m.getMttrHours() : 0.0;

        int probability;
        if (openDefects >= 5) probability = 5;
        else if (openDefects >= 3) probability = 4;
        else if (openDefects >= 1) probability = 3;
        else if (mtbf > 0 && mtbf < 2000) probability = 3;
        else if (mtbf > 0 && mtbf < 4000) probability = 2;
        else probability = 1;

        int risk = Math.min(100, consequence * probability);
        return new EquipmentRiskScore(
                eq.getId(), eq.getCode(), eq.getName(), clsCode,
                consequence, probability, risk, repairPriority,
                openDefects, mtbf, mttr
        );
    }

    private int nz(Integer v) { return v == null ? 0 : v; }
}
