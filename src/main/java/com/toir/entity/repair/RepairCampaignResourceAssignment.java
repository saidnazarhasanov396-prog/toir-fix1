package com.toir.entity.repair;
import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="repair_campaign_resource_assignments") @Getter @Setter
public class RepairCampaignResourceAssignment extends BaseEntity {
 @Column(name="repair_campaign_id",nullable=false) private UUID repairCampaignId;
 @Column(name="work_item_id",nullable=false) private UUID workItemId;
 @Column(name="employee_id") private UUID employeeId;
 @Column(name="brigade_id") private UUID brigadeId;
 @Column(name="counteragent_id") private UUID counteragentId;
 @Column(name="shift_code",nullable=false) private String shiftCode;
 @Column(name="planned_start_at",nullable=false) private Instant plannedStartAt;
 @Column(name="planned_end_at",nullable=false) private Instant plannedEndAt;
 @Column(name="competency_requirement") private String competencyRequirement;
}
