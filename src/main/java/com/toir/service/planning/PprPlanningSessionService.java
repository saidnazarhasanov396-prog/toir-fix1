package com.toir.service.planning;

import com.toir.dto.pprplanning.PprPlanningSessionCreateRequest;
import com.toir.dto.pprplanning.PprPlanningVariantRequest;
import com.toir.entity.planning.PprPlanningSession;
import com.toir.entity.planning.PprPlanningVariant;
import com.toir.entity.planning.PprPlanningVariantItem;
import com.toir.enums.planning.PprPlanningSessionStatus;
import com.toir.enums.planning.PprPlanningVariantStatus;
import com.toir.exception.RestException;
import com.toir.repository.planning.PprPlanningSessionRepository;
import com.toir.repository.planning.PprPlanningVariantItemRepository;
import com.toir.repository.planning.PprPlanningVariantRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PprPlanningSessionService {

    private final PprPlanningSessionRepository sessionRepository;
    private final PprPlanningVariantRepository variantRepository;
    private final PprPlanningVariantItemRepository itemRepository;

    @Transactional
    public PprPlanningSession create(PprPlanningSessionCreateRequest request) {
        if (request == null || !request.isAnnualBoundaryValid()) {
            throw RestException.badRequest("Invalid annual PPR planning boundary");
        }
        PprPlanningSession session = new PprPlanningSession();
        session.setName(request.name().trim());
        session.setYear(request.year());
        session.setDepartmentId(request.departmentId());
        session.setStartDate(request.startDate());
        session.setEndDate(request.endDate());
        session.setNotes(request.notes());
        session.setStatus(PprPlanningSessionStatus.DRAFT);
        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<PprPlanningSession> list() {
        return sessionRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public PprPlanningSession get(UUID sessionId) {
        return findSession(sessionId);
    }

    @Transactional(readOnly = true)
    public List<PprPlanningVariant> variants(UUID sessionId) {
        findSession(sessionId);
        return variantRepository.findAllBySessionIdAndIsDeletedFalseOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public PprPlanningVariant createVariant(UUID sessionId, PprPlanningVariantRequest request) {
        PprPlanningSession session = lockMutableSession(sessionId);
        String name = normalizedRequiredName(request);
        requireUniqueName(sessionId, name, null);
        PprPlanningVariant variant = new PprPlanningVariant();
        variant.setSession(session);
        variant.setName(name);
        variant.setRevision(0);
        variant.setHashVersion(PprVariantContentHasher.VERSION);
        variant.setStatus(PprPlanningVariantStatus.DRAFT);
        return variantRepository.save(variant);
    }

    @Transactional
    public PprPlanningVariant renameVariant(
            UUID sessionId,
            UUID variantId,
            PprPlanningVariantRequest request) {
        lockMutableSession(sessionId);
        PprPlanningVariant variant = findVariant(sessionId, variantId);
        String name = normalizedRequiredName(request);
        requireUniqueName(sessionId, name, variantId);
        variant.setName(name);
        return variantRepository.save(variant);
    }

    @Transactional
    public PprPlanningVariant copyVariant(
            UUID sessionId,
            UUID sourceVariantId,
            PprPlanningVariantRequest request) {
        PprPlanningSession session = lockMutableSession(sessionId);
        PprPlanningVariant source = findVariant(sessionId, sourceVariantId);
        String name = normalizedRequiredName(request);
        requireUniqueName(sessionId, name, null);
        PprPlanningVariant copy = new PprPlanningVariant();
        copy.setSession(session);
        copy.setName(name);
        copy.setRevision(0);
        copy.setHashVersion(PprVariantContentHasher.VERSION);
        copy.setStatus(PprPlanningVariantStatus.DRAFT);
        copy.setInputsJson(source.getInputsJson());
        return variantRepository.save(copy);
    }

    @Transactional(readOnly = true)
    public List<PprPlanningVariantItem> revision(
            UUID sessionId,
            UUID variantId,
            long revision) {
        findVariant(sessionId, variantId);
        if (revision < 1) {
            throw RestException.badRequest("Variant revision must be positive");
        }
        return itemRepository.findAllByVariantIdAndRevisionOrderBySourceItemKey(variantId, revision);
    }

    private PprPlanningSession lockMutableSession(UUID sessionId) {
        PprPlanningSession session = sessionRepository.findByIdAndIsDeletedFalseForUpdate(sessionId)
                .orElseThrow(() -> RestException.notFound(
                        "PPR planning session not found: " + sessionId));
        if (session.getStatus() == PprPlanningSessionStatus.PENDING_APPROVAL
                || session.getStatus() == PprPlanningSessionStatus.APPROVED
                || session.getStatus() == PprPlanningSessionStatus.CANCELLED) {
            throw new RestException(
                    "PPR planning session is immutable",
                    HttpStatus.CONFLICT,
                    "PPR_PLANNING_SESSION_IMMUTABLE");
        }
        return session;
    }

    private PprPlanningSession findSession(UUID sessionId) {
        return sessionRepository.findByIdAndIsDeletedFalse(sessionId)
                .orElseThrow(() -> RestException.notFound(
                        "PPR planning session not found: " + sessionId));
    }

    private PprPlanningVariant findVariant(UUID sessionId, UUID variantId) {
        return variantRepository.findByIdAndSessionIdAndIsDeletedFalse(variantId, sessionId)
                .orElseThrow(() -> RestException.notFound(
                        "PPR planning variant not found: " + variantId));
    }

    private void requireUniqueName(UUID sessionId, String name, UUID currentVariantId) {
        String normalized = name.toLowerCase(Locale.ROOT);
        boolean exists = currentVariantId == null
                ? variantRepository.existsBySessionIdAndNormalizedNameAndIsDeletedFalse(
                        sessionId, normalized)
                : variantRepository.existsBySessionIdAndNormalizedNameAndIdNotAndIsDeletedFalse(
                        sessionId, normalized, currentVariantId);
        if (exists) {
            throw new RestException(
                    "Variant name already exists in the planning session",
                    HttpStatus.CONFLICT,
                    "PPR_PLANNING_VARIANT_NAME_EXISTS");
        }
    }

    private static String normalizedRequiredName(PprPlanningVariantRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw RestException.badRequest("Variant name is required");
        }
        return request.name().trim();
    }
}
