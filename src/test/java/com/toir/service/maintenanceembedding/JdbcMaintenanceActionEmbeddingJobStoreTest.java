package com.toir.service.maintenanceembedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcMaintenanceActionEmbeddingJobStoreTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    private JdbcMaintenanceActionEmbeddingJobStore store;

    @BeforeEach
    void setUp() {
        store = new JdbcMaintenanceActionEmbeddingJobStore(jdbc);
    }

    @Test
    void enqueueLocksActionStalesChangedHashAndUsesRepresentationConflictKey() {
        UUID actionId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.eq(UUID.class))).thenReturn(actionId);
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1, 1);

        assertThat(store.enqueueCurrent(command(actionId)))
                .isEqualTo(MaintenanceActionEmbeddingJobStore.EnqueueOutcome.ENQUEUED);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.times(2)).update(sql.capture(), any(SqlParameterSource.class));
        assertThat(sql.getAllValues().get(0))
                .contains("source_text_hash <> :sourceTextHash", "is_current = false");
        assertThat(sql.getAllValues().get(1))
                .contains("ON CONFLICT", "maintenance_action_id", "model_revision", "source_text_hash");
    }

    @Test
    void sameRepresentationIsReusedInsteadOfCreatingDuplicateLogicalWork() {
        UUID actionId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.eq(UUID.class))).thenReturn(actionId);
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0, 0, 0);

        assertThat(store.enqueueCurrent(command(actionId)))
                .isEqualTo(MaintenanceActionEmbeddingJobStore.EnqueueOutcome.ALREADY_PRESENT);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.times(3)).update(sql.capture(), any(SqlParameterSource.class));
        assertThat(sql.getAllValues().get(2))
                .contains("SET is_current = true", "ELSE 'PENDING'", "is_current = false");
    }

    @Test
    void claimUsesSkipLockedLeaseAndReturnsImmutableFencingToken() {
        UUID leaseToken = UUID.randomUUID();
        MaintenanceActionEmbeddingJobStore.ClaimedJob claimed = new MaintenanceActionEmbeddingJobStore.ClaimedJob(
                UUID.randomUUID(), UUID.randomUUID(), "text", "a".repeat(64),
                "model", "revision", 768, "maintenance-action-text-v1", 1, 3, leaseToken);
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);
        when(jdbc.query(anyString(), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<MaintenanceActionEmbeddingJobStore.ClaimedJob>>any()))
                .thenReturn(List.of(claimed));

        assertThat(store.claimEligible(1, "worker-1", Instant.now().plusSeconds(30)))
                .containsExactly(claimed);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<MaintenanceActionEmbeddingJobStore.ClaimedJob>>any());
        assertThat(sql.getValue()).contains("FOR UPDATE SKIP LOCKED", "lease_token", "attempt_count + 1");
    }

    @Test
    void retryIsBoundedAndRejectsAStaleLeaseFence() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<String>>any()))
                .thenReturn(List.of("RETRY_WAIT"))
                .thenReturn(List.of());
        UUID jobId = UUID.randomUUID();
        UUID leaseToken = UUID.randomUUID();

        assertThat(store.recordRetry(jobId, leaseToken, "TIMEOUT", Instant.now().plusSeconds(10)))
                .isEqualTo(MaintenanceActionEmbeddingJobStore.RetryOutcome.RETRY_SCHEDULED);
        assertThat(store.recordRetry(jobId, UUID.randomUUID(), "TIMEOUT", Instant.now().plusSeconds(10)))
                .isEqualTo(MaintenanceActionEmbeddingJobStore.RetryOutcome.FENCE_REJECTED);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.times(2)).query(sql.capture(), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<String>>any());
        assertThat(sql.getAllValues().getFirst())
                .contains("attempt_count >= maximum_attempts", "status = 'PROCESSING'", "lease_token = :leaseToken");
    }

    @Test
    void vectorIsValidatedThenPersistedBeforeFencedReadyTransition() {
        UUID jobId = UUID.randomUUID();
        UUID leaseToken = UUID.randomUUID();
        List<Double> vector = java.util.Collections.nCopies(768, 0.25d);
        when(jdbc.query(anyString(), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<UUID>>any())).thenReturn(List.of(jobId));

        assertThat(store.persistVectorAndMarkReady(jobId, leaseToken, vector)).isTrue();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<UUID>>any());
        assertThat(sql.getValue()).contains(
                "INSERT INTO maintenance_action_embeddings", "CAST(:embedding AS vector)",
                "status = 'READY'", "job.is_current", "job.lease_token = :leaseToken");
        assertThat(sql.getValue().indexOf("INSERT INTO maintenance_action_embeddings"))
                .isLessThan(sql.getValue().indexOf("status = 'READY'"));
    }

    @Test
    void invalidVectorNeverReachesPersistence() {
        List<Double> invalid = new java.util.ArrayList<>(java.util.Collections.nCopies(768, 0.5d));
        invalid.set(400, Double.NaN);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> store.persistVectorAndMarkReady(
                UUID.randomUUID(), UUID.randomUUID(), invalid)))
                .isInstanceOf(EmbeddingVectorValidator.InvalidEmbeddingException.class);
        verify(jdbc, org.mockito.Mockito.never()).query(anyString(), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<UUID>>any());
    }

    private static MaintenanceActionEmbeddingJobStore.EnqueueCommand command(UUID actionId) {
        return new MaintenanceActionEmbeddingJobStore.EnqueueCommand(
                actionId,
                "ibm-granite/granite-embedding-311m-multilingual-r2",
                "revision-42",
                768,
                "maintenance-action-text-v1",
                "a".repeat(64),
                "Pump inspection",
                3);
    }
}
