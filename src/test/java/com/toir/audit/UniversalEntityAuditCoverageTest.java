package com.toir.audit;

import com.toir.entity.AuditLog;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UniversalEntityAuditCoverageTest {

    private final UniversalAuditEntityResolver resolver = new UniversalAuditEntityResolver();

    @Test
    void everyJpaEntityExceptAuditLogIsCoveredByUniversalAudit() throws Exception {
        List<Class<?>> entityClasses = discoverEntityClasses();

        assertThat(entityClasses).isNotEmpty();
        assertThat(entityClasses).doesNotContain(AuditLog.class);
        assertThat(entityClasses)
                .allSatisfy(entityClass -> {
                    assertThat(resolver.isAuditable(entityClass)).as(entityClass.getName()).isTrue();
                    assertThat(resolver.entityType(entityClass)).as(entityClass.getName()).isNotBlank();
                    assertThat(resolver.module(entityClass)).as(entityClass.getName()).isNotNull();
                });
    }

    private List<Class<?>> discoverEntityClasses() throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        try (var paths = Files.walk(Path.of("src/main/java/com/toir/entity"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                if (!source.contains("@Entity")) {
                    continue;
                }
                String packageName = source.lines()
                        .filter(line -> line.startsWith("package "))
                        .findFirst()
                        .orElseThrow()
                        .replace("package ", "")
                        .replace(";", "")
                        .trim();
                String className = path.getFileName().toString().replace(".java", "");
                Class<?> entityClass = Class.forName(packageName + "." + className);
                if (!entityClass.equals(AuditLog.class) && entityClass.isAnnotationPresent(Entity.class)) {
                    classes.add(entityClass);
                }
            }
        }
        return classes;
    }
}
