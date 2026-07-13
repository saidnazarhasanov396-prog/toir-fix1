package com.toir.service.repair;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void stageAndAttachedWorkOrderFactsChangeAggregateHashDeterministically() {
        var workItems = mock(com.toir.repository.repair.RepairCampaignWorkItemRepository.class);
        var dependencies = mock(com.toir.repository.repair.RepairCampaignWorkDependencyRepository.class);
        var resources = mock(com.toir.repository.repair.RepairCampaignResourceAssignmentRepository.class);
        var materials = mock(com.toir.repository.repair.RepairCampaignMaterialRequirementRepository.class);
        var links = mock(com.toir.repository.plannedshutdown.PlannedShutdownCampaignLinkRepository.class);
        var windows = mock(com.toir.repository.repair.RepairCampaignWorkItemWindowRepository.class);
        var shutdowns = mock(com.toir.repository.PlannedShutdownRepository.class);
        var stages = mock(com.toir.repository.repair.RepairCampaignStageRepository.class);
        var orders = mock(com.toir.repository.WorkOrderRepository.class);
        RepairCampaignApprovalScopeHasher hasher = new RepairCampaignApprovalScopeHasher(
                workItems, dependencies, resources, materials, links, windows, shutdowns, stages, orders);
        var campaign = new com.toir.entity.repair.RepairCampaign();
        campaign.setId(java.util.UUID.randomUUID());
        campaign.setScopeVersion(1L);
        campaign.setTotalBudget(java.math.BigDecimal.TEN);
        var stage = new com.toir.entity.repair.RepairCampaignStage();
        stage.setId(java.util.UUID.randomUUID()); stage.setSequence(1); stage.setName("Scope");
        stage.setStartDate(java.time.LocalDate.of(2026, 8, 1)); stage.setEndDate(java.time.LocalDate.of(2026, 8, 2));
        stage.setPlannedCost(java.math.BigDecimal.ONE); stage.setStatus(com.toir.enums.RepairCampaignStageStatus.DRAFT);
        when(stages.findAllByCampaignIdOrderBySequenceAscIdAsc(campaign.getId())).thenReturn(List.of(stage));
        when(orders.findAllByRepairCampaignIdAndIsDeletedFalseOrderByUpdatedAtDesc(campaign.getId())).thenReturn(List.of());
        String baseline = hasher.hash(campaign);
        stage.setPlannedCost(java.math.BigDecimal.TEN);
        assertThat(hasher.hash(campaign)).isNotEqualTo(baseline);

        stage.setPlannedCost(java.math.BigDecimal.ONE);
        var order = new com.toir.entity.maintenance.WorkOrder();
        order.setId(java.util.UUID.randomUUID()); order.setRepairCampaignStageId(stage.getId());
        order.setStatus(com.toir.enums.WorkOrderStatus.DRAFT); order.setNumber("WO-1");
        when(orders.findAllByRepairCampaignIdAndIsDeletedFalseOrderByUpdatedAtDesc(campaign.getId())).thenReturn(List.of(order));
        assertThat(hasher.hash(campaign)).isNotEqualTo(baseline);
    }
}
