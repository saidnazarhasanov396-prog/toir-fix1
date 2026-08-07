package com.toir.repository.maintenance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcMaintenanceTemplateSemanticSearchRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void exactCosineGroupsByTemplateBeforeDeterministicTopTenLimit() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<MaintenanceTemplateSemanticSearchRepository.ScoredTemplate>>any()))
                .thenReturn(List.of());
        var repository = new JdbcMaintenanceTemplateSemanticSearchRepository(jdbc);

        repository.findTopTemplates(new MaintenanceTemplateSemanticSearchRepository.SearchVector(
                Collections.nCopies(768, 0.25d), "model", "revision-42", 768,
                "maintenance-action-text-v1", 10));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<MaintenanceTemplateSemanticSearchRepository.ScoredTemplate>>any());
        String query = sql.getValue();
        assertThat(query).contains(
                "embedding.embedding <=> CAST(:queryVector AS vector)",
                "MAX(action_similarity_score)",
                "GROUP BY template_id",
                "ORDER BY template_score DESC, template_id ASC",
                "job.status = 'READY'",
                "job.is_current",
                "job.model_revision = :modelRevision",
                "operation.action_id = action.id",
                "operation.template_id");
        assertThat(query.indexOf("GROUP BY template_id")).isLessThan(query.indexOf("LIMIT :limit"));
    }

    @Test
    void vectorDimensionAndFiniteValuesAreValidatedBeforeSql() {
        var repository = new JdbcMaintenanceTemplateSemanticSearchRepository(jdbc);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repository.findTopTemplates(
                        new MaintenanceTemplateSemanticSearchRepository.SearchVector(
                                List.of(1.0, 2.0), "model", "revision", 768,
                                "maintenance-action-text-v1", 10)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(jdbc, org.mockito.Mockito.never()).query(anyString(), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<MaintenanceTemplateSemanticSearchRepository.ScoredTemplate>>any());
    }
}
