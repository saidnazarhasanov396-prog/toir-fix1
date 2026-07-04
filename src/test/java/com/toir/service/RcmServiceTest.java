package com.toir.service;

import com.toir.dto.rcm.EquipmentRiskScore;
import com.toir.entity.RcmSnapshot;
import com.toir.entity.ReliabilityMetric;
import com.toir.entity.DowntimeEvent;
import com.toir.entity.defects.Defect;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.CriticalityLevel;
import com.toir.entity.equipment.CriticalityClass;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.DowntimeType;
import com.toir.enums.DefectStatus;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.exception.RestException;
import com.toir.repository.CriticalityClassRepository;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.RcmSnapshotRepository;
import com.toir.repository.ReliabilityMetricRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.repair.RepairRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
    DowntimeEventRepository downtimeEventRepository;

    @Mock
    RepairRequestRepository repairRequestRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    MaintenanceDueEventRepository maintenanceDueEventRepository;

    @Mock
    RcmSnapshotRepository snapshotRepository;

    MetricExplanationService metricExplanationService = new MetricExplanationService();

    RcmService service;

    @BeforeEach
    void setUp() {
        EquipmentRiskEvidenceService evidenceService = new EquipmentRiskEvidenceService(
                criticalityClassRepository,
                defectRepository,
                reliabilityMetricRepository,
                downtimeEventRepository,
                workOrderRepository,
                repairRequestRepository,
                maintenanceDueEventRepository
        );
        service = new RcmService(
                equipmentRepository,
                snapshotRepository,
                evidenceService,
                new EquipmentRiskScoringService(),
                new RcmFailureForecastService(),
                metricExplanationService
        );
    }

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
        criticalityClass.setLevel(CriticalityLevel.HIGH);

        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(equipment));
        when(criticalityClassRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(criticalityClass));
        stubEmptyRiskEvidenceSources();

        List<EquipmentRiskScore> scores = service.computeAll();

        assertThat(scores).singleElement()
                .satisfies(score -> {
                    assertThat(score.criticalityClass()).isEqualTo("CRIT-HIGH");
                    assertThat(score.criticalityClassName()).isEqualTo("High criticality");
                });
    }

    @Test
    void riskScoresIncludeLocalizedUzbekStructuredRiskExplanation() {
        UUID equipmentId = UUID.randomUUID();
        UUID criticalityClassId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId);
        equipment.setCriticalityClassId(criticalityClassId);

        CriticalityClass criticalityClass = new CriticalityClass();
        criticalityClass.setId(criticalityClassId);
        criticalityClass.setCode("CRIT-MED");
        criticalityClass.setName("Medium criticality");
        criticalityClass.setLevel(CriticalityLevel.HIGH);
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
        when(downtimeEventRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of());
        when(workOrderRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of());
        when(repairRequestRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of());
        when(maintenanceDueEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());

        EquipmentRiskScore score = service.computeAll("uz").getFirst();

        assertThat(score.consequence()).isEqualTo(4);
        assertThat(score.probability()).isEqualTo(4);
        assertThat(score.riskScore()).isEqualTo(16);
        assertThat(score.explanation().locale()).isEqualTo("uz");
        assertThat(score.explanation().formula())
                .isEqualTo("min(100, oqibat × ehtimollik)");
        assertThat(score.explanation().summary())
                .isEqualTo("Xavf 16/100, chunki 3 ta ochiq nuqson aniqlandi, shuning uchun ehtimollik yuqori.");
        assertThat(score.reasons()).extracting(reason -> reason.code().name())
                .containsExactly("OPEN_DEFECTS_HIGH", "HIGH_CRITICALITY");
        assertThat(score.explanation().reasons()).anySatisfy(reason -> {
            assertThat(reason.label()).isEqualTo("Ochiq nuqsonlar");
            assertThat(reason.value()).isEqualTo(3L);
            assertThat(reason.effect()).isEqualTo("Ehtimollik 4 ga o'rnatildi");
        });
        assertThat(score.explanation().steps()).anySatisfy(step -> {
            assertThat(step.label()).isEqualTo("Ehtimollik");
            assertThat(step.value()).isEqualTo(4);
        });
        assertThat(score.explanation().steps()).anySatisfy(step -> {
            assertThat(step.label()).isEqualTo("Yakuniy xavf");
            assertThat(step.value()).isEqualTo(16);
            assertThat(step.unit()).isEqualTo("/100");
        });
        assertThat(score.explanation().steps()).noneSatisfy(step ->
                assertThat(step.label()).isEqualTo("MTTR"));
        assertThat(score.explanation().steps()).extracting(step -> step.label())
                .doesNotContain("Xavfsizlik ta'siri", "Ishlab chiqarish ta'siri", "Ekologik ta'sir", "Energiya ta'siri");
    }

    @Test
    void riskScoresDoNotUseStaticImpactFieldsForConsequence() {
        UUID equipmentId = UUID.randomUUID();
        UUID criticalityClassId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId);
        equipment.setCriticalityClassId(criticalityClassId);

        CriticalityClass criticalityClass = new CriticalityClass();
        criticalityClass.setId(criticalityClassId);
        criticalityClass.setCode("CRIT-HIGH");
        criticalityClass.setName("High criticality");
        criticalityClass.setLevel(CriticalityLevel.HIGH);
        criticalityClass.setSafetyImpact(5);
        criticalityClass.setProductionImpact(5);
        criticalityClass.setEcologicalImpact(5);
        criticalityClass.setEnergyImpact(5);

        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(equipment));
        when(criticalityClassRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(criticalityClass));
        stubEmptyRiskEvidenceSources();

        EquipmentRiskScore score = service.computeAll("en").getFirst();

        assertThat(score.consequence()).isEqualTo(4);
        assertThat(score.probability()).isEqualTo(1);
        assertThat(score.riskScore()).isEqualTo(4);
        assertThat(score.explanation().steps()).extracting(step -> step.label())
                .doesNotContain("Safety impact", "Production impact", "Ecological impact", "Energy impact");
    }

    @Test
    void riskScoresIncludeDowntimeMaintenanceRepairAndStatusReasons() {
        UUID equipmentId = UUID.randomUUID();
        UUID criticalityClassId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId);
        equipment.setCriticalityClassId(criticalityClassId);
        equipment.setStatus(EquipmentStatus.IN_REPAIR);

        CriticalityClass criticalityClass = new CriticalityClass();
        criticalityClass.setId(criticalityClassId);
        criticalityClass.setCode("CRIT-CRITICAL");
        criticalityClass.setName("Critical equipment");
        criticalityClass.setLevel(CriticalityLevel.CRITICAL);

        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(equipment));
        when(criticalityClassRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(criticalityClass));
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(recurringOpenDefect(equipmentId)));
        when(reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(reliabilityMetric(equipmentId, 1500, 30)));
        when(downtimeEventRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(downtimeEvent(equipmentId, 30 * 60)));
        when(workOrderRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of());
        when(repairRequestRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(repairRequest(equipmentId, PriorityLevel.EMERGENCY, RequestStatus.OPEN)));
        when(maintenanceDueEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(overdueMaintenance(equipmentId)));

        EquipmentRiskScore score = service.computeAll("ru").getFirst();

        assertThat(score.probability()).isEqualTo(5);
        assertThat(score.consequence()).isEqualTo(14);
        assertThat(score.riskScore()).isEqualTo(70);
        assertThat(score.explanation().locale()).isEqualTo("ru");
        assertThat(score.explanation().reasons()).extracting(reason -> reason.code().name())
                .contains(
                        "RECURRING_DEFECTS",
                        "OVERDUE_MAINTENANCE",
                        "RECENT_DOWNTIME",
                        "HIGH_MTTR",
                        "OPEN_HIGH_REPAIR_REQUEST",
                        "EQUIPMENT_UNAVAILABLE"
                );
    }

    @Test
    void riskScoreIncludesMtbfBasedFailureForecastWhenLastFailureExists() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId);
        Instant lastFailure = Instant.now().minus(Duration.ofHours(90));

        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(equipment));
        when(criticalityClassRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(reliabilityMetric(equipmentId, 100, 4)));
        when(downtimeEventRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(downtimeEvent(equipmentId, lastFailure)));
        when(workOrderRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of());
        when(repairRequestRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of());
        when(maintenanceDueEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());

        EquipmentRiskScore score = service.computeAll("ru").getFirst();

        assertThat(score.failureForecast()).isNotNull();
        assertThat(score.failureForecast().status()).isEqualTo("WITHIN_7_DAYS");
        assertThat(score.failureForecast().expectedFailureAt()).isEqualTo(lastFailure.plus(Duration.ofHours(100)));
        assertThat(score.failureForecast().mtbfHours()).isEqualTo(100);
        assertThat(score.failureForecast().lastFailureAt()).isEqualTo(lastFailure);
    }

    @Test
    void riskScoreUsesCalculatedReliabilityWhenStoredMetricIsMissing() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId);
        equipment.setOperationStartDate(LocalDate.now().minusDays(30));
        Instant lastFailure = Instant.now().minus(Duration.ofDays(2));

        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(equipment));
        when(criticalityClassRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(downtimeEventRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(downtimeEvent(equipmentId, lastFailure)));
        when(workOrderRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of());
        when(repairRequestRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of());
        when(maintenanceDueEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());

        EquipmentRiskScore score = service.computeAll("ru").getFirst();

        assertThat(score.mtbfHours()).isGreaterThan(0);
        assertThat(score.failureForecast().status()).isNotEqualTo("INSUFFICIENT_DATA");
        assertThat(score.failureForecast().mtbfHours()).isEqualTo(score.mtbfHours());
    }

    @Test
    void riskScoreShowsImmediateActionWithoutDateWhenOpenDefectsDriveProbability() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId);

        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(equipment));
        when(criticalityClassRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                openDefect(equipmentId),
                openDefect(equipmentId),
                openDefect(equipmentId),
                openDefect(equipmentId),
                openDefect(equipmentId)
        ));
        when(reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(downtimeEventRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of());
        when(workOrderRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of());
        when(repairRequestRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of());
        when(maintenanceDueEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());

        EquipmentRiskScore score = service.computeAll("ru").getFirst();

        assertThat(score.probability()).isEqualTo(5);
        assertThat(score.failureForecast().status()).isEqualTo("DUE_NOW_FROM_OPEN_DEFECTS");
        assertThat(score.failureForecast().expectedFailureAt()).isNull();
        assertThat(score.failureForecast().label()).contains("Срок не рассчитан");
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
        stubEmptyRiskEvidenceSources();
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
    void computeAllSortsByRequestedMetric() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(equipment(firstId), equipment(secondId)));
        when(criticalityClassRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(
                        reliabilityMetric(firstId, 4000, 12),
                        reliabilityMetric(secondId, 1200, 4)
                ));
        when(downtimeEventRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(firstId, secondId))).thenReturn(List.of());
        when(repairRequestRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(firstId, secondId))).thenReturn(List.of());
        when(maintenanceDueEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());

        List<EquipmentRiskScore> scores = service.computeAll("mtbfHours", "asc");

        assertThat(scores).extracting(EquipmentRiskScore::equipmentId)
                .containsExactly(secondId, firstId);
    }

    @Test
    void postThenGetHistoryReturnsNonEmptyForSameEquipment() {
        UUID equipmentId = UUID.randomUUID();
        List<RcmSnapshot> storage = new ArrayList<>();

        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(equipment(equipmentId)));
        when(criticalityClassRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        stubEmptyRiskEvidenceSources();
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

    private Defect recurringOpenDefect(UUID equipmentId) {
        Defect defect = openDefect(equipmentId);
        defect.setRecurrenceCount(2);
        return defect;
    }

    private ReliabilityMetric reliabilityMetric(UUID equipmentId, double mtbfHours, double mttrHours) {
        ReliabilityMetric metric = new ReliabilityMetric();
        metric.setEquipmentId(equipmentId);
        metric.setMetricDate(LocalDate.parse("2026-06-01"));
        metric.setMtbfHours(mtbfHours);
        metric.setMttrHours(mttrHours);
        return metric;
    }

    private DowntimeEvent downtimeEvent(UUID equipmentId, int durationMinutes) {
        DowntimeEvent event = new DowntimeEvent();
        event.setEquipmentId(equipmentId);
        event.setDepartmentId(UUID.randomUUID());
        event.setStartAt(Instant.now().minusSeconds(3600));
        event.setDurationMinutes(durationMinutes);
        event.setType(DowntimeType.UNPLANNED);
        return event;
    }

    private DowntimeEvent downtimeEvent(UUID equipmentId, Instant failureAt) {
        DowntimeEvent event = new DowntimeEvent();
        event.setEquipmentId(equipmentId);
        event.setDepartmentId(UUID.randomUUID());
        event.setStartAt(failureAt.minus(Duration.ofHours(2)));
        event.setEndAt(failureAt);
        event.setDurationMinutes(120);
        event.setType(DowntimeType.UNPLANNED);
        return event;
    }

    private RepairRequest repairRequest(UUID equipmentId, PriorityLevel priority, RequestStatus status) {
        RepairRequest request = new RepairRequest();
        request.setEquipmentId(equipmentId);
        request.setDepartmentId(UUID.randomUUID());
        request.setNumber("RR-1");
        request.setTitle("Repair");
        request.setDescription("Repair");
        request.setReporterId(UUID.randomUUID());
        request.setPriority(priority);
        request.setStatus(status);
        return request;
    }

    private MaintenanceDueEvent overdueMaintenance(UUID equipmentId) {
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        event.setEquipmentId(equipmentId);
        event.setDueStatus(MaintenanceDueStatus.OVERDUE);
        event.setStatus(MaintenanceDueEventStatus.DETECTED);
        event.setTriggerSource(MaintenanceTriggerSource.CALENDAR_JOB);
        event.setCycleKey("cycle");
        return event;
    }

    private void stubEmptyRiskEvidenceSources() {
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(downtimeEventRepository.findAllByEquipmentIdInAndIsDeletedFalse(any())).thenReturn(List.of());
        when(workOrderRepository.findAllByEquipmentIdInAndIsDeletedFalse(any())).thenReturn(List.of());
        when(repairRequestRepository.findAllByEquipmentIdInAndIsDeletedFalse(any())).thenReturn(List.of());
        when(maintenanceDueEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
    }
}
