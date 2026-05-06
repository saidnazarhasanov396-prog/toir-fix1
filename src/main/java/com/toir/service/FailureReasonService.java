package com.toir.service;

import com.toir.dto.failurereason.FailureReasonDto;
import com.toir.entity.FailureReason;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.FailureReasonRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FailureReasonService {

    private final FailureReasonRepository repository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<FailureReasonDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(FailureReasonDto::from).toList();
    }

    @Transactional
    public FailureReasonDto create(FailureReasonDto r) {
        FailureReason e = new FailureReason();
        e.setCode(nextCode());
        e.setName(r.name()); e.setDescription(r.description());
        FailureReason saved = repository.save(e);

        auditBuilderService.log(
                "failure_reason",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.FAILURE_REASON,
                "Причина отказа создана",
                null,
                saved
        );

        return FailureReasonDto.from(saved);
    }

    @Transactional
    public FailureReasonDto update(UUID id, FailureReasonDto r) {
        FailureReason e = getOrThrow(id);
        e.setName(r.name()); e.setDescription(r.description());

        FailureReason saved = repository.save(e);

        auditBuilderService.log(
                "failure_reason",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.FAILURE_REASON,
                "Причина отказа обновлена",
                null,
                saved
        );
        return FailureReasonDto.from(e);
    }

    @Transactional
    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        FailureReason saved = repository.save(entity);

        auditBuilderService.log(
                "failure_reason",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.FAILURE_REASON,
                "Причина отказа удалена",
                saved,
                null
        );
    }

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
}
