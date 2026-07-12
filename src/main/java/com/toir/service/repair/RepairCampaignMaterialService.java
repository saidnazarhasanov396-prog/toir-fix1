package com.toir.service.repair;

import com.toir.dto.repaircampaign.RepairCampaignMaterialRequirementRequest;
import com.toir.dto.repaircampaign.RepairCampaignMaterialRequirementResponse;
import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.entity.repair.RepairCampaign;
import com.toir.entity.repair.RepairCampaignMaterialRequirement;
import com.toir.enums.*;
import com.toir.exception.RestException;
import com.toir.repository.*;
import com.toir.repository.maintenance.WorkOrderSparePartRequirementRepository;
import com.toir.repository.repair.*;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RepairCampaignMaterialService {
    private final RepairCampaignRepository campaigns;
    private final RepairCampaignWorkItemRepository items;
    private final RepairCampaignMaterialRequirementRepository requirements;
    private final SparePartRepository spareParts;
    private final WarehouseRepository warehouses;
    private final WarehouseStockBalanceRepository balances;
    private final WorkOrderSparePartRequirementRepository workRequirements;
    private final ReservationRepository reservations;
    private final ScopeAccessService scope;
    private final AuditBuilderService audit;

    @Transactional(readOnly=true)
    public List<RepairCampaignMaterialRequirementResponse> list(UUID campaignId){RepairCampaign c=find(campaignId,false);return rows(campaignId).stream().map(r->response(r,c.getVersion(),true)).toList();}

    @Transactional
    public RepairCampaignMaterialRequirementResponse add(UUID campaignId,RepairCampaignMaterialRequirementRequest request){RepairCampaign c=find(campaignId,true);validateMutation(c,request.version());validateRequest(campaignId,request);RepairCampaignMaterialRequirement r=new RepairCampaignMaterialRequirement();apply(r,campaignId,request);try{requirements.saveAndFlush(r);}catch(DataIntegrityViolationException e){if(messages(e).contains("uq_rc_material_active_identity"))throw RestException.conflict("MATERIAL_REQUIREMENT_DUPLICATE");throw e;}long version=touch(c);var out=response(r,version,true);audit.log("repair_campaign_material",r.getId().toString(),AuditAction.CREATE,AuditModule.REPAIR_CAMPAIGN,"Campaign material demand created",null,out);return out;}

    @Transactional
    public RepairCampaignMaterialRequirementResponse update(UUID campaignId,UUID id,RepairCampaignMaterialRequirementRequest request){RepairCampaign c=find(campaignId,true);validateMutation(c,request.version());RepairCampaignMaterialRequirement r=requirements.findByIdAndRepairCampaignIdAndIsDeletedFalse(id,campaignId).orElseThrow(()->RestException.notFound("Material requirement not found"));var before=response(r,c.getVersion(),true);validateRequest(campaignId,request);apply(r,campaignId,request);requirements.saveAndFlush(r);var out=response(r,touch(c),true);audit.log("repair_campaign_material",id.toString(),AuditAction.UPDATE,AuditModule.REPAIR_CAMPAIGN,"Campaign material demand updated",before,out);return out;}

    @Transactional
    public RepairCampaignMaterialRequirementResponse remove(UUID campaignId,UUID id,Long version){RepairCampaign c=find(campaignId,true);validateMutation(c,version);RepairCampaignMaterialRequirement r=requirements.findByIdAndRepairCampaignIdAndIsDeletedFalse(id,campaignId).orElseThrow(()->RestException.notFound("Material requirement not found"));var before=response(r,c.getVersion(),true);r.setDeleted(true);requirements.saveAndFlush(r);var out=response(r,touch(c),false);audit.log("repair_campaign_material",id.toString(),AuditAction.DELETE,AuditModule.REPAIR_CAMPAIGN,"Campaign material demand removed",before,out);return out;}

    @Transactional(readOnly=true)
    public List<String> blockers(UUID campaignId){find(campaignId,false);List<RepairCampaignMaterialRequirement> rows=rows(campaignId);List<String> blockers=new ArrayList<>();Set<UUID>covered=new HashSet<>();rows.forEach(r->covered.add(r.getWorkItemId()));items.findAllByCampaignIdAndIsDeletedFalseOrderByOrderNumberAsc(campaignId).stream().filter(i->!covered.contains(i.getId())).forEach(i->blockers.add("MATERIAL_REQUIREMENT_MISSING:"+i.getId()));for(RepairCampaignMaterialRequirement r:rows){if(r.isProcurementRequired())blockers.add("PROCUREMENT_REQUIRED:"+r.getId());if(r.isCritical()&&available(r).compareTo(r.getRequiredQuantity())<0)blockers.add("CRITICAL_MATERIAL_DEFICIT:"+r.getId());}List<UUID>ids=rows.stream().map(RepairCampaignMaterialRequirement::getId).sorted().toList();if(!ids.isEmpty())for(WorkOrderSparePartRequirement wr:workRequirements.findAllByCampaignRequirementIdInAndIsDeletedFalse(ids)){if(reservations.findAllByWorkOrderIdAndRequirementIdAndSparePartIdAndStatusAndIsDeletedFalse(wr.getWorkOrderId(),wr.getId(),wr.getSparePartId(),ReservationStatus.ACTIVE).size()>1)blockers.add("RESERVATION_DUPLICATE:"+wr.getId());}return blockers.stream().distinct().sorted().toList();}

    public boolean assigned(UUID campaignId,UUID itemId){return requirements.existsByRepairCampaignIdAndWorkItemIdAndIsDeletedFalse(campaignId,itemId);}
    private BigDecimal available(RepairCampaignMaterialRequirement r){return balances.findAllByWarehouseIdAndSparePartIdAndIsDeletedFalse(r.getWarehouseId(),r.getSparePartId()).stream().map(b->b.getAvailableQty()).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private void validateRequest(UUID campaignId,RepairCampaignMaterialRequirementRequest r){if(r==null||r.requiredQuantity()==null||r.requiredQuantity().signum()<=0||r.requiredQuantity().stripTrailingZeros().scale()>4||r.requiredQuantity().precision()-r.requiredQuantity().scale()>15)throw RestException.badRequest("MATERIAL_QUANTITY_INVALID");items.findByIdAndCampaignIdAndIsDeletedFalse(r.workItemId(),campaignId).orElseThrow(()->RestException.badRequest("MATERIAL_FOREIGN_ITEM"));spareParts.findByIdAndIsDeletedFalse(r.sparePartId()).orElseThrow(()->RestException.badRequest("MATERIAL_SPARE_PART_INVALID"));var warehouse=warehouses.findByIdAndIsDeletedFalse(r.warehouseId()).filter(w->w.isActive()).orElseThrow(()->RestException.badRequest("MATERIAL_WAREHOUSE_INVALID"));if(warehouse.getDepartmentId()!=null)scope.assertCanAccessDepartment(warehouse.getDepartmentId());}
    private void apply(RepairCampaignMaterialRequirement x,UUID campaignId,RepairCampaignMaterialRequirementRequest r){x.setRepairCampaignId(campaignId);x.setWorkItemId(r.workItemId());x.setSparePartId(r.sparePartId());x.setWarehouseId(r.warehouseId());x.setRequiredQuantity(r.requiredQuantity());x.setCritical(r.critical());x.setProcurementRequired(r.procurementRequired());x.setDeleted(false);}
    private RepairCampaign find(UUID id,boolean lock){RepairCampaign c=(lock?campaigns.findLockedByIdAndIsDeletedFalse(id):campaigns.findByIdAndIsDeletedFalse(id)).orElseThrow(()->RestException.notFound("Campaign not found"));scope.assertCanAccessDepartment(c.getDepartmentId());return c;}
    private static void validateMutation(RepairCampaign c,Long version){if(version==null||!Objects.equals(version,c.getVersion()))throw RestException.conflict("CAMPAIGN_VERSION_CONFLICT");if(c.getStatus()==RepairCampaignStatus.PENDING_APPROVAL)throw RestException.conflict("CAMPAIGN_MATERIAL_APPROVAL_INVALIDATION_REQUIRED");if(c.getStatus().ordinal()>=RepairCampaignStatus.APPROVED.ordinal())throw RestException.conflict("CAMPAIGN_MATERIAL_FROZEN");}
    private long touch(RepairCampaign c){c.setUpdatedAt(Instant.now());return campaigns.saveAndFlush(c).getVersion();}
    private List<RepairCampaignMaterialRequirement> rows(UUID id){return requirements.findAllByRepairCampaignIdAndIsDeletedFalseOrderByWorkItemIdAscSparePartIdAsc(id);}
    private static String messages(Throwable e){StringBuilder s=new StringBuilder();for(Throwable x=e;x!=null;x=x.getCause())s.append(' ').append(x.getMessage());return s.toString();}
    private static RepairCampaignMaterialRequirementResponse response(RepairCampaignMaterialRequirement r,long v,boolean active){return new RepairCampaignMaterialRequirementResponse(r.getId(),r.getRepairCampaignId(),r.getWorkItemId(),r.getSparePartId(),r.getWarehouseId(),r.getRequiredQuantity(),r.isCritical(),r.isProcurementRequired(),v,active);}
}
