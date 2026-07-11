package com.toir.entity.plannedshutdown;

import com.toir.entity.BaseEntity;
import com.toir.enums.PlannedShutdownItemStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "planned_shutdown_startup_tests")
@Getter @Setter
public class PlannedShutdownStartupTest extends BaseEntity {
    @Column(name = "planned_shutdown_id", nullable = false) private UUID plannedShutdownId;
    @Column(name = "test_key", nullable = false, length = 128) private String testKey;
    @Column(nullable = false, length = 500) private String title;
    @Column(nullable = false) private boolean mandatory;
    @Column(name = "acceptance_criteria", nullable = false, columnDefinition = "text") private String acceptanceCriteria;
    @Column(length = 64) private String unit;
    @Column(name = "order_number", nullable = false) private Integer orderNumber = 0;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PlannedShutdownItemStatus status = PlannedShutdownItemStatus.PENDING;
    @Column(name = "measured_value", precision = 19, scale = 4) private BigDecimal measuredValue;
    @Column(name = "result_unit", length = 64) private String resultUnit;
    @Column(columnDefinition = "text") private String evidence;
    @Column(name = "performed_by_id") private UUID performerId;
    @Column(name = "verified_by_id") private UUID verifierId;
    @Column(name = "verified_at") private Instant verifiedAt;
}
