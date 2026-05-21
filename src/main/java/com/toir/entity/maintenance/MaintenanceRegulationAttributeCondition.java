package com.toir.entity.maintenance;

import com.toir.entity.BaseEntity;
import com.toir.enums.MaintenanceRegulationConditionOperator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "maintenance_regulation_attribute_conditions")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MaintenanceRegulationAttributeCondition extends BaseEntity {

    @Column(name = "regulation_id", nullable = false)
    private UUID regulationId;

    @Column(name = "attribute_key", nullable = false)
    private String attributeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaintenanceRegulationConditionOperator operator;

    @Column(name = "value_text", columnDefinition = "text")
    private String valueText;

    @Column(name = "value_number")
    private Double valueNumber;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "value_boolean")
    private Boolean valueBoolean;

    @Column(name = "value_option")
    private String valueOption;
}
