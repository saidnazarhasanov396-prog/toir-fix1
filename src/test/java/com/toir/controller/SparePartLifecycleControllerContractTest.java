package com.toir.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SparePartLifecycleControllerContractTest {

    @Test
    void installationControllerExposesExplicitIdempotentLifecycleCommands() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/toir/controller/sparepartlifecycle/SparePartInstallationController.java"));

        assertThat(source).contains("@RequestMapping(\"/api/v1/spare-part-installations\")");
        assertThat(source).contains("@RequestHeader(\"Idempotency-Key\")");
        assertThat(source).contains("SPARE_PART_INSTALL");
        assertThat(source).contains("SPARE_PART_REMOVE");
        assertThat(source).contains("SPARE_PART_REPLACE");
        assertThat(source).contains("/equipment/{equipmentId}/current");
        assertThat(source).contains("/equipment/{equipmentId}/history");
        assertThat(source).contains("/{id}/reevaluate");
        assertThat(source).contains("SPARE_PART_EXPIRY_OVERRIDE");
    }

    @Test
    void ruleDueAndReadinessControllersUseGranularPermissions() throws Exception {
        String rules = Files.readString(Path.of(
                "src/main/java/com/toir/controller/sparepartlifecycle/SparePartLifeRuleController.java"));
        String due = Files.readString(Path.of(
                "src/main/java/com/toir/controller/sparepartlifecycle/SparePartDueEventController.java"));
        String readiness = Files.readString(Path.of(
                "src/main/java/com/toir/controller/sparepartlifecycle/SparePartOperationalReadinessController.java"));
        String nextActions = Files.readString(Path.of(
                "src/main/java/com/toir/controller/sparepartlifecycle/SparePartNextRequiredActionController.java"));
        String effectiveRule = Files.readString(Path.of(
                "src/main/java/com/toir/controller/sparepartlifecycle/SparePartEffectiveLifeRuleController.java"));

        assertThat(rules).contains("SPARE_PART_LIFE_RULE_READ");
        assertThat(rules).contains("SPARE_PART_LIFE_RULE_CREATE");
        assertThat(rules).contains("SPARE_PART_LIFE_RULE_UPDATE");
        assertThat(rules).contains("SPARE_PART_LIFE_RULE_DELETE");
        assertThat(due).contains("SPARE_PART_DUE_READ");
        assertThat(due).contains("SPARE_PART_DUE_ACKNOWLEDGE");
        assertThat(readiness).contains("/api/v1/equipment/{equipmentId}/operational-readiness");
        assertThat(readiness).contains("SPARE_PART_DUE_READ");
        assertThat(nextActions).contains("/api/v1/equipment/{equipmentId}/next-required-actions");
        assertThat(nextActions).contains("SPARE_PART_DUE_READ");
        assertThat(effectiveRule).contains("/api/v1/equipment/{equipmentId}/spare-part-life-rules/effective");
        assertThat(effectiveRule).contains("SPARE_PART_LIFE_RULE_READ");
    }
}
