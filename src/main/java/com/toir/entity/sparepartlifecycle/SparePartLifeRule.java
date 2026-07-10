package com.toir.entity.sparepartlifecycle;

import com.toir.entity.BaseEntity;
import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartLifeCombinationMode;
import com.toir.enums.sparepartlifecycle.SparePartLifeRuleScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "spare_part_life_rules")
@Getter
@Setter
public class SparePartLifeRule extends BaseEntity {

    @Column(name = "spare_part_id", nullable = false)
    private UUID sparePartId;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "equipment_node_id")
    private UUID equipmentNodeId;

    @Column(name = "normalized_slot_code", length = 128)
    private String normalizedSlotCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false)
    private SparePartLifeRuleScope scopeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "combination_mode", nullable = false)
    private SparePartLifeCombinationMode combinationMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "due_action", nullable = false)
    private SparePartDueAction dueAction;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(nullable = false, updatable = false)
    private int revision;

    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Version
    @Column(nullable = false)
    private long version;
}
