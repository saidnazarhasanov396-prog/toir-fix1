package com.toir.service;

import com.toir.dto.knowledge.KnowledgeArticleDto;
import com.toir.entity.KnowledgeArticle;
import com.toir.repository.KnowledgeArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock
    KnowledgeArticleRepository repository;

    @InjectMocks
    KnowledgeService service;

    @Test
    void listWithNoFiltersReturnsEmptyPage() {
        when(repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());

        Page<KnowledgeArticleDto> page = service.list(null, null, null, 0, 10);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        verify(repository).findAllByIsDeletedFalseOrderByUpdatedAtDesc();
    }

    @Test
    void listWithEquipmentFilterReturns200CompatibleData() {
        UUID equipmentId = UUID.randomUUID();
        when(repository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId))
                .thenReturn(List.of(article("KB-1", "Pump alignment")));

        Page<KnowledgeArticleDto> page = service.list(equipmentId, null, null, 0, 10);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).code()).isEqualTo("KB-1");
        verify(repository).findAllByEquipmentIdAndIsDeletedFalse(equipmentId);
    }

    @Test
    void listWithEquipmentTypeFilterReturns200CompatibleData() {
        UUID equipmentTypeId = UUID.randomUUID();
        when(repository.findAllByEquipmentTypeIdAndIsDeletedFalse(equipmentTypeId))
                .thenReturn(List.of(article("KB-1T", "Type guide")));

        Page<KnowledgeArticleDto> page = service.list(null, equipmentTypeId, null, 0, 10);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).code()).isEqualTo("KB-1T");
        verify(repository).findAllByEquipmentTypeIdAndIsDeletedFalse(equipmentTypeId);
    }

    @Test
    void listWithBlankKindFallsBackToDefaultQueryAndNormalizesTags() {
        KnowledgeArticle article = article("KB-2", "No tags item");
        article.setTags(null);
        when(repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(article));

        Page<KnowledgeArticleDto> page = service.list(null, null, "   ", 0, 10);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).tags()).isEmpty();
        verify(repository).findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        verify(repository, never()).findAllByKindAndIsDeletedFalse("   ");
    }

    @Test
    void listWithKindFilterUsesKindQuery() {
        KnowledgeArticle procedure = article("KB-3", "Procedure");
        procedure.setKind("PROCEDURE");
        when(repository.findAllByKindAndIsDeletedFalse("PROCEDURE"))
                .thenReturn(List.of(procedure));

        Page<KnowledgeArticleDto> page = service.list(null, null, "PROCEDURE", 0, 10);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).kind()).isEqualTo("PROCEDURE");
        verify(repository).findAllByKindAndIsDeletedFalse("PROCEDURE");
    }

    @Test
    void createNormalizesNullTagsToEmptyList() {
        KnowledgeArticle article = article("KB-C1", "Create article");
        article.setTags(null);
        when(repository.existsByCodeAndIsDeletedFalse("KB-C1")).thenReturn(false);
        when(repository.save(any(KnowledgeArticle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeArticle created = service.create(article);

        ArgumentCaptor<KnowledgeArticle> captor = ArgumentCaptor.forClass(KnowledgeArticle.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTags()).isNotNull().isEmpty();
        assertThat(created.getTags()).isNotNull().isEmpty();
    }

    @Test
    void createKeepsTagsList() {
        KnowledgeArticle article = article("KB-C2", "Tagged article");
        article.setTags(List.of("pump", "seal"));
        when(repository.existsByCodeAndIsDeletedFalse("KB-C2")).thenReturn(false);
        when(repository.save(any(KnowledgeArticle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeArticle created = service.create(article);

        ArgumentCaptor<KnowledgeArticle> captor = ArgumentCaptor.forClass(KnowledgeArticle.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTags()).containsExactly("pump", "seal");
        assertThat(created.getTags()).containsExactly("pump", "seal");
    }

    private KnowledgeArticle article(String code, String title) {
        KnowledgeArticle article = new KnowledgeArticle();
        ReflectionTestUtils.setField(article, "id", UUID.randomUUID());
        article.setCode(code);
        article.setTitle(title);
        article.setKind("LESSON_LEARNED");
        article.setProblem("Problem");
        article.setRootCause("Cause");
        article.setSolution("Solution");
        article.setTags(List.of("tag1"));
        return article;
    }
}
