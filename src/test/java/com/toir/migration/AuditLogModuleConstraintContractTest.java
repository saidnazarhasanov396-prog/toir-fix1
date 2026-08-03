package com.toir.migration;

import com.toir.enums.AuditModule;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogModuleConstraintContractTest {

    private static final Path MIGRATION_DIRECTORY = Path.of("src/main/resources/db/migration");
    private static final Pattern MIGRATION_FILE = Pattern.compile("^[BV](.+?)__.*\\.sql$");

    @Test
    void latestAuditLogConstraintAcceptsEveryAuditModule() throws Exception {
        String constraintMigration = latestConstraintMigration();

        for (AuditModule module : AuditModule.values()) {
            assertThat(constraintMigration)
                    .as("audit_logs_module_check must accept AuditModule.%s", module)
                    .contains("'" + module.name() + "'");
        }
    }

    @Test
    void latestAuditLogConstraintPreservesPreviouslyAllowedWarehouseModules() throws Exception {
        String constraintMigration = latestConstraintMigration();

        assertThat(constraintMigration).contains(
                "'WAREHOUSE_BIN'",
                "'WAREHOUSE_TASK'",
                "'INVENTORY_COUNT_SESSION'",
                "'WAREHOUSE_WRITEOFF'"
        );
    }

    private String latestConstraintMigration() throws Exception {
        try (var paths = Files.list(MIGRATION_DIRECTORY)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> migrationVersion(path.getFileName().toString()) != null)
                    .filter(path -> read(path).contains("ADD CONSTRAINT audit_logs_module_check"))
                    .max(Comparator.comparing(path -> migrationVersion(path.getFileName().toString())))
                    .map(AuditLogModuleConstraintContractTest::read)
                    .orElseThrow();
        }
    }

    private static MigrationVersion migrationVersion(String fileName) {
        var matcher = MIGRATION_FILE.matcher(fileName);
        return matcher.matches()
                ? MigrationVersion.fromVersion(matcher.group(1).replace('_', '.'))
                : null;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read migration " + path, exception);
        }
    }
}
