package com.toir.entity;
import com.toir.enums.EquipmentNodeType;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "equipment_nodes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"equipment_id", "code"}))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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

}
