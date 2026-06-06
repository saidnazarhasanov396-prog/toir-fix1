package com.toir.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigSecretsTest {

    private static final Path PROD_CONFIG = Path.of("src/main/resources/application-prod.yml");
    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{[A-Z0-9_]+(?::[^}]*)?}");

    @Test
    void productionConfigKeepsSecretsInEnvironmentPlaceholders() throws IOException {
        List<String> lines = Files.readAllLines(PROD_CONFIG);

        assertEnvPlaceholder(lines, "url:");
        assertEnvPlaceholder(lines, "username:");
        assertEnvPlaceholder(lines, "password:");
        assertEnvPlaceholder(lines, "secret:");
        assertEnvPlaceholder(lines, "admin-username:");
        assertEnvPlaceholder(lines, "admin-password:");
        assertEnvPlaceholder(lines, "admin-email:");
    }

    private static void assertEnvPlaceholder(List<String> lines, String key) {
        String line = lines.stream()
                .map(String::trim)
                .filter(candidate -> candidate.startsWith(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing production config key: " + key));

        assertThat(line)
                .as(key + " must use an environment placeholder")
                .containsPattern(ENV_PLACEHOLDER.pattern());
    }
}
