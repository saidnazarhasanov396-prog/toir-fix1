package com.toir.service.repair;

import com.toir.dto.repaircampaign.*;
import com.toir.entity.repair.RepairCampaign;
import com.toir.entity.repair.RepairCampaignRisk;
import com.toir.entity.users.User;
import com.toir.enums.*;
import com.toir.exception.RestException;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.repository.repair.RepairCampaignRiskRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RepairCampaignRiskService {
    private final RepairCampaignRiskRepository repository;
    private final RepairCampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final ScopeAccessService scopeAccessService;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<RepairCampaignRiskResponse> list(UUID campaignId) {
        requireCampaign(campaignId);
        List<RepairCampaignRisk> risks = repository.findAllByCampaignIdAndIsDeletedFalseOrderByCreatedAtDesc(campaignId);
        Set<UUID> ownerIds = risks.stream().map(RepairCampaignRisk::getOwnerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> ownerNames = resolveOwnerNames(ownerIds);
        return risks.stream().map(risk -> RepairCampaignRiskResponse.from(
                risk, ownerNames.get(risk.getOwnerId()))).toList();
    }

    @Transactional
    public RepairCampaignRiskResponse create(UUID campaignId, RepairCampaignRiskCreateRequest request) {
        requireCampaign(campaignId);
        User owner = owner(request.ownerId());
        RepairCampaignRisk risk = new RepairCampaignRisk();
        risk.setId(UUID.randomUUID());
        risk.setCampaignId(campaignId);
        risk.setTitle(requiredTitle(request.title()));
        risk.setDescription(text(request.description()));
        risk.setLikelihood(request.likelihood());
        risk.setImpact(request.impact());
        risk.setStatus(RepairCampaignRiskStatus.OPEN);
        risk.setOwnerId(request.ownerId());
        risk.setMitigationPlan(text(request.mitigationPlan()));
        risk.setDueDate(request.dueDate());
        RepairCampaignRisk saved = repository.save(risk);
        RepairCampaignRiskResponse response = RepairCampaignRiskResponse.from(
                saved, owner == null ? null : displayName(owner));
        auditBuilderService.log("repair_campaign_risk", response.id().toString(), AuditAction.CREATE,
                AuditModule.REPAIR_CAMPAIGN, "Риск ремонтной кампании создан", null, response);
        return response;
    }

    @Transactional
    public RepairCampaignRiskResponse update(UUID campaignId, UUID riskId, RepairCampaignRiskUpdateRequest request) {
        requireCampaign(campaignId);
        RepairCampaignRisk risk = get(campaignId, riskId);
        RepairCampaignRisk before = snapshot(risk);
        if (request.title() != null) risk.setTitle(requiredTitle(request.title()));
        if (request.description() != null) risk.setDescription(text(request.description()));
        if (request.likelihood() != null) risk.setLikelihood(request.likelihood());
        if (request.impact() != null) risk.setImpact(request.impact());
        User owner = request.ownerId() == null ? owner(risk.getOwnerId()) : owner(request.ownerId());
        if (request.ownerId() != null) risk.setOwnerId(request.ownerId());
        if (request.mitigationPlan() != null) risk.setMitigationPlan(text(request.mitigationPlan()));
        if (request.dueDate() != null) risk.setDueDate(request.dueDate());
        if (request.status() != null && request.status() != risk.getStatus()) {
            validateTransition(risk.getStatus(), request.status());
            risk.setStatus(request.status());
        }
        RepairCampaignRisk saved = repository.save(risk);
        auditBuilderService.log("repair_campaign_risk", saved.getId().toString(), AuditAction.UPDATE,
                AuditModule.REPAIR_CAMPAIGN, "Риск ремонтной кампании обновлён", before, saved);
        return RepairCampaignRiskResponse.from(saved, owner == null ? null : owner.getFullName());
    }

    @Transactional
    public void delete(UUID campaignId, UUID riskId) {
        requireCampaign(campaignId);
        RepairCampaignRisk risk = get(campaignId, riskId);
        RepairCampaignRisk before = snapshot(risk);
        risk.setDeleted(true);
        repository.save(risk);
        auditBuilderService.log("repair_campaign_risk", riskId.toString(), AuditAction.DELETE,
                AuditModule.REPAIR_CAMPAIGN, "Риск ремонтной кампании удалён", before, risk);
    }

    private RepairCampaign requireCampaign(UUID campaignId) {
        RepairCampaign campaign = campaignRepository.findByIdAndIsDeletedFalse(campaignId)
                .orElseThrow(() -> RestException.notFound("Repair campaign not found: " + campaignId));
        scopeAccessService.assertCanAccessDepartment(campaign.getDepartmentId());
        return campaign;
    }

    private RepairCampaignRisk get(UUID campaignId, UUID riskId) {
        return repository.findByIdAndCampaignIdAndIsDeletedFalse(riskId, campaignId)
                .orElseThrow(() -> RestException.notFound("Repair campaign risk not found: " + riskId));
    }


    private Map<UUID, String> resolveOwnerNames(Set<UUID> ownerIds) {
        if (ownerIds.isEmpty()) return Map.of();
        Map<UUID, String> ownerNames = new HashMap<>();
        userRepository.findAllById(ownerIds).stream()
                .filter(user -> !user.isDeleted())
                .forEach(user -> ownerNames.put(user.getId(), displayName(user)));
        return ownerNames;
    }

    private static String displayName(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) return user.getFullName();
        if (user.getUsername() != null && !user.getUsername().isBlank()) return user.getUsername();
        return user.getId() == null ? null : user.getId().toString();
    }

    private User owner(UUID ownerId) {
        if (ownerId == null) return null;
        return userRepository.findByIdAndIsDeletedFalse(ownerId)
                .orElseThrow(() -> RestException.notFound("Risk owner not found: " + ownerId));
    }

    private static void validateTransition(RepairCampaignRiskStatus from, RepairCampaignRiskStatus to) {
        boolean valid = (from == RepairCampaignRiskStatus.OPEN
                && (to == RepairCampaignRiskStatus.MITIGATING || to == RepairCampaignRiskStatus.ACCEPTED))
                || (from == RepairCampaignRiskStatus.MITIGATING && to == RepairCampaignRiskStatus.CLOSED);
        if (!valid) throw RestException.badRequest("Invalid repair campaign risk status transition: " + from + " -> " + to);
    }

    private static String requiredTitle(String value) {
        if (value == null || value.isBlank()) throw RestException.badRequest("Risk title is required");
        return value.trim();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static RepairCampaignRisk snapshot(RepairCampaignRisk source) {
        RepairCampaignRisk copy = new RepairCampaignRisk();
        copy.setId(source.getId());
        copy.setCampaignId(source.getCampaignId());
        copy.setTitle(source.getTitle());
        copy.setDescription(source.getDescription());
        copy.setLikelihood(source.getLikelihood());
        copy.setImpact(source.getImpact());
        copy.setStatus(source.getStatus());
        copy.setOwnerId(source.getOwnerId());
        copy.setMitigationPlan(source.getMitigationPlan());
        copy.setDueDate(source.getDueDate());
        return copy;
    }
}
