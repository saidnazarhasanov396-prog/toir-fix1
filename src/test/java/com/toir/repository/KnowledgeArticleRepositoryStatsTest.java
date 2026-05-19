package com.toir.repository;

import com.toir.entity.KnowledgeArticle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class KnowledgeArticleRepositoryStatsTest {

    @Autowired
    KnowledgeArticleRepository repository;

    @Test
    void getKnowledgeStatsWithNoFiltersCountsAllKinds() {
        saveArticle("LL-001", "Pump lesson", "LESSON_LEARNED", null, null);
        saveArticle("LL-002", "Motor lesson", "LESSON_LEARNED", null, null);
        saveArticle("PR-001", "Pump procedure", "PROCEDURE", null, null);
        saveArticle("TS-001", "Pump troubleshooting", "TROUBLESHOOTING", null, null);

        KnowledgeStatsProjection stats = repository.getKnowledgeStats(null, null, null);

        assertThat(stats.getTotalArticles()).isEqualTo(4);
        assertThat(stats.getLessonLearned()).isEqualTo(2);
        assertThat(stats.getProcedures()).isEqualTo(1);
        assertThat(stats.getTroubleshooting()).isEqualTo(1);
    }

    @Test
    void getKnowledgeStatsWithKindFilterCountsOnlyThatKind() {
        saveArticle("LL-101", "Pump lesson", "LESSON_LEARNED", null, null);
        saveArticle("PR-101", "Pump procedure", "PROCEDURE", null, null);

        KnowledgeStatsProjection stats = repository.getKnowledgeStats(null, null, "PROCEDURE");

        assertThat(stats.getTotalArticles()).isEqualTo(1);
        assertThat(stats.getProcedures()).isEqualTo(1);
        assertThat(stats.getLessonLearned()).isEqualTo(0);
    }

    @Test
    void getKnowledgeStatsWithEquipmentIdFilterCountsOnlyThatEquipment() {
        UUID equipmentId = UUID.randomUUID();
        saveArticle("LL-201", "Pump lesson", "LESSON_LEARNED", equipmentId, null);
        saveArticle("LL-202", "Other lesson", "LESSON_LEARNED", UUID.randomUUID(), null);

        KnowledgeStatsProjection stats = repository.getKnowledgeStats(equipmentId, null, null);

        assertThat(stats.getTotalArticles()).isEqualTo(1);
        assertThat(stats.getLessonLearned()).isEqualTo(1);
    }

    @Test
    void getKnowledgeStatsWithEquipmentTypeIdFilterCountsOnlyThatType() {
        UUID equipmentTypeId = UUID.randomUUID();
        saveArticle("LL-301", "Pump lesson", "LESSON_LEARNED", null, equipmentTypeId);
        saveArticle("PR-301", "Motor procedure", "PROCEDURE", null, UUID.randomUUID());

        KnowledgeStatsProjection stats = repository.getKnowledgeStats(null, equipmentTypeId, null);

        assertThat(stats.getTotalArticles()).isEqualTo(1);
        assertThat(stats.getLessonLearned()).isEqualTo(1);
    }

    @Test
    void getKnowledgeStatsExcludesDeletedArticles() {
        saveArticle("LL-401", "Active lesson", "LESSON_LEARNED", null, null);
        KnowledgeArticle deleted = saveArticle("LL-402", "Deleted lesson", "LESSON_LEARNED", null, null);
        deleted.setDeleted(true);
        repository.save(deleted);

        KnowledgeStatsProjection stats = repository.getKnowledgeStats(null, null, null);

        assertThat(stats.getTotalArticles()).isEqualTo(1);
    }

    private KnowledgeArticle saveArticle(String code, String title, String kind, UUID equipmentId, UUID equipmentTypeId) {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setCode(code + "-" + UUID.randomUUID());
        article.setTitle(title);
        article.setKind(kind);
        article.setEquipmentId(equipmentId);
        article.setEquipmentTypeId(equipmentTypeId);
        article.setProblem("Problem");
        article.setRootCause("Cause");
        article.setSolution("Solution");
        article.setTags(List.of());
        article.setDeleted(false);
        return repository.save(article);
    }
}
