package com.toir.service;

import com.toir.dto.knowledge.KnowledgeArticleDto;
import com.toir.dto.knowledge.KnowledgeArticleLinkDto;
import com.toir.dto.knowledge.KnowledgeArticleRequest;
import com.toir.dto.knowledge.KnowledgeContextResponse;
import com.toir.dto.knowledge.KnowledgeStatsResponse;
import com.toir.dto.knowledge.KnowledgeSuggestionDto;
import com.toir.entity.KnowledgeArticle;
import com.toir.entity.KnowledgeArticleLink;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.KnowledgeTargetType;
import com.toir.exception.RestException;
import com.toir.repository.KnowledgeArticleLinkRepository;
import com.toir.repository.KnowledgeArticleRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeArticleRepository repository;
    private final KnowledgeArticleLinkRepository linkRepository;
    private final EquipmentRepository equipmentRepository;
    private final DefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final ScopeAccessService scopeAccessService;
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 50;

    public KnowledgeService(KnowledgeArticleRepository repository,
                            EquipmentRepository equipmentRepository,
                            DefectRepository defectRepository,
                            WorkOrderRepository workOrderRepository,
                            ScopeAccessService scopeAccessService) {
        this.repository = repository;
        this.linkRepository = null;
        this.equipmentRepository = equipmentRepository;
        this.defectRepository = defectRepository;
        this.workOrderRepository = workOrderRepository;
        this.repairRequestRepository = null;
        this.scopeAccessService = scopeAccessService;
    }

    @Transactional(readOnly = true)
    public Page<KnowledgeArticleDto> list(UUID equipmentId, UUID equipmentTypeId, String kind, int page, int size) {
        String normalizedKind = kind == null || kind.isBlank() ? null : kind.trim();
        if (equipmentId != null) {
            return PaginationUtils
                    .page(repository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId), page, size)
                    .map(this::toDto);
        }
        if (equipmentTypeId != null) {
            return PaginationUtils
                    .page(repository.findAllByEquipmentTypeIdAndIsDeletedFalse(equipmentTypeId), page, size)
                    .map(this::toDto);
        }
        if (normalizedKind != null) {
            return PaginationUtils
                    .page(repository.findAllByKindAndIsDeletedFalse(normalizedKind), page, size)
                    .map(this::toDto);
        }
        return PaginationUtils
                .page(repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc(), page, size)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public KnowledgeContextResponse context(KnowledgeTargetType targetType, UUID targetId, int size) {
        TargetContext target = resolveTargetContext(targetType, targetId);
        int limit = Math.max(1, Math.min(size, 20));

        List<KnowledgeArticleLink> directLinks = linksForTarget(targetType, targetId);
        List<UUID> linkedArticleIds = directLinks.stream()
                .map(KnowledgeArticleLink::getKnowledgeArticleId)
                .distinct()
                .toList();
        Map<UUID, List<KnowledgeArticleLink>> directLinksByArticle = directLinks.stream()
                .collect(Collectors.groupingBy(KnowledgeArticleLink::getKnowledgeArticleId));
        List<KnowledgeArticleDto> linked = linkedArticleIds.isEmpty()
                ? List.of()
                : repository.findAllByIdInAndIsDeletedFalse(linkedArticleIds).stream()
                .map(article -> KnowledgeArticleDto.from(
                        article,
                        toLinkDtos(directLinksByArticle.getOrDefault(article.getId(), List.of()))))
                .toList();

        List<KnowledgeArticle> candidates = repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        List<UUID> candidateIds = candidates.stream().map(KnowledgeArticle::getId).toList();
        Map<UUID, List<KnowledgeArticleLink>> linksByArticle = linksForArticleIds(candidateIds).stream()
                .collect(Collectors.groupingBy(KnowledgeArticleLink::getKnowledgeArticleId));
        Set<UUID> linkedIdSet = new LinkedHashSet<>(linkedArticleIds);

        Comparator<KnowledgeSuggestionDto> suggestionComparator = Comparator
                .comparingInt(KnowledgeSuggestionDto::score)
                .reversed()
                .thenComparing(suggestion -> suggestion.article().updatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder()));

        List<KnowledgeSuggestionDto> suggestions = candidates.stream()
                .filter(article -> article.getId() != null && !linkedIdSet.contains(article.getId()))
                .map(article -> scoreSuggestion(
                        article,
                        linksByArticle.getOrDefault(article.getId(), List.of()),
                        target))
                .filter(suggestion -> suggestion.score() > 0)
                .sorted(suggestionComparator)
                .limit(limit)
                .toList();

        return new KnowledgeContextResponse(linked, suggestions);
    }

    @Transactional(readOnly = true)
    public KnowledgeStatsResponse getStats(UUID equipmentId, UUID equipmentTypeId, String kind) {
        String normalizedKind = kind == null || kind.isBlank() ? null : kind.trim();
        var stats = repository.getKnowledgeStats(equipmentId, equipmentTypeId, normalizedKind);
        return new KnowledgeStatsResponse(
                stats.getTotalArticles() == null ? 0 : stats.getTotalArticles(),
                stats.getLessonLearned() == null ? 0 : stats.getLessonLearned(),
                stats.getProcedures() == null ? 0 : stats.getProcedures(),
                stats.getTroubleshooting() == null ? 0 : stats.getTroubleshooting()
        );
    }

    @Transactional
    public KnowledgeArticle get(UUID id) {
        KnowledgeArticle article = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Article not found: " + id));
        article.setViewCount(article.getViewCount() + 1);
        article.setLinks(linksForArticle(article.getId()));
        return article;
    }

    @Transactional
    public KnowledgeArticleDto create(KnowledgeArticleRequest request) {
        return toDto(create(request.toArticle()));
    }

    @Transactional
    public KnowledgeArticle create(KnowledgeArticle article) {
        validateLinkedScope(article);
        explicitLinks(article).forEach(this::validateLinkTarget);
        List<KnowledgeArticleLinkDto> requestedLinks = requestedLinks(article);
        if (article.getKind() == null) {
            article.setKind("LESSON_LEARNED");
        }
        article.setTags(normalizeTags(article.getTags()));
        KnowledgeArticle saved = saveWithGeneratedCode(article);
        upsertLinks(saved.getId(), requestedLinks);
        saved.setLinks(linksForArticle(saved.getId()));
        return saved;
    }

    @Transactional
    public KnowledgeArticle update(UUID id, KnowledgeArticle patch) {
        KnowledgeArticle existing = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Article not found: " + id));
        validateLinkedScope(existing);
        validateLinkedScope(patch);
        explicitLinks(patch).forEach(this::validateLinkTarget);
        List<KnowledgeArticleLinkDto> requestedLinks = requestedLinks(patch);
        existing.setTitle(patch.getTitle());
        existing.setKind(patch.getKind() != null ? patch.getKind() : existing.getKind());
        existing.setEquipmentTypeId(patch.getEquipmentTypeId());
        existing.setEquipmentId(patch.getEquipmentId());
        existing.setDefectId(patch.getDefectId());
        existing.setWorkOrderId(patch.getWorkOrderId());
        existing.setProblem(patch.getProblem());
        existing.setRootCause(patch.getRootCause());
        existing.setSolution(patch.getSolution());
        existing.setPreventiveActions(patch.getPreventiveActions());
        existing.setTags(normalizeTags(patch.getTags()));
        upsertLinks(existing.getId(), requestedLinks);
        existing.setLinks(linksForArticle(existing.getId()));
        return existing;
    }

    @Transactional
    public KnowledgeArticleDto link(UUID articleId, KnowledgeArticleLinkDto link) {
        KnowledgeArticle article = repository.findByIdAndIsDeletedFalse(articleId)
                .orElseThrow(() -> RestException.notFound("Article not found: " + articleId));
        validateLinkTarget(link);
        upsertLink(articleId, link);
        return toDto(article);
    }

    @Transactional
    public void unlink(UUID articleId, KnowledgeTargetType targetType, UUID targetId) {
        repository.findByIdAndIsDeletedFalse(articleId)
                .orElseThrow(() -> RestException.notFound("Article not found: " + articleId));
        validateLinkTarget(new KnowledgeArticleLinkDto(null, targetType, targetId));
        if (linkRepository == null) {
            return;
        }
        linkRepository.findActive(articleId, targetType, targetId).ifPresent(link -> {
            link.setDeleted(true);
            linkRepository.save(link);
        });
    }

    @Transactional
    public void delete(UUID id) {
        KnowledgeArticle entity = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Article not found: " + id));
        validateLinkedScope(entity);
        entity.setDeleted(true);
        repository.save(entity);
    }

    private KnowledgeArticleDto toDto(KnowledgeArticle article) {
        return KnowledgeArticleDto.from(article, linksForArticle(article.getId()));
    }

    private List<KnowledgeArticleLinkDto> linksForArticle(UUID articleId) {
        if (articleId == null || linkRepository == null) {
            return List.of();
        }
        return toLinkDtos(linkRepository.findAllByKnowledgeArticleIdAndIsDeletedFalseOrderByUpdatedAtDesc(articleId));
    }

    private List<KnowledgeArticleLink> linksForArticleIds(List<UUID> articleIds) {
        if (articleIds == null || articleIds.isEmpty() || linkRepository == null) {
            return List.of();
        }
        return linkRepository.findAllByKnowledgeArticleIdInAndIsDeletedFalse(articleIds);
    }

    private List<KnowledgeArticleLink> linksForTarget(KnowledgeTargetType targetType, UUID targetId) {
        if (linkRepository == null) {
            return List.of();
        }
        return linkRepository.findAllByTargetTypeAndTargetIdAndIsDeletedFalseOrderByUpdatedAtDesc(targetType, targetId);
    }

    private List<KnowledgeArticleLinkDto> toLinkDtos(List<KnowledgeArticleLink> links) {
        return links == null ? List.of() : links.stream().map(KnowledgeArticleLinkDto::from).toList();
    }

    private List<KnowledgeArticleLinkDto> requestedLinks(KnowledgeArticle article) {
        Map<String, KnowledgeArticleLinkDto> unique = new LinkedHashMap<>();
        explicitLinks(article).forEach(link -> unique.put(linkKey(link.targetType(), link.targetId()), link));
        putLegacyLink(unique, KnowledgeTargetType.EQUIPMENT, article.getEquipmentId());
        putLegacyLink(unique, KnowledgeTargetType.WORK_ORDER, article.getWorkOrderId());
        putLegacyLink(unique, KnowledgeTargetType.DEFECT, article.getDefectId());
        return new ArrayList<>(unique.values());
    }

    private List<KnowledgeArticleLinkDto> explicitLinks(KnowledgeArticle article) {
        if (article.getLinks() == null) {
            return List.of();
        }
        return article.getLinks().stream()
                .filter(link -> link != null && link.targetType() != null && link.targetId() != null)
                .toList();
    }

    private void putLegacyLink(Map<String, KnowledgeArticleLinkDto> links,
                               KnowledgeTargetType targetType,
                               UUID targetId) {
        if (targetId == null) {
            return;
        }
        links.putIfAbsent(linkKey(targetType, targetId), new KnowledgeArticleLinkDto(null, targetType, targetId));
    }

    private String linkKey(KnowledgeTargetType targetType, UUID targetId) {
        return targetType.name() + ":" + targetId;
    }

    private void upsertLinks(UUID articleId, List<KnowledgeArticleLinkDto> links) {
        if (articleId == null || links == null || linkRepository == null) {
            return;
        }
        links.forEach(link -> upsertLink(articleId, link));
    }

    private void upsertLink(UUID articleId, KnowledgeArticleLinkDto dto) {
        if (dto == null || dto.targetType() == null || dto.targetId() == null || linkRepository == null) {
            return;
        }
        if (linkRepository.findActive(articleId, dto.targetType(), dto.targetId()).isPresent()) {
            return;
        }
        KnowledgeArticleLink link = new KnowledgeArticleLink();
        link.setKnowledgeArticleId(articleId);
        link.setTargetType(dto.targetType());
        link.setTargetId(dto.targetId());
        linkRepository.save(link);
    }

    private KnowledgeSuggestionDto scoreSuggestion(KnowledgeArticle article,
                                                   List<KnowledgeArticleLink> articleLinks,
                                                   TargetContext target) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        if (target.equipmentId() != null
                && (target.equipmentId().equals(article.getEquipmentId())
                || hasTarget(articleLinks, KnowledgeTargetType.EQUIPMENT, target.equipmentId()))) {
            score += 100;
            reasons.add("Same equipment");
        }
        if (target.repairRequestId() != null
                && hasTarget(articleLinks, KnowledgeTargetType.REPAIR_REQUEST, target.repairRequestId())) {
            score += 80;
            reasons.add("Same repair request");
        }
        if (target.workOrderId() != null
                && (target.workOrderId().equals(article.getWorkOrderId())
                || hasTarget(articleLinks, KnowledgeTargetType.WORK_ORDER, target.workOrderId()))) {
            score += 80;
            reasons.add("Same work order");
        }
        if (target.equipmentTypeId() != null && target.equipmentTypeId().equals(article.getEquipmentTypeId())) {
            score += 60;
            reasons.add("Same equipment type");
        }
        int textHits = textHits(target.searchText(), article);
        if (textHits > 0) {
            score += Math.min(40, textHits * 5);
            reasons.add("Similar text/tags");
        }
        return new KnowledgeSuggestionDto(toDto(article), score, reasons);
    }

    private boolean hasTarget(List<KnowledgeArticleLink> links, KnowledgeTargetType targetType, UUID targetId) {
        return links != null && links.stream()
                .anyMatch(link -> targetType == link.getTargetType() && targetId.equals(link.getTargetId()));
    }

    private int textHits(String searchText, KnowledgeArticle article) {
        Set<String> targetTokens = tokens(searchText);
        if (targetTokens.isEmpty()) {
            return 0;
        }
        Set<String> articleTokens = tokens(String.join(" ",
                safe(article.getTitle()),
                safe(article.getProblem()),
                safe(article.getRootCause()),
                safe(article.getSolution()),
                safe(article.getPreventiveActions()),
                article.getTags() == null ? "" : String.join(" ", article.getTags())));
        targetTokens.retainAll(articleTokens);
        return targetTokens.size();
    }

    private Set<String> tokens(String text) {
        if (text == null || text.isBlank()) {
            return new LinkedHashSet<>();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 3) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private TargetContext resolveTargetContext(KnowledgeTargetType targetType, UUID targetId) {
        if (targetType == null || targetId == null) {
            throw RestException.badRequest("targetType and targetId are required");
        }
        return switch (targetType) {
            case EQUIPMENT -> {
                Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(targetId)
                        .orElseThrow(() -> RestException.notFound("Equipment not found: " + targetId));
                assertDepartmentScope(equipment.getDepartmentId());
                yield new TargetContext(targetId, targetId, equipment.getEquipmentTypeId(), null, null,
                        String.join(" ", safe(equipment.getCode()), safe(equipment.getName()), safe(equipment.getModel())));
            }
            case REPAIR_REQUEST -> {
                RepairRequest request = requireRepairRequest(targetId);
                assertDepartmentScope(request.getDepartmentId());
                UUID equipmentTypeId = equipmentTypeId(request.getEquipmentId());
                yield new TargetContext(request.getId(), request.getEquipmentId(), equipmentTypeId, request.getId(), null,
                        String.join(" ", safe(request.getNumber()), safe(request.getTitle()),
                                safe(request.getDescription()), safe(request.getCloseResult())));
            }
            case WORK_ORDER -> {
                WorkOrder workOrder = workOrderRepository.findByIdAndIsDeletedFalse(targetId)
                        .orElseThrow(() -> RestException.notFound("Work order not found: " + targetId));
                assertDepartmentScope(workOrder.getDepartmentId());
                UUID equipmentTypeId = equipmentTypeId(workOrder.getEquipmentId());
                yield new TargetContext(workOrder.getId(), workOrder.getEquipmentId(), equipmentTypeId,
                        workOrder.getRepairRequestId(), workOrder.getId(),
                        String.join(" ", safe(workOrder.getNumber()), safe(workOrder.getTitle()),
                                safe(workOrder.getSummary()), safe(workOrder.getResult()), safe(workOrder.getClosureNotes())));
            }
            case DEFECT -> {
                Defect defect = defectRepository.findByIdAndIsDeletedFalse(targetId)
                        .orElseThrow(() -> RestException.notFound("Defect not found: " + targetId));
                Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(defect.getEquipmentId())
                        .orElseThrow(() -> RestException.notFound("Equipment not found: " + defect.getEquipmentId()));
                assertDepartmentScope(equipment.getDepartmentId());
                yield new TargetContext(defect.getId(), defect.getEquipmentId(), equipment.getEquipmentTypeId(),
                        defect.getRepairRequestId(), null,
                        String.join(" ", safe(defect.getCode()), safe(defect.getTitle()),
                                safe(defect.getDescription()), safe(defect.getRootCause()), safe(defect.getFailureReason())));
            }
        };
    }

    private RepairRequest requireRepairRequest(UUID id) {
        if (repairRequestRepository == null) {
            throw RestException.notFound("Repair request not found: " + id);
        }
        return repairRequestRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Repair request not found: " + id));
    }

    private UUID equipmentTypeId(UUID equipmentId) {
        if (equipmentId == null) {
            return null;
        }
        return equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .map(Equipment::getEquipmentTypeId)
                .orElse(null);
    }

    private void validateLinkTarget(KnowledgeArticleLinkDto link) {
        if (link == null || link.targetType() == null || link.targetId() == null) {
            throw RestException.badRequest("Knowledge article link targetType and targetId are required");
        }
        resolveTargetContext(link.targetType(), link.targetId());
    }

    private void validateLinkedScope(KnowledgeArticle article) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (article.getEquipmentId() != null) {
            UUID departmentId = equipmentRepository.findByIdAndIsDeletedFalse(article.getEquipmentId())
                    .orElseThrow(() -> RestException.notFound("Equipment not found: " + article.getEquipmentId()))
                    .getDepartmentId();
            scopeAccessService.assertCanAccessDepartment(departmentId);
        }
        if (article.getDefectId() != null) {
            Defect defect = defectRepository.findByIdAndIsDeletedFalse(article.getDefectId())
                    .orElseThrow(() -> RestException.notFound("Defect not found: " + article.getDefectId()));
            UUID departmentId = equipmentRepository.findByIdAndIsDeletedFalse(defect.getEquipmentId())
                    .orElseThrow(() -> RestException.notFound("Equipment not found: " + defect.getEquipmentId()))
                    .getDepartmentId();
            scopeAccessService.assertCanAccessDepartment(departmentId);
        }
        if (article.getWorkOrderId() != null) {
            UUID departmentId = workOrderRepository.findByIdAndIsDeletedFalse(article.getWorkOrderId())
                    .orElseThrow(() -> RestException.notFound("Work order not found: " + article.getWorkOrderId()))
                    .getDepartmentId();
            scopeAccessService.assertCanAccessDepartment(departmentId);
        }
    }

    private void assertDepartmentScope(UUID departmentId) {
        if (!scopeAccessService.isScopeAdmin()) {
            scopeAccessService.assertCanAccessDepartment(departmentId);
        }
    }

    private List<String> normalizeTags(List<String> tags) {
        return tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    private KnowledgeArticle saveWithGeneratedCode(KnowledgeArticle article) {
        int year = Year.now().getValue();
        String codePrefix = "LL-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;

        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = formatCode("LL", year, sequence + attempt);
            if (repository.existsByCode(code)) {
                continue;
            }
            article.setCode(code);
            try {
                return repository.save(article);
            } catch (DataIntegrityViolationException ex) {
                if (isCodeConflict(ex)) {
                    continue;
                }
                throw ex;
            }
        }

        throw RestException.conflict("Could not generate unique knowledge article code");
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }

    private boolean isCodeConflict(DataIntegrityViolationException ex) {
        Throwable root = ex.getMostSpecificCause();
        String message = root != null ? root.getMessage() : ex.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("knowledge_articles_code_key")
                || (normalized.contains("knowledge_articles")
                && normalized.contains("duplicate")
                && normalized.contains("code"));
    }

    private record TargetContext(
            UUID targetId,
            UUID equipmentId,
            UUID equipmentTypeId,
            UUID repairRequestId,
            UUID workOrderId,
            String searchText
    ) {
    }
}
