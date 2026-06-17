package com.toir.service.repair;

import com.toir.dto.repaircampaign.RepairCampaignDto;
import com.toir.dto.repaircampaign.RepairCampaignRequest;
import com.toir.dto.repaircampaign.RepairCampaignStageDto;
import com.toir.entity.Department;
import com.toir.entity.repair.RepairCampaign;
import com.toir.entity.repair.RepairCampaignStage;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.RepairCampaignStatus;
import com.toir.exception.RestException;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.repository.repair.RepairCampaignStageRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.CodeGenerationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RepairCampaignService {

    private final RepairCampaignRepository repository;
    private final RepairCampaignStageRepository stageRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<RepairCampaignDto> findAll() {
        return toDtoList(repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc());
    }

    @Transactional(readOnly = true)
    public List<RepairCampaignDto> findByYear(int year) {
        return toDtoList(repository.findAllByYearAndIsDeletedFalseOrderByStartDateAsc(year));
    }

    @Transactional(readOnly = true)
    public List<RepairCampaignDto> findAllFiltered(String search, Integer year, RepairCampaignStatus status) {
        String statusStr = status != null ? status.name() : null;
        String searchPattern = (search != null && !search.isBlank()) ? "%" + search.trim().toLowerCase() + "%" : null;
        return toDtoList(repository.findAllFiltered(year, statusStr, searchPattern));
    }

    @Transactional(readOnly = true)
    public RepairCampaignDto findById(UUID id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public RepairCampaignDto create(RepairCampaignRequest r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        if (!r.endDate().isAfter(r.startDate())) {
            throw RestException.badRequest("End date must be after start date");
        }
        RepairCampaign c = new RepairCampaign();
        c.setCode(nextCode());
        c.setName(r.name());
        c.setYear(r.year());
        c.setQuarter(r.quarter());
        c.setDepartmentId(r.departmentId());
        c.setStartDate(r.startDate());
        c.setEndDate(r.endDate());
        c.setTotalBudget(r.totalBudget());
        c.setScope(r.scope());
        c.setNotes(r.notes());
        RepairCampaign saved = repository.save(c);

        auditBuilderService.log(
                "repair_campaign",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.REPAIR_CAMPAIGN,
                "Ремонтная кампания создана",
                null,
                saved
        );

        return toDto(saved);
    }

    private String nextCode() {
        String prefix = "RCMP-" + java.time.Year.now().getValue() + "-";
        return CodeGenerationUtils.nextYearSequenceCode(
                "RCMP",
                () -> repository.maxSequenceByCodePrefix(prefix),
                repository::existsByCodeAndIsDeletedFalse
        );
    }

    @Transactional
    public RepairCampaignDto finalizeApprovalFromApprovalRequest(UUID id) {
        RepairCampaign c = getOrThrow(id);
        if (c.getStatus() != RepairCampaignStatus.DRAFT) {
            throw RestException.badRequest("Only DRAFT campaigns can be approved");
        }
        c.setStatus(RepairCampaignStatus.APPROVED);

        RepairCampaign saved = repository.save(c);

        auditBuilderService.log(
                "repair_campaign",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.REPAIR_CAMPAIGN,
                "Ремонтная кампания обновлена",
                c,
                saved
        );
        return toDto(saved);
    }

    /**
     * @deprecated Approval decisions must go through ApprovalService. This wrapper remains for tests and
     * compatibility with older internal callers; approval handlers should call
     * {@link #finalizeApprovalFromApprovalRequest(UUID)}.
     */
    @Deprecated(forRemoval = false)
    @Transactional
    public RepairCampaignDto approve(UUID id) {
        return finalizeApprovalFromApprovalRequest(id);
    }

    @Transactional
    public RepairCampaignDto start(UUID id) {
        RepairCampaign c = getOrThrow(id);
        if (c.getStatus() != RepairCampaignStatus.APPROVED) {
            throw RestException.badRequest("Only APPROVED campaigns can be started");
        }
        c.setStatus(RepairCampaignStatus.IN_PROGRESS);

        RepairCampaign save = repository.save(c);

        auditBuilderService.log(
                "repair_campaign",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.REPAIR_CAMPAIGN,
                "Ремонтная кампания обновлена",
                c,
                save
        );
        return toDto(save);
    }

    @Transactional
    public RepairCampaignDto close(UUID id) {
        RepairCampaign c = getOrThrow(id);
        if (c.getStatus() != RepairCampaignStatus.IN_PROGRESS
                && c.getStatus() != RepairCampaignStatus.COMPLETED) {
            throw RestException.badRequest("Only IN_PROGRESS/COMPLETED campaigns can be closed");
        }

        c.setStatus(RepairCampaignStatus.CLOSED);

        RepairCampaign save = repository.save(c);

        auditBuilderService.log(
                "repair_campaign",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.REPAIR_CAMPAIGN,
                "Ремонтная кампания обновлена",
                c,
                save
        );
        return toDto(save);
    }

    @Transactional
    public RepairCampaignStageDto addStage(UUID campaignId, RepairCampaignStageDto r) {
        RepairCampaign c = getOrThrow(campaignId);
        if (c.getStatus() == RepairCampaignStatus.CLOSED || c.getStatus() == RepairCampaignStatus.CANCELLED) {
            throw RestException.badRequest("Cannot add stages to closed/cancelled campaign");
        }

        RepairCampaignStage s = new RepairCampaignStage();
        s.setCampaign(c);
        s.setSequence(r.sequence());
        s.setName(r.name());
        s.setStartDate(r.startDate());
        s.setEndDate(r.endDate());
        s.setPlannedCost(r.plannedCost());
        s.setActualCost(r.actualCost());
        s.setNotes(r.notes());
        c.getStages().add(s);
        RepairCampaignStage repairCampaignStage = stageRepository.save(s);
        recalcTotals(c);


        auditBuilderService.log(
                "repair_campaign_stage",
                repairCampaignStage.getId().toString(),
                AuditAction.CREATE,
                AuditModule.REPAIR_CAMPAIGN_STAGE,
                "Этап ремонтной кампании создан",
                null,
                repairCampaignStage
        );


        RepairCampaign repairCampaign = repository.save(c);

        auditBuilderService.log(
                "repair_campaign",
                repairCampaign.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.REPAIR_CAMPAIGN,
                "Ремонтная кампания обновлена",
                c,
                repairCampaign
        );
        return RepairCampaignStageDto.from(repairCampaignStage);
    }

    @Transactional
    public RepairCampaignStageDto completeStage(UUID stageId, double actualCost) {
        RepairCampaignStage s = stageRepository.findByIdAndIsDeletedFalse(stageId)
                .orElseThrow(() -> RestException.notFound("Stage not found: " + stageId));

        s.setActualCost(actualCost);
        s.setStatus(RepairCampaignStatus.COMPLETED);
        recalcTotals(s.getCampaign());


        RepairCampaignStage repairCampaignStage = stageRepository.save(s);

        auditBuilderService.log(
                "repair_campaign_stage",
                repairCampaignStage.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.REPAIR_CAMPAIGN_STAGE,
               "Этап ремонтной кампании обновлен",
                s,
                repairCampaignStage
        );


        auditBuilderService.log(
                "repair_campaign",
                repairCampaignStage.getCampaign().getId().toString(),
                AuditAction.UPDATE,
                AuditModule.REPAIR_CAMPAIGN,
                "Ремонтная кампания обновлена",
                s.getCampaign(),
                repairCampaignStage.getCampaign()
        );
        return RepairCampaignStageDto.from(s);
    }

    private void recalcTotals(RepairCampaign c) {
        double totalActual = c.getStages().stream().mapToDouble(RepairCampaignStage::getActualCost).sum();
        c.setTotalActual(totalActual);
        if (c.getStages().stream().allMatch(s -> s.getStatus() == RepairCampaignStatus.COMPLETED)
                && !c.getStages().isEmpty()) {
            c.setStatus(RepairCampaignStatus.COMPLETED);
        }
    }

    private RepairCampaign getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Repair campaign not found: " + id));
    }

    private RepairCampaignDto toDto(RepairCampaign c) {
        if (c == null) {
            return null;
        }
        if (c.getDepartmentId() == null) {
            return RepairCampaignDto.from(c, null);
        }
        String departmentName = departmentRepository.findByIdAndIsDeletedFalse(c.getDepartmentId())
                .map(Department::getName)
                .orElse(null);
        return RepairCampaignDto.from(c, departmentName);
    }

    private List<RepairCampaignDto> toDtoList(List<RepairCampaign> campaigns) {
        if (campaigns == null || campaigns.isEmpty()) {
            return List.of();
        }
        Set<UUID> departmentIds = campaigns.stream()
                .map(RepairCampaign::getDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> departmentNames = departmentRepository.findAllByIdInAndIsDeletedFalse(departmentIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
        return campaigns.stream()
                .map(c -> RepairCampaignDto.from(c, departmentNames.getOrDefault(c.getDepartmentId(), null)))
                .toList();
    }
}
