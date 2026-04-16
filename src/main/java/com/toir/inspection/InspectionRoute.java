package com.toir.inspection;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Маршрут обхода — шаблон регулярного осмотра с набором чек-пунктов.
 * Оператор/механик обходит точки и фиксирует результаты в InspectionRound.
 */
@Entity
@Table(name = "inspection_routes")
public class InspectionRoute extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "department_id")
    private UUID departmentId;

    /** Периодичность: DAILY / SHIFT / WEEKLY / MONTHLY. */
    @Column(nullable = false)
    private String frequency = "DAILY";

    /** Целевое время выполнения, минуты. */
    @Column(name = "target_duration_min")
    private Integer targetDurationMin;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<InspectionCheckpoint> checkpoints = new ArrayList<>();

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public Integer getTargetDurationMin() { return targetDurationMin; }
    public void setTargetDurationMin(Integer targetDurationMin) { this.targetDurationMin = targetDurationMin; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<InspectionCheckpoint> getCheckpoints() { return checkpoints; }
    public void setCheckpoints(List<InspectionCheckpoint> checkpoints) { this.checkpoints = checkpoints; }
}
