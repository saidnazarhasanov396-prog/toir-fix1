package com.toir.equipmentnode;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "equipment_nodes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"equipment_id", "code"}))
public class EquipmentNode extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false)
    private EquipmentNodeType nodeType;

    @Column(name = "serial_number")
    private String serialNumber;

    private String description;

    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public EquipmentNodeType getNodeType() { return nodeType; }
    public void setNodeType(EquipmentNodeType nodeType) { this.nodeType = nodeType; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
