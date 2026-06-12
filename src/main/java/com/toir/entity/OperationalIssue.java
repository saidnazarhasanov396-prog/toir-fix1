package com.toir.entity;

import com.toir.enums.EquipmentRiskLevel;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueStatus;
import com.toir.enums.OperationalIssueType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "operational_issues")
@Getter
@Setter
public class OperationalIssue extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationalIssueType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationalIssueStatus status = OperationalIssueStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationSeverity severity = NotificationSeverity.INFO;

    @Enumerated(EnumType.STRING)
    @Column(name = "equipment_risk_level")
    private EquipmentRiskLevel equipmentRiskLevel;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String message;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
