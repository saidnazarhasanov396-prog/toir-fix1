package com.toir.service.maintanance;

import com.toir.dto.maintenanceaction.MaintenanceActionDto;
import com.toir.dto.maintenanceaction.MaintenanceActionRequest;
import com.toir.entity.maintenance.MaintenanceAction;
import com.toir.exception.RestException;
import com.toir.repository.maintenance.MaintenanceActionRepository;
import com.toir.service.SparePartService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceActionService {

    private final MaintenanceActionRepository repository;
    private final SparePartService sparePartService;

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
        String code = normalizeCode(request.code());
        if (repository.existsByCodeIgnoreCaseAndIsDeletedFalse(code)) {
            throw RestException.conflict("Maintenance action code already exists: " + code);
        }

        MaintenanceAction action = new MaintenanceAction();
        action.setCode(code);
        apply(action, request);

        try {
            return MaintenanceActionDto.from(repository.save(action));
        } catch (DataIntegrityViolationException ex) {
            throw RestException.conflict("Maintenance action code already exists: " + code);
        }
    }

    @Transactional
    public MaintenanceActionDto update(UUID id, MaintenanceActionRequest request) {
        MaintenanceAction action = getOrThrow(id);
        String code = normalizeCode(request.code());
        if (!action.getCode().equalsIgnoreCase(code)
                && repository.existsByCodeIgnoreCaseAndIsDeletedFalse(code)) {
            throw RestException.conflict("Maintenance action code already exists: " + code);
        }
        action.setCode(code);
        apply(action, request);
        return MaintenanceActionDto.from(repository.save(action));
    }

    @Transactional
    public void delete(UUID id) {
        MaintenanceAction action = getOrThrow(id);
        action.setDeleted(true);
        repository.save(action);
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

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw RestException.badRequest("Maintenance action code is required");
        }
        return code.trim().replaceAll("\\s+", "_").toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
