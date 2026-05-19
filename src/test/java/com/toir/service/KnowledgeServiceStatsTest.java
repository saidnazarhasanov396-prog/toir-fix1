package com.toir.service;

import com.toir.dto.knowledge.KnowledgeStatsResponse;
import com.toir.repository.KnowledgeArticleRepository;
import com.toir.repository.KnowledgeStatsProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceStatsTest {

    @Mock
    KnowledgeArticleRepository repository;

    @InjectMocks
    KnowledgeService service;

    @Test
    void getStatsWithNoFiltersReturnsMappedResponse() {
        KnowledgeStatsProjection projection = mockProjection(10L, 5L, 3L, 2L);
        when(repository.getKnowledgeStats(null, null, null)).thenReturn(projection);

        KnowledgeStatsResponse stats = service.getStats(null, null, null);

        assertThat(stats.totalArticles()).isEqualTo(10);
        assertThat(stats.lessonLearned()).isEqualTo(5);
        assertThat(stats.procedures()).isEqualTo(3);
        assertThat(stats.troubleshooting()).isEqualTo(2);
        verify(repository).getKnowledgeStats(null, null, null);
    }

    @Test
    void getStatsWithKindFilterPassesTrimmedKind() {
        KnowledgeStatsProjection projection = mockProjection(3L, 0L, 3L, 0L);
        when(repository.getKnowledgeStats(null, null, "PROCEDURE")).thenReturn(projection);

        KnowledgeStatsResponse stats = service.getStats(null, null, "  PROCEDURE  ");

        assertThat(stats.totalArticles()).isEqualTo(3);
        verify(repository).getKnowledgeStats(null, null, "PROCEDURE");
    }

    @Test
    void getStatsWithBlankKindPassesNullToRepository() {
        KnowledgeStatsProjection projection = mockProjection(5L, 5L, 0L, 0L);
        when(repository.getKnowledgeStats(null, null, null)).thenReturn(projection);

        KnowledgeStatsResponse stats = service.getStats(null, null, "   ");

        verify(repository).getKnowledgeStats(null, null, null);
    }

    @Test
    void getStatsWithEquipmentFilterPassesIdToRepository() {
        UUID equipmentId = UUID.randomUUID();
        KnowledgeStatsProjection projection = mockProjection(2L, 2L, 0L, 0L);
        when(repository.getKnowledgeStats(equipmentId, null, null)).thenReturn(projection);

        service.getStats(equipmentId, null, null);

        verify(repository).getKnowledgeStats(equipmentId, null, null);
    }

    @Test
    void getStatsHandlesNullProjectionValuesGracefully() {
        KnowledgeStatsProjection projection = mockProjection(null, null, null, null);
        when(repository.getKnowledgeStats(null, null, null)).thenReturn(projection);

        KnowledgeStatsResponse stats = service.getStats(null, null, null);

        assertThat(stats.totalArticles()).isZero();
        assertThat(stats.lessonLearned()).isZero();
        assertThat(stats.procedures()).isZero();
        assertThat(stats.troubleshooting()).isZero();
    }

    private KnowledgeStatsProjection mockProjection(Long total, Long ll, Long proc, Long ts) {
        KnowledgeStatsProjection p = mock(KnowledgeStatsProjection.class);
        when(p.getTotalArticles()).thenReturn(total);
        when(p.getLessonLearned()).thenReturn(ll);
        when(p.getProcedures()).thenReturn(proc);
        when(p.getTroubleshooting()).thenReturn(ts);
        return p;
    }
}
