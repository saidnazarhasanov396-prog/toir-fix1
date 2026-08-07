package com.toir.service.maintenanceembedding;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
@Transactional
public class JdbcMaintenanceActionEmbeddingJobStore implements MaintenanceActionEmbeddingJobStore {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMaintenanceActionEmbeddingJobStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public EnqueueOutcome enqueueCurrent(EnqueueCommand command) {
        validate(command);
        MapSqlParameterSource parameters = identityParameters(command)
                .addValue("id", UUID.randomUUID())
                .addValue("text", command.normalizedSourceText())
                .addValue("status", command.normalizedSourceText().isBlank() ? "SKIPPED" : "PENDING")
                .addValue("maximumAttempts", command.maximumAttempts())
                .addValue("now", databaseTimestamp(Instant.now()));

        jdbc.queryForObject("""
                SELECT id
                FROM maintenance_actions
                WHERE id = :actionId
                FOR UPDATE
                """, parameters, UUID.class);

        jdbc.update("""
                UPDATE maintenance_action_embedding_jobs
                SET status = 'STALE',
                    is_current = false,
                    next_attempt_at = NULL,
                    lease_owner = NULL,
                    lease_token = NULL,
                    lease_until = NULL,
                    updated_at = :now
                WHERE maintenance_action_id = :actionId
                  AND source_schema_version = :sourceSchemaVersion
                  AND model_name = :modelName
                  AND model_revision = :modelRevision
                  AND dimension = :dimension
                  AND is_current
                  AND source_text_hash <> :sourceTextHash
                """, parameters);

        int inserted = jdbc.update("""
                INSERT INTO maintenance_action_embedding_jobs (
                    id, maintenance_action_id, source_schema_version, source_text_hash,
                    normalized_source_text, model_name, model_revision, dimension,
                    status, is_current, attempt_count, maximum_attempts, created_at, updated_at
                ) VALUES (
                    :id, :actionId, :sourceSchemaVersion, :sourceTextHash,
                    :text, :modelName, :modelRevision, :dimension,
                    :status, true, 0, :maximumAttempts, :now, :now
                )
                ON CONFLICT (
                    maintenance_action_id, source_schema_version, source_text_hash,
                    model_name, model_revision, dimension
                ) DO NOTHING
                """, parameters);
        if (inserted == 1) {
            return command.normalizedSourceText().isBlank()
                    ? EnqueueOutcome.SKIPPED_BLANK
                    : EnqueueOutcome.ENQUEUED;
        }

        int reactivated = jdbc.update("""
                UPDATE maintenance_action_embedding_jobs
                SET is_current = true,
                    status = CASE
                        WHEN :status = 'SKIPPED' THEN 'SKIPPED'
                        ELSE 'PENDING'
                    END,
                    normalized_source_text = :text,
                    attempt_count = 0,
                    maximum_attempts = :maximumAttempts,
                    next_attempt_at = NULL,
                    lease_owner = NULL,
                    lease_token = NULL,
                    lease_until = NULL,
                    safe_error_code = NULL,
                    updated_at = :now
                WHERE maintenance_action_id = :actionId
                  AND source_schema_version = :sourceSchemaVersion
                  AND source_text_hash = :sourceTextHash
                  AND model_name = :modelName
                  AND model_revision = :modelRevision
                  AND dimension = :dimension
                  AND is_current = false
                """, parameters);
        if (reactivated == 1 && !command.normalizedSourceText().isBlank()) {
            return EnqueueOutcome.ENQUEUED;
        }
        return command.normalizedSourceText().isBlank()
                ? EnqueueOutcome.SKIPPED_BLANK
                : EnqueueOutcome.ALREADY_PRESENT;
    }

