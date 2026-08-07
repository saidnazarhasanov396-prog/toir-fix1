package com.toir.service.maintenanceembedding;

import com.toir.repository.maintenance.JdbcMaintenanceTemplateSemanticSearchRepository;
import com.toir.repository.maintenance.MaintenanceTemplateSemanticSearchRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class MaintenanceActionSemanticSearchPgvectorIntegrationTest {

    private static final String MODEL = "ibm-granite/granite-embedding-311m-multilingual-r2";
    private static final String REVISION = "integration-revision-42";
    private static final String SCHEMA_VERSION = "maintenance-action-text-v1";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:0.8.1-pg17");

    private static DataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcMaintenanceActionEmbeddingJobStore jobStore;
    private JdbcMaintenanceTemplateSemanticSearchRepository searchRepository;

    @BeforeAll
    static void migrateProductionChain() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION vector");
            try (ResultSet result = statement.executeQuery(
                    "SELECT extversion FROM pg_extension WHERE extname = 'vector'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).startsWith("0.8.");
            }
        }
        var migration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .load()
                .migrate();
        assertThat(migration.success).isTrue();
        assertThat(migration.migrationsExecuted).isGreaterThan(0);

        DriverManagerDataSource configured = new DriverManagerDataSource();
        configured.setDriverClassName("org.postgresql.Driver");
        configured.setUrl(POSTGRES.getJdbcUrl());
        configured.setUsername(POSTGRES.getUsername());
        configured.setPassword(POSTGRES.getPassword());
        dataSource = configured;
    }

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource);
        jobStore = new JdbcMaintenanceActionEmbeddingJobStore(named);
        searchRepository = new JdbcMaintenanceTemplateSemanticSearchRepository(named);
        jdbc.update("DELETE FROM maintenance_action_embeddings");
        jdbc.update("DELETE FROM maintenance_action_embedding_jobs");
    }

    @Test
    void productionMigrationsCreateVector768AndFencedCompletionIsAtomic() {
        String type = jdbc.queryForObject("""
                SELECT format_type(attribute.atttypid, attribute.atttypmod)
                FROM pg_attribute attribute
                WHERE attribute.attrelid = 'maintenance_action_embeddings'::regclass
                  AND attribute.attname = 'embedding'
                """, String.class);
        assertThat(type).isEqualTo("vector(768)");

        UUID actionId = insertAction("atomic-ready");
        UUID jobId = UUID.randomUUID();
        UUID leaseToken = UUID.randomUUID();
        insertProcessingJob(jobId, actionId, leaseToken, "a".repeat(64));

        assertThat(jobStore.persistVectorAndMarkReady(jobId, leaseToken, vector(1.0))).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM maintenance_action_embedding_jobs WHERE id = ?", String.class, jobId))
                .isEqualTo("READY");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM maintenance_action_embeddings WHERE job_id = ?", Integer.class, jobId))
                .isEqualTo(1);

        UUID staleJob = UUID.randomUUID();
        UUID currentLease = UUID.randomUUID();
        insertProcessingJob(staleJob, insertAction("stale-fence"), currentLease, "b".repeat(64));
        assertThat(jobStore.persistVectorAndMarkReady(staleJob, UUID.randomUUID(), vector(0.9))).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM maintenance_action_embedding_jobs WHERE id = ?", String.class, staleJob))
                .isEqualTo("PROCESSING");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM maintenance_action_embeddings WHERE job_id = ?", Integer.class, staleJob))
                .isZero();
    }

    @Test
    void exactCosineUsesMaxPerTemplateBeforeTopTenAndExcludesIncompatibleRows() {
        List<UUID> templateIds = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            UUID templateId = insertTemplate("template-" + index);
            templateIds.add(templateId);
            double score = index == 0 ? 0.5 : 0.94 - (index * 0.05);
            linkReadyAction(templateId, "eligible-" + index, score, REVISION, true);
        }
        linkReadyAction(templateIds.getFirst(), "max-winner", 0.99, REVISION, true);
        linkReadyAction(templateIds.get(1), "wrong-revision", 1.0, "old-revision", true);
        linkReadyAction(templateIds.get(2), "stale", 1.0, REVISION, false);
        insertReadyActionWithoutTemplate("unlinked", 1.0);

        List<MaintenanceTemplateSemanticSearchRepository.ScoredTemplate> results =
                searchRepository.findTopTemplates(new MaintenanceTemplateSemanticSearchRepository.SearchVector(
                        vector(1.0), MODEL, REVISION, 768, SCHEMA_VERSION, 10));

        assertThat(results).hasSize(10);
        assertThat(results).extracting(
                MaintenanceTemplateSemanticSearchRepository.ScoredTemplate::maintenanceTemplateId)
                .doesNotHaveDuplicates();
        assertThat(results.getFirst().maintenanceTemplateId()).isEqualTo(templateIds.getFirst());
        assertThat(results.getFirst().similarityScore()).isCloseTo(0.99,
                org.assertj.core.data.Offset.offset(0.000001));
        assertThat(results).isSortedAccordingTo(Comparator
                .comparingDouble(MaintenanceTemplateSemanticSearchRepository.ScoredTemplate::similarityScore)
                .reversed()
                .thenComparing(MaintenanceTemplateSemanticSearchRepository.ScoredTemplate::maintenanceTemplateId));
        assertThat(results).extracting(
                MaintenanceTemplateSemanticSearchRepository.ScoredTemplate::maintenanceTemplateId)
                .doesNotContain(templateIds.get(10), templateIds.get(11));

        assertThat(searchRepository.findTopTemplates(
                new MaintenanceTemplateSemanticSearchRepository.SearchVector(
                        vector(1.0), MODEL, "missing-revision", 768, SCHEMA_VERSION, 10)))
                .isEmpty();
    }

    private UUID insertAction(String suffix) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO maintenance_actions (
                    id, created_at, updated_at, is_deleted, code, name, is_active
                ) VALUES (?, now(), now(), false, ?, ?, true)
                """, id, "MA-IT-" + suffix + '-' + id, "Action " + suffix);
        return id;
    }

    private UUID insertTemplate(String suffix) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO maintenance_templates (
                    id, created_at, updated_at, is_deleted, is_active, code, name,
                    equipment_type_id, maintenance_kind, normative_labor_hours
                ) VALUES (?, now(), now(), false, true, ?, ?, ?, 'PREVENTIVE', 1.0)
                """, id, "MT-IT-" + suffix + '-' + id, "Template " + suffix, UUID.randomUUID());
        return id;
    }

    private void linkReadyAction(
            UUID templateId,
            String suffix,
            double score,
            String revision,
            boolean current
    ) {
        UUID actionId = insertAction(suffix);
        UUID jobId = insertReadyJob(actionId, suffix, score, revision, current);
        jdbc.update("""
                INSERT INTO maintenance_operations (
                    id, created_at, updated_at, is_deleted, template_id, action_id,
                    sequence, name, duration_hours
                ) VALUES (?, now(), now(), false, ?, ?, ?, ?, 1.0)
                """, UUID.randomUUID(), templateId, actionId,
                Math.abs(jobId.hashCode() % 1000000), "Operation " + suffix);
    }

    private void insertReadyActionWithoutTemplate(String suffix, double score) {
        insertReadyJob(insertAction(suffix), suffix, score, REVISION, true);
    }

    private UUID insertReadyJob(
            UUID actionId,
            String suffix,
            double score,
            String revision,
            boolean current
    ) {
        UUID jobId = UUID.randomUUID();
        String hash = String.format("%064x", Math.abs(jobId.getMostSignificantBits()));
        jdbc.update("""
                INSERT INTO maintenance_action_embedding_jobs (
                    id, maintenance_action_id, source_schema_version, source_text_hash,
                    normalized_source_text, model_name, model_revision, dimension,
                    status, is_current, attempt_count, maximum_attempts, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 768, 'READY', ?, 1, 3, now(), now())
                """, jobId, actionId, SCHEMA_VERSION, hash, "text " + suffix, MODEL, revision, current);
        jdbc.update("""
                INSERT INTO maintenance_action_embeddings (
                    job_id, maintenance_action_id, source_schema_version, source_text_hash,
                    model_name, model_revision, dimension, embedding, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 768, CAST(? AS vector), now(), now())
                """, jobId, actionId, SCHEMA_VERSION, hash, MODEL, revision,
                PgvectorLiteral.validated(vector(score), 768));
        return jobId;
    }

    private void insertProcessingJob(UUID jobId, UUID actionId, UUID leaseToken, String hash) {
        jdbc.update("""
                INSERT INTO maintenance_action_embedding_jobs (
                    id, maintenance_action_id, source_schema_version, source_text_hash,
                    normalized_source_text, model_name, model_revision, dimension,
                    status, is_current, attempt_count, maximum_attempts,
                    lease_owner, lease_token, lease_until, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'immutable text', ?, ?, 768,
                    'PROCESSING', true, 1, 3, 'integration-worker', ?, ?, now(), now())
                """, jobId, actionId, SCHEMA_VERSION, hash, MODEL, REVISION,
                leaseToken, OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(60));
    }

    private static List<Double> vector(double cosineScore) {
        List<Double> values = new ArrayList<>(java.util.Collections.nCopies(768, 0.0));
        values.set(0, cosineScore);
        values.set(1, Math.sqrt(Math.max(0.0, 1.0 - cosineScore * cosineScore)));
        return values;
    }
}
