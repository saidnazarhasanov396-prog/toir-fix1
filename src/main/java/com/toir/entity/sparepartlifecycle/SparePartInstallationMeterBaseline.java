package com.toir.entity.sparepartlifecycle;

import com.toir.entity.BaseEntity;
import com.toir.enums.MeterType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "spare_part_installation_meter_baselines")
@Getter
@Setter
public class SparePartInstallationMeterBaseline extends BaseEntity {

    @Column(name = "installation_id", nullable = false, updatable = false)
    private UUID installationId;

    @Column(name = "equipment_meter_id", nullable = false, updatable = false)
    private UUID equipmentMeterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "meter_type", nullable = false, updatable = false)
    private MeterType meterType;

    @Column(name = "baseline_value", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal baselineValue;

    @Column(name = "baseline_recorded_at", nullable = false, updatable = false)
    private Instant baselineRecordedAt;

    @Column(name = "baseline_reading_id", updatable = false)
    private UUID baselineReadingId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rollover_context", columnDefinition = "jsonb", updatable = false)
    private String rolloverContext;
}