    @Override
    public List<ClaimedJob> claimEligible(int maximumJobs, String leaseOwner, Instant leaseUntil) {
        if (maximumJobs <= 0 || leaseOwner == null || leaseOwner.isBlank() || leaseOwner.length() > 255
                || leaseUntil == null || !leaseUntil.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Claim limit, lease owner and lease expiry are required");
        }
        Instant now = Instant.now();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("limit", maximumJobs)
                .addValue("leaseOwner", leaseOwner)
                .addValue("leaseToken", UUID.randomUUID())
                .addValue("leaseUntil", databaseTimestamp(leaseUntil))
                .addValue("now", databaseTimestamp(now));

        jdbc.update("""
                UPDATE maintenance_action_embedding_jobs
                SET status = 'FAILED', safe_error_code = 'EMBEDDING_ATTEMPTS_EXHAUSTED',
                    lease_owner = NULL, lease_token = NULL, lease_until = NULL, updated_at = :now
                WHERE status = 'PROCESSING' AND lease_until <= :now
                  AND attempt_count >= maximum_attempts
                """, parameters);

        return jdbc.query("""
                WITH eligible AS (
                    SELECT id
                    FROM maintenance_action_embedding_jobs
                    WHERE is_current
                      AND attempt_count < maximum_attempts
                      AND (
                          status = 'PENDING'
                          OR (status = 'RETRY_WAIT' AND next_attempt_at <= :now)
                          OR (status = 'PROCESSING' AND lease_until <= :now)
                      )
                    ORDER BY COALESCE(next_attempt_at, created_at), created_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT :limit
                )
                UPDATE maintenance_action_embedding_jobs job
                SET status = 'PROCESSING',
                    attempt_count = job.attempt_count + 1,
                    next_attempt_at = NULL,
                    lease_owner = :leaseOwner,
                    lease_token = :leaseToken,
                    lease_until = :leaseUntil,
                    safe_error_code = NULL,
                    updated_at = :now
                FROM eligible
                WHERE job.id = eligible.id
                RETURNING job.id, job.maintenance_action_id, job.normalized_source_text,
                    job.source_text_hash, job.model_name, job.model_revision, job.dimension,
                    job.source_schema_version, job.attempt_count, job.maximum_attempts, job.lease_token
                """, parameters, this::claimedJob);
    }

    @Override
    public RetryOutcome recordRetry(UUID jobId, UUID leaseToken, String safeErrorCode, Instant nextAttemptAt) {
        requireFence(jobId, leaseToken);
        if (nextAttemptAt == null) {
            throw new IllegalArgumentException("Next attempt time is required");
        }
        List<String> statuses = jdbc.query("""
                UPDATE maintenance_action_embedding_jobs
                SET status = CASE WHEN attempt_count >= maximum_attempts THEN 'FAILED' ELSE 'RETRY_WAIT' END,
                    next_attempt_at = CASE WHEN attempt_count >= maximum_attempts THEN NULL ELSE :nextAttemptAt END,
                    safe_error_code = :safeErrorCode,
                    lease_owner = NULL, lease_token = NULL, lease_until = NULL,
                    updated_at = :now
                WHERE id = :id AND status = 'PROCESSING' AND lease_token = :leaseToken
                RETURNING status
                """, fenceParameters(jobId, leaseToken, safeErrorCode)
                .addValue("nextAttemptAt", databaseTimestamp(nextAttemptAt)),
                (resultSet, rowNumber) -> resultSet.getString("status"));
        if (statuses.isEmpty()) {
            return RetryOutcome.FENCE_REJECTED;
        }
        return "FAILED".equals(statuses.getFirst())
                ? RetryOutcome.TERMINAL_FAILURE
                : RetryOutcome.RETRY_SCHEDULED;
    }

    @Override
    public boolean markTerminalFailure(UUID jobId, UUID leaseToken, String safeErrorCode) {
        requireFence(jobId, leaseToken);
        return jdbc.update("""
                UPDATE maintenance_action_embedding_jobs
                SET status = 'FAILED', safe_error_code = :safeErrorCode,
                    next_attempt_at = NULL, lease_owner = NULL, lease_token = NULL, lease_until = NULL,
                    updated_at = :now
                WHERE id = :id AND status = 'PROCESSING' AND lease_token = :leaseToken
                """, fenceParameters(jobId, leaseToken, safeErrorCode)) == 1;
    }

