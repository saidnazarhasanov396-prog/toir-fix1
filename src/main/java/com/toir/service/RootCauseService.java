package com.toir.service;

import com.toir.dto.rootcause.RootCauseDto;
import com.toir.entity.RootCause;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.RootCauseRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.CodeGenerationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RootCauseService {

    private final RootCauseRepository repository;
    private final AuditBuilderService auditBuilderService;


    @Transactional(readOnly = true)
    public List<RootCauseDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(RootCauseDto::from).toList();
    }

    @Transactional
    public RootCauseDto create(RootCauseDto r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        RootCause e = new RootCause();
        e.setCode(nextCode());
        e.setName(r.name());
        e.setDescription(r.description());
        RootCause saved = repository.save(e);

        auditBuilderService.log(
                "root_cause",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.ROOT_CAUSE,
                "Корневая причина создана",
                null,
                saved
        );

        return RootCauseDto.from(saved);
    }

    @Transactional
    public RootCauseDto update(UUID id, RootCauseDto r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        RootCause e = getOrThrow(id);
        e.setName(r.name());
        e.setDescription(r.description());

        RootCause saved = repository.save(e);
        auditBuilderService.log(
                "root_cause",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.ROOT_CAUSE,
                "Корневая причина обновлена",
                e,
                saved
        );

        return RootCauseDto.from(e);
    }

    @Transactional
    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        RootCause saved = repository.save(entity);

        auditBuilderService.log(
                "root_cause",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.ROOT_CAUSE,
                "Корневая причина удалена",
                saved,
                null
        );

    }

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
}
