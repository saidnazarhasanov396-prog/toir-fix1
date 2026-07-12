package com.toir.service.repair;

import com.toir.entity.repair.RepairCampaign;
import com.toir.enums.RepairCampaignMutationType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RepairCampaignMutationClassifierTest {
    @Test
    void financeChangesStrictlyOverrideMetadataAndFxOverridesBudget() {
        RepairCampaign current = new RepairCampaign();
        current.setMaintenanceBudgetId(UUID.randomUUID());
        current.setTotalBudget(new BigDecimal("10.0000"));
        current.setCurrencyCode("UZS");

        assertThat(RepairCampaignMutationClassifier.classify(current,
                current.getMaintenanceBudgetId(), new BigDecimal("10.0000"), "UZS"))
                .isEqualTo(RepairCampaignMutationType.METADATA);
        assertThat(RepairCampaignMutationClassifier.classify(current,
                UUID.randomUUID(), new BigDecimal("10.0000"), "UZS"))
                .isEqualTo(RepairCampaignMutationType.BUDGET);
        assertThat(RepairCampaignMutationClassifier.classify(current,
                UUID.randomUUID(), new BigDecimal("20.0000"), "USD"))
                .isEqualTo(RepairCampaignMutationType.FX);
    }
}
