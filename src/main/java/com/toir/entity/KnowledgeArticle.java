package com.toir.entity;

import com.toir.persistence.StringListJsonConverter;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * База знаний / lessons learned: уроки, извлечённые из расследований
 * отказов, типовые решения, чек-листы по обслуживанию.
 * ТЗ §4.2.13 — основа для переиспользования опыта RCA.
 */
@Entity
@Table(name = "knowledge_articles")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class KnowledgeArticle extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    /** LESSON_LEARNED / KB / PROCEDURE / TROUBLESHOOTING. */
    @Column(nullable = false)
    private String kind = "LESSON_LEARNED";

    @Column(name = "equipment_type_id")
    private UUID equipmentTypeId;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "defect_id")
    private UUID defectId;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Column(nullable = false, columnDefinition = "text")
    private String problem;

    @Column(nullable = false, columnDefinition = "text")
    private String rootCause;

    @Column(nullable = false, columnDefinition = "text")
    private String solution;

    @Column(name = "preventive_actions", columnDefinition = "text")
    private String preventiveActions;

    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "jsonb")
    private List<String> tags;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

}