    @Override
    public boolean persistVectorAndMarkReady(UUID jobId, UUID leaseToken, List<? extends Number> vector) {
        requireFence(jobId, leaseToken);
        String vectorLiteral = PgvectorLiteral.validated(vector, 768);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", jobId)
                .addValue("leaseToken", leaseToken)
                .addValue("embedding", vectorLiteral)
                .addValue("now", databaseTimestamp(Instant.now()));
        List<UUID> completed = jdbc.query("""
                WITH eligible AS (
                    SELECT id, maintenance_action_id, source_schema_version, source_text_hash,
                           model_name, model_revision, dimension
                    FROM maintenance_action_embedding_jobs
                    WHERE id = :id
                      AND status = 'PROCESSING'
                      AND is_current
                      AND lease_token = :leaseToken
                      AND lease_until > :now
                    FOR UPDATE
                ), persisted AS (
                    INSERT INTO maintenance_action_embeddings (
                        job_id, maintenance_action_id, source_schema_version, source_text_hash,
                        model_name, model_revision, dimension, embedding, created_at, updated_at
                    )
                    SELECT id, maintenance_action_id, source_schema_version, source_text_hash,
                           model_name, model_revision, dimension, CAST(:embedding AS vector), :now, :now
                    FROM eligible
                    ON CONFLICT (job_id) DO UPDATE
                    SET embedding = EXCLUDED.embedding, updated_at = EXCLUDED.updated_at
                    RETURNING job_id
                )
                UPDATE maintenance_action_embedding_jobs job
                SET status = 'READY', next_attempt_at = NULL, safe_error_code = NULL,
                    lease_owner = NULL, lease_token = NULL, lease_until = NULL, updated_at = :now
                FROM persisted
                WHERE job.id = persisted.job_id
                  AND job.status = 'PROCESSING'
                  AND job.is_current
                  AND job.lease_token = :leaseToken
                RETURNING job.id
                """, parameters, (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class));
        return completed.size() == 1;
    }

    @Override
    public void markCurrentStale(UUID maintenanceActionId) {
        jdbc.update("""
                UPDATE maintenance_action_embedding_jobs
                SET status = 'STALE',
                    is_current = false, next_attempt_at = NULL,
                    lease_owner = NULL, lease_token = NULL, lease_until = NULL, updated_at = :now
                WHERE maintenance_action_id = :actionId AND is_current
                """, new MapSqlParameterSource()
                .addValue("actionId", maintenanceActionId)
                .addValue("now", databaseTimestamp(Instant.now())));
    }

    private MapSqlParameterSource identityParameters(EnqueueCommand command) {
        return new MapSqlParameterSource()
                .addValue("actionId", command.maintenanceActionId())
                .addValue("sourceSchemaVersion", command.sourceSchemaVersion())
                .addValue("sourceTextHash", command.sourceTextHash())
                .addValue("modelName", command.modelName())
                .addValue("modelRevision", command.modelRevision())
                .addValue("dimension", command.dimension());
    }

    private MapSqlParameterSource fenceParameters(UUID jobId, UUID leaseToken, String safeErrorCode) {
        if (safeErrorCode == null || !safeErrorCode.matches("[A-Z0-9_]{1,128}")) {
            throw new IllegalArgumentException("A stable safe error code is required");
        }
        return new MapSqlParameterSource()
                .addValue("id", jobId)
                .addValue("leaseToken", leaseToken)
                .addValue("safeErrorCode", safeErrorCode)
                .addValue("now", databaseTimestamp(Instant.now()));
    }

    private static OffsetDateTime databaseTimestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private ClaimedJob claimedJob(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ClaimedJob(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("maintenance_action_id", UUID.class),
                resultSet.getString("normalized_source_text"),
                resultSet.getString("source_text_hash"),
                resultSet.getString("model_name"),
                resultSet.getString("model_revision"),
                resultSet.getInt("dimension"),
                resultSet.getString("source_schema_version"),
                resultSet.getInt("attempt_count"),
                resultSet.getInt("maximum_attempts"),
                resultSet.getObject("lease_token", UUID.class));
    }

    private static void validate(EnqueueCommand command) {
        if (command == null || command.maintenanceActionId() == null
                || command.modelName() == null || command.modelName().isBlank() || command.modelName().length() > 255
                || command.modelRevision() == null || command.modelRevision().isBlank()
                || command.modelRevision().length() > 255
                || command.dimension() <= 0
                || command.sourceSchemaVersion() == null || command.sourceSchemaVersion().isBlank()
                || command.sourceTextHash() == null || !command.sourceTextHash().matches("[0-9a-f]{64}")
                || command.normalizedSourceText() == null || command.maximumAttempts() <= 0) {
            throw new IllegalArgumentException("Complete immutable embedding job identity is required");
        }
    }

    private static void requireFence(UUID jobId, UUID leaseToken) {
        if (jobId == null || leaseToken == null) {
            throw new IllegalArgumentException("Job ID and lease token are required");
        }
    }
}
