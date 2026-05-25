package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationVersionContractTest {

    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V(.+?)__.*\\.sql$");

    @Test
    void versionedMigrationsMustHaveUniqueFlywayVersions() throws Exception {
        Map<String, List<String>> filesByVersion;
        try (var paths = Files.list(Path.of("src/main/resources/db/migration"))) {
            filesByVersion = paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .map(VERSIONED_MIGRATION::matcher)
                    .filter(matcher -> matcher.matches())
                    .collect(Collectors.groupingBy(
                            matcher -> matcher.group(1),
                            LinkedHashMap::new,
                            Collectors.mapping(matcher -> matcher.group(0), Collectors.toList())
                    ));
        }

        Map<String, List<String>> duplicateVersions = filesByVersion.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));

        assertThat(duplicateVersions).isEmpty();
    }
}
