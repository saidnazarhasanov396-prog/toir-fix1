package com.toir.service;
import com.toir.entity.Defect;
import com.toir.entity.DefectList;
import com.toir.entity.DefectListLine;
import com.toir.entity.DefectListStatus;
import com.toir.repository.DefectListLineRepository;
import com.toir.repository.DefectListRepository;

import com.toir.exception.RestException;
import com.toir.dto.defectlist.DefectListDto;
import com.toir.dto.defectlist.DefectListLineDto;
import com.toir.dto.defectlist.DefectListRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DefectListService {

    private final DefectListRepository repository;
    private final DefectListLineRepository lineRepository;

    public DefectListService(DefectListRepository repository, DefectListLineRepository lineRepository) {
        this.repository = repository;
        this.lineRepository = lineRepository;
    }

    @Transactional(readOnly = true)
    public List<DefectListDto> findAll() {
        return repository.findAll().stream().map(DefectListDto::from).toList();
    }

    @Transactional(readOnly = true)
    public DefectListDto findById(UUID id) {
        return DefectListDto.from(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<DefectListDto> findByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentId(equipmentId).stream().map(DefectListDto::from).toList();
    }

    public DefectListDto create(DefectListRequest request) {
        if (repository.existsByCode(request.code())) {
            throw RestException.conflict("Defect list code already exists: " + request.code());
        }
        DefectList d = new DefectList();
        d.setCode(request.code());
        d.setTitle(request.title());
        d.setEquipmentId(request.equipmentId());
        d.setRepairRequestId(request.repairRequestId());
        d.setWorkOrderId(request.workOrderId());
        d.setCreatedById(request.createdById());
        d.setNotes(request.notes());
        return DefectListDto.from(repository.save(d));
    }

    public DefectListDto update(UUID id, DefectListRequest request) {
        DefectList d = getOrThrow(id);
        if (d.getStatus() != DefectListStatus.DRAFT) {
            throw RestException.badRequest("Only DRAFT defect lists can be updated");
        }
        if (!d.getCode().equals(request.code()) && repository.existsByCode(request.code())) {
            throw RestException.conflict("Defect list code already exists: " + request.code());
        }
        d.setCode(request.code());
        d.setTitle(request.title());
        d.setEquipmentId(request.equipmentId());
        d.setRepairRequestId(request.repairRequestId());
        d.setWorkOrderId(request.workOrderId());
        d.setNotes(request.notes());
        return DefectListDto.from(d);
    }

    public DefectListDto approve(UUID id, UUID approverId) {
        DefectList d = getOrThrow(id);
        if (d.getStatus() != DefectListStatus.DRAFT) {
            throw RestException.badRequest("Only DRAFT defect lists can be approved");
        }
        if (d.getLines().isEmpty()) {
            throw RestException.badRequest("Cannot approve empty defect list");
        }
        d.setStatus(DefectListStatus.APPROVED);
        d.setApprovedById(approverId);
        return DefectListDto.from(d);
    }

    public DefectListDto close(UUID id) {
        DefectList d = getOrThrow(id);
        if (d.getStatus() != DefectListStatus.APPROVED) {
            throw RestException.badRequest("Only APPROVED defect lists can be closed");
        }
        d.setStatus(DefectListStatus.CLOSED);
        return DefectListDto.from(d);
    }

    public DefectListLineDto addLine(UUID defectListId, DefectListLineDto r) {
        DefectList d = getOrThrow(defectListId);
        if (d.getStatus() == DefectListStatus.CLOSED || d.getStatus() == DefectListStatus.CANCELLED) {
            throw RestException.badRequest("Cannot modify closed/cancelled defect list");
        }
        DefectListLine line = new DefectListLine();
        line.setDefectList(d);
        line.setDefectId(r.defectId());
        line.setDescription(r.description());
        line.setWorkScope(r.workScope());
        line.setMaterialSpecification(r.materialSpecification());
        line.setSparePartId(r.sparePartId());
        line.setRequiredQuantity(r.requiredQuantity());
        line.setEstimatedLaborHours(r.estimatedLaborHours());
        line.setEstimatedCost(r.estimatedCost());
        d.getLines().add(line);
        DefectListLine saved = lineRepository.save(line);

        recalcTotals(d);

        return DefectListLineDto.from(saved);
    }

    public void removeLine(UUID lineId) {
        DefectListLine line = lineRepository.findById(lineId)
                .orElseThrow(() -> RestException.notFound("Line not found: " + lineId));
        DefectList parent = line.getDefectList();
        if (parent.getStatus() == DefectListStatus.CLOSED || parent.getStatus() == DefectListStatus.CANCELLED) {
            throw RestException.badRequest("Cannot modify closed/cancelled defect list");
        }
        parent.getLines().remove(line);
        lineRepository.delete(line);
        recalcTotals(parent);
    }

    private void recalcTotals(DefectList d) {
        double hours = d.getLines().stream().mapToDouble(DefectListLine::getEstimatedLaborHours).sum();
        double cost = d.getLines().stream().mapToDouble(DefectListLine::getEstimatedCost).sum();
        d.setTotalLaborHours(hours);
        d.setTotalEstimatedCost(cost);
    }

    private DefectList getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Defect list not found: " + id));
    }
}
