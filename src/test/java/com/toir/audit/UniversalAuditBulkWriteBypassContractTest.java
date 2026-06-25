package com.toir.audit;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UniversalAuditBulkWriteBypassContractTest {

    @Test
    void productionCodeDoesNotUseBulkWritesThatBypassHibernateAuditEvents() throws Exception {
        List<Path> offenders;
        try (var paths = Files.walk(Path.of("src/main/java/com/toir"))) {
            offenders = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return source.contains("@Modifying") || source.contains("executeUpdate(");
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
        }

        assertThat(offenders)
                .as("Bulk JPQL/native writes bypass Hibernate entity events; refactor to entity saves or add explicit audited handling.")
                .isEmpty();
    }
}
