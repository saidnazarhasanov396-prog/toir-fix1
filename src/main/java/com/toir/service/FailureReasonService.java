package com.toir.service;
import com.toir.entity.FailureReason;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.FailureReasonRepository;

import com.toir.exception.RestException;
import com.toir.dto.failurereason.FailureReasonDto;
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
public class FailureReasonService {

    private final FailureReasonRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<FailureReasonDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(FailureReasonDto::from).toList();
    }

    public FailureReasonDto create(FailureReasonDto r) {
        FailureReason e = new FailureReason();
        e.setCode(nextCode());
        e.setName(r.name()); e.setDescription(r.description());
        FailureReason saved = repository.save(e);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return FailureReasonDto.from(saved);
    }

    public FailureReasonDto update(UUID id, FailureReasonDto r) {
        FailureReason e = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(e);
        e.setName(r.name()); e.setDescription(r.description());
        audit(AuditAction.UPDATE, e.getId(), oldJson, e);
        return FailureReasonDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        FailureReason saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null); }

    private FailureReason getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Failure reason not found: " + id));
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "FR-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("FR", year, sequence);
        while (repository.existsByCodeAndIsDeletedFalse(code)) {
            sequence++;
            code = formatCode("FR", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }

    private void audit(AuditAction action, UUID id, String oldJson, FailureReason current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "failure_reason",
                id != null ? id.toString() : null,
                action,
                AuditModule.FAILURE_REASON,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Причина отказа создана";
            case UPDATE -> "Причина отказа обновлена";
            case DELETE -> "Причина отказа удалена";
            default -> "Действие выполнено над причиной отказа";
        };
    }
}
