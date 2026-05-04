package com.toir.service;
import com.toir.entity.RootCause;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.RootCauseRepository;

import com.toir.exception.RestException;
import com.toir.dto.rootcause.RootCauseDto;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class RootCauseService {

    private final RootCauseRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<RootCauseDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(RootCauseDto::from).toList();
    }

    public RootCauseDto create(RootCauseDto r) {
        RootCause e = new RootCause();
        e.setCode(nextCode());
        e.setName(r.name()); e.setDescription(r.description());
        RootCause saved = repository.save(e);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return RootCauseDto.from(saved);
    }

    public RootCauseDto update(UUID id, RootCauseDto r) {
        RootCause e = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(e);
        e.setName(r.name()); e.setDescription(r.description());
        audit(AuditAction.UPDATE, e.getId(), oldJson, e);
        return RootCauseDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        RootCause saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null); }

    private RootCause getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Root cause not found: " + id));
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "RC-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("RC", year, sequence);
        while (repository.existsByCodeAndIsDeletedFalse(code)) {
            sequence++;
            code = formatCode("RC", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }

    private void audit(AuditAction action, UUID id, String oldJson, RootCause current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "root_cause",
                id != null ? id.toString() : null,
                action,
                AuditModule.ROOT_CAUSE,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Корневая причина создана";
            case UPDATE -> "Корневая причина обновлена";
            case DELETE -> "Корневая причина удалена";
            default -> "Действие выполнено над корневой причиной";
        };
    }
}
