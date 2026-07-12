package com.toir.service.repair;

import com.toir.dto.repaircampaign.*;
import com.toir.entity.repair.*;
import com.toir.enums.*;
import com.toir.exception.RestException;
import com.toir.repository.repair.*;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class RepairCampaignDependencyPolicy {
    private final RepairCampaignRepository campaigns;
    private final RepairCampaignWorkItemRepository items;
    private final RepairCampaignWorkDependencyRepository dependencies;
    private final ScopeAccessService scope;
    private final AuditBuilderService audit;
    private final RepairCampaignMutationImpactService mutationImpactService;

    @Transactional(readOnly=true)
    public List<RepairCampaignDependencyResponse> list(UUID campaignId) {
        var c = find(campaignId, false);
        return dependencies.findAllByRepairCampaignIdAndIsDeletedFalseOrderByPredecessorWorkItemIdAscSuccessorWorkItemIdAsc(campaignId)
                .stream().map(d -> response(d, c.getVersion(), true)).toList();
    }

    @Transactional
    public RepairCampaignDependencyResponse add(UUID campaignId, RepairCampaignDependencyRequest r) {
        var c=find(campaignId,true); version(c,r.version()); mutable(c);
        if (r.predecessorId().equals(r.successorId())) throw RestException.badRequest("DEPENDENCY_SELF_EDGE");
        item(campaignId,r.predecessorId()); item(campaignId,r.successorId());
        var all=dependencies.findAllByRepairCampaignIdAndIsDeletedFalseOrderByPredecessorWorkItemIdAscSuccessorWorkItemIdAsc(campaignId);
        if (cycle(all,r.predecessorId(),r.successorId())) throw RestException.conflict("DEPENDENCY_CYCLE");
        var d=dependencies.findByRepairCampaignIdAndPredecessorWorkItemIdAndSuccessorWorkItemId(campaignId,r.predecessorId(),r.successorId()).orElseGet(RepairCampaignWorkDependency::new);
        if(d.getId()!=null&&!d.isDeleted()) throw RestException.conflict("DEPENDENCY_DUPLICATE");
        mutationImpactService.apply(c, RepairCampaignMutationType.DEPENDENCIES);
        d.setRepairCampaignId(campaignId); d.setPredecessorWorkItemId(r.predecessorId()); d.setSuccessorWorkItemId(r.successorId()); d.setDeleted(false);
        try { dependencies.saveAndFlush(d); } catch(DataIntegrityViolationException e) {
            if(messages(e).contains("uq_repair_campaign_dependencies_active_edge")) throw RestException.conflict("DEPENDENCY_DUPLICATE"); throw e;
        }
        long v=touch(c); var out=response(d,v,true);
        audit.log("repair_campaign_dependency",d.getId().toString(),AuditAction.CREATE,AuditModule.REPAIR_CAMPAIGN,"Dependency added",null,out);
        return out;
    }

    @Transactional
    public RepairCampaignDependencyResponse remove(UUID campaignId,UUID id,Long v) {
        var c=find(campaignId,true); version(c,v); mutable(c);
        var d=dependencies.findByIdAndRepairCampaignIdAndIsDeletedFalse(id,campaignId).orElseThrow(()->RestException.notFound("Dependency not found"));
        var before=response(d,c.getVersion(),true); mutationImpactService.apply(c, RepairCampaignMutationType.DEPENDENCIES); d.setDeleted(true); dependencies.saveAndFlush(d);
        var out=response(d,touch(c),false); audit.log("repair_campaign_dependency",id.toString(),AuditAction.DELETE,AuditModule.REPAIR_CAMPAIGN,"Dependency removed",before,out); return out;
    }

    public List<String> blockers(UUID campaignId) {
        var ds=dependencies.findAllByRepairCampaignIdAndIsDeletedFalseOrderByPredecessorWorkItemIdAscSuccessorWorkItemIdAsc(campaignId);
        Map<UUID,RepairCampaignWorkItemStatus> states=new HashMap<>();
        items.findAllByCampaignIdAndIsDeletedFalseOrderByOrderNumberAsc(campaignId).forEach(i->states.put(i.getId(),i.getStatus()));
        List<String>b=new ArrayList<>(); if(cycle(ds,null,null))b.add("DEPENDENCY_CYCLE");
        ds.stream().filter(d->states.get(d.getPredecessorWorkItemId())!=RepairCampaignWorkItemStatus.COMPLETED)
                .filter(d->states.get(d.getSuccessorWorkItemId())==RepairCampaignWorkItemStatus.IN_PROGRESS
                        || states.get(d.getSuccessorWorkItemId())==RepairCampaignWorkItemStatus.COMPLETED)
                .forEach(d->b.add("PREDECESSOR_INCOMPLETE:"+d.getPredecessorWorkItemId()+":"+d.getSuccessorWorkItemId()));
        return b.stream().sorted().toList();
    }
    public boolean assigned(UUID campaignId,UUID itemId){return dependencies.existsByRepairCampaignIdAndPredecessorWorkItemIdAndIsDeletedFalse(campaignId,itemId)||dependencies.existsByRepairCampaignIdAndSuccessorWorkItemIdAndIsDeletedFalse(campaignId,itemId);}
    private RepairCampaign find(UUID id,boolean lock){var c=(lock?campaigns.findLockedByIdAndIsDeletedFalse(id):campaigns.findByIdAndIsDeletedFalse(id)).orElseThrow(()->RestException.notFound("Campaign not found"));scope.assertCanAccessDepartment(c.getDepartmentId());return c;}
    private void item(UUID c,UUID id){items.findByIdAndCampaignIdAndIsDeletedFalse(id,c).orElseThrow(()->RestException.badRequest("DEPENDENCY_FOREIGN_ITEM"));}
    private static void version(RepairCampaign c,Long v){if(v==null||!Objects.equals(v,c.getVersion()))throw RestException.conflict("CAMPAIGN_VERSION_CONFLICT");}
    private static void mutable(RepairCampaign c){if(c.getStatus().ordinal()>RepairCampaignStatus.PREPARATION.ordinal()||(c.getStatus().ordinal()>=RepairCampaignStatus.APPROVED.ordinal()&&c.getApprovalScopeHash()==null))throw RestException.conflict("CAMPAIGN_PLANNING_FROZEN");if(c.getStatus()==RepairCampaignStatus.PENDING_APPROVAL&&c.getApprovalScopeHash()==null)throw RestException.conflict("CAMPAIGN_PLANNING_APPROVAL_INVALIDATION_REQUIRED");}
    private long touch(RepairCampaign c){c.setUpdatedAt(Instant.now());return campaigns.saveAndFlush(c).getVersion();}
    private static RepairCampaignDependencyResponse response(RepairCampaignWorkDependency d,long v,boolean a){return new RepairCampaignDependencyResponse(d.getId(),d.getRepairCampaignId(),d.getPredecessorWorkItemId(),d.getSuccessorWorkItemId(),v,a);}
    static boolean cycle(List<RepairCampaignWorkDependency> rows,UUID p,UUID s){Map<UUID,List<UUID>>g=new TreeMap<>();rows.forEach(d->g.computeIfAbsent(d.getPredecessorWorkItemId(),x->new ArrayList<>()).add(d.getSuccessorWorkItemId()));if(p!=null)g.computeIfAbsent(p,x->new ArrayList<>()).add(s);Set<UUID>seen=new HashSet<>(),stack=new HashSet<>();for(UUID n:g.keySet())if(dfs(n,g,seen,stack))return true;return false;}
    private static boolean dfs(UUID n,Map<UUID,List<UUID>>g,Set<UUID>seen,Set<UUID>stack){if(stack.contains(n))return true;if(!seen.add(n))return false;stack.add(n);for(UUID x:g.getOrDefault(n,List.of()))if(dfs(x,g,seen,stack))return true;stack.remove(n);return false;}
    private static String messages(Throwable e){StringBuilder s=new StringBuilder();for(Throwable x=e;x!=null;x=x.getCause())s.append(' ').append(x.getMessage());return s.toString();}
}
