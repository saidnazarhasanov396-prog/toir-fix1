package com.toir.inspection;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inspection_round_results")
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

    public List<UUID> getPhotoFileIds() { return photoFileIds; }
    public void setPhotoFileIds(List<UUID> photoFileIds) { this.photoFileIds = photoFileIds; }

    public InspectionRound getRound() { return round; }
    public void setRound(InspectionRound round) { this.round = round; }
    public UUID getCheckpointId() { return checkpointId; }
    public void setCheckpointId(UUID checkpointId) { this.checkpointId = checkpointId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getMeasuredValue() { return measuredValue; }
    public void setMeasuredValue(Double measuredValue) { this.measuredValue = measuredValue; }
    public String getMeasuredUnit() { return measuredUnit; }
    public void setMeasuredUnit(String measuredUnit) { this.measuredUnit = measuredUnit; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public UUID getDefectId() { return defectId; }
    public void setDefectId(UUID defectId) { this.defectId = defectId; }
}
