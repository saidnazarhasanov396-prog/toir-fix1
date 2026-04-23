package com.toir.entity;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

/**
 * База знаний / lessons learned: уроки, извлечённые из расследований
 * отказов, типовые решения, чек-листы по обслуживанию.
 * ТЗ §4.2.13 — основа для переиспользования опыта RCA.
 */
@Entity
@Table(name = "knowledge_articles")
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> tags;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public UUID getEquipmentTypeId() { return equipmentTypeId; }
    public void setEquipmentTypeId(UUID equipmentTypeId) { this.equipmentTypeId = equipmentTypeId; }
    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public UUID getDefectId() { return defectId; }
    public void setDefectId(UUID defectId) { this.defectId = defectId; }
    public UUID getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(UUID workOrderId) { this.workOrderId = workOrderId; }
    public String getProblem() { return problem; }
    public void setProblem(String problem) { this.problem = problem; }
    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public String getSolution() { return solution; }
    public void setSolution(String solution) { this.solution = solution; }
    public String getPreventiveActions() { return preventiveActions; }
    public void setPreventiveActions(String preventiveActions) { this.preventiveActions = preventiveActions; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public UUID getAuthorId() { return authorId; }
    public void setAuthorId(UUID authorId) { this.authorId = authorId; }
    public int getViewCount() { return viewCount; }
    public void setViewCount(int viewCount) { this.viewCount = viewCount; }
}
