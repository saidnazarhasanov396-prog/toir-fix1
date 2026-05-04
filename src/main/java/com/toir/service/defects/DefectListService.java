package com.toir.service.defects;
import com.toir.entity.defects.DefectList;
import com.toir.entity.defects.DefectListLine;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.DefectListStatus;
import com.toir.repository.defects.DefectListLineRepository;
import com.toir.repository.defects.DefectListRepository;

import com.toir.exception.RestException;
import com.toir.util.PaginationUtils;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import com.toir.dto.defectlist.DefectListDto;
import com.toir.dto.defectlist.DefectListLineDto;
import com.toir.dto.defectlist.DefectListRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class DefectListService {

    private final DefectListRepository repository;
    private final DefectListLineRepository lineRepository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;



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
        auditList(AuditAction.CREATE, saved.getId(), null, saved);
        return DefectListDto.from(saved);
    }

    public DefectListDto update(UUID id, DefectListRequest request) {
        DefectList d = getOrThrow(id);
        if (d.getStatus() != DefectListStatus.DRAFT) {
            throw RestException.badRequest("Only DRAFT defect lists can be updated");
        }
        if (!d.getCode().equals(request.code()) && repository.existsByCodeAndIsDeletedFalse(request.code())) {
            throw RestException.conflict("Defect list code already exists: " + request.code());
        }
        String oldJson = auditSerializationService.toJson(d);
        d.setCode(request.code());
        d.setTitle(request.title());
        d.setEquipmentId(request.equipmentId());
        d.setRepairRequestId(request.repairRequestId());
        d.setWorkOrderId(request.workOrderId());
        d.setNotes(request.notes());
        auditList(AuditAction.UPDATE, d.getId(), oldJson, d);
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
        String oldJson = auditSerializationService.toJson(d);
        d.setStatus(DefectListStatus.APPROVED);
        d.setApprovedById(approverId);
        auditList(AuditAction.UPDATE, d.getId(), oldJson, d);
        return DefectListDto.from(d);
    }

    public DefectListDto close(UUID id) {
        DefectList d = getOrThrow(id);
        if (d.getStatus() != DefectListStatus.APPROVED) {
            throw RestException.badRequest("Only APPROVED defect lists can be closed");
        }
        String oldJson = auditSerializationService.toJson(d);
        d.setStatus(DefectListStatus.CLOSED);
        auditList(AuditAction.UPDATE, d.getId(), oldJson, d);
        return DefectListDto.from(d);
    }

    public DefectListLineDto addLine(UUID defectListId, DefectListLineDto r) {
        DefectList d = getOrThrow(defectListId);
        if (d.getStatus() == DefectListStatus.CLOSED || d.getStatus() == DefectListStatus.CANCELLED) {
            throw RestException.badRequest("Cannot modify closed/cancelled defect list");
        }
        String oldJson = auditSerializationService.toJson(d);
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
        auditLine(AuditAction.CREATE, saved.getId(), null, saved);
        auditList(AuditAction.UPDATE, d.getId(), oldJson, d);

        return DefectListLineDto.from(saved);
    }

    public void removeLine(UUID lineId) {
        DefectListLine line = lineRepository.findByIdAndIsDeletedFalse(lineId)
                .orElseThrow(() -> RestException.notFound("Line not found: " + lineId));
        DefectList parent = line.getDefectList();
        if (parent.getStatus() == DefectListStatus.CLOSED || parent.getStatus() == DefectListStatus.CANCELLED) {
            throw RestException.badRequest("Cannot modify closed/cancelled defect list");
        }
        String oldParentJson = auditSerializationService.toJson(parent);
        String oldLineJson = auditSerializationService.toJson(line);
        parent.getLines().remove(line);
        line.setDeleted(true);
        lineRepository.save(line);
        recalcTotals(parent);
        auditLine(AuditAction.DELETE, line.getId(), oldLineJson, null);
        auditList(AuditAction.UPDATE, parent.getId(), oldParentJson, parent);
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

    private void auditList(AuditAction action, UUID id, String oldJson, DefectList current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "defect_list",
                id != null ? id.toString() : null,
                action,
                AuditModule.DEFECT_LIST,
                auditListMessage(action),
                oldJson,
                newJson
        );
    }

    private void auditLine(AuditAction action, UUID id, String oldJson, DefectListLine current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "defect_list_line",
                id != null ? id.toString() : null,
                action,
                AuditModule.DEFECT_LIST_LINE,
                auditLineMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditListMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Ведомость дефектов создана";
            case UPDATE -> "Ведомость дефектов обновлена";
            case DELETE -> "Ведомость дефектов удалена";
            default -> "Действие выполнено над ведомостью дефектов";
        };
    }

    private String auditLineMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Строка ведомости дефектов создана";
            case UPDATE -> "Строка ведомости дефектов обновлена";
            case DELETE -> "Строка ведомости дефектов удалена";
            default -> "Действие выполнено над строкой ведомости дефектов";
        };
    }
}
