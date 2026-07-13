package com.toir.service.sparepartlifecycle;

import com.toir.dto.sparepartlifecycle.SparePartLifeLimitRequest;
import com.toir.dto.sparepartlifecycle.SparePartLifeRuleRequest;
import com.toir.entity.SparePart;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentNode;
import com.toir.entity.sparepartlifecycle.SparePartLifeLimit;
import com.toir.entity.sparepartlifecycle.SparePartLifeRule;
import com.toir.enums.sparepartlifecycle.SparePartCalendarUnit;
import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartLifeCombinationMode;
import com.toir.enums.sparepartlifecycle.SparePartLifeLimitKind;
import com.toir.enums.sparepartlifecycle.SparePartLifeRuleScope;
import com.toir.repository.SparePartRepository;
import com.toir.repository.equipment.EquipmentNodeRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.sparepartlifecycle.SparePartLifeLimitRepository;
import com.toir.repository.sparepartlifecycle.SparePartLifeRuleRepository;
import com.toir.util.AuditBuilderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SparePartLifeRuleServiceTest {

    @Mock SparePartLifeRuleRepository ruleRepository;
    @Mock SparePartLifeLimitRepository limitRepository;
    @Mock SparePartRepository sparePartRepository;
    @Mock EquipmentRepository equipmentRepository;
    @Mock EquipmentNodeRepository equipmentNodeRepository;
    @Mock SparePartSlotNormalizer slotNormalizer;
    @Mock SparePartLifeRuleValidator validator;
    @Mock AuditBuilderService auditBuilderService;

    @InjectMocks SparePartLifeRuleService service;

    @Test
    void listBatchEnrichesNamesAndLimits() {
        UUID partId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        SparePartLifeRule rule = rule(partId, equipmentId);
        SparePartLifeLimit limit = limit(rule.getId(), "1000.000000", "900.000000");
        SparePart part = new SparePart();
        part.setId(partId);
        part.setName("Podshipnik 6205");
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setName("Kompressor K-101");
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"));
        when(ruleRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(rule), pageable, 1));
        when(limitRepository.findAllByRuleIdInAndIsDeletedFalseOrderBySequenceAsc(List.of(rule.getId())))
                .thenReturn(List.of(limit));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(part));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(equipment));

        var result = service.list(new com.toir.dto.sparepartlifecycle.SparePartLifeRuleFilter(
                partId, equipmentId, null, SparePartLifeRuleScope.EQUIPMENT, true, null), pageable);

        assertThat(result.getContent()).singleElement().satisfies(dto -> {
            assertThat(dto.sparePartId()).isEqualTo(partId);
            assertThat(dto.sparePartName()).isEqualTo("Podshipnik 6205");
            assertThat(dto.equipmentId()).isEqualTo(equipmentId);
            assertThat(dto.equipmentName()).isEqualTo("Kompressor K-101");
            assertThat(dto.limits()).singleElement().satisfies(limitDto -> {
                assertThat(limitDto.limitValue()).isEqualByComparingTo("1000");
                assertThat(limitDto.limitValue().scale()).isZero();
                assertThat(limitDto.warningBeforeValue()).isEqualByComparingTo("900");
                assertThat(limitDto.warningBeforeValue().scale()).isZero();
            });
        });
    }

    @Test
    void listUsesSingleBatchLookupPerReferenceType() {
        SparePartLifeRule first = rule(UUID.randomUUID(), UUID.randomUUID());
        SparePartLifeRule second = rule(UUID.randomUUID(), UUID.randomUUID());
        Pageable pageable = PageRequest.of(0, 20);
        when(ruleRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
        when(limitRepository.findAllByRuleIdInAndIsDeletedFalseOrderBySequenceAsc(any()))
                .thenReturn(List.of());
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of());
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of());

        service.list(new com.toir.dto.sparepartlifecycle.SparePartLifeRuleFilter(
                null, null, null, null, null, null), pageable);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> sparePartIds = ArgumentCaptor.forClass(Collection.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> equipmentIds = ArgumentCaptor.forClass(Collection.class);
        verify(sparePartRepository, times(1)).findAllByIdInAndIsDeletedFalse(sparePartIds.capture());
        verify(equipmentRepository, times(1)).findAllByIdInAndIsDeletedFalse(equipmentIds.capture());
        assertThat(sparePartIds.getValue()).containsExactlyInAnyOrder(first.getSparePartId(), second.getSparePartId());
        assertThat(equipmentIds.getValue()).containsExactlyInAnyOrder(first.getEquipmentId(), second.getEquipmentId());
    }

    @Test
    void listReturnsNullEquipmentNameForCatalogScope() {
        UUID partId = UUID.randomUUID();
        SparePartLifeRule rule = rule(partId, null);
        SparePart part = new SparePart();
        part.setId(partId);
        part.setName("Podshipnik 6205");
        Pageable pageable = PageRequest.of(0, 20);
        when(ruleRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(rule), pageable, 1));
        when(limitRepository.findAllByRuleIdInAndIsDeletedFalseOrderBySequenceAsc(any())).thenReturn(List.of());
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(part));

        var dto = service.list(new com.toir.dto.sparepartlifecycle.SparePartLifeRuleFilter(
                partId, null, null, SparePartLifeRuleScope.CATALOG, true, null), pageable).getContent().get(0);

        assertThat(dto.equipmentId()).isNull();
        assertThat(dto.equipmentName()).isNull();
        verify(equipmentRepository, never()).findAllByIdInAndIsDeletedFalse(any());
    }

    @Test
    void listReturnsNullNameWhenReferencedEntityIsSoftDeleted() {
        SparePartLifeRule rule = rule(UUID.randomUUID(), UUID.randomUUID());
        Pageable pageable = PageRequest.of(0, 20);
        when(ruleRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(rule), pageable, 1));
        when(limitRepository.findAllByRuleIdInAndIsDeletedFalseOrderBySequenceAsc(any())).thenReturn(List.of());
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of());
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of());

        var dto = service.list(new com.toir.dto.sparepartlifecycle.SparePartLifeRuleFilter(
                null, null, null, null, null, null), pageable).getContent().get(0);

        assertThat(dto.sparePartId()).isEqualTo(rule.getSparePartId());
        assertThat(dto.sparePartName()).isNull();
        assertThat(dto.equipmentId()).isEqualTo(rule.getEquipmentId());
        assertThat(dto.equipmentName()).isNull();
    }

    @Test
    void listPreservesFiltersSortingAndTotalElements() {
        UUID partId = UUID.randomUUID();
        com.toir.dto.sparepartlifecycle.SparePartLifeRuleFilter filter =
                new com.toir.dto.sparepartlifecycle.SparePartLifeRuleFilter(
                        partId, null, null, SparePartLifeRuleScope.CATALOG, true,
                        Instant.parse("2026-07-01T00:00:00Z"));
        Pageable pageable = PageRequest.of(1, 5, Sort.by(Sort.Direction.ASC, "revision"));
        SparePartLifeRule rule = rule(partId, null);
        when(ruleRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(rule), pageable, 13));
        when(limitRepository.findAllByRuleIdInAndIsDeletedFalseOrderBySequenceAsc(any())).thenReturn(List.of());
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of());

        var result = service.list(filter, pageable);

        verify(ruleRepository).findAll(any(Specification.class), eq(pageable));
        assertThat(result.getTotalElements()).isEqualTo(13);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getSort().getOrderFor("revision").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void createsNodeSlotRuleWithDerivedScopeRevisionAndOrderedLimits() {
        UUID partId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        SparePartLifeRuleRequest request = request(partId, equipmentId, nodeId, "front left");
        SparePart part = new SparePart();
        part.setId(partId);
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        EquipmentNode node = new EquipmentNode();
        node.setId(nodeId);
        node.setEquipmentId(equipmentId);
        when(sparePartRepository.findByIdAndIsDeletedFalse(partId)).thenReturn(Optional.of(part));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(equipmentNodeRepository.findByIdAndIsDeletedFalse(nodeId)).thenReturn(Optional.of(node));
        when(slotNormalizer.normalizeNullable("front left")).thenReturn("FRONT_LEFT");
        when(ruleRepository.findAllBySparePartIdAndActiveTrueAndIsDeletedFalse(partId)).thenReturn(List.of());
        when(ruleRepository.save(any(SparePartLifeRule.class))).thenAnswer(invocation -> {
            SparePartLifeRule saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(limitRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create(request);

        assertThat(created.scopeType()).isEqualTo(SparePartLifeRuleScope.NODE_SLOT);
        assertThat(created.normalizedSlotCode()).isEqualTo("FRONT_LEFT");
        assertThat(created.revision()).isEqualTo(1);
        assertThat(created.limits()).extracting(limit -> limit.sequence()).containsExactly(0);
        verify(ruleRepository).save(any(SparePartLifeRule.class));
        verify(limitRepository).saveAll(any());
    }

    @Test
    void rejectsNodeThatDoesNotBelongToEquipment() {
        UUID partId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        SparePartLifeRuleRequest request = request(partId, equipmentId, nodeId, null);
        SparePart part = new SparePart();
        part.setId(partId);
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        EquipmentNode node = new EquipmentNode();
        node.setId(nodeId);
        node.setEquipmentId(UUID.randomUUID());
        when(sparePartRepository.findByIdAndIsDeletedFalse(partId)).thenReturn(Optional.of(part));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(equipmentNodeRepository.findByIdAndIsDeletedFalse(nodeId)).thenReturn(Optional.of(node));

        assertThatThrownBy(() -> service.create(request)).hasMessageStartingWith("RULE_NODE_MISMATCH:");
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void rejectsOverlappingActiveRuleAtTheSameExactScope() {
        UUID partId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        SparePartLifeRuleRequest request = request(partId, equipmentId, null, null);
        SparePartLifeRule existing = new SparePartLifeRule();
        existing.setId(UUID.randomUUID());
        existing.setSparePartId(partId);
        existing.setEquipmentId(equipmentId);
        existing.setScopeType(SparePartLifeRuleScope.EQUIPMENT);
        existing.setActive(true);
        existing.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
        existing.setEffectiveTo(Instant.parse("2027-01-01T00:00:00Z"));
        SparePart part = new SparePart();
        part.setId(partId);
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        when(sparePartRepository.findByIdAndIsDeletedFalse(partId)).thenReturn(Optional.of(part));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(slotNormalizer.normalizeNullable(null)).thenReturn(null);
        when(ruleRepository.findAllBySparePartIdAndActiveTrueAndIsDeletedFalse(partId))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.create(request)).hasMessageStartingWith("RULE_SCOPE_OVERLAP:");
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void reviseRejectsMovingEquipmentRuleToNodeScope() {
        UUID partId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        SparePartLifeRule previous = new SparePartLifeRule();
        previous.setId(ruleId);
        previous.setSparePartId(partId);
        previous.setEquipmentId(equipmentId);
        previous.setScopeType(SparePartLifeRuleScope.EQUIPMENT);
        when(ruleRepository.findByIdAndIsDeletedFalse(ruleId)).thenReturn(Optional.of(previous));
        // Bir xil part/equipment, ammo node qo'shilgan -> node scope. Bu rad etilishi kerak.
        SparePartLifeRuleRequest request = request(partId, equipmentId, nodeId, null);

        assertThatThrownBy(() -> service.revise(ruleId, request))
                .hasMessageStartingWith("RULE_REVISION_PART_IMMUTABLE:");
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void reviseKeepsExactScopeAndIncrementsRevisionForThatTuple() {
        UUID partId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        SparePartLifeRule previous = new SparePartLifeRule();
        previous.setId(ruleId);
        previous.setSparePartId(partId);
        previous.setEquipmentId(equipmentId);
        previous.setScopeType(SparePartLifeRuleScope.EQUIPMENT);
        previous.setActive(true);
        previous.setRevision(1);
        SparePart part = new SparePart();
        part.setId(partId);
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        when(ruleRepository.findByIdAndIsDeletedFalse(ruleId)).thenReturn(Optional.of(previous));
        when(sparePartRepository.findByIdAndIsDeletedFalse(partId)).thenReturn(Optional.of(part));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(slotNormalizer.normalizeNullable(null)).thenReturn(null);
        when(ruleRepository.findAllBySparePartIdAndActiveTrueAndIsDeletedFalse(partId)).thenReturn(List.of());
        when(ruleRepository.findAllBySparePartIdAndIsDeletedFalse(partId)).thenReturn(List.of(previous));
        when(ruleRepository.save(any(SparePartLifeRule.class))).thenAnswer(invocation -> {
            SparePartLifeRule saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(limitRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Bir xil equipment scope (node/slot yo'q), part o'zgarmaydi.
        SparePartLifeRuleRequest request = request(partId, equipmentId, null, null);
        var revised = service.revise(ruleId, request);

        assertThat(previous.isActive()).isFalse();
        assertThat(revised.scopeType()).isEqualTo(SparePartLifeRuleScope.EQUIPMENT);
        assertThat(revised.revision()).isEqualTo(2);
    }

    private static SparePartLifeRuleRequest request(UUID partId,
                                                    UUID equipmentId,
                                                    UUID nodeId,
                                                    String slot) {
        return new SparePartLifeRuleRequest(
                partId,
                equipmentId,
                nodeId,
                slot,
                SparePartLifeCombinationMode.ANY,
                SparePartDueAction.MAINTENANCE_REQUIRED,
                true,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-12-01T00:00:00Z"),
                "Seal life",
                null,
                List.of(new SparePartLifeLimitRequest(
                        SparePartLifeLimitKind.CALENDAR,
                        SparePartCalendarUnit.MONTH,
                        null,
                        null,
                        new BigDecimal("6"),
                        BigDecimal.ONE,
                        0
                ))
        );
    }

    private static SparePartLifeRule rule(UUID partId, UUID equipmentId) {
        SparePartLifeRule rule = new SparePartLifeRule();
        rule.setId(UUID.randomUUID());
        rule.setSparePartId(partId);
        rule.setEquipmentId(equipmentId);
        rule.setScopeType(equipmentId == null
                ? SparePartLifeRuleScope.CATALOG
                : SparePartLifeRuleScope.EQUIPMENT);
        rule.setCombinationMode(SparePartLifeCombinationMode.ANY);
        rule.setDueAction(SparePartDueAction.WARNING_ONLY);
        rule.setActive(true);
        rule.setRevision(1);
        rule.setName("Rule name");
        rule.setCreatedAt(Instant.parse("2026-07-01T00:00:00Z"));
        rule.setUpdatedAt(Instant.parse("2026-07-01T00:00:00Z"));
        return rule;
    }

    private static SparePartLifeLimit limit(UUID ruleId, String value, String warning) {
        SparePartLifeLimit limit = new SparePartLifeLimit();
        limit.setId(UUID.randomUUID());
        limit.setRuleId(ruleId);
        limit.setLimitKind(SparePartLifeLimitKind.METER);
        limit.setLimitValue(new BigDecimal(value));
        limit.setWarningBeforeValue(new BigDecimal(warning));
        limit.setSequence(0);
        return limit;
    }
}
