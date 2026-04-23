package com.toir.entity;
import com.toir.entity.InspectionRoute;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "inspection_checkpoints")
public class InspectionCheckpoint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private InspectionRoute route;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String instruction;

    /** Тип проверки: VISUAL / MEASUREMENT / BOOLEAN / READING. */
    @Column(nullable = false)
    private String checkType = "VISUAL";

    /** Expected parameter, если checkType = READING/MEASUREMENT. */
    @Column(name = "expected_min")
    private Double expectedMin;

    @Column(name = "expected_max")
    private Double expectedMax;

    @Column(name = "expected_unit")
    private String expectedUnit;

    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory = true;

    public InspectionRoute getRoute() { return route; }
    public void setRoute(InspectionRoute route) { this.route = route; }
    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public UUID getLocationId() { return locationId; }
    public void setLocationId(UUID locationId) { this.locationId = locationId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
    public String getCheckType() { return checkType; }
    public void setCheckType(String checkType) { this.checkType = checkType; }
    public Double getExpectedMin() { return expectedMin; }
    public void setExpectedMin(Double expectedMin) { this.expectedMin = expectedMin; }
    public Double getExpectedMax() { return expectedMax; }
    public void setExpectedMax(Double expectedMax) { this.expectedMax = expectedMax; }
    public String getExpectedUnit() { return expectedUnit; }
    public void setExpectedUnit(String expectedUnit) { this.expectedUnit = expectedUnit; }
    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
}
