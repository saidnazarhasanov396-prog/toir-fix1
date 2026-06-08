package com.toir.entity.maintenance;

import com.toir.entity.BaseEntity;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "safety_checklist_templates")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SafetyChecklistTemplate extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_order_type")
    private WorkOrderType workOrderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type")
    private WorkType workType;

    @Column(nullable = false)
    private boolean active = true;

    @Column(columnDefinition = "text")
    private String description;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SafetyChecklistTemplateItem> items = new ArrayList<>();
}
