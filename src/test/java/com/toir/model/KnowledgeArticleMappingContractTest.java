package com.toir.model;

import com.toir.entity.KnowledgeArticle;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeArticleMappingContractTest {

    @Test
    void tagsFieldUsesJdbcTypeCodeJson() throws Exception {
        Field tagsField = KnowledgeArticle.class.getDeclaredField("tags");

        JdbcTypeCode jdbcTypeCode = tagsField.getAnnotation(JdbcTypeCode.class);
        assertThat(jdbcTypeCode).isNotNull();
        assertThat(jdbcTypeCode.value()).isEqualTo(SqlTypes.JSON);
    }

    @Test
    void tagsFieldDoesNotUseStringListJsonConverter() throws Exception {
        Field tagsField = KnowledgeArticle.class.getDeclaredField("tags");

        Convert convert = tagsField.getAnnotation(Convert.class);
        assertThat(convert).isNull();
    }

    @Test
    void tagsColumnDefinitionContainsJsonb() throws Exception {
        Field tagsField = KnowledgeArticle.class.getDeclaredField("tags");

        Column column = tagsField.getAnnotation(Column.class);
        assertThat(column).isNotNull();
        assertThat(column.columnDefinition().toLowerCase()).contains("jsonb");
    }
}

