package com.toir.entity.repair;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "repair_campaign_material_requirements")
@Getter
@Setter
public class RepairCampaignMaterialRequirement extends BaseEntity {
    @Column(name="repair_campaign_id",nullable=false) private UUID repairCampaignId;
    @Column(name="work_item_id",nullable=false) private UUID workItemId;
    @Column(name="spare_part_id",nullable=false) private UUID sparePartId;
    @Column(name="warehouse_id",nullable=false) private UUID warehouseId;
    @Column(name="required_quantity",nullable=false,precision=19,scale=4) private BigDecimal requiredQuantity;
    @Column(nullable=false) private boolean critical;
    @Column(name="procurement_required",nullable=false) private boolean procurementRequired;
}
