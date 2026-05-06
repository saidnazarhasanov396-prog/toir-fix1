package com.toir.service.defects;

import com.toir.dto.defectlist.DefectListDto;
import com.toir.dto.defectlist.DefectListLineDto;
import com.toir.dto.defectlist.DefectListRequest;
import com.toir.entity.defects.DefectList;
import com.toir.entity.defects.DefectListLine;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.DefectListStatus;
import com.toir.exception.RestException;
import com.toir.repository.defects.DefectListLineRepository;
import com.toir.repository.defects.DefectListRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefectListService {

    private final DefectListRepository repository;
    private final DefectListLineRepository lineRepository;
    private final AuditBuilderService auditBuilderService;



    @Transactional(readOnly = true)
    public List<DefectListDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(DefectListDto::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<DefectListDto> search(UUID equipmentId, int page, int pageSize, String search) {
        var pageable = PaginationUtils.pageRequest(page, pageSize);
        return repository.searchPaginated(
                equipmentId,
                search,
                pageable
        ).map(DefectListDto::from);
    }

    @Transactional(readOnly = true)
    public DefectListDto findById(UUID id) {
        return DefectListDto.from(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<DefectListDto> findByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId).stream().map(DefectListDto::from).toList();
    }

    @Transactional
    public DefectListDto create(DefectListRequest request) {
        if (repository.existsByCodeAndIsDeletedFalse(request.code())) {
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
        DefectList saved = repository.save(d);

        auditBuilderService.log(
                "defect_list",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.DEFECT_LIST,
                "Ведомость дефектов создана",
                null,
                saved
        );

        return DefectListDto.from(saved);
    }

    @Transactional
    public DefectListDto update(UUID id, DefectListRequest request) {
        DefectList d = getOrThrow(id);
        if (d.getStatus() != DefectListStatus.DRAFT) {
            throw RestException.badRequest("Only DRAFT defect lists can be updated");
        }
        if (!d.getCode().equals(request.code()) && repository.existsByCodeAndIsDeletedFalse(request.code())) {
            throw RestException.conflict("Defect list code already exists: " + request.code());
        }
        d.setCode(request.code());
        d.setTitle(request.title());
        d.setEquipmentId(request.equipmentId());
        d.setRepairRequestId(request.repairRequestId());
        d.setWorkOrderId(request.workOrderId());
        d.setNotes(request.notes());

        DefectList save = repository.save(d);

        auditBuilderService.log(
                "defect_list",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.DEFECT_LIST,
                "Ведомость дефектов обновлена",
                d,
                save
        );
        return DefectListDto.from(d);
    }

    @Transactional
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

        DefectList save = repository.save(d);

        auditBuilderService.log(
                "defect_list",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.DEFECT_LIST,
                "Ведомость дефектов обновлена",
                d,
                save
        );
        return DefectListDto.from(d);
    }

    @Transactional
    public DefectListDto close(UUID id) {
        DefectList d = getOrThrow(id);
        if (d.getStatus() != DefectListStatus.APPROVED) {
            throw RestException.badRequest("Only APPROVED defect lists can be closed");
        }
        d.setStatus(DefectListStatus.CLOSED);

        DefectList save = repository.save(d);

        auditBuilderService.log(
                "defect_list",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.DEFECT_LIST,
                "Ведомость дефектов обновлена",
                d,
                save
        );

        return DefectListDto.from(d);
    }

    @Transactional
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



        recalcTotals(d);

        DefectListLine createdLine = lineRepository.save(line);
        DefectList savedList = repository.save(d);


        auditBuilderService.log(
                "defect_list_line",
                createdLine.getId().toString(),
                AuditAction.CREATE,
                AuditModule.DEFECT_LIST_LINE,
                "Строка ведомости дефектов создана",
                null,
                createdLine
        );

        auditBuilderService.log(
                "defect_list",
                savedList.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.DEFECT_LIST,
                "Ведомость дефектов обновлена",
                d,
                savedList
        );

        return DefectListLineDto.from(createdLine);
    }

    @Transactional
    public void removeLine(UUID lineId) {
        DefectListLine line = lineRepository.findByIdAndIsDeletedFalse(lineId)
                .orElseThrow(() -> RestException.notFound("Line not found: " + lineId));
        DefectList parent = line.getDefectList();
        if (parent.getStatus() == DefectListStatus.CLOSED || parent.getStatus() == DefectListStatus.CANCELLED) {
            throw RestException.badRequest("Cannot modify closed/cancelled defect list");
        }


        parent.getLines().remove(line);

        line.setDeleted(true);
        DefectListLine deletedLine = lineRepository.save(line);
        recalcTotals(parent);

        DefectList save = repository.save(parent);


        auditBuilderService.log(
                "defect_list_line",
                line.getId().toString(),
                AuditAction.DELETE,
                AuditModule.DEFECT_LIST_LINE,
                "Строка ведомости дефектов удалена",
                deletedLine,
                null
        );

        auditBuilderService.log(
                "defect_list",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.DEFECT_LIST,
                "Ведомость дефектов обновлена",
                parent,
                save
        );
    }

    private void recalcTotals(DefectList d) {
        double hours = d.getLines().stream().mapToDouble(DefectListLine::getEstimatedLaborHours).sum();
        double cost = d.getLines().stream().mapToDouble(DefectListLine::getEstimatedCost).sum();
        d.setTotalLaborHours(hours);
        d.setTotalEstimatedCost(cost);
    }

    private DefectList getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Defect list not found: " + id));
    }


}
