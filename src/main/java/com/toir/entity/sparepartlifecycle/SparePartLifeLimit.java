package com.toir.entity.sparepartlifecycle;

import com.toir.entity.BaseEntity;
import com.toir.enums.MeterType;
import com.toir.enums.sparepartlifecycle.SparePartCalendarUnit;
import com.toir.enums.sparepartlifecycle.SparePartLifeLimitKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "spare_part_life_limits")
@Getter
@Setter
public class SparePartLifeLimit extends BaseEntity {

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "limit_kind", nullable = false)
    private SparePartLifeLimitKind limitKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "calendar_unit")
    private SparePartCalendarUnit calendarUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "meter_type")
    private MeterType meterType;

    @Column(name = "explicit_equipment_meter_id")
    private UUID explicitEquipmentMeterId;

    @Column(name = "limit_value", nullable = false, precision = 19, scale = 6)
    private BigDecimal limitValue;

    @Column(name = "warning_before_value", precision = 19, scale = 6)
    private BigDecimal warningBeforeValue;

    @Column(nullable = false)
    private int sequence;
}
