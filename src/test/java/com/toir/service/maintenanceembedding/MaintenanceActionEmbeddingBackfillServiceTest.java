package com.toir.service.maintenanceembedding;

import com.toir.config.MaintenanceActionSemanticSearchProperties;
import com.toir.entity.maintenance.MaintenanceAction;
import com.toir.repository.maintenance.MaintenanceActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceActionEmbeddingBackfillServiceTest {

    @Mock
    private MaintenanceActionEmbeddingBackfillStore backfillStore;

    @Mock
    private MaintenanceActionRepository actionRepository;

    @Mock
    private DurableMaintenanceActionEmbeddingLifecycle lifecycle;

    private MaintenanceActionSemanticSearchProperties properties;
    private MaintenanceActionEmbeddingBackfillService service;

    @BeforeEach
    void setUp() {
        properties = properties();
        service = new MaintenanceActionEmbeddingBackfillService(
                backfillStore, actionRepository, lifecycle, properties);
    }

    @Test
    void scansOneBoundedKeysetBatchAndPersistsCursorAndCounters() {
        UUID runId = UUID.randomUUID();
        UUID previousCursor = UUID.randomUUID();
        MaintenanceAction first = action(UUID.randomUUID());
        MaintenanceAction second = action(UUID.randomUUID());
        when(backfillStore.lockForBatch(runId)).thenReturn(run(runId, previousCursor,
                MaintenanceActionBackfillStatus.RUNNING, 2));
        when(actionRepository.findEmbeddingBackfillBatch(previousCursor, 2))
                .thenReturn(List.of(first, second));
        when(lifecycle.enqueueWhenEnabled(first))
                .thenReturn(MaintenanceActionEmbeddingJobStore.EnqueueOutcome.ENQUEUED);
        when(lifecycle.enqueueWhenEnabled(second))
                .thenReturn(MaintenanceActionEmbeddingJobStore.EnqueueOutcome.ALREADY_PRESENT);
        when(backfillStore.recordBatch(org.mockito.ArgumentMatchers.eq(runId),
                org.mockito.ArgumentMatchers.any())).thenReturn(run(runId, second.getId(),
                MaintenanceActionBackfillStatus.RUNNING, 2));

        service.processNextBatch(runId);

        ArgumentCaptor<MaintenanceActionEmbeddingBackfillStore.BatchProgress> progress =
                ArgumentCaptor.forClass(MaintenanceActionEmbeddingBackfillStore.BatchProgress.class);
        verify(backfillStore).recordBatch(org.mockito.ArgumentMatchers.eq(runId), progress.capture());
        assertThat(progress.getValue().cursor()).isEqualTo(second.getId());
        assertThat(progress.getValue().scanned()).isEqualTo(2);
        assertThat(progress.getValue().enqueued()).isEqualTo(1);
        assertThat(progress.getValue().alreadyPresent()).isEqualTo(1);
        assertThat(progress.getValue().scanCompleted()).isFalse();
    }

    @Test
    void restartContinuesFromPersistedCursorAndCompletesOnShortBatch() {
        UUID runId = UUID.randomUUID();
        UUID persistedCursor = UUID.randomUUID();
        MaintenanceAction last = action(UUID.randomUUID());
        when(backfillStore.lockForBatch(runId)).thenReturn(run(runId, persistedCursor,
                MaintenanceActionBackfillStatus.RUNNING, 3));
        when(actionRepository.findEmbeddingBackfillBatch(persistedCursor, 3)).thenReturn(List.of(last));
        when(lifecycle.enqueueWhenEnabled(last))
                .thenReturn(MaintenanceActionEmbeddingJobStore.EnqueueOutcome.SKIPPED_BLANK);
        when(backfillStore.recordBatch(org.mockito.ArgumentMatchers.eq(runId),
                org.mockito.ArgumentMatchers.any())).thenReturn(run(runId, last.getId(),
                MaintenanceActionBackfillStatus.SCAN_COMPLETED, 3));

        service.processNextBatch(runId);

        ArgumentCaptor<MaintenanceActionEmbeddingBackfillStore.BatchProgress> progress =
                ArgumentCaptor.forClass(MaintenanceActionEmbeddingBackfillStore.BatchProgress.class);
        verify(backfillStore).recordBatch(org.mockito.ArgumentMatchers.eq(runId), progress.capture());
        assertThat(progress.getValue().cursor()).isEqualTo(last.getId());
        assertThat(progress.getValue().skipped()).isEqualTo(1);
        assertThat(progress.getValue().scanCompleted()).isTrue();
    }

    @Test
    void pausedRunSurvivesRestartWithoutScanningOrEnqueueing() {
        UUID runId = UUID.randomUUID();
        MaintenanceActionEmbeddingBackfillStore.BackfillRun paused = run(
                runId, UUID.randomUUID(), MaintenanceActionBackfillStatus.PAUSED, 2);
        when(backfillStore.lockForBatch(runId)).thenReturn(paused);

        assertThat(service.processNextBatch(runId)).isSameAs(paused);

        verify(actionRepository, never()).findEmbeddingBackfillBatch(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(lifecycle, never()).enqueueWhenEnabled(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void startPersistsImmutableRunIdentityAndDoesNotStartScanning() {
        UUID runId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        when(backfillStore.start(org.mockito.ArgumentMatchers.any()))
                .thenReturn(run(runId, null, MaintenanceActionBackfillStatus.REQUESTED, 2));

        service.start("operator-request-42", 2, requestedBy);

        ArgumentCaptor<MaintenanceActionEmbeddingBackfillStore.StartCommand> command =
                ArgumentCaptor.forClass(MaintenanceActionEmbeddingBackfillStore.StartCommand.class);
        verify(backfillStore).start(command.capture());
        assertThat(command.getValue().idempotencyKey()).isEqualTo("operator-request-42");
        assertThat(command.getValue().modelRevision()).isEqualTo("revision-42");
        assertThat(command.getValue().sourceSchemaVersion()).isEqualTo("maintenance-action-text-v1");
        verify(actionRepository, never()).findEmbeddingBackfillBatch(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private static MaintenanceAction action(UUID id) {
        MaintenanceAction action = new MaintenanceAction();
        action.setId(id);
        action.setName("Action " + id);
        return action;
    }

    private static MaintenanceActionEmbeddingBackfillStore.BackfillRun run(
            UUID id,
            UUID cursor,
            MaintenanceActionBackfillStatus status,
            int batchSize
    ) {
        return new MaintenanceActionEmbeddingBackfillStore.BackfillRun(
                id, status, cursor, 0, 0, 0, 0, batchSize, Instant.EPOCH, Instant.EPOCH);
    }

    private static MaintenanceActionSemanticSearchProperties properties() {
        MaintenanceActionSemanticSearchProperties properties = new MaintenanceActionSemanticSearchProperties();
        properties.setEnabled(true);
        properties.setBackfillEnabled(true);
        properties.setModelDimensionContractConfirmed(true);
        properties.setModelRevision("revision-42");
        properties.setRetryCount(2);
        properties.getBackfill().setMinimumBatchSize(1);
        properties.getBackfill().setDefaultBatchSize(2);
        properties.getBackfill().setMaximumBatchSize(10);
        MaintenanceActionSemanticSearchProperties.InputLimits limits = properties.getInputLimits();
        limits.setNameCharacters(100);
        limits.setCategoryCharacters(100);
        limits.setRequiredSkillCharacters(100);
        limits.setSafetyNotesCharacters(100);
        limits.setToolsRequiredCharacters(100);
        limits.setSparePartsRequiredCharacters(100);
        limits.setConsumablesRequiredCharacters(100);
        limits.setComposedCharacters(700);
        limits.setComposedUtf8Bytes(2800);
        return properties;
    }
}
