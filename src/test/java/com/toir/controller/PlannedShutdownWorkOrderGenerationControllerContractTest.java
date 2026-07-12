package com.toir.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlannedShutdownWorkOrderGenerationControllerContractTest {
    @Test
    void generationRequiresPreparePermissionAndIdempotencyHeader() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/toir/controller/PlannedShutdownController.java"));
        assertThat(source).contains("/{id}/work-orders/generate")
                .contains("PLANNED_SHUTDOWN_PREPARE")
                .contains("Idempotency-Key");
    }
}
