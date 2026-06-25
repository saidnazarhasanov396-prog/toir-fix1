package com.toir.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuditRedactionServiceTest {

    private final AuditRedactionService service = new AuditRedactionService(new ObjectMapper().findAndRegisterModules());

    @Test
    void redactsDefaultSensitiveFieldsRecursively() {
        String raw = """
                {
                  "username": "tech",
                  "passwordHash": "raw-hash",
                  "profile": {
                    "accessToken": "raw-token",
                    "displayName": "Technician"
                  },
                  "sessions": [
                    { "refreshToken": "raw-refresh", "device": "mobile" }
                  ]
                }
                """;

        String redacted = service.redactJson(raw, Set.of());

        assertThat(redacted).contains("\"passwordHash\":\"***REDACTED***\"");
        assertThat(redacted).contains("\"accessToken\":\"***REDACTED***\"");
        assertThat(redacted).contains("\"refreshToken\":\"***REDACTED***\"");
        assertThat(redacted).contains("\"username\":\"tech\"");
        assertThat(redacted).contains("\"displayName\":\"Technician\"");
        assertThat(redacted).doesNotContain("raw-hash", "raw-token", "raw-refresh");
    }

    @Test
    void redactsAnnotationProvidedFieldNames() {
        String raw = """
                {
                  "name": "Pump",
                  "integrationPassword": "raw-field-value",
                  "businessSecretCode": "raw-extra-value"
                }
                """;

        String redacted = service.redactJson(raw, Set.of("businessSecretCode"));

        assertThat(redacted).contains("\"businessSecretCode\":\"***REDACTED***\"");
        assertThat(redacted).contains("\"integrationPassword\":\"***REDACTED***\"");
        assertThat(redacted).doesNotContain("raw-field-value", "raw-extra-value");
    }
}
