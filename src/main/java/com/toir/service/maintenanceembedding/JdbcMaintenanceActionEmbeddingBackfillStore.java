package com.toir.service.maintenanceembedding;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

@Repository
@Transactional
public class JdbcMaintenanceActionEmbeddingBackfillStore implements MaintenanceActionEmbeddingBackfillStore {

    private static final String COLUMNS = """
            id, status, cursor, scanned, already_present, enqueued, skipped,
            batch_size, created_at, updated_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMaintenanceActionEmbeddingBackfillStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public BackfillRun start(StartCommand command) {
        validate(command);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("idempotencyKey", command.idempotencyKey())
                .addValue("modelName", command.modelName())
                .addValue("modelRevision", command.modelRevision())
                .addValue("dimension", command.dimension())
                .addValue("sourceSchemaVersion", command.sourceSchemaVersion())
                .addValue("batchSize", command.batchSize())
                .addValue("requestedBy", command.requestedBy())
                .addValue("now", now);
        int inserted = jdbc.update("""
                INSERT INTO maintenance_action_embedding_backfill_runs (
                    id, idempotency_key, model_name, model_revision, dimension,
                    source_schema_version, batch_size, requested_by, status,
                    scanned, already_present, enqueued, skipped, created_at, updated_at
                ) VALUES (
                    :id, :idempotencyKey, :modelName, :modelRevision, :dimension,
                    :sourceSchemaVersion, :batchSize, :requestedBy, 'REQUESTED',
                    0, 0, 0, 0, :now, :now
                )
                ON CONFLICT (idempotency_key) DO NOTHING
                """, parameters);
        if (inserted == 1) {
            return find(id);
        }
        return jdbc.queryForObject("""
                SELECT %s FROM maintenance_action_embedding_backfill_runs
                WHERE idempotency_key = :idempotencyKey
                  AND model_name = :modelName
                  AND model_revision = :modelRevision
                  AND dimension = :dimension
                  AND source_schema_version = :sourceSchemaVersion
                  AND batch_size = :batchSize
                """.formatted(COLUMNS), parameters, this::mapRun);
    }

    @Override
    public BackfillRun find(UUID runId) {
        return jdbc.queryForObject("""
                SELECT %s FROM maintenance_action_embedding_backfill_runs WHERE id = :id
                """.formatted(COLUMNS), id(runId), this::mapRun);
    }

    @Override
    public BackfillRun transition(UUID runId, MaintenanceActionBackfillStatus requestedStatus) {
        BackfillRun current = lockForBatch(runId);
        if (!allowed(current.status(), requestedStatus)) {
            throw new IllegalStateException("Invalid backfill transition: " + current.status() + " -> " + requestedStatus);
        }
        jdbc.update("""
                UPDATE maintenance_action_embedding_backfill_runs
                SET status = :status, updated_at = :now
                WHERE id = :id
                """, id(runId)
                .addValue("status", requestedStatus.name())
                .addValue("now", Instant.now()));
        return find(runId);
    }

    @Override
    public BackfillRun lockForBatch(UUID runId) {
        return jdbc.queryForObject("""
                SELECT %s FROM maintenance_action_embedding_backfill_runs
                WHERE id = :id FOR UPDATE
                """.formatted(COLUMNS), id(runId), this::mapRun);
    }

    @Override
    public BackfillRun recordBatch(UUID runId, BatchProgress progress) {
        if (progress == null || progress.scanned() < 0 || progress.alreadyPresent() < 0
                || progress.enqueued() < 0 || progress.skipped() < 0
                || progress.alreadyPresent() + progress.enqueued() + progress.skipped() != progress.scanned()) {
            throw new IllegalArgumentException("Backfill batch counters are invalid");
        }
        int updated = jdbc.update("""
                UPDATE maintenance_action_embedding_backfill_runs
                SET cursor = COALESCE(:cursor, cursor),
                    scanned = scanned + :scanned,
                    already_present = already_present + :alreadyPresent,
                    enqueued = enqueued + :enqueued,
                    skipped = skipped + :skipped,
                    status = CASE WHEN :scanCompleted THEN 'SCAN_COMPLETED' ELSE 'RUNNING' END,
                    updated_at = :now
                WHERE id = :id AND status = 'RUNNING'
                """, id(runId)
                .addValue("cursor", progress.cursor())
                .addValue("scanned", progress.scanned())
                .addValue("alreadyPresent", progress.alreadyPresent())
                .addValue("enqueued", progress.enqueued())
                .addValue("skipped", progress.skipped())
                .addValue("scanCompleted", progress.scanCompleted())
                .addValue("now", Instant.now()));
        if (updated != 1) {
            throw new IllegalStateException("Backfill run is not RUNNING");
        }
        return find(runId);
    }

    private BackfillRun mapRun(ResultSet resultSet, int rowNumber) throws SQLException {
        return new BackfillRun(
                resultSet.getObject("id", UUID.class),
                MaintenanceActionBackfillStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("cursor", UUID.class),
                resultSet.getLong("scanned"),
                resultSet.getLong("already_present"),
                resultSet.getLong("enqueued"),
                resultSet.getLong("skipped"),
                resultSet.getInt("batch_size"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private static boolean allowed(
            MaintenanceActionBackfillStatus current,
            MaintenanceActionBackfillStatus requested
    ) {
        if (current == requested) {
            return true;
        }
        return switch (current) {
            case REQUESTED -> requested == MaintenanceActionBackfillStatus.RUNNING
                    || requested == MaintenanceActionBackfillStatus.PAUSED
                    || requested == MaintenanceActionBackfillStatus.CANCELLED;
            case RUNNING -> requested == MaintenanceActionBackfillStatus.PAUSED
                    || requested == MaintenanceActionBackfillStatus.SCAN_COMPLETED
                    || requested == MaintenanceActionBackfillStatus.FAILED
                    || requested == MaintenanceActionBackfillStatus.CANCELLED;
            case PAUSED -> requested == MaintenanceActionBackfillStatus.RUNNING
                    || requested == MaintenanceActionBackfillStatus.CANCELLED;
            case SCAN_COMPLETED -> requested == MaintenanceActionBackfillStatus.COMPLETED;
            case COMPLETED, FAILED, CANCELLED -> false;
        };
    }

    private static MapSqlParameterSource id(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Backfill run ID is required");
        }
        return new MapSqlParameterSource("id", id);
    }

    private static void validate(StartCommand command) {
        if (command == null || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > 255
                || command.modelName() == null || command.modelName().isBlank() || command.modelName().length() > 255
                || command.modelRevision() == null || command.modelRevision().isBlank()
                || command.modelRevision().length() > 255
                || command.dimension() <= 0 || command.sourceSchemaVersion() == null
                || command.sourceSchemaVersion().isBlank() || command.batchSize() <= 0) {
            throw new IllegalArgumentException("Complete backfill identity and a positive batch size are required");
        }
    }
}
