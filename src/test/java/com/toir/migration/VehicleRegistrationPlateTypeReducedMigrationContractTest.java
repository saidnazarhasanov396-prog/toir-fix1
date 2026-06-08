package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleRegistrationPlateTypeReducedMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260608_6__vehicle_registration_plate_type_reduced.sql"
    );

    @Test
    void migrationMapsExistingDetailedTypesToReducedTemplateFamilies() throws Exception {
        assertThat(Files.exists(MIGRATION)).isTrue();

        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("DROP CONSTRAINT IF EXISTS chk_vehicle_details_plate_type")
                .contains("LEGAL_ENTITY_TWO_LINE")
                .contains("THEN 'LEGAL_ENTITY'")
                .contains("INDIVIDUAL_TWO_LINE")
                .contains("THEN 'INDIVIDUAL'")
                .contains("AMBULANCE_STY")
                .contains("THEN 'EMERGENCY'")
                .contains("DIPLOMATIC_CMD")
                .contains("THEN 'DIPLOMATIC'")
                .contains("UN_TWO_LINE")
                .contains("THEN 'INTERNATIONAL_ORG'")
                .contains("FOREIGN_ORG_M")
                .contains("THEN 'FOREIGN_ORG'")
                .contains("FOREIGN_INDIVIDUAL_H")
                .contains("THEN 'FOREIGN_PERSON'")
                .contains("ELECTRIC_MOTORCYCLE")
                .contains("THEN 'MOTORCYCLE'")
                .contains("TEMPORARY_TRANSIT")
                .contains("THEN 'TRANSIT'");
    }

    @Test
    void migrationRecreatesCheckConstraintWithReducedValuesOnly() throws Exception {
        assertThat(Files.exists(MIGRATION)).isTrue();

        String sql = Files.readString(MIGRATION);
        String reducedConstraint = sql.substring(sql.indexOf("ADD CONSTRAINT chk_vehicle_details_plate_type"));

        assertThat(reducedConstraint)
                .contains("'INDIVIDUAL'")
                .contains("'LEGAL_ENTITY'")
                .contains("'INDIVIDUAL_ECO'")
                .contains("'LEGAL_ENTITY_ECO'")
                .contains("'GOVERNMENT'")
                .contains("'EMERGENCY'")
                .contains("'DIPLOMATIC'")
                .contains("'INTERNATIONAL_ORG'")
                .contains("'FOREIGN_PERSON'")
                .contains("'FOREIGN_ORG'")
                .contains("'MOTORCYCLE'")
                .contains("'TRAILER'")
                .contains("'TRANSIT'")
                .contains("'UNKNOWN'")
                .doesNotContain("'LEGAL_ENTITY_TWO_LINE'")
                .doesNotContain("'DIPLOMATIC_CMD'")
                .doesNotContain("'TEMPORARY_TRANSIT'")
                .doesNotContain("'OTHER'");
    }
}
