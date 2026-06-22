package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeArticleLinksMigrationContractTest {

    @Test
    void migrationCreatesPolymorphicKnowledgeArticleLinksAndBackfillsLegacyColumns() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V20260622_3__knowledge_article_links.sql"
        );
        assertThat(Files.exists(migration)).isTrue();

        String sql = Files.readString(migration).toLowerCase();

        assertThat(sql).contains("create table if not exists knowledge_article_links");
        assertThat(sql).contains("knowledge_article_id uuid not null");
        assertThat(sql).contains("target_type varchar(50) not null");
        assertThat(sql).contains("target_id uuid not null");
        assertThat(sql).contains("foreign key (knowledge_article_id) references knowledge_articles");
        assertThat(sql).contains("uq_knowledge_article_links_active_target");
        assertThat(sql).contains("idx_knowledge_article_links_target");
        assertThat(sql).contains("'equipment'");
        assertThat(sql).contains("'work_order'");
        assertThat(sql).contains("'defect'");
        assertThat(sql).contains("from knowledge_articles");
        assertThat(sql).contains("equipment_id");
        assertThat(sql).contains("work_order_id");
        assertThat(sql).contains("defect_id");
    }
}
