package com.toir.service.maintanance;

import static org.assertj.core.api.Assertions.assertThat;

import com.toir.dto.pprplanning.PprPlanDto;
import com.toir.entity.PprPlan;
import jakarta.persistence.Column;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class MaintenanceScheduleContentHashStaticContractTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/toir");
    private static final Path MIGRATION_ROOT =
            Path.of("src/main/resources/db/migration");

    @Test
    void pprPlanKeepsCanonicalRevisionAndHashColumnMappings() throws Exception {
        assertColumn("calculationRevision", "calculation_revision", null);
        assertColumn("calculationContentHash", "calculation_content_hash", 64);
        assertColumn(
                "calculationContentHashVersion",
                "calculation_content_hash_version",
                null
        );
    }

    @Test
    void fullCalculationHashIsNotExposedByExistingPlanDto() {
        assertThat(Arrays.stream(PprPlanDto.class.getRecordComponents())
                .map(component -> component.getName())
                .toList())
                .doesNotContain(
                        "calculationContentHash",
                        "calculation_content_hash",
                        "canonicalContent"
                );
    }

    @Test
    void featureFlagRemainsDefaultFalseAndLegacyRuntimeHasNoW2Integration()
            throws Exception {
        String application = Files.readString(
                Path.of("src/main/resources/application.yml"),
                StandardCharsets.UTF_8
        );
        assertThat(application).contains(
                "TOIR_FEATURES_ANNUAL_MAINTENANCE_APPROVAL_FIRST_ENABLED:false");

        for (String source : List.of(
                "service/maintanance/MaintenanceScheduleCalculationService.java",
                "service/PprGeneratorService.java",
                "service/PprPlanService.java",
                "service/approval/PprPlanApprovalHandler.java",
                "service/ApprovalService.java"
        )) {
            assertThat(Files.readString(
                    SOURCE_ROOT.resolve(source),
                    StandardCharsets.UTF_8
            ))
                    .doesNotContain(
                            "MaintenanceScheduleContentHasher",
                            "MaintenanceScheduleRevisionService",
                            "MaintenanceScheduleSnapshotService"
                    );
        }
    }

    @Test
    void protectedMigrationsRemainByteForByteUnchanged() throws Exception {
        assertThat(sha256("V20260728_3__maintenance_schedule_weekday_shifting.sql"))
                .isEqualTo(
                        "f86bc7db5dc33afb08ead2e7e53916cdf29544199b4d77de2f9900493a607d31");
        assertThat(sha256(
                "V20260728_4__annual_maintenance_approval_first_foundation.sql"))
                .isEqualTo(
                        "37df0235e21abc2d737e134457b622d7ee8fca41a5f97888223147f2eb161f83");
        assertThat(sha256(
                "V20260728_5__annual_maintenance_calculation_snapshots.sql"))
                .isEqualTo(
                        "a82612f764d17af70bff44760f43373abf81b0365d8597cbb20a937a92d3e1d7");
    }

    private static void assertColumn(
            String fieldName,
            String expectedName,
            Integer expectedLength) throws Exception {
        Column column = PprPlan.class.getDeclaredField(fieldName)
                .getAnnotation(Column.class);
        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo(expectedName);
        if (expectedLength != null) {
            assertThat(column.length()).isEqualTo(expectedLength);
        }
    }

    private static String sha256(String fileName) throws Exception {
        byte[] bytes = Files.readAllBytes(MIGRATION_ROOT.resolve(fileName));
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
