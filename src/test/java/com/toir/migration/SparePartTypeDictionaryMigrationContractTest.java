package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SparePartTypeDictionaryMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260613_6__spare_part_type_dictionary.sql");

    @Test
    void migrationCreatesDictionaryAndBackfillsSpareParts() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS spare_part_types");
        assertThat(sql).contains("code varchar(100) NOT NULL");
        assertThat(sql).contains("name varchar(255) NOT NULL");
        assertThat(sql).contains("default_unit varchar(50)");
        assertThat(sql).contains("active boolean NOT NULL DEFAULT true");
        assertThat(sql).contains("ALTER TABLE spare_parts");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS type_id uuid");
        assertThat(sql).contains("UPDATE spare_parts sp");
        assertThat(sql).contains("SET type_id = spt.id");
        assertThat(sql).contains("ALTER COLUMN type_id SET NOT NULL");
    }

    @Test
    void migrationSeedsDefaultDictionaryValues() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("'OIL', 'Oil', 'Lubricants and oils', 'LITER', true");
        assertThat(sql).contains("'BEARING', 'Bearing', 'Bearings and bearing assemblies', 'PCS', true");
        assertThat(sql).contains("'FILTER', 'Filter', 'Filters and filtration parts', 'PCS', true");
        assertThat(sql).contains("'BELT', 'Belt', 'Belts and belt drives', 'METER', true");
        assertThat(sql).contains("'CABLE', 'Cable', 'Cables and wiring', 'METER', true");
        assertThat(sql).contains("'METAL', 'Metal', 'Metal materials and stock', 'KG', true");
        assertThat(sql).contains("'CHEMICAL', 'Chemical', 'Chemicals and process fluids', 'LITER', true");
        assertThat(sql).contains("'ELECTRICAL_PART', 'Electrical Part', 'Electrical spare parts', 'PCS', true");
        assertThat(sql).contains("'MECHANICAL_PART', 'Mechanical Part', 'Mechanical spare parts', 'PCS', true");
        assertThat(sql).contains("'CONSUMABLE', 'Consumable', 'Consumable inventory', 'PCS', true");
        assertThat(sql).contains("'OTHER', 'Other', 'Unclassified spare parts', 'PCS', true");
    }
}
