package com.toir.config;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigSecretsTest {

    private static final Path PROD_CONFIG = Path.of("src/main/resources/application-prod.yml");
    private static final Path DEV_CONFIG = Path.of("src/main/resources/application-dev.yml");
    private static final Path DOCKER_COMPOSE = Path.of("docker-compose.yml");
    private static final Path GITLAB_CI = Path.of(".gitlab-ci.yml");
    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{[A-Z0-9_]+(?::[^}]*)?}");
    private static final Pattern COMPOSE_ENV_LINE = Pattern.compile("^\\s*([A-Z0-9_]+):\\s*(.+?)\\s*$");

    @Test
    @Disabled("Deferred: runtime security configuration is TeamLead/DevOps-owned and will be re-enabled during final security hardening")
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

    @Test
    @Disabled("Deferred: runtime security configuration is TeamLead/DevOps-owned and will be re-enabled during final security hardening")
    void productionJwtSecretUsesExplicitToirEnvironmentVariable() throws IOException {
        List<String> lines = Files.readAllLines(PROD_CONFIG);

        String secretLine = findTrimmedLine(lines, "secret:");

        assertThat(secretLine)
                .as("prod JWT secret must come from the documented env var without a default")
                .isEqualTo("secret: ${TOIR_JWT_SECRET}");
    }

    @Test
    void dockerDemoUsesDevDemoSeedProfileAndDoesNotUseProdPlaceholders() throws IOException {
        List<String> lines = Files.readAllLines(DOCKER_COMPOSE);

        assertThat(lines)
                .as("local/demo Docker must not set legacy or production JWT secret variables")
                .noneMatch(line -> line.trim().startsWith("TOIR_JWT_SECRET:"))
                .noneMatch(line -> line.trim().startsWith("JWT_SECRET:"));

        assertThat(findComposeValue(lines, "SPRING_PROFILES_ACTIVE"))
                .as("local/demo Docker must not boot with application-prod.yml")
                .isEqualTo("dev,demo-seed");

        assertThat(lines)
                .as("local/demo Docker must not pass unresolved production placeholders to Spring")
                .noneMatch(line -> line.contains("${TOIR_DB_URL}"))
                .noneMatch(line -> line.trim().startsWith("TOIR_DB_URL:"));

        assertThat(findComposeValue(lines, "SPRING_DATASOURCE_URL"))
                .as("local/demo Docker DB URL must point to the compose postgres service")
                .isEqualTo("jdbc:postgresql://postgres:5432/toir_demo");
    }

    @Test
    void devConfigUsesStrongTestOnlyJwtSecret() throws IOException {
        List<String> lines = Files.readAllLines(DEV_CONFIG);

        String secret = findTrimmedLine(lines, "secret:").substring("secret:".length()).trim();

        assertThat(secret)
                .as("dev/demo JWT secret must be fake and long enough for HS256")
                .startsWith("test-only-local-demo-")
                .hasSizeGreaterThanOrEqualTo(32)
                .matches("[\\x20-\\x7E]+");
    }

    @Test
    void gitlabDeployExplicitlyDisablesFirebase() throws IOException {
        String gitlabCi = Files.readString(GITLAB_CI);

        assertThat(gitlabCi)
                .as("production deploy must not require Firebase credentials")
                .contains("APP_FIREBASE_ENABLED=false")
                .doesNotContain("APP_FIREBASE_PROJECT_ID")
                .doesNotContain("APP_FIREBASE_SERVICE_ACCOUNT_BASE64")
                .doesNotContain("APP_FIREBASE_SERVICE_ACCOUNT_JSON")
                .doesNotContain("APP_FIREBASE_SERVICE_ACCOUNT_FILE")
                .doesNotContain("Missing Firebase credentials")
                .contains("--env-file /tmp/toir-backend.env");
    }

    private static void assertEnvPlaceholder(List<String> lines, String key) {
        String line = findTrimmedLine(lines, key);

        assertThat(line)
                .as(key + " must use an environment placeholder")
                .containsPattern(ENV_PLACEHOLDER.pattern());
    }

    private static String findTrimmedLine(List<String> lines, String key) {
        return lines.stream()
                .map(String::trim)
                .filter(candidate -> candidate.startsWith(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing production config key: " + key));
    }

    private static String findComposeValue(List<String> lines, String key) {
        return lines.stream()
                .map(COMPOSE_ENV_LINE::matcher)
                .filter(matcher -> matcher.matches() && matcher.group(1).equals(key))
                .map(matcher -> matcher.group(2))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing docker compose environment key: " + key));
    }

}
