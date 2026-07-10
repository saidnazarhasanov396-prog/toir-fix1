package com.toir.service.sparepartlifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.entity.sparepartlifecycle.SparePartLifecycleCommand;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleCommandStatus;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleCommandType;
import com.toir.repository.sparepartlifecycle.SparePartLifecycleCommandRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SparePartLifecycleCommandCoordinatorTest {

    @Mock
    SparePartLifecycleCommandRepository repository;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    SparePartLifecycleCommandCoordinator coordinator;

    @Test
    void createsInProgressCommandForNewKey() {
        UUID actorId = UUID.randomUUID();
        TestPayload payload = new TestPayload("FRONT_LEFT", 1);
        when(repository.findByIdempotencyKeyForUpdate("install-1")).thenReturn(Optional.empty());
        when(repository.save(any(SparePartLifecycleCommand.class))).thenAnswer(invocation -> {
            SparePartLifecycleCommand command = invocation.getArgument(0);
            command.setId(UUID.randomUUID());
            return command;
        });

        var handle = coordinator.acquire(
                "install-1",
                SparePartLifecycleCommandType.INSTALL,
                payload,
                actorId,
                null
        );

        assertThat(handle.replay()).isFalse();
        assertThat(handle.command().getStatus()).isEqualTo(SparePartLifecycleCommandStatus.IN_PROGRESS);
        assertThat(handle.command().getRequestHash()).hasSize(64);
    }

    @Test
    void successfulSamePayloadReturnsReplayAndDifferentPayloadConflicts() {
        UUID actorId = UUID.randomUUID();
        TestPayload payload = new TestPayload("FRONT_LEFT", 1);
        SparePartLifecycleCommand existing = command(
                "install-1",
                coordinator.requestHash(payload),
                SparePartLifecycleCommandStatus.SUCCEEDED
        );
        when(repository.findByIdempotencyKeyForUpdate("install-1")).thenReturn(Optional.of(existing));

        var replay = coordinator.acquire(
                "install-1",
                SparePartLifecycleCommandType.INSTALL,
                payload,
                actorId,
                null
        );

        assertThat(replay.replay()).isTrue();
        assertThat(replay.command()).isSameAs(existing);
        assertThatThrownBy(() -> coordinator.acquire(
                "install-1",
                SparePartLifecycleCommandType.INSTALL,
                new TestPayload("FRONT_RIGHT", 1),
                actorId,
                null
        )).hasMessageStartingWith("IDEMPOTENCY_KEY_REUSED:");
    }

    @Test
    void inProgressDuplicateReturnsRetryableConflict() {
        TestPayload payload = new TestPayload("FRONT_LEFT", 1);
        SparePartLifecycleCommand existing = command(
                "install-1",
                coordinator.requestHash(payload),
                SparePartLifecycleCommandStatus.IN_PROGRESS
        );
        when(repository.findByIdempotencyKeyForUpdate("install-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> coordinator.acquire(
                "install-1",
                SparePartLifecycleCommandType.INSTALL,
                payload,
                UUID.randomUUID(),
                null
        )).hasMessageStartingWith("IDEMPOTENCY_IN_PROGRESS:");
    }

    @Test
    void requiresNonBlankKeyAndCommandTypeMustMatchReplay() {
        assertThatThrownBy(() -> coordinator.acquire(
                " ",
                SparePartLifecycleCommandType.INSTALL,
                new TestPayload("A", 1),
                UUID.randomUUID(),
                null
        )).hasMessageStartingWith("IDEMPOTENCY_KEY_REQUIRED:");

        TestPayload payload = new TestPayload("A", 1);
        SparePartLifecycleCommand existing = command(
                "same-key",
                coordinator.requestHash(payload),
                SparePartLifecycleCommandStatus.SUCCEEDED
        );
        when(repository.findByIdempotencyKeyForUpdate("same-key")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> coordinator.acquire(
                "same-key",
                SparePartLifecycleCommandType.REMOVE,
                payload,
                UUID.randomUUID(),
                null
        )).hasMessageStartingWith("IDEMPOTENCY_KEY_REUSED:");
    }

    private static SparePartLifecycleCommand command(String key,
                                                     String hash,
                                                     SparePartLifecycleCommandStatus status) {
        SparePartLifecycleCommand command = new SparePartLifecycleCommand();
        command.setId(UUID.randomUUID());
        command.setIdempotencyKey(key);
        command.setCommandType(SparePartLifecycleCommandType.INSTALL);
        command.setRequestHash(hash);
        command.setStatus(status);
        command.setCreatedBy(UUID.randomUUID());
        return command;
    }

    private record TestPayload(String slot, int quantity) {
    }
}
