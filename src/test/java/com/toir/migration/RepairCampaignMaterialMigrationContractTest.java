package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RepairCampaignMaterialMigrationContractTest {
    @Test
    void v5DefinesExactDecimalDemandAndCanonicalActiveReservationIdentity() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V20260712_5__campaign_material_reservation_integrity.sql");
        assertThat(migration).exists();
        String sql = Files.readString(migration);
        assertThat(sql).contains("numeric(19,4)", "repair_campaign_material_requirements",
                "campaign_requirement_id", "REPAIR_CAMPAIGN_WORK_ITEM",
                "uq_reservations_active_work_requirement_spare",
                "work_order_id, requirement_id, spare_part_id");
        assertThat(sql).contains("RC_V5_INVALID_LEGACY_QUANTITY_REMEDIATION_REQUIRED",
                "RC_V5_DUPLICATE_ACTIVE_RESERVATION_REMEDIATION_REQUIRED");
    }
}
