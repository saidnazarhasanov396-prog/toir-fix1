package com.toir.service.equipmentfleetlifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.toir.dto.equipmentfleetlifecycle.EquipmentFleetLifecycleV1;
import com.toir.security.ScopeAccessService;
import com.toir.service.equipmentfleetlifecycle.EquipmentFleetLifecycleBatchLoader.Batch;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EquipmentFleetLifecycleStreamServiceTest {

    private static final UUID DEPARTMENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID FIRST_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID THIRD_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant AS_OF = Instant.parse("2026-08-03T08:15:30Z");
    private static final String CORRELATION_ID = "fleet-request-42";
    private static final Instant NANOSECOND_AS_OF =
            Instant.parse("2026-08-03T08:15:30.123456999Z");
    private static final Instant MICROSECOND_AS_OF =
            Instant.parse("2026-08-03T08:15:30.123456Z");

    @Mock
    private ScopeAccessService scopeAccessService;

    @Mock
    private EquipmentFleetLifecycleBatchLoader loader;

    private EquipmentFleetLifecycleStreamService service;

    @BeforeEach
    void setUp() {
        service = new EquipmentFleetLifecycleStreamService(scopeAccessService, loader);
    }

    @Test
    void prepareResolvesDepartmentScopeAndLoadsFirstBatchOnCallingThread() {
        AtomicReference<Thread> loadingThread = new AtomicReference<>();
        Batch firstBatch = new Batch(List.of(line(FIRST_ID)), FIRST_ID, false);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(DEPARTMENT_ID);
        when(loader.load(DEPARTMENT_ID, false, null, AS_OF, 100)).thenAnswer(invocation -> {
            loadingThread.set(Thread.currentThread());
            return firstBatch;
        });

        EquipmentFleetLifecycleStreamService.PreparedFleetStream prepared = service.prepare(AS_OF, CORRELATION_ID);

        assertThat(loadingThread.get()).isSameAs(Thread.currentThread());
        assertThat(prepared.scopeDepartmentId()).isEqualTo(DEPARTMENT_ID);
        assertThat(prepared.denyAll()).isFalse();
        assertThat(prepared.generatedAt()).isEqualTo(AS_OF);
        assertThat(prepared.correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(prepared.firstBatch()).isSameAs(firstBatch);
        verify(loader).load(DEPARTMENT_ID, false, null, AS_OF, 100);
    }

    @Test
    void prepareUsesUnscopedAccessForScopeAdministrator() {
        Batch empty = new Batch(List.of(), null, false);
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(loader.load(null, false, null, AS_OF, 100)).thenReturn(empty);

        EquipmentFleetLifecycleStreamService.PreparedFleetStream prepared = service.prepare(AS_OF, CORRELATION_ID);

        assertThat(prepared.scopeDepartmentId()).isNull();
        assertThat(prepared.denyAll()).isFalse();
        verify(scopeAccessService, never()).currentDepartmentIdOrNull();
        verify(loader).load(null, false, null, AS_OF, 100);
    }

    @Test
    void prepareDeniesAllWhenNonAdministratorHasNoDepartment() {
        Batch empty = new Batch(List.of(), null, false);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(null);
        when(loader.load(null, true, null, AS_OF, 100)).thenReturn(empty);

        EquipmentFleetLifecycleStreamService.PreparedFleetStream prepared = service.prepare(AS_OF, CORRELATION_ID);

        assertThat(prepared.scopeDepartmentId()).isNull();
        assertThat(prepared.denyAll()).isTrue();
        verify(loader).load(null, true, null, AS_OF, 100);
    }

    @Test
    void prepareTruncatesTheResponseWatermarkToMicrosecondsBeforeEveryBatchAndDto() throws IOException {
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(DEPARTMENT_ID);
        when(loader.load(DEPARTMENT_ID, false, null, MICROSECOND_AS_OF, 100))
                .thenReturn(new Batch(
                        List.of(line(FIRST_ID, MICROSECOND_AS_OF)), FIRST_ID, true));
        when(loader.load(DEPARTMENT_ID, false, FIRST_ID, MICROSECOND_AS_OF, 100))
                .thenReturn(new Batch(
                        List.of(line(SECOND_ID, MICROSECOND_AS_OF)), SECOND_ID, false));

        EquipmentFleetLifecycleStreamService.PreparedFleetStream prepared =
                service.prepare(NANOSECOND_AS_OF, CORRELATION_ID);
        List<EquipmentFleetLifecycleV1.Line> emitted = new ArrayList<>();
        service.stream(prepared, emitted::add);

        assertThat(prepared.generatedAt()).isEqualTo(MICROSECOND_AS_OF);
        assertThat(emitted)
                .extracting(EquipmentFleetLifecycleV1.Line::generatedAt)
                .containsExactly(MICROSECOND_AS_OF, MICROSECOND_AS_OF);
        verify(loader).load(DEPARTMENT_ID, false, null, MICROSECOND_AS_OF, 100);
        verify(loader).load(DEPARTMENT_ID, false, FIRST_ID, MICROSECOND_AS_OF, 100);
    }

    @Test
    void streamUsesFinalEmittedCursorAndDeliversEveryLineExactlyOnceWithoutReadingScopeAgain()
            throws IOException {
        EquipmentFleetLifecycleV1.Line first = line(FIRST_ID);
        EquipmentFleetLifecycleV1.Line second = line(SECOND_ID);
        EquipmentFleetLifecycleV1.Line third = line(THIRD_ID);
        Batch firstBatch = new Batch(List.of(first, second), SECOND_ID, true);
        Batch secondBatch = new Batch(List.of(third), THIRD_ID, false);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(DEPARTMENT_ID);
        when(loader.load(DEPARTMENT_ID, false, null, AS_OF, 100)).thenReturn(firstBatch);
        when(loader.load(DEPARTMENT_ID, false, SECOND_ID, AS_OF, 100)).thenReturn(secondBatch);
        EquipmentFleetLifecycleStreamService.PreparedFleetStream prepared = service.prepare(AS_OF, CORRELATION_ID);
        clearInvocations(scopeAccessService);
        List<EquipmentFleetLifecycleV1.Line> accepted = new ArrayList<>();

        long count = service.stream(prepared, accepted::add);

        assertThat(count).isEqualTo(3L);
        assertThat(accepted).containsExactly(first, second, third);
        verify(loader).load(DEPARTMENT_ID, false, SECOND_ID, AS_OF, 100);
        verifyNoInteractions(scopeAccessService);
    }

    @Test
    void sinkIOExceptionStopsImmediatelyAndPropagatesWithoutLoadingAnotherBatch() {
        EquipmentFleetLifecycleV1.Line first = line(FIRST_ID);
        EquipmentFleetLifecycleV1.Line second = line(SECOND_ID);
        Batch firstBatch = new Batch(List.of(first, second), SECOND_ID, true);
        EquipmentFleetLifecycleStreamService.PreparedFleetStream prepared =
                new EquipmentFleetLifecycleStreamService.PreparedFleetStream(
                        DEPARTMENT_ID, false, AS_OF, CORRELATION_ID, firstBatch);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> service.stream(prepared, line -> {
            attempts.incrementAndGet();
            throw new IOException("client disconnected");
        }))
                .isInstanceOf(IOException.class)
                .hasMessage("client disconnected");

        assertThat(attempts).hasValue(1);
        verify(loader, never()).load(DEPARTMENT_ID, false, SECOND_ID, AS_OF, 100);
        verifyNoMoreInteractions(scopeAccessService);
    }

    private static EquipmentFleetLifecycleV1.Line line(UUID equipmentId) {
        return line(equipmentId, AS_OF);
    }

    private static EquipmentFleetLifecycleV1.Line line(UUID equipmentId, Instant generatedAt) {
        return new EquipmentFleetLifecycleV1.Line(
                EquipmentFleetLifecycleV1.SCHEMA_VERSION,
                generatedAt,
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
