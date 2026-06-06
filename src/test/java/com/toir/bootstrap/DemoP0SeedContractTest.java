package com.toir.bootstrap;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DemoP0SeedContractTest {

    private static final Path P0_DEMO_SEED =
            Path.of("src/main/resources/db/demo-seed/phase-5-p0-demo.sql");
    private static final Path P0_DEMO_SEEDER =
            Path.of("src/main/java/com/toir/config/DemoP0LeadershipDemoSeeder.java");

    @Test
    void p0DemoSeedContainsExactLeadershipAssets() throws Exception {
        assertThat(P0_DEMO_SEED).exists();

        String sql = Files.readString(P0_DEMO_SEED);

        assertThat(sql)
                .contains("AUTO-PUMP-A1")
                .contains("AUTO-PUMP-A2")
                .contains("AUTO-PUMP-A3-NOMETER")
                .contains("AUTO-PUMP-PM-2026")
                .contains("AUTO-PUMP-PM-2026-OP-01")
                .contains("AUTO-PUMP-PM-2026-OP-02")
                .contains("AUTO-PUMP-PM-2026-OP-03")
                .contains("AUTO-PUMP-A3-NOMETER:CYCLES:BLOCKED:MISSING_ACTIVE_METER")
                .contains("source_type, source_id")
                .contains("LABOR_ENTRY")
                .contains("MATERIAL_ISSUE")
                .contains("ON CONFLICT");
    }

    @Test
    void p0DemoSeederRunsOnlyWithDevDemoSeedProfile() throws Exception {
        assertThat(P0_DEMO_SEEDER).exists();

        String java = Files.readString(P0_DEMO_SEEDER);

        assertThat(java)
                .contains("@Profile(\"dev & demo-seed\")")
                .contains("@Order(15)")
                .contains("db/demo-seed/phase-5-p0-demo.sql");
    }
}
