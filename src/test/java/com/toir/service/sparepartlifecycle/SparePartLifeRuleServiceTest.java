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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
}
