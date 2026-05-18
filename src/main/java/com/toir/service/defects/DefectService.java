package com.toir.service.defects;

import com.toir.dto.defect.DefectDto;
import com.toir.dto.defect.DefectRequest;
import com.toir.dto.defect.DefectResponse;
import com.toir.dto.defect.DefectStatsResponse;
import com.toir.dto.triad.RepairRequestBriefDto;
import com.toir.dto.triad.TriadLinkMapper;
import com.toir.dto.triad.WorkOrderBriefDto;
import com.toir.entity.KnowledgeArticle;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.DefectStatus;
import com.toir.enums.RequestStatus;
import com.toir.exception.RestException;
import com.toir.repository.KnowledgeArticleRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.projection.DefectStatsProjection;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.Locale;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DefectService {

    private final DefectRepository repository;
    private final EquipmentRepository equipmentRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final WorkOrderRepository workOrderRepository;
    private final AuditBuilderService auditBuilderService;
    private static final Set<RequestStatus> DISALLOWED_REPAIR_REQUEST_STATUSES_FOR_DEFECT_LINK =
            EnumSet.of(RequestStatus.REJECTED, RequestStatus.CLOSED, RequestStatus.CANCELLED);
    private final KnowledgeArticleRepository knowledgeRepository;
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 50;


    @Transactional(readOnly = true)
    public List<DefectResponse> findAll() {
        return toResponses(repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc());
    }

    @Transactional(readOnly = true)
    public Page<DefectResponse> search(UUID equipmentId, UUID repairRequestId, int page, int size, String search) {
        var pageable = PaginationUtils.pageRequest(page, size);
        Page<Defect> resultPage = repository.searchPaginated(
                equipmentId,
                repairRequestId,
                search,
                pageable
        );
        return toResponsePage(resultPage);
    }

    @Transactional(readOnly = true)
    public DefectResponse findById(UUID id) {
        return toResponses(List.of(getOrThrow(id))).getFirst();
    }

    @Transactional(readOnly = true)
    public List<DefectResponse> findByEquipment(UUID equipmentId) {
        return toResponses(repository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId));
    }

    @Transactional(readOnly = true)
    public DefectStatsResponse getStats(
            UUID equipmentId,
            UUID repairRequestId,
            String search
    ) {
        String searchPattern = toSearchPattern(search);

        DefectStatsProjection stats = repository.getDefectStats(
                equipmentId,
                repairRequestId,
                searchPattern,
                DefectStatus.OPEN.name(),
                DefectStatus.RESOLVED.name()
        );

        return new DefectStatsResponse(
                safe(stats.getTotalDefects()),
                safe(stats.getOpen()),
                safe(stats.getResolved()),
                safe(stats.getWithRecurrence())
        );
    }


    @Transactional
    public DefectResponse create(DefectRequest request) {
        Defect saved = saveWithGeneratedCode(request);

        auditBuilderService.log(
                "defect",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.DEFECT,
                "Дефект создан",
                null,
                saved
        );

        return toResponses(List.of(saved)).getFirst();
    }

    @Transactional
    public DefectResponse update(UUID id, DefectRequest request) {
        Defect entity = getOrThrow(id);
        apply(entity, request);

        Defect save = repository.save(entity);

        auditBuilderService.log(
                "defect",
                id != null ? id.toString() : null,
                AuditAction.UPDATE,
                AuditModule.DEFECT,
                "Дефект обновлен",
                entity,
                save
        );

        return toResponses(List.of(entity)).getFirst();
    }

    @Transactional
    public DefectResponse resolve(UUID id) {
        Defect entity = getOrThrow(id);
        entity.setStatus(DefectStatus.RESOLVED);
        entity.setResolvedAt(Instant.now());

        Defect saved = repository.save(entity);

        auditBuilderService.log(
                "defect",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.DEFECT,
                "Дефект устранен",
                entity,
                saved
        );
        return toResponses(List.of(saved)).getFirst();
    }

    @Transactional
    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        Defect saved = repository.save(entity);

        auditBuilderService.log(
                "defect",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.DEFECT,
                "Дефект удален",
                saved,
                null
        );
    }

    private Defect getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Defect not found: " + id));
    }

    private void apply(Defect entity, DefectRequest request) {
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setEquipmentId(request.equipmentId());
        validateRepairRequestLink(request.repairRequestId());
        entity.setRepairRequestId(request.repairRequestId());
        entity.setCategory(request.category());
        entity.setSeverity(request.severity());
        entity.setFailureReason(request.failureReason());
        entity.setRootCause(request.rootCause());
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }

    private Defect saveWithGeneratedCode(DefectRequest request) {
        int year = Year.now().getValue();
        String codePrefix = "DEF-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;

        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = formatCode("DEF", year, sequence + attempt);
            if (repository.existsByCode(code)) {
                continue;
            }

            Defect entity = new Defect();
            entity.setCode(code);
            apply(entity, request);

            try {
                return repository.save(entity);
            } catch (DataIntegrityViolationException ex) {
                if (isCodeConflict(ex)) {
                    continue;
                }
                throw ex;
            }
        }

        throw RestException.conflict("Could not generate unique defect code");
    }

    private boolean isCodeConflict(DataIntegrityViolationException ex) {
        Throwable root = ex.getMostSpecificCause();
        String message = root != null ? root.getMessage() : ex.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("defects_code_key")
                || (normalized.contains("defects")
                && normalized.contains("duplicate")
                && normalized.contains("code"));
    }

    private void validateRepairRequestLink(UUID repairRequestId) {
        if (repairRequestId == null) {
            return;
        }
        RepairRequest repairRequest = repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)
                .orElseThrow(() -> RestException.notFound("Repair request not found: " + repairRequestId));
        if (DISALLOWED_REPAIR_REQUEST_STATUSES_FOR_DEFECT_LINK.contains(repairRequest.getStatus())) {
            throw RestException.badRequest(
                    "Cannot link defect to repair request in status " + repairRequest.getStatus());
        }
    }

    private Page<DefectResponse> toResponsePage(Page<Defect> defectPage) {
        if (defectPage.isEmpty()) {
            return new PageImpl<>(List.of(), defectPage.getPageable(), defectPage.getTotalElements());
        }
        List<DefectResponse> responses = toResponses(defectPage.getContent());
        return new PageImpl<>(responses, defectPage.getPageable(), defectPage.getTotalElements());
    }

    private List<DefectResponse> toResponses(List<Defect> defects) {
        if (defects.isEmpty()) {
            return List.of();
        }
        List<UUID> equipmentIds = defects.stream()
                .map(Defect::getEquipmentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, String> equipmentNameById = equipmentIds.isEmpty()
                ? Map.of()
                : equipmentRepository.findAllByIdInAndIsDeletedFalse(equipmentIds)
                .stream()
                .collect(Collectors.toMap(Equipment::getId, Equipment::getName));

        List<UUID> repairRequestIds = defects.stream()
                .map(Defect::getRepairRequestId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        Map<UUID, RepairRequest> repairRequestById = repairRequestIds.isEmpty()
                ? Map.of()
                : repairRequestRepository.findAllByIdInAndIsDeletedFalse(repairRequestIds)
                .stream()
                .collect(Collectors.toMap(RepairRequest::getId, Function.identity()));

        List<UUID> defectIds = defects.stream()
                .map(Defect::getId)
                .toList();

        Set<UUID> defectIdsWithLesson = defectIds.isEmpty()
                ? Set.of()
                : Set.copyOf(knowledgeRepository.findDefectIdsWithLesson(
                defectIds,
                "LESSON_LEARNED"
        ));

        Map<UUID, List<WorkOrder>> workOrdersByDefectId = workOrderRepository
                .findAllByDefectIdInAndIsDeletedFalseOrderByUpdatedAtDesc(defectIds)
                .stream()
                .collect(Collectors.groupingBy(WorkOrder::getDefectId));

        return defects.stream()
                .map(defect -> toResponse(
                        defect,
                        equipmentNameById,
                        repairRequestById,
                        workOrdersByDefectId,
                        defectIdsWithLesson))
                .toList();
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

    public KnowledgeArticle createLesson(UUID id) {
        Defect d = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Defect not found: " + id));
        String code = "LL-DEF-" + d.getCode();
        if (knowledgeRepository.existsByCodeAndIsDeletedFalse(code)) {
            throw RestException.conflict("Lesson already exists for defect: " + code);
        }
        KnowledgeArticle a = new KnowledgeArticle();
        a.setCode(code);
        a.setTitle("Дефект " + d.getCode() + ": " + d.getTitle());
        a.setKind("LESSON_LEARNED");
        a.setEquipmentId(d.getEquipmentId());
        a.setDefectId(d.getId());
        a.setProblem(d.getDescription() != null ? d.getDescription() : d.getTitle());
        a.setRootCause(
                d.getRootCause() != null
                        ? d.getRootCause()
                        : (d.getFailureReason() != null
                        ? "Причина отказа: " + d.getFailureReason()
                        : "Требуется заполнить по результатам расследования."));
        a.setSolution("Требуется заполнить по результатам расследования.");
        a.setPreventiveActions("Требуется заполнить по результатам расследования.");

        return knowledgeRepository.save(a);
    }

    private DefectResponse toResponse(Defect defect,
                                      Map<UUID, String> equipmentNameById,
                                      Map<UUID, RepairRequest> repairRequestById,
                                      Map<UUID, List<WorkOrder>> workOrdersByDefectId,
                                      Set<UUID> defectIdsWithLesson) {
        DefectDto dto = DefectDto.from(defect);

        RepairRequestBriefDto repairRequest = getBriefDto(dto,repairRequestById);
        List<WorkOrderBriefDto> linkedWorkOrders = getLinkedWorkOrderBrief(dto,workOrdersByDefectId);

        return DefectResponse.from(
                dto,
                equipmentNameById.get(dto.equipmentId()),
                repairRequest,
                linkedWorkOrders,
                defectIdsWithLesson.contains(dto.id())
        );
    }

    private RepairRequestBriefDto getBriefDto(DefectDto dto, Map<UUID, RepairRequest> repairRequestById){
        return dto.repairRequestId() == null
                ? null
                : TriadLinkMapper.toRepairRequestBrief(repairRequestById.get(dto.repairRequestId()));

    }

    private List<WorkOrderBriefDto> getLinkedWorkOrderBrief(DefectDto dto, Map<UUID, List<WorkOrder>> workOrdersByDefectId) {
        return workOrdersByDefectId
                .getOrDefault(dto.id(), List.of())
                .stream()
                .map(TriadLinkMapper::toWorkOrderBrief)
                .toList();
    }
}
