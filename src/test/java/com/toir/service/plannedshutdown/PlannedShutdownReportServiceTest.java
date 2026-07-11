package com.toir.service.plannedshutdown;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.toir.entity.PlannedShutdown;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.plannedshutdown.*;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.enums.*;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.plannedshutdown.*;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlannedShutdownReportServiceTest {
    @Mock PlannedShutdownClosureSnapshotRepository snapshotRepository;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock PlannedShutdownWorkItemRepository workItemRepository;
    @Mock PlannedShutdownStatusHistoryRepository historyRepository;
    @Mock RepairMaterialUsageRepository materialUsageRepository;
    @Mock ActualCostRepository costRepository;
    @Mock PlannedShutdownStartupTestRepository testRepository;
    @Mock PlannedShutdownProductionReturnRepository productionReturnRepository;
    PlannedShutdownReportService service;
    UUID shutdownId;
    PlannedShutdown shutdown;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new PlannedShutdownReportService(snapshotRepository, workOrderRepository, workItemRepository,
                historyRepository, materialUsageRepository, costRepository, testRepository,
                productionReturnRepository, mapper);
        shutdownId = UUID.randomUUID();
        shutdown = new PlannedShutdown();
        shutdown.setId(shutdownId);
        shutdown.setCode("PS-REPORT");
        shutdown.setScopeVersion(4L);
        shutdown.setWindowVersion(3L);
        shutdown.setPlannedStartAt(Instant.parse("2026-07-12T01:00:00Z"));
        shutdown.setPlannedEndAt(Instant.parse("2026-07-12T05:00:00Z"));
        shutdown.setActualShutdownAt(Instant.parse("2026-07-12T01:10:00Z"));
        shutdown.setActualCompletedAt(Instant.parse("2026-07-12T04:40:00Z"));
        lenient().when(snapshotRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(snapshotRepository.findByPlannedShutdownId(shutdownId))
                .thenReturn(Optional.empty());
        lenient().when(historyRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOccurredAtAsc(shutdownId))
                .thenReturn(List.of());
        lenient().when(testRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId))
                .thenReturn(List.of());
        PlannedShutdownProductionReturn signoff = new PlannedShutdownProductionReturn();
        signoff.setPlannedShutdownId(shutdownId);
        signoff.setApprovedById(UUID.randomUUID());
        signoff.setApprovedAt(Instant.parse("2026-07-12T04:35:00Z"));
        signoff.setEvidence("stable operation");
        lenient().when(productionReturnRepository.findByPlannedShutdownIdAndIsDeletedFalse(shutdownId))
                .thenReturn(Optional.of(signoff));
    }

    @Test
    void snapshotDeduplicatesCanonicalWorkMaterialCostDefectAndSourceIds() {
        UUID workOrderId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        WorkOrder order = new WorkOrder();
        order.setId(workOrderId);
        order.setStatus(WorkOrderStatus.COMPLETED);
        order.setDefectId(defectId);
        order.setResult("seal replaced");
        when(workOrderRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByUpdatedAtDesc(shutdownId))
                .thenReturn(List.of(order, order));

        UUID sourceId = UUID.randomUUID();
        PlannedShutdownWorkItem first = workItem(sourceId);
        PlannedShutdownWorkItem duplicate = workItem(sourceId);
        when(workItemRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId))
                .thenReturn(List.of(first, duplicate));

        RepairMaterialUsage usage = new RepairMaterialUsage();
        usage.setId(UUID.randomUUID());
        when(materialUsageRepository.findAllByWorkOrderIdInAndIsDeletedFalseOrderByUpdatedAtDesc(List.of(workOrderId)))
                .thenReturn(List.of(usage, usage));
        ActualCost cost = new ActualCost();
        cost.setId(UUID.randomUUID());
        when(costRepository.findAllByWorkOrderIdInAndIsDeletedFalseOrderByUpdatedAtDesc(List.of(workOrderId)))
                .thenReturn(List.of(cost, cost));

        var report = service.createSnapshot(shutdown, UUID.randomUUID());

        assertThat(report.workOrders()).hasSize(1);
        assertThat(report.sourceIds()).containsExactly(sourceId);
        assertThat(report.defectIds()).containsExactly(defectId);
        assertThat(report.materialUsageIds()).containsExactly(usage.getId());
        assertThat(report.actualCostIds()).containsExactly(cost.getId());
        assertThat(report.workOrderStatusCounts()).containsEntry("COMPLETED", 1L);
        assertThat(report.plannedDowntimeMinutes()).isEqualTo(240L);
        assertThat(report.actualDowntimeMinutes()).isEqualTo(210L);
        verify(snapshotRepository).saveAndFlush(argThat(snapshot -> snapshot.getSnapshotHash().matches("[0-9a-f]{64}")));
    }

    @Test
    void closureSnapshotIsSingleAndReadIsIdempotent() {
        PlannedShutdownClosureSnapshot existing = new PlannedShutdownClosureSnapshot();
        existing.setPlannedShutdownId(shutdownId);
        existing.setSnapshotJson("{}");
        existing.setSnapshotHash(sha256("{}"));
        when(snapshotRepository.findByPlannedShutdownId(shutdownId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createSnapshot(shutdown, UUID.randomUUID()))
                .hasMessageContaining("CLOSURE_SNAPSHOT_ALREADY_EXISTS");
        service.readSnapshot(shutdownId);
        service.readSnapshot(shutdownId);
        verify(snapshotRepository, times(3)).findByPlannedShutdownId(shutdownId);
        verify(snapshotRepository, never()).saveAndFlush(any());
    }

    @Test
    void deletedSnapshotStillBlocksSecondSnapshot() {
        PlannedShutdownClosureSnapshot existing = new PlannedShutdownClosureSnapshot();
        existing.setPlannedShutdownId(shutdownId);
        existing.setDeleted(true);
        when(snapshotRepository.findByPlannedShutdownId(shutdownId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createSnapshot(shutdown, UUID.randomUUID()))
                .hasMessageContaining("CLOSURE_SNAPSHOT_ALREADY_EXISTS");
        verify(snapshotRepository, never()).saveAndFlush(any());
    }

    @Test
    void readRejectsStoredJsonWhoseHashDoesNotMatch() {
        PlannedShutdownClosureSnapshot tampered = new PlannedShutdownClosureSnapshot();
        tampered.setPlannedShutdownId(shutdownId);
        tampered.setSnapshotJson("{}");
        tampered.setSnapshotHash("0".repeat(64));
        when(snapshotRepository.findByPlannedShutdownId(shutdownId)).thenReturn(Optional.of(tampered));

        assertThatThrownBy(() -> service.readSnapshot(shutdownId))
                .hasMessageContaining("CLOSURE_SNAPSHOT_INTEGRITY_FAILED");
    }

    @Test
    void snapshotEntityIsHibernateImmutableAndColumnsCannotBeUpdated() throws Exception {
        assertThat(PlannedShutdownClosureSnapshot.class
                .isAnnotationPresent(org.hibernate.annotations.Immutable.class)).isTrue();
        for (var field : PlannedShutdownClosureSnapshot.class.getDeclaredFields()) {
            jakarta.persistence.Column column = field.getAnnotation(jakarta.persistence.Column.class);
            if (column != null) assertThat(column.updatable()).as(field.getName()).isFalse();
        }
        var overrides = PlannedShutdownClosureSnapshot.class
                .getAnnotation(jakarta.persistence.AttributeOverrides.class);
        assertThat(overrides).isNotNull();
        assertThat(overrides.value()).extracting(jakarta.persistence.AttributeOverride::name)
                .contains("updatedAt", "isDeleted");
        assertThat(overrides.value()).allMatch(override -> !override.column().updatable());
    }

    private PlannedShutdownWorkItem workItem(UUID sourceId) {
        PlannedShutdownWorkItem item = new PlannedShutdownWorkItem();
        item.setSourceType(PlannedShutdownWorkItemSourceType.DEFECT);
        item.setSourceId(sourceId);
        return item;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
