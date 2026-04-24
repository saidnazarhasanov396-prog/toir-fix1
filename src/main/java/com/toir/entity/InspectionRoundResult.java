package com.toir.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inspection_round_results")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InspectionRoundResult extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private InspectionRound round;

    @Column(name = "checkpoint_id", nullable = false)
    private UUID checkpointId;

    /** OK / WARN / FAIL / SKIP. */
    @Column(nullable = false)
    private String status = "OK";

    @Column(name = "measured_value")
    private Double measuredValue;

    @Column(name = "measured_unit")
    private String measuredUnit;

    @Column(columnDefinition = "text")
    private String comment;

    /** Автоматически созданная defect/repair-request по этому пункту. */
    @Column(name = "defect_id")
    private UUID defectId;

    /** Фото/файлы, прикреплённые к результату чек-пункта. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "photo_file_ids", columnDefinition = "jsonb")
    private List<UUID> photoFileIds;

}
