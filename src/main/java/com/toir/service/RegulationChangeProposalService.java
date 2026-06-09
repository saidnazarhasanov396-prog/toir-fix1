package com.toir.service;

import com.toir.dto.regulationchangeproposal.RegulationChangeProposalDto;
import com.toir.dto.regulationchangeproposal.RegulationChangeProposalRequest;
import com.toir.dto.regulationchangeproposal.RegulationChangeProposalReviewRequest;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.entity.maintenance.RegulationChangeProposal;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.RegulationChangeProposalStatus;
import com.toir.exception.RestException;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.repository.maintenance.RegulationChangeProposalRepository;
import com.toir.security.SecurityScope;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegulationChangeProposalService {

    private final RegulationChangeProposalRepository repository;
    private final MaintenanceRegulationRepository regulationRepository;
    private final SecurityScope securityScope;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<RegulationChangeProposalDto> list(UUID regulationId) {
        if (regulationId != null) {
            return repository
                    .findAllByRegulationIdAndIsDeletedFalseOrderByUpdatedAtDesc(regulationId)
                    .stream().map(RegulationChangeProposalDto::from).toList();
        }
        return repository
                .findAllByIsDeletedFalseOrderByUpdatedAtDesc()
                .stream().map(RegulationChangeProposalDto::from).toList();
    }

    @Transactional(readOnly = true)
    public RegulationChangeProposalDto get(UUID id) {
        return RegulationChangeProposalDto.from(getOrThrow(id));
    }

    @Transactional
    public RegulationChangeProposalDto create(RegulationChangeProposalRequest request) {
        // Regulation mavjudligini tekshir
        regulationRepository.findByIdAndIsDeletedFalse(request.regulationId())
                .orElseThrow(() -> RestException.notFound("Maintenance regulation not found"));

        RegulationChangeProposal proposal = new RegulationChangeProposal();
        proposal.setRegulationId(request.regulationId());
        proposal.setRcmSnapshotId(request.rcmSnapshotId());
        proposal.setTitle(request.title());
        proposal.setDescription(request.description());
        proposal.setProposedPeriodicityValue(request.proposedPeriodicityValue());
        proposal.setProposedPeriodicityUnit(request.proposedPeriodicityUnit());
        proposal.setProposedTemplateId(request.proposedTemplateId());
        proposal.setChangeReason(request.changeReason());
        proposal.setStatus(RegulationChangeProposalStatus.DRAFT);
        proposal.setCreatedById(currentUserId());

        RegulationChangeProposal saved = repository.save(proposal);
        auditBuilderService.log(
                "regulation_change_proposal",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.MAINTENANCE_REGULATION,
                "Regulation change proposal created",
                null,
                saved
        );
        return RegulationChangeProposalDto.from(saved);
    }

    @Transactional
    public RegulationChangeProposalDto submit(UUID id) {
        RegulationChangeProposal proposal = getOrThrow(id);
        if (proposal.getStatus() != RegulationChangeProposalStatus.DRAFT) {
            throw RestException.badRequest("Only DRAFT proposals can be submitted");
        }
        RegulationChangeProposal before = snapshot(proposal);
        proposal.setStatus(RegulationChangeProposalStatus.SUBMITTED);

        RegulationChangeProposal saved = repository.save(proposal);
        auditBuilderService.log(
                "regulation_change_proposal",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.MAINTENANCE_REGULATION,
                "Regulation change proposal submitted",
                before,
                saved
        );
        return RegulationChangeProposalDto.from(saved);
    }

    @Transactional
    public RegulationChangeProposalDto approve(UUID id, RegulationChangeProposalReviewRequest request) {
        RegulationChangeProposal proposal = getOrThrow(id);
        if (proposal.getStatus() != RegulationChangeProposalStatus.SUBMITTED) {
            throw RestException.badRequest("Only SUBMITTED proposals can be approved");
        }
        RegulationChangeProposal before = snapshot(proposal);
        proposal.setStatus(RegulationChangeProposalStatus.APPROVED);
        proposal.setReviewedById(currentUserId());
        proposal.setReviewedAt(Instant.now());
        proposal.setReviewComment(request != null ? request.reviewComment() : null);

        // Regulation ni yangilash
        applyToRegulation(proposal);

        RegulationChangeProposal saved = repository.save(proposal);
        auditBuilderService.log(
                "regulation_change_proposal",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.MAINTENANCE_REGULATION,
                "Regulation change proposal approved",
                before,
                saved
        );
        return RegulationChangeProposalDto.from(saved);
    }

    @Transactional
    public RegulationChangeProposalDto reject(UUID id, RegulationChangeProposalReviewRequest request) {
        RegulationChangeProposal proposal = getOrThrow(id);
        if (proposal.getStatus() != RegulationChangeProposalStatus.SUBMITTED) {
            throw RestException.badRequest("Only SUBMITTED proposals can be rejected");
        }
        RegulationChangeProposal before = snapshot(proposal);
        proposal.setStatus(RegulationChangeProposalStatus.REJECTED);
        proposal.setReviewedById(currentUserId());
        proposal.setReviewedAt(Instant.now());
        proposal.setReviewComment(request != null ? request.reviewComment() : null);

        RegulationChangeProposal saved = repository.save(proposal);
        auditBuilderService.log(
                "regulation_change_proposal",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.MAINTENANCE_REGULATION,
                "Regulation change proposal rejected",
                before,
                saved
        );
        return RegulationChangeProposalDto.from(saved);
    }

    // Tasdiqlangan taklif asosida regulationni yangilaydi
    private void applyToRegulation(RegulationChangeProposal proposal) {
        MaintenanceRegulation regulation = regulationRepository
                .findByIdAndIsDeletedFalse(proposal.getRegulationId())
                .orElseThrow(() -> RestException.notFound("Maintenance regulation not found"));

        boolean changed = false;

        if (proposal.getProposedPeriodicityValue() != null) {
            regulation.setPeriodicityValue(proposal.getProposedPeriodicityValue());
            changed = true;
        }
        if (proposal.getProposedPeriodicityUnit() != null) {
            try {
                regulation.setPeriodicityUnit(
                        com.toir.enums.PeriodicityUnit.valueOf(proposal.getProposedPeriodicityUnit())
                );
                changed = true;
            } catch (IllegalArgumentException ignored) {
                throw RestException.badRequest("Invalid periodicity unit: " + proposal.getProposedPeriodicityUnit());
            }
        }
        if (proposal.getProposedTemplateId() != null) {
            regulation.setTemplateId(proposal.getProposedTemplateId());
            changed = true;
        }

        if (changed) {
            regulationRepository.save(regulation);
            auditBuilderService.log(
                    "maintenance_regulation",
                    regulation.getId().toString(),
                    AuditAction.UPDATE,
                    AuditModule.MAINTENANCE_REGULATION,
                    "Regulation updated via approved change proposal: " + proposal.getId(),
                    null,
                    regulation
            );
        }
    }

    private RegulationChangeProposal getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Regulation change proposal not found"));
    }

    private RegulationChangeProposal snapshot(RegulationChangeProposal p) {
        RegulationChangeProposal copy = new RegulationChangeProposal();
        copy.setRegulationId(p.getRegulationId());
        copy.setRcmSnapshotId(p.getRcmSnapshotId());
        copy.setTitle(p.getTitle());
        copy.setDescription(p.getDescription());
        copy.setProposedPeriodicityValue(p.getProposedPeriodicityValue());
        copy.setProposedPeriodicityUnit(p.getProposedPeriodicityUnit());
        copy.setProposedTemplateId(p.getProposedTemplateId());
        copy.setChangeReason(p.getChangeReason());
        copy.setStatus(p.getStatus());
        copy.setCreatedById(p.getCreatedById());
        copy.setReviewedById(p.getReviewedById());
        copy.setReviewedAt(p.getReviewedAt());
        copy.setReviewComment(p.getReviewComment());
        return copy;
    }

    private UUID currentUserId() {
        var user = securityScope.currentUser();
        if (user == null || user.id() == null) return null;
        try {
            return UUID.fromString(user.id());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}