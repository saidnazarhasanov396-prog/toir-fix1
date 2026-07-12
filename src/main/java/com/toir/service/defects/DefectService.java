package com.toir.service.defects;

import com.toir.dto.attachment.AttachmentPhotoSummary;
import com.toir.dto.defect.DefectDto;
import com.toir.dto.defect.DefectRequest;
import com.toir.dto.defect.DefectResponse;
import com.toir.dto.defect.DefectStatsResponse;
import com.toir.dto.triad.RepairRequestBriefDto;
import com.toir.dto.triad.TriadLinkMapper;
import com.toir.dto.triad.WorkOrderBriefDto;
import com.toir.entity.Department;
import com.toir.entity.KnowledgeArticle;
import com.toir.entity.Location;
import com.toir.entity.defects.Defect;
import com.toir.entity.defects.DefectList;
import com.toir.entity.defects.DefectListLine;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentNode;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.repair.RepairCampaign;
import com.toir.entity.users.BrigadeMember;
import com.toir.entity.users.User;
import com.toir.enums.AttachmentTargetType;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.DefectListStatus;
import com.toir.enums.DefectStatus;
import com.toir.enums.RequestStatus;
import com.toir.exception.RestException;
import com.toir.repository.KnowledgeArticleRepository;
import com.toir.repository.LocationRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.defects.DefectListLineRepository;
import com.toir.repository.defects.DefectListRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentNodeRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.projects.BrigadeMemberRepository;
import com.toir.repository.projection.DefectStatsProjection;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.OperationalIssueLifecycleSyncService;
import com.toir.service.attachment.AttachmentGroupService;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import com.toir.util.AuditBuilderService;
import com.toir.util.CodeGenerationUtils;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
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
    private final DefectListRepository defectListRepository;
    private final DefectListLineRepository defectListLineRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentNodeRepository equipmentNodeRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final RepairCampaignRepository repairCampaignRepository;
    private final WorkOrderRepository workOrderRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final BrigadeMemberRepository brigadeMemberRepository;
    private final AuditBuilderService auditBuilderService;
    private final ScopeAccessService scopeAccessService;
    private final EquipmentStatusLifecycleService equipmentStatusLifecycleService;
    private final OperationalIssueLifecycleSyncService operationalIssueLifecycleSyncService;
    private static final Set<RequestStatus> DISALLOWED_REPAIR_REQUEST_STATUSES_FOR_DEFECT_LINK =
            EnumSet.of(RequestStatus.REJECTED, RequestStatus.CLOSED, RequestStatus.CANCELLED);
    private final KnowledgeArticleRepository knowledgeRepository;
    private final AttachmentGroupService attachmentGroupService;
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 50;


    @Transactional(readOnly = true)
    public List<DefectResponse> findAll() {
        return toResponses(repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc());
    }

    @Transactional(readOnly = true)
    public Page<DefectResponse> search(
            UUID equipmentId,
            UUID repairRequestId,
            DefectStatus status,
            String category,
            String severity,
            int page,
            int size,
            String search
    ) {
        var pageable = PaginationUtils.pageRequest(page, size);
        Page<Defect> resultPage = repository.searchPaginated(
                equipmentId,
                repairRequestId,
                status == null ? null : status.name(),
                category,
                severity,
                search,
                pageable
        );
        if (!scopeAccessService.isScopeAdmin()) {
            List<Defect> scopedContent = resultPage.getContent()
                    .stream()
                    .filter(this::canAccessDefect)
                    .toList();
            return toResponsePage(new PageImpl<>(scopedContent, pageable, scopedContent.size()));
        }
        return toResponsePage(resultPage);
    }

    @Transactional(readOnly = true)
    public Page<DefectResponse> searchByRepairCampaign(
            UUID campaignId, DefectStatus status, String severity, int page, int size) {
        RepairCampaign campaign = repairCampaignRepository.findByIdAndIsDeletedFalse(campaignId)
                .orElseThrow(() -> RestException.notFound("Repair campaign not found: " + campaignId));
        scopeAccessService.assertCanAccessDepartment(campaign.getDepartmentId());
        var pageable = PaginationUtils.pageRequest(page, size);
        return toResponsePage(repository.searchByRepairCampaign(
                campaignId,
                status == null ? null : status.name(),
                severity == null || severity.isBlank() ? null : severity.trim(),
                pageable));
    }

    @Transactional(readOnly = true)
    public Page<DefectResponse> search(UUID equipmentId, UUID repairRequestId, DefectStatus status, int page, int size, String search, Sort sort) {
        return search(equipmentId, repairRequestId, status, null, null, page, size, search, sort);
    }

    @Transactional(readOnly = true)
    public Page<DefectResponse> search(UUID equipmentId, UUID repairRequestId, DefectStatus status, String category, String severity, int page, int size, String search, Sort sort) {
        var pageable = PaginationUtils.pageRequest(page, size, sort == null ? Sort.by(Sort.Direction.DESC, "updatedAt") : sort);
        Page<Defect> resultPage = repository.findAll(defectListSpecification(equipmentId, repairRequestId, status, category, severity, search), pageable);
        if (!scopeAccessService.isScopeAdmin()) {
            List<Defect> scopedContent = resultPage.getContent()
                    .stream()
                    .filter(this::canAccessDefect)
                    .toList();
            return toResponsePage(new PageImpl<>(scopedContent, pageable, scopedContent.size()));
        }
        return toResponsePage(resultPage);
    }

    private Specification<Defect> defectListSpecification(UUID equipmentId,
                                                          UUID repairRequestId,
                                                          DefectStatus status,
                                                          String category,
                                                          String severity,
                                                          String search) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (equipmentId != null) {
                predicates.add(cb.equal(root.get("equipmentId"), equipmentId));
            }
            if (repairRequestId != null) {
                predicates.add(cb.equal(root.get("repairRequestId"), repairRequestId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("category")), category.trim().toUpperCase(Locale.ROOT)));
            }
            if (severity != null && !severity.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("severity")), severity.trim().toUpperCase(Locale.ROOT)));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("code"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("title"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("category"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("severity"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("failureReason"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("rootCause"), "")), pattern)
                ));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public DefectResponse findById(UUID id) {
        Defect defect = getOrThrow(id);
        assertCanAccessDefect(defect);
        return toResponses(List.of(defect)).getFirst();
    }

    @Transactional(readOnly = true)
    public List<DefectResponse> findByEquipment(UUID equipmentId) {
        return toResponses(repository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId));
    }

    @Transactional(readOnly = true)
    public DefectStatsResponse getStats(
            UUID equipmentId,
            UUID repairRequestId,
            String category,
            String severity,
            String search
    ) {
        String searchPattern = toSearchPattern(search);

        if (!scopeAccessService.isScopeAdmin()) {
            List<Defect> scopedDefects = repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()
                    .stream()
                    .filter(defect -> matchesStatsFilter(defect, equipmentId, repairRequestId, category, severity, search))
                    .filter(this::canAccessDefect)
                    .toList();
            return scopedStats(scopedDefects);
        }

        DefectStatsProjection stats = repository.getDefectStats(
                equipmentId,
                repairRequestId,
                category,
                severity,
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
        CodeGenerationUtils.rejectClientProvidedCode(request.code());
        DefectList defectList = validateDefectListForCreate(request);
        assertCanAccessDefectRequest(request);
        equipmentStatusLifecycleService.assertOperationallyAllowed(request.equipmentId(), "create defect");
        Defect saved = saveWithGeneratedCode(request);
        createDefectListLine(defectList, saved);

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
        CodeGenerationUtils.rejectClientProvidedCode(request.code());
        Defect entity = getOrThrow(id);
        assertCanAccessDefect(entity);
        assertCanAccessDefectRequest(request);
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
        assertCanAccessDefect(entity);
        entity.setStatus(DefectStatus.RESOLVED);
        entity.setResolvedAt(Instant.now());

        Defect saved = repository.save(entity);
        operationalIssueLifecycleSyncService.resolveDefectIssueIfTerminal(saved, "Defect resolved directly.");

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
        assertCanAccessDefect(entity);
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
        validateRepairRequestLink(request.repairRequestId(), request.equipmentId());
        entity.setRepairRequestId(request.repairRequestId());
        validateEquipmentNodeLink(request.equipmentNodeId(), request.equipmentId());
        entity.setEquipmentNodeId(request.equipmentNodeId());
        entity.setCategory(request.category());
        entity.setSeverity(request.severity());
        entity.setFailureReason(request.failureReason());
        entity.setRootCause(request.rootCause());
    }

    private DefectList validateDefectListForCreate(DefectRequest request) {
        if (request.defectListId() == null) {
            throw RestException.badRequest("defectListId is required");
        }
        DefectList defectList = defectListRepository.findByIdAndIsDeletedFalse(request.defectListId())
                .orElseThrow(() -> RestException.notFound("Defect list not found: " + request.defectListId()));
        if (!canAccessDefectList(defectList)) {
            throw new AccessDeniedException("Access denied by defect list department scope");
        }
        if (defectList.getStatus() == DefectListStatus.CLOSED || defectList.getStatus() == DefectListStatus.CANCELLED) {
            throw RestException.badRequest("Cannot add defect to closed/cancelled defect list");
        }
        if (!Objects.equals(defectList.getEquipmentId(), request.equipmentId())) {
            throw RestException.badRequest("Defect list belongs to a different equipment");
        }
        if (request.repairRequestId() != null
                && defectList.getRepairRequestId() != null
                && !Objects.equals(defectList.getRepairRequestId(), request.repairRequestId())) {
            throw RestException.badRequest("Defect list belongs to a different repair request");
        }
        return defectList;
    }

    private void createDefectListLine(DefectList defectList, Defect defect) {
        DefectListLine line = new DefectListLine();
        line.setDefectList(defectList);
        line.setDefectId(defect.getId());
        line.setDescription(defect.getDescription());
        line.setRequiredQuantity(0);
        line.setEstimatedLaborHours(0);
        line.setEstimatedCost(0);
        defectList.getLines().add(line);
        defectListLineRepository.save(line);
        defectListRepository.save(defectList);
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

    private void validateRepairRequestLink(UUID repairRequestId, UUID equipmentId) {
        if (repairRequestId == null) {
            return;
        }
        RepairRequest repairRequest = repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)
                .orElseThrow(() -> RestException.notFound("Repair request not found: " + repairRequestId));
        if (DISALLOWED_REPAIR_REQUEST_STATUSES_FOR_DEFECT_LINK.contains(repairRequest.getStatus())) {
            throw RestException.badRequest(
                    "Cannot link defect to repair request in status " + repairRequest.getStatus());
        }
        if (equipmentId != null
                && repairRequest.getEquipmentId() != null
                && !equipmentId.equals(repairRequest.getEquipmentId())) {
            throw RestException.badRequest(
                    "Repair request belongs to a different equipment");
        }
    }

    private void validateEquipmentNodeLink(UUID equipmentNodeId, UUID equipmentId) {
        if (equipmentNodeId == null) {
            return;
        }
        EquipmentNode node = equipmentNodeRepository.findByIdAndIsDeletedFalse(equipmentNodeId)
                .orElseThrow(() -> RestException.notFound("Equipment node not found: " + equipmentNodeId));
        if (!Objects.equals(node.getEquipmentId(), equipmentId)) {
            throw RestException.badRequest("Equipment node belongs to a different equipment");
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

        List<UUID> equipmentNodeIds = defects.stream()
                .map(Defect::getEquipmentNodeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, EquipmentNode> equipmentNodeById = equipmentNodeIds.isEmpty()
                ? Map.of()
                : equipmentNodeRepository.findAllByIdInAndIsDeletedFalse(equipmentNodeIds)
                .stream()
                .collect(Collectors.toMap(EquipmentNode::getId, Function.identity()));

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

        Map<UUID, UUID> defectListIdByDefectId = defectListLineRepository
                .findAllByDefectIdInAndIsDeletedFalse(defectIds)
                .stream()
                .filter(line -> line.getDefectId() != null && line.getDefectList() != null)
                .collect(Collectors.toMap(
                        DefectListLine::getDefectId,
                        line -> line.getDefectList().getId(),
                        (first, second) -> first
                ));

        Map<UUID, String> departmentNameById = departmentNameById(repairRequestById.values());
        Map<UUID, String> locationNameById = locationNameById(repairRequestById.values());

        List<WorkOrder> allWorkOrders = workOrdersByDefectId.values().stream()
                .flatMap(List::stream)
                .toList();
        Map<UUID, UUID> userIdByBrigadeMemberId = userIdByBrigadeMemberId(allWorkOrders);

        Set<UUID> userIds = new HashSet<>();
        repairRequestById.values().stream()
                .map(RepairRequest::getAssignedToId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        userIdByBrigadeMemberId.values().stream()
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        Map<UUID, String> userNameById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllByIdInAndIsDeletedFalse(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        Map<UUID, AttachmentPhotoSummary> photoSummaryByDefectId =
                attachmentGroupService.getPhotoSummaries(AttachmentTargetType.DEFECT, defectIds);

        return defects.stream()
                .map(defect -> toResponse(
                        defect,
                        equipmentNameById,
                        equipmentNodeById,
                        repairRequestById,
                        workOrdersByDefectId,
                        defectListIdByDefectId,
                        defectIdsWithLesson,
                        departmentNameById,
                        locationNameById,
                        userIdByBrigadeMemberId,
                        userNameById,
                        photoSummaryByDefectId))
                .toList();
    }

    private Map<UUID, String> departmentNameById(Collection<RepairRequest> repairRequests) {
        List<UUID> departmentIds = repairRequests.stream()
                .map(RepairRequest::getDepartmentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return departmentIds.isEmpty()
                ? Map.of()
                : departmentRepository.findAllByIdInAndIsDeletedFalse(departmentIds)
                .stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
    }

    private Map<UUID, String> locationNameById(Collection<RepairRequest> repairRequests) {
        List<UUID> locationIds = repairRequests.stream()
                .map(RepairRequest::getLocationId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return locationIds.isEmpty()
                ? Map.of()
                : locationRepository.findAllByIdInAndIsDeletedFalse(locationIds)
                .stream()
                .collect(Collectors.toMap(Location::getId, Location::getName));
    }

    private Map<UUID, UUID> userIdByBrigadeMemberId(List<WorkOrder> workOrders) {
        List<UUID> brigadeMemberIds = workOrders.stream()
                .map(workOrder -> workOrder.getPerformer() == null ? null : workOrder.getPerformer().getId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return brigadeMemberIds.isEmpty()
                ? Map.of()
                : brigadeMemberRepository.findAllByIdInAndIsDeletedFalse(brigadeMemberIds)
                .stream()
                .collect(Collectors.toMap(BrigadeMember::getId, BrigadeMember::getUserId));
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
        assertCanAccessDefect(d);
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

    private void assertCanAccessDefect(Defect defect) {
        if (!canAccessDefect(defect)) {
            throw new AccessDeniedException("Access denied by defect department scope");
        }
    }

    private boolean canAccessDefect(Defect defect) {
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        return resolveDefectDepartmentId(defect)
                .map(scopeAccessService::canAccessDepartment)
                .orElse(false);
    }

    private boolean canAccessDefectList(DefectList defectList) {
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        return resolveDefectListDepartmentId(defectList)
                .map(scopeAccessService::canAccessDepartment)
                .orElse(false);
    }

    private void assertCanAccessDefectRequest(DefectRequest request) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        Optional<UUID> departmentId = resolveDefectDepartmentId(request.equipmentId(), request.repairRequestId());
        if (departmentId.isEmpty() || !scopeAccessService.canAccessDepartment(departmentId.get())) {
            throw new AccessDeniedException("Access denied by defect department scope");
        }
    }

    private Optional<UUID> resolveDefectDepartmentId(Defect defect) {
        return resolveDefectDepartmentId(defect.getEquipmentId(), defect.getRepairRequestId());
    }

    private Optional<UUID> resolveDefectDepartmentId(UUID equipmentId, UUID repairRequestId) {
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
        return departments.size() == 1 ? Optional.of(departments.iterator().next()) : Optional.empty();
    }

    private Optional<UUID> resolveDefectListDepartmentId(DefectList defectList) {
        return resolveDefectDepartmentId(defectList.getEquipmentId(), defectList.getRepairRequestId());
    }

    private boolean matchesStatsFilter(
            Defect defect,
            UUID equipmentId,
            UUID repairRequestId,
            String category,
            String severity,
            String search
    ) {
        if (equipmentId != null && !equipmentId.equals(defect.getEquipmentId())) {
            return false;
        }
        if (repairRequestId != null && !repairRequestId.equals(defect.getRepairRequestId())) {
            return false;
        }
        if (category != null && !category.equalsIgnoreCase(defect.getCategory())) {
            return false;
        }
        if (severity != null && !severity.equalsIgnoreCase(defect.getSeverity())) {
            return false;
        }
        if (search == null || search.isBlank()) {
            return true;
        }
        String normalized = search.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(defect.getCode(), normalized)
                || containsIgnoreCase(defect.getTitle(), normalized)
                || containsIgnoreCase(defect.getDescription(), normalized)
                || containsIgnoreCase(defect.getCategory(), normalized)
                || containsIgnoreCase(defect.getSeverity(), normalized)
                || containsIgnoreCase(defect.getFailureReason(), normalized)
                || containsIgnoreCase(defect.getRootCause(), normalized);
    }

    private boolean containsIgnoreCase(String value, String normalizedSearch) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedSearch);
    }

    private DefectStatsResponse scopedStats(List<Defect> defects) {
        long open = defects.stream().filter(defect -> defect.getStatus() == DefectStatus.OPEN).count();
        long resolved = defects.stream().filter(defect -> defect.getStatus() == DefectStatus.RESOLVED).count();
        long withRecurrence = defects.stream().filter(defect -> defect.getRecurrenceCount() > 0).count();
        return new DefectStatsResponse(defects.size(), open, resolved, withRecurrence);
    }

    private DefectResponse toResponse(Defect defect,
                                      Map<UUID, String> equipmentNameById,
                                      Map<UUID, EquipmentNode> equipmentNodeById,
                                      Map<UUID, RepairRequest> repairRequestById,
                                      Map<UUID, List<WorkOrder>> workOrdersByDefectId,
                                      Map<UUID, UUID> defectListIdByDefectId,
                                      Set<UUID> defectIdsWithLesson,
                                      Map<UUID, String> departmentNameById,
                                      Map<UUID, String> locationNameById,
                                      Map<UUID, UUID> userIdByBrigadeMemberId,
                                      Map<UUID, String> userNameById,
                                      Map<UUID, AttachmentPhotoSummary> photoSummaryByDefectId) {
        DefectDto dto = DefectDto.from(defect);

        RepairRequestBriefDto repairRequest = getBriefDto(dto, repairRequestById, departmentNameById, locationNameById, userNameById);
        List<WorkOrderBriefDto> linkedWorkOrders = getLinkedWorkOrderBrief(dto, workOrdersByDefectId, userIdByBrigadeMemberId, userNameById);
        EquipmentNode equipmentNode = dto.equipmentNodeId() == null ? null : equipmentNodeById.get(dto.equipmentNodeId());

        return DefectResponse.from(
                dto,
                equipmentNameById.get(dto.equipmentId()),
                equipmentNode == null ? null : equipmentNode.getCode(),
                equipmentNode == null ? null : equipmentNode.getName(),
                equipmentNode == null ? null : equipmentNode.getNodeType(),
                defectListIdByDefectId.get(dto.id()),
                repairRequest,
                linkedWorkOrders,
                defectIdsWithLesson.contains(dto.id()),
                photoSummaryByDefectId.get(dto.id())
        );
    }

    private RepairRequestBriefDto getBriefDto(DefectDto dto,
                                              Map<UUID, RepairRequest> repairRequestById,
                                              Map<UUID, String> departmentNameById,
                                              Map<UUID, String> locationNameById,
                                              Map<UUID, String> userNameById) {
        if (dto.repairRequestId() == null) {
            return null;
        }
        RepairRequest repairRequest = repairRequestById.get(dto.repairRequestId());
        if (repairRequest == null) {
            return null;
        }
        return TriadLinkMapper.toRepairRequestBrief(
                repairRequest,
                repairRequest.getAssignedToId() == null ? null : userNameById.get(repairRequest.getAssignedToId()),
                repairRequest.getDepartmentId() == null ? null : departmentNameById.get(repairRequest.getDepartmentId()),
                repairRequest.getLocationId() == null ? null : locationNameById.get(repairRequest.getLocationId())
        );
    }

    private List<WorkOrderBriefDto> getLinkedWorkOrderBrief(DefectDto dto,
                                                             Map<UUID, List<WorkOrder>> workOrdersByDefectId,
                                                             Map<UUID, UUID> userIdByBrigadeMemberId,
                                                             Map<UUID, String> userNameById) {
        return workOrdersByDefectId
                .getOrDefault(dto.id(), List.of())
                .stream()
                .map(workOrder -> {
                    UUID brigadeMemberId = workOrder.getPerformer() == null ? null : workOrder.getPerformer().getId();
                    UUID userId = brigadeMemberId == null ? null : userIdByBrigadeMemberId.get(brigadeMemberId);
                    String assigneeName = userId == null ? null : userNameById.get(userId);
                    return TriadLinkMapper.toWorkOrderBrief(workOrder, assigneeName);
                })
                .toList();
    }
}
