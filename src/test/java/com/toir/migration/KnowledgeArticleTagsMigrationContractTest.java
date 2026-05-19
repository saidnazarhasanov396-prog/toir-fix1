package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeArticleTagsMigrationContractTest {

    @Test
    void migrationNormalizesKnowledgeArticleTagsToJsonArray() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V20260517_1__normalize_knowledge_article_tags.sql"
        );

        String sql = Files.readString(migration).toLowerCase();

        assertThat(sql).contains("knowledge_articles");
        assertThat(sql).contains("if to_regclass('public.knowledge_articles') is not null then");
        assertThat(sql).contains("jsonb_typeof(tags)");
        assertThat(sql).contains("jsonb_build_array");
        assertThat(sql).contains("chk_knowledge_articles_tags_array");
        assertThat(sql).contains("from pg_constraint");
        assertThat(sql).contains("alter column tags set default");
    }
}
