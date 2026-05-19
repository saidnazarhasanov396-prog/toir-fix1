package com.toir.service;

import com.toir.dto.knowledge.KnowledgeArticleDto;
import com.toir.dto.knowledge.KnowledgeStatsResponse;
import com.toir.entity.KnowledgeArticle;
import com.toir.entity.defects.Defect;
import com.toir.exception.RestException;
import com.toir.repository.KnowledgeArticleRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.time.Year;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeArticleRepository repository;
    private final EquipmentRepository equipmentRepository;
    private final DefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final ScopeAccessService scopeAccessService;
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 50;

    @Transactional(readOnly = true)
    public Page<KnowledgeArticleDto> list(UUID equipmentId, UUID equipmentTypeId, String kind, int page, int size) {
        String normalizedKind = kind == null || kind.isBlank() ? null : kind.trim();
        if (equipmentId != null) {
            return PaginationUtils
                    .page(repository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId), page, size)
                    .map(KnowledgeArticleDto::from);
        }
        if (equipmentTypeId != null) {
            return PaginationUtils
                    .page(repository.findAllByEquipmentTypeIdAndIsDeletedFalse(equipmentTypeId), page, size)
                    .map(KnowledgeArticleDto::from);
        }
        if (normalizedKind != null) {
            return PaginationUtils
                    .page(repository.findAllByKindAndIsDeletedFalse(normalizedKind), page, size)
                    .map(KnowledgeArticleDto::from);
        }
        return PaginationUtils
                .page(repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc(), page, size)
                .map(KnowledgeArticleDto::from);
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
        return article;
    }

    @Transactional
    public KnowledgeArticle create(KnowledgeArticle article) {
        validateLinkedScope(article);
        if (article.getKind() == null) {
            article.setKind("LESSON_LEARNED");
        }
        article.setTags(normalizeTags(article.getTags()));
        return saveWithGeneratedCode(article);
    }

    @Transactional
    public KnowledgeArticle update(UUID id, KnowledgeArticle patch) {
        KnowledgeArticle existing = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Article not found: " + id));
        validateLinkedScope(existing);
        validateLinkedScope(patch);
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
        return existing;
    }

    @Transactional
    public void delete(UUID id) {
        KnowledgeArticle entity = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Article not found: " + id));
        validateLinkedScope(entity);
        entity.setDeleted(true);
        repository.save(entity);
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
}
