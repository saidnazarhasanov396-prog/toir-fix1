package com.toir.service.repair;

import com.toir.dto.repaircampaign.RepairCampaignWorkItemRequest;
import com.toir.dto.repaircampaign.RepairCampaignWorkItemResponse;
import com.toir.entity.repair.RepairCampaign;
import com.toir.entity.repair.RepairCampaignWorkItem;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.RepairCampaignStatus;
import com.toir.enums.RepairCampaignWorkItemSourceType;
import com.toir.enums.RepairCampaignWorkItemStatus;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairCampaignDepartmentRepository;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.repository.repair.RepairCampaignWorkItemRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RepairCampaignWorkItemService {

    private static final Set<RepairCampaignStatus> FROZEN = Set.of(
            RepairCampaignStatus.APPROVED, RepairCampaignStatus.PREPARATION,
            RepairCampaignStatus.IN_PROGRESS, RepairCampaignStatus.SUSPENDED,
            RepairCampaignStatus.COMPLETED, RepairCampaignStatus.CLOSING,
            RepairCampaignStatus.CLOSED, RepairCampaignStatus.CANCELLED);

    private final RepairCampaignRepository campaignRepository;
    private final RepairCampaignDepartmentRepository departmentRepository;
    private final RepairCampaignWorkItemRepository repository;
    private final EquipmentRepository equipmentRepository;
    private final CanonicalWorkSourceResolver sourceResolver;
    private final AuditBuilderService auditBuilderService;
    private final RepairCampaignDependencyPolicy dependencyPolicy;
    private final RepairCampaignResourcePolicy resourcePolicy;
    private final RepairCampaignMaterialService materialService;
    private final RepairCampaignMutationImpactService mutationImpactService;

    public List<com.toir.dto.repaircampaign.RepairCampaignDependencyResponse> listDependencies(UUID campaignId) { return dependencyPolicy.list(campaignId); }
    public com.toir.dto.repaircampaign.RepairCampaignDependencyResponse addDependency(UUID campaignId, com.toir.dto.repaircampaign.RepairCampaignDependencyRequest r) { return dependencyPolicy.add(campaignId, r); }
    public com.toir.dto.repaircampaign.RepairCampaignDependencyResponse removeDependency(UUID campaignId, UUID id, Long version) { return dependencyPolicy.remove(campaignId, id, version); }
    public List<com.toir.dto.repaircampaign.RepairCampaignResourceResponse> listResources(UUID campaignId) { return resourcePolicy.list(campaignId); }
    public com.toir.dto.repaircampaign.RepairCampaignResourceResponse assignResource(UUID campaignId, com.toir.dto.repaircampaign.RepairCampaignResourceRequest r) { return resourcePolicy.add(campaignId, r); }
    public com.toir.dto.repaircampaign.RepairCampaignResourceResponse removeResource(UUID campaignId, UUID id, Long version) { return resourcePolicy.remove(campaignId, id, version); }
    public com.toir.dto.repaircampaign.RepairCampaignPlanningAssessment assessPlanning(UUID campaignId) {
        RepairCampaign c = find(campaignId); dependencyPolicy.list(campaignId); List<String> blockers = new ArrayList<>();
        blockers.addAll(dependencyPolicy.blockers(campaignId)); blockers.addAll(resourcePolicy.blockers(campaignId)); blockers.addAll(materialService.blockers(campaignId));
        return new com.toir.dto.repaircampaign.RepairCampaignPlanningAssessment(campaignId, c.getVersion(),
                blockers.stream().sorted().toList(), c.getStatus() == RepairCampaignStatus.PENDING_APPROVAL);
    }

    @Transactional(readOnly = true)
    public List<RepairCampaignWorkItemResponse> list(UUID campaignId) {
        RepairCampaign campaign = find(campaignId);
        return repository.findAllByCampaignIdAndIsDeletedFalseOrderByOrderNumberAsc(campaignId).stream()
                .map(item -> response(item, campaign.getVersion())).toList();
    }

    @Transactional
    public RepairCampaignWorkItemResponse add(UUID campaignId, RepairCampaignWorkItemRequest request) {
        RepairCampaign campaign = findLocked(campaignId);
        requireVersion(campaign, request.version());
        requireMutable(campaign);
        ResolvedInput input = resolveInput(campaign, request);
        requireAvailable(campaignId, null, request.sourceType(), request.sourceId(), request.orderNumber());
        mutationImpactService.apply(campaign, com.toir.enums.RepairCampaignMutationType.WORK_ITEMS);
        RepairCampaignWorkItem item = new RepairCampaignWorkItem();
        item.setCampaign(campaign);
        apply(item, request, input);
        item.setStatus(RepairCampaignWorkItemStatus.PENDING);
        save(item);
        Long version = touch(campaign);
        RepairCampaignWorkItemResponse result = response(item, version);
        auditBuilderService.log("repair_campaign_work_item", item.getId().toString(), AuditAction.CREATE,
                AuditModule.REPAIR_CAMPAIGN, "Источник работ добавлен в ремонтную кампанию", null, result);
        return result;
    }

    @Transactional
    public RepairCampaignWorkItemResponse update(
            UUID campaignId, UUID itemId, RepairCampaignWorkItemRequest request) {
        RepairCampaign campaign = findLocked(campaignId);
        requireVersion(campaign, request.version());
        RepairCampaignWorkItem item = findItem(campaignId, itemId);
        boolean frozen = FROZEN.contains(campaign.getStatus())
                && (campaign.getStatus().ordinal() > RepairCampaignStatus.PREPARATION.ordinal()
                || campaign.getApprovalScopeHash() == null);
        if (frozen && identityChanged(item, request)) throw RestException.conflict("CAMPAIGN_SCOPE_FROZEN");
        ResolvedInput input = frozen
                ? new ResolvedInput(item.getEquipmentId(), item.getSourceType() == RepairCampaignWorkItemSourceType.MANUAL
                        ? requireManualTitle(request.title()) : item.getTitle())
                : resolveInput(campaign, request);
        requireAvailable(campaignId, itemId, request.sourceType(), request.sourceId(), request.orderNumber());
        mutationImpactService.apply(campaign, com.toir.enums.RepairCampaignMutationType.WORK_ITEMS);
        RepairCampaignWorkItemResponse before = response(item, campaign.getVersion());
        apply(item, request, input);
        save(item);
        Long version = touch(campaign);
        RepairCampaignWorkItemResponse result = response(item, version);
        auditBuilderService.log("repair_campaign_work_item", itemId.toString(), AuditAction.UPDATE,
                AuditModule.REPAIR_CAMPAIGN, "Источник работ ремонтной кампании обновлён", before, result);
        return result;
    }

    @Transactional
    public void remove(UUID campaignId, UUID itemId, Long version) {
        RepairCampaign campaign = findLocked(campaignId);
        requireVersion(campaign, version);
        requireMutable(campaign);
        RepairCampaignWorkItem item = findItem(campaignId, itemId);
        if (dependencyPolicy.assigned(campaignId, itemId) || resourcePolicy.assigned(campaignId, itemId)
                || materialService.assigned(campaignId, itemId)) {
            throw RestException.conflict("CAMPAIGN_WORK_ITEM_PLANNING_LINKED");
        }
        mutationImpactService.apply(campaign, com.toir.enums.RepairCampaignMutationType.WORK_ITEMS);
        RepairCampaignWorkItemResponse before = response(item, campaign.getVersion());
        item.setDeleted(true);
        repository.saveAndFlush(item);
        Long newVersion = touch(campaign);
        auditBuilderService.log("repair_campaign_work_item", itemId.toString(), AuditAction.DELETE,
                AuditModule.REPAIR_CAMPAIGN, "Источник работ удалён из ремонтной кампании", before,
                Map.of("campaignVersion", newVersion));
    }

    @Transactional
    public List<RepairCampaignWorkItemResponse> reorder(UUID campaignId, List<UUID> itemIds, Long version) {
        RepairCampaign campaign = findLocked(campaignId);
        requireVersion(campaign, version);
        requireMutable(campaign);
        List<RepairCampaignWorkItem> items = repository
                .findAllByCampaignIdAndIsDeletedFalseOrderByOrderNumberAsc(campaignId);
        if (itemIds == null || itemIds.size() != items.size()
                || new HashSet<>(itemIds).size() != items.size()) {
            throw RestException.badRequest("Reorder must contain every active campaign work item exactly once");
        }
        Map<UUID, RepairCampaignWorkItem> byId = new HashMap<>();
        items.forEach(item -> byId.put(item.getId(), item));
        if (!byId.keySet().equals(new HashSet<>(itemIds))) {
            throw RestException.badRequest("Reorder contains a work item outside this campaign");
        }
        mutationImpactService.apply(campaign, com.toir.enums.RepairCampaignMutationType.WORK_ITEMS);
        List<RepairCampaignWorkItemResponse> before = items.stream()
                .map(item -> response(item, campaign.getVersion())).toList();
        int temporary = items.stream().mapToInt(RepairCampaignWorkItem::getOrderNumber).max().orElse(0)
                + items.size() + 1;
        for (int i = 0; i < itemIds.size(); i++) byId.get(itemIds.get(i)).setOrderNumber(temporary + i);
        repository.saveAllAndFlush(items);
        for (int i = 0; i < itemIds.size(); i++) byId.get(itemIds.get(i)).setOrderNumber(i);
        repository.saveAllAndFlush(items);
        Long newVersion = touch(campaign);
        List<RepairCampaignWorkItemResponse> result = new ArrayList<>();
        for (UUID id : itemIds) result.add(response(byId.get(id), newVersion));
        auditBuilderService.log("repair_campaign_work_items", campaignId.toString(), AuditAction.UPDATE,
                AuditModule.REPAIR_CAMPAIGN, "Порядок работ ремонтной кампании изменён", before, result);
        return result;
    }

    private ResolvedInput resolveInput(RepairCampaign campaign, RepairCampaignWorkItemRequest request) {
        if (request == null || request.sourceType() == null || request.equipmentId() == null
                || request.orderNumber() == null || request.orderNumber() < 0) {
            throw RestException.badRequest("Invalid campaign work item request");
        }
        if (request.sourceType() == RepairCampaignWorkItemSourceType.MANUAL) {
            if (request.sourceId() != null) throw RestException.badRequest("MANUAL work item sourceId must be null");
            if (request.title() == null || request.title().isBlank()) {
                throw RestException.badRequest("MANUAL work item title is required");
            }
            var equipment = equipmentRepository.findByIdAndIsDeletedFalse(request.equipmentId())
                    .orElseThrow(() -> RestException.notFound("Campaign work item equipment not found: " + request.equipmentId()));
            if (!campaignDepartments(campaign).isEmpty()
                    && !campaignDepartments(campaign).contains(equipment.getDepartmentId())) {
                throw RestException.badRequest("CAMPAIGN_WORK_SOURCE_FOREIGN_DEPARTMENT");
            }
            return new ResolvedInput(request.equipmentId(), request.title().trim());
        }
        if (request.sourceId() == null) throw RestException.badRequest("Canonical work item sourceId is required");
        var source = sourceResolver.resolve(request.sourceType(), request.sourceId(),
                new CanonicalWorkSourceResolver.ResolutionScope(
                        request.equipmentId(), campaignDepartments(campaign)));
        return new ResolvedInput(source.equipmentId(), source.title());
    }

    private Set<UUID> campaignDepartments(RepairCampaign campaign) {
        Set<UUID> departments = new HashSet<>();
        if (campaign.getDepartmentId() != null) departments.add(campaign.getDepartmentId());
        departmentRepository.findAllByCampaignIdAndIsDeletedFalse(campaign.getId()).stream()
                .map(d -> d.getDepartmentId()).forEach(departments::add);
        return departments;
    }

    private void requireAvailable(UUID campaignId, UUID itemId, RepairCampaignWorkItemSourceType sourceType,
                                  UUID sourceId, Integer order) {
        boolean duplicateSource = sourceId != null && (itemId == null
                ? repository.existsByCampaignIdAndSourceTypeAndSourceIdAndIsDeletedFalse(campaignId, sourceType, sourceId)
                : repository.existsByCampaignIdAndSourceTypeAndSourceIdAndIdNotAndIsDeletedFalse(
                        campaignId, sourceType, sourceId, itemId));
        if (duplicateSource) throw RestException.conflict("CAMPAIGN_WORK_SOURCE_DUPLICATE");
        boolean duplicateOrder = itemId == null
                ? repository.existsByCampaignIdAndOrderNumberAndIsDeletedFalse(campaignId, order)
                : repository.existsByCampaignIdAndOrderNumberAndIdNotAndIsDeletedFalse(campaignId, order, itemId);
        if (duplicateOrder) throw RestException.conflict("CAMPAIGN_WORK_ORDER_DUPLICATE");
    }

    private static void apply(RepairCampaignWorkItem item, RepairCampaignWorkItemRequest request, ResolvedInput input) {
        item.setSourceType(request.sourceType());
        item.setSourceId(request.sourceId());
        item.setEquipmentId(input.equipmentId());
        item.setTitle(input.title());
        item.setOrderNumber(request.orderNumber());
        item.setNotes(normalize(request.notes()));
        item.setPriority(request.priority());
    }

    private RepairCampaignWorkItem save(RepairCampaignWorkItem item) {
        try {
            return repository.saveAndFlush(item);
        } catch (DataIntegrityViolationException ex) {
            String message = constraint(ex);
            if (message.contains("uq_repair_campaign_work_items_active_source")) {
                throw RestException.conflict("CAMPAIGN_WORK_SOURCE_DUPLICATE");
            }
            if (message.contains("uq_repair_campaign_work_items_active_order")) {
                throw RestException.conflict("CAMPAIGN_WORK_ORDER_DUPLICATE");
            }
            throw ex;
        }
    }

    private Long touch(RepairCampaign campaign) {
        campaign.setUpdatedAt(Instant.now());
        return campaignRepository.saveAndFlush(campaign).getVersion();
    }

    private RepairCampaign find(UUID id) {
        return campaignRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Repair campaign not found: " + id));
    }

    private RepairCampaign findLocked(UUID id) {
        return campaignRepository.findLockedByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Repair campaign not found: " + id));
    }

    private RepairCampaignWorkItem findItem(UUID campaignId, UUID itemId) {
        return repository.findByIdAndCampaignIdAndIsDeletedFalse(itemId, campaignId)
                .orElseThrow(() -> RestException.notFound("Repair campaign work item not found: " + itemId));
    }

    private static void requireVersion(RepairCampaign campaign, Long version) {
        if (version == null) throw RestException.badRequest("Repair campaign version is required");
        if (!Objects.equals(campaign.getVersion(), version)) {
            throw RestException.conflict("Repair campaign was modified by another request");
        }
    }

    private static void requireMutable(RepairCampaign campaign) {
        if (FROZEN.contains(campaign.getStatus())
                && (campaign.getStatus().ordinal() > RepairCampaignStatus.PREPARATION.ordinal()
                || campaign.getApprovalScopeHash() == null)) {
            throw RestException.conflict("CAMPAIGN_SCOPE_FROZEN");
        }
    }

    private static boolean identityChanged(RepairCampaignWorkItem item, RepairCampaignWorkItemRequest request) {
        return item.getSourceType() != request.sourceType()
                || !Objects.equals(item.getSourceId(), request.sourceId())
                || !Objects.equals(item.getEquipmentId(), request.equipmentId())
                || !Objects.equals(item.getOrderNumber(), request.orderNumber())
                || !Objects.equals(item.getPriority(), request.priority());
    }

    private static String requireManualTitle(String title) {
        if (title == null || title.isBlank()) throw RestException.badRequest("MANUAL work item title is required");
        if (title.trim().length() > 500) throw RestException.badRequest("MANUAL work item title must not exceed 500 characters");
        return title.trim();
    }

    private static RepairCampaignWorkItemResponse response(RepairCampaignWorkItem item, Long version) {
        return new RepairCampaignWorkItemResponse(item.getId(), item.getCampaign().getId(), item.getSourceType(),
                item.getSourceId(), item.getEquipmentId(), item.getTitle(), item.getStatus(),
                item.getOrderNumber(), item.getNotes(), version, item.getPriority());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String constraint(Throwable error) {
        StringBuilder result = new StringBuilder();
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null) result.append(' ').append(current.getMessage().toLowerCase());
        }
        return result.toString();
    }

    private record ResolvedInput(UUID equipmentId, String title) { }
}
