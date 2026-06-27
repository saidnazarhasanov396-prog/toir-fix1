package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MxikEquipmentSparePartLinkMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260627_8__mxik_equipment_spare_part_links.sql");

    @Test
    void migrationAddsMxikLinksToEquipmentAndSpareParts() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("ALTER TABLE equipment");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS mxik_id uuid");
        assertThat(sql).contains("ALTER TABLE spare_parts");
        assertThat(sql).contains("fk_equipment_mxik");
        assertThat(sql).contains("fk_spare_parts_mxik");
        assertThat(sql).contains("idx_equipment_mxik_id");
        assertThat(sql).contains("idx_spare_parts_mxik_id");
    }
}
