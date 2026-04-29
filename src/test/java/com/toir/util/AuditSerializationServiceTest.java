package com.toir.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditSerializationServiceTest {

    private final AuditSerializationService service = new AuditSerializationService(new ObjectMapper().findAndRegisterModules());

    @Test
    void diffReturnsChangedTopLevelFields() {
        String oldJson = """
                {"id":"1","name":"Old","active":true}
                """;
        String newJson = """
                {"id":"1","name":"New","active":true}
                """;

        String diff = service.diff(oldJson, newJson);

        assertThat(diff).contains("\"name\"");
        assertThat(diff).contains("\"old\":\"Old\"");
        assertThat(diff).contains("\"new\":\"New\"");
        assertThat(diff).doesNotContain("\"active\"");
    }

    @Test
    void diffHandlesCreateState() {
        String diff = service.diff(null, "{\"id\":\"1\",\"name\":\"Created\"}");

        assertThat(diff).contains("\"id\"");
        assertThat(diff).contains("\"name\"");
        assertThat(diff).contains("\"new\":\"Created\"");
    }
}
