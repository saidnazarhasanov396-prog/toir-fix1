package com.toir.service;

import com.toir.dto.serviceclass.ServiceClassDto;
import com.toir.entity.ServiceClass;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.ServiceClassRepository;
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
public class ServiceClassService {

    private final ServiceClassRepository repository;
    private final AuditBuilderService auditBuilderService;


    @Transactional(readOnly = true)
    public List<ServiceClassDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(ServiceClassDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ServiceClassDto findById(UUID id) {
        return ServiceClassDto.from(getOrThrow(id));
    }

    @Transactional
    public ServiceClassDto create(ServiceClassDto r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        ServiceClass e = new ServiceClass();
        e.setCode(nextCode());
        e.setName(r.name()); e.setDescription(r.description());
        ServiceClass saved = repository.save(e);

        auditBuilderService.log(
                "service_class",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.SERVICE_CLASS,
                "Класс обслуживания создан",
                null,
                saved
        );
        return ServiceClassDto.from(saved);
    }

    @Transactional
    public ServiceClassDto update(UUID id, ServiceClassDto r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        ServiceClass e = getOrThrow(id);
        e.setName(r.name()); e.setDescription(r.description());

        ServiceClass saved = repository.save(e);

        auditBuilderService.log(
                "service_class",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.SERVICE_CLASS,
                "Класс обслуживания обновлен",
                e,
                saved
        );

        return ServiceClassDto.from(e);
    }

    @Transactional
    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        ServiceClass saved = repository.save(entity);

        auditBuilderService.log(
                "service_class",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.SERVICE_CLASS,
                "Класс обслуживания удален",
                saved,
                null
        );
    }

    private ServiceClass getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Service class not found: " + id));
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "SC-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("SC", year, sequence);
        while (repository.existsByCodeAndIsDeletedFalse(code)) {
            sequence++;
            code = formatCode("SC", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }
}
