package com.toir.entity.repair;
import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;
@Entity @Table(name="repair_campaign_work_dependencies") @Getter @Setter
public class RepairCampaignWorkDependency extends BaseEntity {
 @Column(name="repair_campaign_id",nullable=false) private UUID repairCampaignId;
 @Column(name="predecessor_work_item_id",nullable=false) private UUID predecessorWorkItemId;
 @Column(name="successor_work_item_id",nullable=false) private UUID successorWorkItemId;
}
