package com.toir.service.repair;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RepairCampaignApprovalScopeHasherTest {

    @Test
    void canonicalHashIsOrderIndependentAndSensitiveToEveryApprovalFactFamily() {
        List<String> facts = List.of(
                "metadata:name=Turnaround", "dates:start=2026-08-01", "work:1=PUMP-1",
                "order:1=10", "dependency:1>2", "resource:crew=4", "material:seal=2",
                "shutdown:link=ps-1", "window:ps-1=7", "budget:amount=100.0000",
                "budget:currency=UZS", "scopeVersion=3");

        String baseline = RepairCampaignApprovalScopeHasher.hashCanonicalFacts(facts);
        List<String> reversed = new ArrayList<>(facts);
        java.util.Collections.reverse(reversed);
        assertThat(RepairCampaignApprovalScopeHasher.hashCanonicalFacts(reversed)).isEqualTo(baseline);

        for (int index = 0; index < facts.size(); index++) {
            List<String> changed = new ArrayList<>(facts);
            changed.set(index, facts.get(index) + "-changed");
            assertThat(RepairCampaignApprovalScopeHasher.hashCanonicalFacts(changed))
                    .as("fact family %s must be approval-relevant", facts.get(index))
                    .isNotEqualTo(baseline);
        }
    }
}
