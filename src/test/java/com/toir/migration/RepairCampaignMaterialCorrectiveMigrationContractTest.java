package com.toir.migration;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;
class RepairCampaignMaterialCorrectiveMigrationContractTest {
 @Test void correctiveMigrationIsForwardOnlyAndEnforcesPrecisionSourceCouplingAndRemovalGuard()throws Exception{Path p=Path.of("src/main/resources/db/migration/V20260712_5_1__campaign_material_reservation_corrective.sql");assertThat(p).exists();String s=Files.readString(p);assertThat(s).contains("RC_V5_1_NUMERIC_OVERFLOW_REMEDIATION_REQUIRED","numeric(19,4)","chk_wo_spare_req_campaign_source_coupling","RC_MATERIAL_REQUIREMENT_IN_USE","quantity < 0","amount < 0","ERRCODE = '23514'","CONSTRAINT = 'RC_MATERIAL_REQUIREMENT_IN_USE'");}
}
