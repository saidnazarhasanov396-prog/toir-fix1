package com.toir.repository.maintenance;

import com.toir.service.maintenanceembedding.PgvectorQueryLiteral;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional(readOnly = true)
public class JdbcMaintenanceTemplateSemanticSearchRepository
        implements MaintenanceTemplateSemanticSearchRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMaintenanceTemplateSemanticSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ScoredTemplate> findTopTemplates(SearchVector query) {
        if (query == null || query.limit() <= 0 || query.limit() > 10) {
            throw new IllegalArgumentException("Semantic search limit must be between 1 and 10");
        }
        String vector = PgvectorQueryLiteral.validated(query.values(), query.dimension());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("queryVector", vector)
                .addValue("modelName", query.modelName())
                .addValue("modelRevision", query.modelRevision())
                .addValue("dimension", query.dimension())
                .addValue("sourceSchemaVersion", query.sourceSchemaVersion())
                .addValue("limit", query.limit());
        return jdbc.query("""
                WITH eligible_action_template_scores AS (
                    SELECT DISTINCT
                        operation.template_id,
                        job.maintenance_action_id,
                        1.0 - (embedding.embedding <=> CAST(:queryVector AS vector)) AS action_similarity_score
                    FROM maintenance_action_embeddings embedding
                    JOIN maintenance_action_embedding_jobs job ON job.id = embedding.job_id
                    JOIN maintenance_actions action ON action.id = job.maintenance_action_id
                    JOIN maintenance_operations operation ON operation.action_id = action.id
                    JOIN maintenance_templates template ON template.id = operation.template_id
                    WHERE job.status = 'READY'
                      AND job.is_current
                      AND job.model_name = :modelName
                      AND job.model_revision = :modelRevision
                      AND job.dimension = :dimension
                      AND job.source_schema_version = :sourceSchemaVersion
                      AND embedding.model_name = :modelName
                      AND embedding.model_revision = :modelRevision
                      AND embedding.dimension = :dimension
                      AND embedding.source_schema_version = :sourceSchemaVersion
                      AND embedding.source_text_hash = job.source_text_hash
                      AND action.is_deleted = false
                      AND action.is_active = true
                      AND operation.is_deleted = false
                      AND template.is_deleted = false
                      AND template.is_active = true
                ), template_scores AS (
                    SELECT template_id, MAX(action_similarity_score) AS template_score
                    FROM eligible_action_template_scores
                    GROUP BY template_id
                )
                SELECT template_id, template_score
                FROM template_scores
                ORDER BY template_score DESC, template_id ASC
                LIMIT :limit
                """, parameters, (resultSet, rowNumber) -> new ScoredTemplate(
                resultSet.getObject("template_id", java.util.UUID.class),
                resultSet.getDouble("template_score")));
    }
}
