package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationFilenameContractTest {

    private static final Path MIGRATION_DIRECTORY = Path.of("src/main/resources/db/migration");
    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V([^_]+(?:_[^_]+)*)__.+\\.sql$");

    @Test
    void versionedSqlMigrationsHaveUniqueFlywayVersions() throws Exception {
        try (var files = Files.list(MIGRATION_DIRECTORY)) {
            Map<String, List<String>> migrationsByVersion = files
                    .map(path -> path.getFileName().toString())
                    .map(FlywayMigrationFilenameContractTest::versionedMigration)
                    .flatMap(List::stream)
                    .collect(Collectors.groupingBy(MigrationFile::version,
                            Collectors.mapping(MigrationFile::fileName, Collectors.toList())));

            Map<String, List<String>> duplicates = migrationsByVersion.entrySet().stream()
                    .filter(entry -> entry.getValue().size() > 1)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            assertThat(duplicates)
                    .as("Flyway rejects duplicate migration versions before startup")
                    .isEmpty();
        }
    }

    private static List<MigrationFile> versionedMigration(String fileName) {
        Matcher matcher = VERSIONED_MIGRATION.matcher(fileName);
        if (!matcher.matches()) {
            return List.of();
        }
        return List.of(new MigrationFile(matcher.group(1).replace('_', '.'), fileName));
    }

    private record MigrationFile(String version, String fileName) {
    }
}
