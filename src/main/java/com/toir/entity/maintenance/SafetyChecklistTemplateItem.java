package com.toir.entity.maintenance;

import com.toir.entity.BaseEntity;
import com.toir.enums.SafetyChecklistCategory;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "safety_checklist_template_items")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SafetyChecklistTemplateItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private SafetyChecklistTemplate template;

    @Column(nullable = false)
    private int sequence;

    @Column(nullable = false)
    private String label;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SafetyChecklistCategory category = SafetyChecklistCategory.OTHER;

    @Column(nullable = false)
    private boolean critical;

    @Column(name = "requires_comment", nullable = false)
    private boolean requiresComment;

    @Column(nullable = false)
    private boolean active = true;
}
