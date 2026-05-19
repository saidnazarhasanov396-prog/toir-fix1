package com.toir.service.defects;

import com.toir.dto.defectlist.DefectListDto;
import com.toir.dto.defectlist.DefectListLineDto;
import com.toir.dto.defectlist.DefectListRequest;
import com.toir.dto.defectlist.DefectListStatsResponse;
import com.toir.entity.defects.DefectList;
import com.toir.entity.defects.DefectListLine;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.DefectListStatus;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectListLineRepository;
import com.toir.repository.defects.DefectListRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.projection.DefectListStatsProjection;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefectListService {

    private final DefectListRepository repository;
    private final DefectListLineRepository lineRepository;
    private final AuditBuilderService auditBuilderService;
    private final EquipmentRepository equipmentRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final WorkOrderRepository workOrderRepository;
    private final ScopeAccessService scopeAccessService;



    @Transactional(readOnly = true)
    public List<DefectListDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(DefectListDto::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<DefectListDto> search(UUID equipmentId, int page, int pageSize, String search) {
        var pageable = PaginationUtils.pageRequest(page, pageSize);
        Page<DefectList> resultPage = repository.searchPaginated(
                equipmentId,
                search,
                pageable
        );
        if (!scopeAccessService.isScopeAdmin()) {
            List<DefectList> scopedContent = resultPage.getContent()
                    .stream()
                    .filter(this::canAccessDefectList)
                    .toList();
            return new PageImpl<>(scopedContent, pageable, scopedContent.size()).map(DefectListDto::from);
        }
        return resultPage.map(DefectListDto::from);
    }

    @Transactional(readOnly = true)
    public DefectListDto findById(UUID id) {
        DefectList defectList = getOrThrow(id);
        assertCanAccessDefectList(defectList);
        return DefectListDto.from(defectList);
    }

    @Transactional(readOnly = true)
    public List<DefectListDto> findByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId).stream().map(DefectListDto::from).toList();
    }

    @Transactional(readOnly = true)
    public DefectListStatsResponse getStats(UUID equipmentId, String search) {
        String searchPattern = toSearchPattern(search);

        if (!scopeAccessService.isScopeAdmin()) {
            List<DefectList> scopedLists = repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()
                    .stream()
                    .filter(defectList -> matchesStatsFilter(defectList, equipmentId, search))
                    .filter(this::canAccessDefectList)
                    .toList();
            return scopedStats(scopedLists);
        }

        DefectListStatsProjection stats = repository.getDefectListStats(
                equipmentId,
                searchPattern,
                DefectListStatus.DRAFT.name(),
                DefectListStatus.APPROVED.name(),
                DefectListStatus.CLOSED.name()
        );

        return new DefectListStatsResponse(
                safe(stats.getTotalDefectLists()),
                safe(stats.getDraft()),
                safe(stats.getApproved()),
                safe(stats.getClosed())
        );
    }

    @Transactional
    public DefectListDto create(DefectListRequest request) {
        assertCanAccessDefectListRequest(request);
        DefectList d = new DefectList();
        d.setCode(nextCode());
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
        assertCanAccessDefectList(d);
        assertCanAccessDefectListRequest(request);
        if (d.getStatus() != DefectListStatus.DRAFT) {
            throw RestException.badRequest("Only DRAFT defect lists can be updated");
        }
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
        assertCanAccessDefectList(d);
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
        assertCanAccessDefectList(d);
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
        assertCanAccessDefectList(d);
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
        assertCanAccessDefectList(parent);
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

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "DL-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("DL", year, sequence);
        while (repository.existsByCode(code)) {
            sequence++;
            code = formatCode("DL", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }

    private String toSearchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        return "%" + search.trim().toLowerCase() + "%";
    }

    private long safe(Long value) {
        return value == null ? 0L : value;
    }

    private void assertCanAccessDefectList(DefectList defectList) {
        if (!canAccessDefectList(defectList)) {
            throw new AccessDeniedException("Access denied by defect list department scope");
        }
    }

    private boolean canAccessDefectList(DefectList defectList) {
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        return resolveDefectListDepartmentId(defectList)
                .map(scopeAccessService::canAccessDepartment)
                .orElse(false);
    }

    private void assertCanAccessDefectListRequest(DefectListRequest request) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        Optional<UUID> departmentId = resolveDefectListDepartmentId(
                request.equipmentId(),
                request.repairRequestId(),
                request.workOrderId()
        );
        if (departmentId.isEmpty() || !scopeAccessService.canAccessDepartment(departmentId.get())) {
            throw new AccessDeniedException("Access denied by defect list department scope");
        }
    }

    private Optional<UUID> resolveDefectListDepartmentId(DefectList defectList) {
        return resolveDefectListDepartmentId(
                defectList.getEquipmentId(),
                defectList.getRepairRequestId(),
                defectList.getWorkOrderId()
        );
    }

    private Optional<UUID> resolveDefectListDepartmentId(UUID equipmentId, UUID repairRequestId, UUID workOrderId) {
        Set<UUID> departments = new LinkedHashSet<>();
        if (equipmentId != null) {
            equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                    .map(Equipment::getDepartmentId)
                    .filter(Objects::nonNull)
                    .ifPresent(departments::add);
        }
        if (repairRequestId != null) {
            repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)
                    .map(RepairRequest::getDepartmentId)
                    .filter(Objects::nonNull)
                    .ifPresent(departments::add);
        }
        if (workOrderId != null) {
            workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)
                    .map(WorkOrder::getDepartmentId)
                    .filter(Objects::nonNull)
                    .ifPresent(departments::add);
        }
        return departments.size() == 1 ? Optional.of(departments.iterator().next()) : Optional.empty();
    }

    private boolean matchesStatsFilter(DefectList defectList, UUID equipmentId, String search) {
        if (equipmentId != null && !equipmentId.equals(defectList.getEquipmentId())) {
            return false;
        }
        if (search == null || search.isBlank()) {
            return true;
        }
        String normalized = search.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(defectList.getCode(), normalized)
                || containsIgnoreCase(defectList.getTitle(), normalized)
                || containsIgnoreCase(defectList.getNotes(), normalized);
    }

    private boolean containsIgnoreCase(String value, String normalizedSearch) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedSearch);
    }

    private DefectListStatsResponse scopedStats(List<DefectList> defectLists) {
        long draft = defectLists.stream().filter(defectList -> defectList.getStatus() == DefectListStatus.DRAFT).count();
        long approved = defectLists.stream().filter(defectList -> defectList.getStatus() == DefectListStatus.APPROVED).count();
        long closed = defectLists.stream().filter(defectList -> defectList.getStatus() == DefectListStatus.CLOSED).count();
        return new DefectListStatsResponse(defectLists.size(), draft, approved, closed);
    }

}
