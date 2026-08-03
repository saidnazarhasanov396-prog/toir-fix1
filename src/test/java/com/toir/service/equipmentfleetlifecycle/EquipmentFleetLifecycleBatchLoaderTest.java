package com.toir.service.equipmentfleetlifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.toir.dto.equipmentfleetlifecycle.EquipmentFleetLifecycleV1;
import com.toir.repository.equipmentfleetlifecycle.EquipmentFleetLifecycleQueryRepository;
import com.toir.repository.equipmentfleetlifecycle.EquipmentFleetLifecycleQueryRepository.EquipmentRow;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class EquipmentFleetLifecycleBatchLoaderTest {

    private static final UUID DEPARTMENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID AFTER_EXCLUSIVE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID FIRST_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID SENTINEL_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final Instant AS_OF = Instant.parse("2026-08-03T08:15:30Z");

    @Mock
    private EquipmentFleetLifecycleQueryRepository repository;

    @Mock
    private EquipmentFleetLifecycleBatchAssembler assembler;

    private EquipmentFleetLifecycleBatchLoader loader;

    @BeforeEach
    void setUp() {
        loader = new EquipmentFleetLifecycleBatchLoader(repository, assembler);
    }

    @Test
    void loadOwnsAReadOnlyTransactionBoundary() throws Exception {
        Method load = EquipmentFleetLifecycleBatchLoader.class.getMethod(
                "load", UUID.class, boolean.class, UUID.class, Instant.class, int.class);

        Transactional transactional = load.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    void loadTrimsSentinelBeforeBulkQueriesAndUsesFinalEmittedEquipmentAsCursor() {
        EquipmentRow first = equipment(FIRST_ID);
        EquipmentRow second = equipment(SECOND_ID);
        EquipmentRow sentinel = equipment(SENTINEL_ID);
        List<EquipmentRow> selected = List.of(first, second, sentinel);
        List<EquipmentFleetLifecycleV1.Line> lines = List.of(line(FIRST_ID), line(SECOND_ID));
        when(repository.findEquipmentBatch(DEPARTMENT_ID, false, AFTER_EXCLUSIVE, 3))
                .thenReturn(selected);
        when(repository.findMeters(List.of(FIRST_ID, SECOND_ID))).thenReturn(List.of());
        when(repository.findLatestReadings(List.of(FIRST_ID, SECOND_ID), AS_OF)).thenReturn(List.of());
        when(repository.findRepairs(List.of(FIRST_ID, SECOND_ID), AS_OF)).thenReturn(List.of());
        when(repository.findRepairMeterSnapshots(List.of(FIRST_ID, SECOND_ID), AS_OF)).thenReturn(List.of());
        when(assembler.assemble(
                List.of(first, second), List.of(), List.of(), List.of(), List.of(), AS_OF))
                .thenReturn(lines);

        EquipmentFleetLifecycleBatchLoader.Batch batch =
                loader.load(DEPARTMENT_ID, false, AFTER_EXCLUSIVE, AS_OF, 2);

        assertThat(batch.lines()).containsExactlyElementsOf(lines);
        assertThat(batch.lastEquipmentId()).isEqualTo(SECOND_ID);
        assertThat(batch.hasMore()).isTrue();
        verify(repository).findEquipmentBatch(DEPARTMENT_ID, false, AFTER_EXCLUSIVE, 3);
        verify(repository).findMeters(List.of(FIRST_ID, SECOND_ID));
        verify(repository).findLatestReadings(List.of(FIRST_ID, SECOND_ID), AS_OF);
        verify(repository).findRepairs(List.of(FIRST_ID, SECOND_ID), AS_OF);
        verify(repository).findRepairMeterSnapshots(List.of(FIRST_ID, SECOND_ID), AS_OF);
        verify(assembler).assemble(
                List.of(first, second), List.of(), List.of(), List.of(), List.of(), AS_OF);
    }

    @Test
    void emptyEquipmentBatchSkipsAllRelationQueriesAndAssembly() {
        when(repository.findEquipmentBatch(null, true, null, 101)).thenReturn(List.of());

        EquipmentFleetLifecycleBatchLoader.Batch batch = loader.load(null, true, null, AS_OF, 100);

        assertThat(batch.lines()).isEmpty();
        assertThat(batch.lastEquipmentId()).isNull();
        assertThat(batch.hasMore()).isFalse();
        verify(repository).findEquipmentBatch(null, true, null, 101);
        verify(repository, never()).findMeters(org.mockito.ArgumentMatchers.anyList());
        verify(repository, never()).findLatestReadings(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any());
        verify(repository, never()).findRepairs(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any());
        verify(repository, never()).findRepairMeterSnapshots(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(assembler);
    }

    private static EquipmentRow equipment(UUID id) {
        return new EquipmentRow(
                id,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static EquipmentFleetLifecycleV1.Line line(UUID equipmentId) {
        return new EquipmentFleetLifecycleV1.Line(
                EquipmentFleetLifecycleV1.SCHEMA_VERSION,
                AS_OF,
                EquipmentFleetLifecycleV1.CONSISTENCY,
                new EquipmentFleetLifecycleV1.EquipmentCore(
                        equipmentId,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                List.of(),
                null,
                List.of(),
                new EquipmentFleetLifecycleV1.DataQuality(true, List.of()));
    }
}
