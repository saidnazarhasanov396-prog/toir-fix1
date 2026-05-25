package com.toir.model;

import com.toir.entity.users.BrigadeMember;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class BrigadeMemberMappingContractTest {

    @Test
    void qualificationsFieldUsesJdbcTypeCodeJson() throws Exception {
        Field qualificationsField = BrigadeMember.class.getDeclaredField("qualifications");

        JdbcTypeCode jdbcTypeCode = qualificationsField.getAnnotation(JdbcTypeCode.class);
        assertThat(jdbcTypeCode).isNotNull();
        assertThat(jdbcTypeCode.value()).isEqualTo(SqlTypes.JSON);
    }

    @Test
    void qualificationsFieldDoesNotUseLegacyStringConverter() throws Exception {
        Field qualificationsField = BrigadeMember.class.getDeclaredField("qualifications");

        Convert convert = qualificationsField.getAnnotation(Convert.class);
        assertThat(convert).isNull();
    }

    @Test
    void qualificationsColumnDefinitionContainsJsonb() throws Exception {
        Field qualificationsField = BrigadeMember.class.getDeclaredField("qualifications");

        Column column = qualificationsField.getAnnotation(Column.class);
        assertThat(column).isNotNull();
        assertThat(column.columnDefinition().toLowerCase()).contains("jsonb");
    }
}
