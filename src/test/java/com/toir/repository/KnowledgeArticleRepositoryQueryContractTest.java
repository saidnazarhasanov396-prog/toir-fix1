package com.toir.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeArticleRepositoryQueryContractTest {

    @Test
    void existsByCodeMethodPresent() {
        Method method = Arrays.stream(KnowledgeArticleRepository.class.getMethods())
                .filter(m -> m.getName().equals("existsByCode"))
                .findFirst()
                .orElse(null);

        assertThat(method).isNotNull();
    }

    @Test
    void latestCodeQueryUsesPrefixAndNumericSuffix() {
        Method method = Arrays.stream(KnowledgeArticleRepository.class.getMethods())
                .filter(m -> m.getName().equals("maxSequenceByCodePrefix"))
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();

        String sql = query.value().toLowerCase();
        assertThat(sql).contains("from knowledge_articles");
        assertThat(sql).contains("code like concat(:prefix, '%')");
        assertThat(sql).contains("substring(code from length(:prefix) + 1) ~ '^[0-9]+$'");
    }
}

