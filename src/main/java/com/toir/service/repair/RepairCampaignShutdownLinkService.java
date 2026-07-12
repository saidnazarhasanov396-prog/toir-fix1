package com.toir.service.repair;

import com.toir.dto.repaircampaign.*;
import com.toir.entity.PlannedShutdown;
import com.toir.entity.plannedshutdown.PlannedShutdownCampaignLink;
import com.toir.entity.repair.RepairCampaign;
import com.toir.entity.repair.RepairCampaignWorkItem;
import com.toir.entity.repair.RepairCampaignWorkItemWindow;
import com.toir.enums.*;
import com.toir.exception.RestException;
import com.toir.repository.PlannedShutdownRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownCampaignLinkRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownWorkItemRepository;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.repository.repair.RepairCampaignWorkItemRepository;
import com.toir.repository.repair.RepairCampaignWorkItemWindowRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RepairCampaignShutdownLinkService {
    private static final Set<PlannedShutdownStatus> SHUTDOWN_STARTED = EnumSet.of(
            PlannedShutdownStatus.SHUTDOWN_STARTED, PlannedShutdownStatus.SAFE_STATE,
            PlannedShutdownStatus.REPAIR_IN_PROGRESS, PlannedShutdownStatus.TESTING,
            PlannedShutdownStatus.STARTUP, PlannedShutdownStatus.COMPLETED, PlannedShutdownStatus.CLOSED);
    private static final Set<RepairCampaignStatus> CAMPAIGN_STARTED = EnumSet.of(
            RepairCampaignStatus.IN_PROGRESS, RepairCampaignStatus.SUSPENDED, RepairCampaignStatus.COMPLETED,
            RepairCampaignStatus.CLOSING, RepairCampaignStatus.CLOSED);

    private final RepairCampaignRepository campaignRepository;
    private final PlannedShutdownRepository shutdownRepository;
    private final PlannedShutdownCampaignLinkRepository linkRepository;
    private final RepairCampaignWorkItemWindowRepository windowRepository;
    private final RepairCampaignWorkItemRepository campaignItemRepository;
    private final PlannedShutdownWorkItemRepository shutdownItemRepository;
    private final ScopeAccessService scopeAccessService;
    private final AuditBuilderService audit;

    @Transactional
    public RepairCampaignShutdownLinkResponse link(UUID campaignId, UUID shutdownId,
                                                    RepairCampaignShutdownLinkRequest request) {
        Pair pair = lockPair(campaignId, shutdownId);
        validate(pair, request.repairCampaignVersion(), request.plannedShutdownVersion());
        PlannedShutdownCampaignLink link = linkRepository
                .findByPlannedShutdownIdAndRepairCampaignId(shutdownId, campaignId)
                .orElseGet(PlannedShutdownCampaignLink::new);
        if (!link.isDeleted() && link.getId() != null) throw RestException.conflict("CAMPAIGN_SHUTDOWN_LINK_DUPLICATE");
        link.setPlannedShutdownId(shutdownId); link.setRepairCampaignId(campaignId); link.setDeleted(false);
        saveLink(link);
        Versions versions = touch(pair);
        var response = linkResponse(link, versions, true);
        auditBoth(pair, link.getId(), AuditAction.CREATE, "Campaign linked to planned shutdown", null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public RepairCampaignShutdownLinkResponse get(UUID campaignId, UUID shutdownId,
            Long campaignVersion, Long shutdownVersion) {
        Pair pair = findPair(campaignId, shutdownId);
        validate(pair, campaignVersion, shutdownVersion);
        var link = activeLink(campaignId, shutdownId);
        return linkResponse(link, new Versions(pair.campaign.getVersion(), pair.shutdown.getVersion()), true);
    }

    @Transactional
    public RepairCampaignShutdownLinkResponse unlink(UUID campaignId, UUID shutdownId,
                                                      RepairCampaignShutdownLinkRequest request) {
        Pair pair = lockPair(campaignId, shutdownId);
        validate(pair, request.repairCampaignVersion(), request.plannedShutdownVersion());
        if (SHUTDOWN_STARTED.contains(pair.shutdown.getLifecycleStatus())
                || CAMPAIGN_STARTED.contains(pair.campaign.getStatus())) {
            throw RestException.conflict("CAMPAIGN_SHUTDOWN_UNLINK_AFTER_START");
        }
        if (windowRepository.existsByRepairCampaignIdAndPlannedShutdownIdAndIsDeletedFalse(campaignId, shutdownId)) {
            throw RestException.conflict("CAMPAIGN_SHUTDOWN_LINK_HAS_WINDOWS");
        }
        var link = activeLink(campaignId, shutdownId);
        link.setDeleted(true); linkRepository.saveAndFlush(link);
        Versions versions = touch(pair);
        var response = linkResponse(link, versions, false);
        auditBoth(pair, link.getId(), AuditAction.DELETE, "Campaign unlinked from planned shutdown", null, response);
        return response;
    }

    @Transactional
    public RepairCampaignWorkItemWindowResponse addWindow(UUID campaignId, UUID shutdownId,
            RepairCampaignWorkItemWindowRequest request) {
        Pair pair = lockPair(campaignId, shutdownId);
        validate(pair, request.repairCampaignVersion(), request.plannedShutdownVersion());
        activeLink(campaignId, shutdownId);
        RepairCampaignWorkItem campaignItem = campaignItemRepository
                .findByIdAndCampaignIdAndIsDeletedFalse(request.repairCampaignWorkItemId(), campaignId)
                .orElseThrow(() -> RestException.notFound("Repair campaign work item not found"));
        var shutdownItem = shutdownItemRepository
                .findByIdAndPlannedShutdownIdAndIsDeletedFalse(request.shutdownWorkItemId(), shutdownId)
                .orElseThrow(() -> RestException.notFound("Shutdown work item not found"));
        requireSameCanonicalIdentity(campaignItem, shutdownItem);
        RepairCampaignWorkItemWindow window = windowRepository
                .findByRepairCampaignWorkItemIdAndPlannedShutdownIdAndShutdownWorkItemId(
                        campaignItem.getId(), shutdownId, shutdownItem.getId())
                .orElseGet(RepairCampaignWorkItemWindow::new);
        if (!window.isDeleted() && window.getId() != null) throw RestException.conflict("CAMPAIGN_WINDOW_DUPLICATE");
        window.setRepairCampaignId(campaignId); window.setRepairCampaignWorkItemId(campaignItem.getId());
        window.setPlannedShutdownId(shutdownId); window.setShutdownWorkItemId(shutdownItem.getId());
        window.setDeleted(false); saveWindow(window);
        Versions versions = touch(pair);
        var response = windowResponse(window, versions, true);
        auditBoth(pair, window.getId(), AuditAction.CREATE, "Campaign work item linked to shutdown window", null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<RepairCampaignWorkItemWindowResponse> listWindows(UUID campaignId, UUID shutdownId,
            Long campaignVersion, Long shutdownVersion) {
        Pair pair = findPair(campaignId, shutdownId); validate(pair, campaignVersion, shutdownVersion);
        activeLink(campaignId, shutdownId);
        Versions versions = new Versions(pair.campaign.getVersion(), pair.shutdown.getVersion());
        return windowRepository.findAllByRepairCampaignIdAndPlannedShutdownIdAndIsDeletedFalseOrderById(
                campaignId, shutdownId).stream().map(w -> windowResponse(w, versions, true)).toList();
    }

    @Transactional
    public RepairCampaignWorkItemWindowResponse removeWindow(UUID campaignId, UUID shutdownId, UUID windowId,
            RepairCampaignShutdownLinkRequest request) {
        Pair pair = lockPair(campaignId, shutdownId); validate(pair, request.repairCampaignVersion(), request.plannedShutdownVersion());
        if (SHUTDOWN_STARTED.contains(pair.shutdown.getLifecycleStatus())
                || CAMPAIGN_STARTED.contains(pair.campaign.getStatus()))
            throw RestException.conflict("CAMPAIGN_WINDOW_UNLINK_AFTER_START");
        var window = windowRepository.findByIdAndRepairCampaignIdAndPlannedShutdownIdAndIsDeletedFalse(
                windowId, campaignId, shutdownId).orElseThrow(() -> RestException.notFound("Campaign work item window not found"));
        window.setDeleted(true); windowRepository.saveAndFlush(window);
        Versions versions = touch(pair); var response = windowResponse(window, versions, false);
        auditBoth(pair, windowId, AuditAction.DELETE, "Campaign work item removed from shutdown window", null, response);
        return response;
    }

    private Pair lockPair(UUID campaignId, UUID shutdownId) {
        RepairCampaign campaign; PlannedShutdown shutdown;
        if (campaignId.compareTo(shutdownId) <= 0) {
            campaign = lockCampaign(campaignId); shutdown = lockShutdown(shutdownId);
        } else {
            shutdown = lockShutdown(shutdownId); campaign = lockCampaign(campaignId);
        }
        return new Pair(campaign, shutdown);
    }
    private Pair findPair(UUID campaignId, UUID shutdownId) {
        return new Pair(campaignRepository.findByIdAndIsDeletedFalse(campaignId)
                .orElseThrow(() -> RestException.notFound("Repair campaign not found")),
                shutdownRepository.findByIdAndIsDeletedFalse(shutdownId)
                        .orElseThrow(() -> RestException.notFound("Planned shutdown not found")));
    }
    private RepairCampaign lockCampaign(UUID id) { return campaignRepository.findLockedByIdAndIsDeletedFalse(id)
            .orElseThrow(() -> RestException.notFound("Repair campaign not found")); }
    private PlannedShutdown lockShutdown(UUID id) { return shutdownRepository.findByIdAndIsDeletedFalseForUpdate(id)
            .orElseThrow(() -> RestException.notFound("Planned shutdown not found")); }
    private void validate(Pair pair, Long campaignVersion, Long shutdownVersion) {
        scopeAccessService.assertCanAccessDepartment(pair.campaign.getDepartmentId());
        scopeAccessService.assertCanAccessDepartment(pair.shutdown.getDepartmentId());
        if (campaignVersion == null || shutdownVersion == null) throw RestException.badRequest("Both aggregate versions are required");
        if (!Objects.equals(pair.campaign.getVersion(), campaignVersion)
                || !Objects.equals(pair.shutdown.getVersion(), shutdownVersion))
            throw RestException.conflict("CAMPAIGN_SHUTDOWN_VERSION_CONFLICT");
    }
    private PlannedShutdownCampaignLink activeLink(UUID campaignId, UUID shutdownId) {
        return linkRepository.findByPlannedShutdownIdAndRepairCampaignIdAndIsDeletedFalse(shutdownId, campaignId)
                .orElseThrow(() -> RestException.notFound("Campaign-shutdown link not found"));
    }
    private static void requireSameCanonicalIdentity(RepairCampaignWorkItem campaignItem,
            com.toir.entity.plannedshutdown.PlannedShutdownWorkItem shutdownItem) {
        if (campaignItem.getSourceType() == RepairCampaignWorkItemSourceType.MANUAL
                || shutdownItem.getSourceType() == PlannedShutdownWorkItemSourceType.MANUAL
                || !campaignItem.getSourceType().name().equals(shutdownItem.getSourceType().name())
                || !Objects.equals(campaignItem.getSourceId(), shutdownItem.getSourceId())
                || !Objects.equals(campaignItem.getEquipmentId(), shutdownItem.getEquipmentId()))
            throw RestException.badRequest("CAMPAIGN_WINDOW_CANONICAL_IDENTITY_MISMATCH");
    }
    private Versions touch(Pair pair) {
        pair.campaign.setUpdatedAt(Instant.now()); pair.shutdown.setUpdatedAt(Instant.now());
        RepairCampaign campaign = campaignRepository.saveAndFlush(pair.campaign);
        PlannedShutdown shutdown = shutdownRepository.saveAndFlush(pair.shutdown);
        return new Versions(campaign.getVersion(), shutdown.getVersion());
    }
    private PlannedShutdownCampaignLink saveLink(PlannedShutdownCampaignLink link) {
        try { return linkRepository.saveAndFlush(link); }
        catch (DataIntegrityViolationException ex) {
            if (constraintMessage(ex).contains("uq_planned_shutdown_campaigns_active_pair")) {
                throw RestException.conflict("CAMPAIGN_SHUTDOWN_LINK_DUPLICATE");
            }
            throw ex;
        }
    }
    private RepairCampaignWorkItemWindow saveWindow(RepairCampaignWorkItemWindow window) {
        try { return windowRepository.saveAndFlush(window); }
        catch (DataIntegrityViolationException ex) {
            if (constraintMessage(ex).contains("uq_repair_campaign_work_item_windows_active_identity")) {
                throw RestException.conflict("CAMPAIGN_WINDOW_DUPLICATE");
            }
            throw ex;
        }
    }
    private static String constraintMessage(Throwable failure) {
        StringBuilder message = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null) message.append(' ').append(current.getMessage());
        }
        return message.toString();
    }
    private void auditBoth(Pair pair, UUID id, AuditAction action, String description, Object before, Object after) {
        audit.log("repair_campaign_shutdown_link", pair.campaign.getId().toString(), action,
                AuditModule.REPAIR_CAMPAIGN, description + " link=" + id, before, after);
        audit.log("repair_campaign_shutdown_link", pair.shutdown.getId().toString(), action,
                AuditModule.PLANNED_SHUTDOWN, description + " link=" + id, before, after);
    }
    private static RepairCampaignShutdownLinkResponse linkResponse(PlannedShutdownCampaignLink link, Versions v, boolean active) {
        return new RepairCampaignShutdownLinkResponse(link.getId(), link.getRepairCampaignId(), link.getPlannedShutdownId(), v.campaign, v.shutdown, active);
    }
    private static RepairCampaignWorkItemWindowResponse windowResponse(RepairCampaignWorkItemWindow w, Versions v, boolean active) {
        return new RepairCampaignWorkItemWindowResponse(w.getId(), w.getRepairCampaignId(), w.getRepairCampaignWorkItemId(), w.getPlannedShutdownId(), w.getShutdownWorkItemId(), v.campaign, v.shutdown, active);
    }
    private record Pair(RepairCampaign campaign, PlannedShutdown shutdown) { }
    private record Versions(Long campaign, Long shutdown) { }
}
