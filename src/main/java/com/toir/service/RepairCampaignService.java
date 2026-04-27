package com.toir.service;
import com.toir.entity.RepairCampaign;
import com.toir.entity.RepairCampaignStage;
import com.toir.enums.RepairCampaignStatus;
import com.toir.repository.RepairCampaignRepository;
import com.toir.repository.RepairCampaignStageRepository;

import com.toir.exception.RestException;
import com.toir.dto.repaircampaign.RepairCampaignDto;
import com.toir.dto.repaircampaign.RepairCampaignRequest;
import com.toir.dto.repaircampaign.RepairCampaignStageDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class RepairCampaignService {

    private final RepairCampaignRepository repository;
    private final RepairCampaignStageRepository stageRepository;


    @Transactional(readOnly = true)
    public List<RepairCampaignDto> findAll() {
        return repository.findAllByIsDeletedFalse().stream().map(RepairCampaignDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<RepairCampaignDto> findByYear(int year) {
        return repository.findAllByYearAndIsDeletedFalseOrderByStartDateAsc(year).stream()
                .map(RepairCampaignDto::from).toList();
    }

    @Transactional(readOnly = true)
    public RepairCampaignDto findById(UUID id) {
        return RepairCampaignDto.from(getOrThrow(id));
    }

    public RepairCampaignDto create(RepairCampaignRequest r) {
        if (repository.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("Campaign code already exists: " + r.code());
        }
        if (!r.endDate().isAfter(r.startDate())) {
            throw RestException.badRequest("End date must be after start date");
        }
        RepairCampaign c = new RepairCampaign();
        c.setCode(r.code());
        c.setName(r.name());
        c.setYear(r.year());
        c.setQuarter(r.quarter());
        c.setDepartmentId(r.departmentId());
        c.setStartDate(r.startDate());
        c.setEndDate(r.endDate());
        c.setTotalBudget(r.totalBudget());
        c.setScope(r.scope());
        c.setNotes(r.notes());
        return RepairCampaignDto.from(repository.save(c));
    }

    public RepairCampaignDto approve(UUID id) {
        RepairCampaign c = getOrThrow(id);
        if (c.getStatus() != RepairCampaignStatus.DRAFT) {
            throw RestException.badRequest("Only DRAFT campaigns can be approved");
        }
        c.setStatus(RepairCampaignStatus.APPROVED);
        return RepairCampaignDto.from(c);
    }

    public RepairCampaignDto start(UUID id) {
        RepairCampaign c = getOrThrow(id);
        if (c.getStatus() != RepairCampaignStatus.APPROVED) {
            throw RestException.badRequest("Only APPROVED campaigns can be started");
        }
        c.setStatus(RepairCampaignStatus.IN_PROGRESS);
        return RepairCampaignDto.from(c);
    }

    public RepairCampaignDto close(UUID id) {
        RepairCampaign c = getOrThrow(id);
        if (c.getStatus() != RepairCampaignStatus.IN_PROGRESS
                && c.getStatus() != RepairCampaignStatus.COMPLETED) {
            throw RestException.badRequest("Only IN_PROGRESS/COMPLETED campaigns can be closed");
        }
        c.setStatus(RepairCampaignStatus.CLOSED);
        return RepairCampaignDto.from(c);
    }

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
        RepairCampaignStage saved = stageRepository.save(s);
        recalcTotals(c);
        return RepairCampaignStageDto.from(saved);
    }

    public RepairCampaignStageDto completeStage(UUID stageId, double actualCost) {
        RepairCampaignStage s = stageRepository.findByIdAndIsDeletedFalse(stageId)
                .orElseThrow(() -> RestException.notFound("Stage not found: " + stageId));
        s.setActualCost(actualCost);
        s.setStatus(RepairCampaignStatus.COMPLETED);
        recalcTotals(s.getCampaign());
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
}
