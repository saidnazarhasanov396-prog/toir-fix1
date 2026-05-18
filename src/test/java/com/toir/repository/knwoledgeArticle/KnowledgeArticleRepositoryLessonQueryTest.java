package com.toir.repository.knwoledgeArticle;


import com.toir.entity.KnowledgeArticle;
import com.toir.repository.KnowledgeArticleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;


import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class KnowledgeArticleRepositoryLessonQueryTest {

    @Autowired
    KnowledgeArticleRepository repository;

    @Test
    void findDefectIdsWithLessonReturnsOnlyMatchingLessonLearnedDefectIds() {
        UUID defectWithLesson = UUID.randomUUID();
        UUID defectWithDifferentKind = UUID.randomUUID();
        UUID defectWithoutArticle = UUID.randomUUID();
        UUID softDeletedLessonDefect = UUID.randomUUID();

        KnowledgeArticle lesson = article(
                "LL-001",
                "Lesson for defect",
                "LESSON_LEARNED",
                defectWithLesson
        );
        repository.save(lesson);

        KnowledgeArticle differentKind = article(
                "KB-001",
                "KB for defect",
                "KB",
                defectWithDifferentKind
        );
        repository.save(differentKind);

        KnowledgeArticle softDeletedLesson = article(
                "LL-002",
                "Deleted lesson",
                "LESSON_LEARNED",
                softDeletedLessonDefect
        );
        softDeletedLesson.setDeleted(true);
        repository.save(softDeletedLesson);

        List<UUID> result = repository.findDefectIdsWithLesson(
                List.of(
                        defectWithLesson,
                        defectWithDifferentKind,
                        defectWithoutArticle,
                        softDeletedLessonDefect
                ),
                "LESSON_LEARNED"
        );

        assertThat(result)
                .containsExactly(defectWithLesson)
                .doesNotContain(defectWithDifferentKind)
                .doesNotContain(defectWithoutArticle)
                .doesNotContain(softDeletedLessonDefect);
    }

    @Test
    void findDefectIdsWithLessonReturnsDistinctDefectIdsWhenMultipleLessonsExist() {
        UUID defectId = UUID.randomUUID();

        repository.save(article("LL-101", "Lesson 1", "LESSON_LEARNED", defectId));
        repository.save(article("LL-102", "Lesson 2", "LESSON_LEARNED", defectId));

        List<UUID> result = repository.findDefectIdsWithLesson(
                List.of(defectId),
                "LESSON_LEARNED"
        );

        assertThat(result).containsExactly(defectId);
    }

    private KnowledgeArticle article(
            String code,
            String title,
            String kind,
            UUID defectId
    ) {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setCode(code);
        article.setTitle(title);
        article.setKind(kind);
        article.setDefectId(defectId);
        article.setProblem("Problem");
        article.setRootCause("Root cause");
        article.setSolution("Solution");
        article.setPreventiveActions("Preventive actions");
        article.setTags(List.of());
        article.setDeleted(false);
        return article;
    }
}