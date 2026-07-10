package com.toir.service.sparepartlifecycle;

import com.toir.entity.sparepartlifecycle.SparePartLifeRule;
import com.toir.enums.sparepartlifecycle.SparePartLifeRuleScope;
import com.toir.repository.sparepartlifecycle.SparePartLifeRuleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SparePartLifeRuleResolverTest {

    private static final Instant AT = Instant.parse("2026-07-10T10:00:00Z");

    @Mock
    SparePartLifeRuleRepository ruleRepository;

    @Mock
    SparePartSlotNormalizer slotNormalizer;

    @InjectMocks
    SparePartLifeRuleResolver resolver;

    @Test
    void choosesNodeSlotBeforeNodeEquipmentAndCatalog() {
        UUID partId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        SparePartLifeRule catalog = rule(partId, SparePartLifeRuleScope.CATALOG, null, null, null);
        SparePartLifeRule equipment = rule(partId, SparePartLifeRuleScope.EQUIPMENT, equipmentId, null, null);
        SparePartLifeRule node = rule(partId, SparePartLifeRuleScope.NODE, equipmentId, nodeId, null);
        SparePartLifeRule slot = rule(partId, SparePartLifeRuleScope.NODE_SLOT, equipmentId, nodeId, "FRONT_LEFT");
        when(slotNormalizer.normalizeNullable(" front-left ")).thenReturn("FRONT_LEFT");
        when(ruleRepository.findAllBySparePartIdAndActiveTrueAndIsDeletedFalse(partId))
                .thenReturn(List.of(catalog, equipment, node, slot));

        Optional<SparePartLifeRule> resolved = resolver.resolve(
                partId,
                equipmentId,
                nodeId,
                " front-left ",
                AT
        );

        assertThat(resolved).containsSame(slot);
    }

    @Test
    void fallsBackThroughNodeEquipmentAndCatalogScopes() {
        UUID partId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        SparePartLifeRule catalog = rule(partId, SparePartLifeRuleScope.CATALOG, null, null, null);
        SparePartLifeRule equipment = rule(partId, SparePartLifeRuleScope.EQUIPMENT, equipmentId, null, null);
        SparePartLifeRule node = rule(partId, SparePartLifeRuleScope.NODE, equipmentId, nodeId, null);
        when(slotNormalizer.normalizeNullable(null)).thenReturn(null);
        when(ruleRepository.findAllBySparePartIdAndActiveTrueAndIsDeletedFalse(partId))
                .thenReturn(List.of(catalog, equipment, node));

        assertThat(resolver.resolve(partId, equipmentId, nodeId, null, AT)).containsSame(node);
        assertThat(resolver.resolve(partId, equipmentId, UUID.randomUUID(), null, AT)).containsSame(equipment);
        assertThat(resolver.resolve(partId, UUID.randomUUID(), UUID.randomUUID(), null, AT)).containsSame(catalog);
    }

    @Test
    void effectiveStartIsInclusiveAndEndIsExclusive() {
        UUID partId = UUID.randomUUID();
        SparePartLifeRule atStart = rule(partId, SparePartLifeRuleScope.CATALOG, null, null, null);
        atStart.setEffectiveFrom(AT);
        atStart.setEffectiveTo(AT.plusSeconds(60));
        when(slotNormalizer.normalizeNullable(null)).thenReturn(null);
        when(ruleRepository.findAllBySparePartIdAndActiveTrueAndIsDeletedFalse(partId))
                .thenReturn(List.of(atStart));

        assertThat(resolver.resolve(partId, UUID.randomUUID(), null, null, AT)).containsSame(atStart);
        assertThat(resolver.resolve(partId, UUID.randomUUID(), null, null, AT.plusSeconds(60))).isEmpty();
    }

    @Test
    void reportsAmbiguityAtTheHighestMatchingScope() {
        UUID partId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        SparePartLifeRule first = rule(partId, SparePartLifeRuleScope.EQUIPMENT, equipmentId, null, null);
        SparePartLifeRule second = rule(partId, SparePartLifeRuleScope.EQUIPMENT, equipmentId, null, null);
        when(slotNormalizer.normalizeNullable(null)).thenReturn(null);
        when(ruleRepository.findAllBySparePartIdAndActiveTrueAndIsDeletedFalse(partId))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> resolver.resolve(partId, equipmentId, null, null, AT))
                .hasMessageStartingWith("RULE_AMBIGUOUS:");
    }

    @Test
    void slotNormalizerProducesStableMachineKeys() {
        SparePartSlotNormalizer normalizer = new SparePartSlotNormalizer();

        assertThat(normalizer.normalizeNullable("  front-left / outer  ")).isEqualTo("FRONT_LEFT_OUTER");
        assertThat(normalizer.normalizeNullable("   ")).isNull();
        assertThat(normalizer.normalizeRequired("   ")).isEqualTo("DEFAULT");
        assertThat(normalizer.positionKey(UUID.fromString("11111111-1111-1111-1111-111111111111"), "front-left"))
                .isEqualTo("N:11111111-1111-1111-1111-111111111111:S:FRONT_LEFT");
    }

    private static SparePartLifeRule rule(UUID partId,
                                          SparePartLifeRuleScope scope,
                                          UUID equipmentId,
                                          UUID nodeId,
                                          String slot) {
        SparePartLifeRule rule = new SparePartLifeRule();
        rule.setId(UUID.randomUUID());
        rule.setSparePartId(partId);
        rule.setScopeType(scope);
        rule.setEquipmentId(equipmentId);
        rule.setEquipmentNodeId(nodeId);
        rule.setNormalizedSlotCode(slot);
        rule.setActive(true);
        rule.setRevision(1);
        return rule;
    }
}
