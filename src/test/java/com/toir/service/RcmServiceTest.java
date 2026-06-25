package com.toir.service;

import com.toir.dto.rcm.EquipmentRiskScore;
import com.toir.entity.RcmSnapshot;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.CriticalityClass;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.DefectStatus;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.exception.RestException;
import com.toir.repository.CriticalityClassRepository;
import com.toir.repository.RcmSnapshotRepository;
import com.toir.repository.ReliabilityMetricRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RcmServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    CriticalityClassRepository criticalityClassRepository;

    @Mock
    DefectRepository defectRepository;

    @Mock
    ReliabilityMetricRepository reliabilityMetricRepository;

    @Mock
    RcmSnapshotRepository snapshotRepository;

    @Spy
    MetricExplanationService metricExplanationService = new MetricExplanationService();

    @InjectMocks
    RcmService service;

    @Test
    void riskScoresIncludeCriticalityClassCodeAndName() {
        UUID equipmentId = UUID.randomUUID();
        UUID criticalityClassId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId);
        equipment.setCriticalityClassId(criticalityClassId);

        CriticalityClass criticalityClass = new CriticalityClass();
        criticalityClass.setId(criticalityClassId);
        criticalityClass.setCode("CRIT-HIGH");
        criticalityClass.setName("High criticality");

        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(equipment));
        when(criticalityClassRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(criticalityClass));
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());

        List<EquipmentRiskScore> scores = service.computeAll();

        assertThat(scores).singleElement()
                .satisfies(score -> {
                    assertThat(score.criticalityClass()).isEqualTo("CRIT-HIGH");
                    assertThat(score.criticalityClassName()).isEqualTo("High criticality");
                });
    }

    @Test
    void riskScoresIncludeLocalizedUzbekCalculationExplanation() {
        UUID equipmentId = UUID.randomUUID();
        UUID criticalityClassId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId);
        equipment.setCriticalityClassId(criticalityClassId);

        CriticalityClass criticalityClass = new CriticalityClass();
        criticalityClass.setId(criticalityClassId);
        criticalityClass.setCode("CRIT-MED");
        criticalityClass.setName("Medium criticality");
        criticalityClass.setSafetyImpact(4);
        criticalityClass.setProductionImpact(5);
        criticalityClass.setEcologicalImpact(3);
        criticalityClass.setEnergyImpact(2);

        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(equipment));
        when(criticalityClassRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(criticalityClass));
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                openDefect(equipmentId),
                openDefect(equipmentId),
                openDefect(equipmentId)
        ));
        when(reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());

        EquipmentRiskScore score = service.computeAll("uz").getFirst();

        assertThat(score.riskScore()).isEqualTo(56);
        assertThat(score.explanation().locale()).isEqualTo("uz");
        assertThat(score.explanation().formula()).isEqualTo("min(100, oqibat × ehtimollik)");
        assertThat(score.explanation().summary())
                .isEqualTo("Xavf 56/100, chunki oqibat 14 va ehtimollik 4.");
        assertThat(score.explanation().steps()).anySatisfy(step -> {
            assertThat(step.label()).isEqualTo("Xavfsizlik ta'siri");
            assertThat(step.value()).isEqualTo(4);
        });
        assertThat(score.explanation().steps()).anySatisfy(step -> {
            assertThat(step.label()).isEqualTo("Ishlab chiqarish ta'siri");
            assertThat(step.value()).isEqualTo(5);
        });
        assertThat(score.explanation().steps()).anySatisfy(step -> {
            assertThat(step.label()).isEqualTo("Yakuniy xavf");
            assertThat(step.value()).isEqualTo(56);
            assertThat(step.unit()).isEqualTo("/100");
        });
    }

    @Test
    void getHistoryUnknownEquipmentReturns404() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.historyFor(equipmentId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Equipment not found");
    }

    @Test
    void getHistoryKnownEquipmentWithoutSnapshotsReturnsEmptyArray() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        when(snapshotRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByCapturedAtDesc(equipmentId))
                .thenReturn(List.of());

        List<RcmSnapshot> history = service.historyFor(equipmentId);

        assertThat(history).isNotNull().isEmpty();
    }

    @Test
    void postSnapshotGeneratesAndSavesSnapshots() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(equipment(equipmentId)));
        when(criticalityClassRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(snapshotRepository.save(any(RcmSnapshot.class))).thenAnswer(invocation -> {
            RcmSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(UUID.randomUUID());
            return snapshot;
        });

        List<RcmSnapshot> created = service.captureSnapshot();

        assertThat(created).hasSize(1);
        ArgumentCaptor<RcmSnapshot> captor = ArgumentCaptor.forClass(RcmSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getEquipmentId()).isEqualTo(equipmentId);
    }

    @Test
    void postThenGetHistoryReturnsNonEmptyForSameEquipment() {
        UUID equipmentId = UUID.randomUUID();
        List<RcmSnapshot> storage = new ArrayList<>();

        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(equipment(equipmentId)));
        when(criticalityClassRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(snapshotRepository.save(any(RcmSnapshot.class))).thenAnswer(invocation -> {
            RcmSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(UUID.randomUUID());
            storage.add(snapshot);
            return snapshot;
        });
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        when(snapshotRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByCapturedAtDesc(equipmentId))
                .thenAnswer(invocation -> storage.stream()
                        .filter(s -> equipmentId.equals(s.getEquipmentId()))
                        .toList());

        service.captureSnapshot();
        List<RcmSnapshot> history = service.historyFor(equipmentId);

        assertThat(history).isNotEmpty();
        assertThat(history.getFirst().getEquipmentId()).isEqualTo(equipmentId);
    }

    private Equipment equipment(UUID id) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("EQ-2026-1001");
        equipment.setName("Compressor");
        equipment.setInventoryNumber("INV-1001");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(UUID.randomUUID());
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }

    private Defect openDefect(UUID equipmentId) {
        Defect defect = new Defect();
        defect.setEquipmentId(equipmentId);
        defect.setStatus(DefectStatus.OPEN);
        return defect;
    }
}
