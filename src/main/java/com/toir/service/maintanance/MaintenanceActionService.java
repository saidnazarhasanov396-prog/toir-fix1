package com.toir.service.maintanance;

import com.toir.dto.maintenanceaction.MaintenanceActionDto;
import com.toir.dto.maintenanceaction.MaintenanceActionRequest;
import com.toir.entity.maintenance.MaintenanceAction;
import com.toir.exception.RestException;
import com.toir.repository.maintenance.MaintenanceActionRepository;
import com.toir.service.SparePartService;
import com.toir.service.maintenanceembedding.MaintenanceActionEmbeddingLifecyclePort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceActionService {

    private final MaintenanceActionRepository repository;
    private final SparePartService sparePartService;
    private final MaintenanceActionEmbeddingLifecyclePort embeddingLifecycle;
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 50;

    @Transactional(readOnly = true)
    public List<MaintenanceActionDto> findAll(String search, Boolean active) {
        String searchPattern = search != null && !search.isBlank()
                ? sparePartService.toSearchPattern(search)
                : null;
        return repository.search(searchPattern, active).stream()
                .map(MaintenanceActionDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MaintenanceActionDto findById(UUID id) {
        return MaintenanceActionDto.from(getOrThrow(id));
    }

    @Transactional
    public MaintenanceActionDto create(MaintenanceActionRequest request) {
        String codePrefix = "MA-" + Year.now().getValue() + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        MaintenanceAction action = new MaintenanceAction();
        apply(action, request);

        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = codePrefix + String.format("%04d", sequence + attempt);
            action.setCode(code);
            try {
                MaintenanceAction saved = repository.saveAndFlush(action);
                embeddingLifecycle.actionCreated(saved);
                return MaintenanceActionDto.from(saved);
            } catch (DataIntegrityViolationException ex) {
                if (!repository.existsByCodeIgnoreCaseAndIsDeletedFalse(code)) {
                    throw ex;
                }
            }
        }

        throw RestException.conflict("Could not generate unique maintenance action code");
    }

    @Transactional
    public MaintenanceActionDto update(UUID id, MaintenanceActionRequest request) {
        MaintenanceAction action = getOrThrow(id);
        apply(action, request);
        MaintenanceAction saved = repository.saveAndFlush(action);
        embeddingLifecycle.actionUpdated(saved);
        return MaintenanceActionDto.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        MaintenanceAction action = getOrThrow(id);
        action.setDeleted(true);
        MaintenanceAction saved = repository.saveAndFlush(action);
        embeddingLifecycle.actionDeleted(saved);
    }

    private MaintenanceAction getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Maintenance action not found: " + id));
    }

    private void apply(MaintenanceAction action, MaintenanceActionRequest request) {
        action.setName(request.name().trim());
        action.setCategory(blankToNull(request.category()));
        action.setDefaultDurationHours(request.defaultDurationHours());
        action.setRequiredSkill(blankToNull(request.requiredSkill()));
        action.setSafetyNotes(blankToNull(request.safetyNotes()));
        action.setToolsRequired(blankToNull(request.toolsRequired()));
        action.setSparePartsRequired(blankToNull(request.sparePartsRequired()));
        action.setConsumablesRequired(blankToNull(request.consumablesRequired()));
        if (request.active() != null) {
            action.setActive(request.active());
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
