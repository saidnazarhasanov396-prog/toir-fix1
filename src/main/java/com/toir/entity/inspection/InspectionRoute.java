package com.toir.entity.inspection;
import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Маршрут обхода — шаблон регулярного осмотра с набором чек-пунктов.
 * Оператор/механик обходит точки и фиксирует результаты в InspectionRound.
 */
@Entity
@Table(name = "inspection_routes")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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
}
