package com.toir.service.maintenanceembedding;

import com.toir.config.MaintenanceActionSemanticSearchProperties;
import com.toir.entity.maintenance.MaintenanceAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DurableMaintenanceActionEmbeddingLifecycleTest {

    @Mock
    private MaintenanceActionEmbeddingJobStore jobStore;

    @Mock
    private MaintenanceActionEmbeddingTextBuilder textBuilder;

    private MaintenanceActionSemanticSearchProperties properties;
    private DurableMaintenanceActionEmbeddingLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        properties = new MaintenanceActionSemanticSearchProperties();
        properties.setEnabled(true);
        properties.setModelRevision("revision-42");
        properties.setRetryCount(2);
        lifecycle = new DurableMaintenanceActionEmbeddingLifecycle(jobStore, textBuilder, properties);
    }

    @Test
    void createEnqueuesImmutableRepresentationWithoutCallingAi() {
        MaintenanceAction action = action();
        when(textBuilder.build(action)).thenReturn(new MaintenanceActionEmbeddingTextBuilder.SourceText(
                "Nasos podshipnigi", "a".repeat(64), false, 18));

        lifecycle.actionCreated(action);

        ArgumentCaptor<MaintenanceActionEmbeddingJobStore.EnqueueCommand> command =
                ArgumentCaptor.forClass(MaintenanceActionEmbeddingJobStore.EnqueueCommand.class);
        verify(jobStore).enqueueCurrent(command.capture());
        assertThat(command.getValue().maintenanceActionId()).isEqualTo(action.getId());
        assertThat(command.getValue().sourceSchemaVersion()).isEqualTo("maintenance-action-text-v1");
        assertThat(command.getValue().sourceTextHash()).isEqualTo("a".repeat(64));
        assertThat(command.getValue().modelRevision()).isEqualTo("revision-42");
        assertThat(command.getValue().maximumAttempts()).isEqualTo(3);
    }

    @Test
    void updateUsesSameIdempotentEnqueueBoundaryForSameOrChangedHash() {
        MaintenanceAction action = action();
        when(textBuilder.build(action)).thenReturn(new MaintenanceActionEmbeddingTextBuilder.SourceText(
                "changed", "b".repeat(64), false, 7));

        lifecycle.actionUpdated(action);

        verify(jobStore).enqueueCurrent(org.mockito.ArgumentMatchers.argThat(command ->
                command.sourceTextHash().equals("b".repeat(64))));
    }

    @Test
    void disabledLifecycleDoesNotTouchDurableStore() {
        properties.setEnabled(false);

        lifecycle.actionCreated(action());

        verify(jobStore, never()).enqueueCurrent(org.mockito.ArgumentMatchers.any());
        verify(textBuilder, never()).build(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteMakesAllCurrentRepresentationsIneligible() {
        MaintenanceAction action = action();

        lifecycle.actionDeleted(action);

        verify(jobStore).markCurrentStale(action.getId());
    }

    private static MaintenanceAction action() {
        MaintenanceAction action = new MaintenanceAction();
        action.setId(UUID.randomUUID());
        action.setName("Nasos podshipnigi");
        return action;
    }
}
