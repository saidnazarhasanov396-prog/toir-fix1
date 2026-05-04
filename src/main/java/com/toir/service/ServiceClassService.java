package com.toir.service;
import com.toir.entity.ServiceClass;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.ServiceClassRepository;

import com.toir.exception.RestException;
import com.toir.dto.serviceclass.ServiceClassDto;
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
public class ServiceClassService {

    private final ServiceClassRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<ServiceClassDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(ServiceClassDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ServiceClassDto findById(UUID id) {
        return ServiceClassDto.from(getOrThrow(id));
    }

    public ServiceClassDto create(ServiceClassDto r) {
        ServiceClass e = new ServiceClass();
        e.setCode(nextCode());
        e.setName(r.name()); e.setDescription(r.description());
        ServiceClass saved = repository.save(e);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return ServiceClassDto.from(saved);
    }

    public ServiceClassDto update(UUID id, ServiceClassDto r) {
        ServiceClass e = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(e);
        e.setName(r.name()); e.setDescription(r.description());
        audit(AuditAction.UPDATE, e.getId(), oldJson, e);
        return ServiceClassDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        ServiceClass saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null); }

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

    private void audit(AuditAction action, UUID id, String oldJson, ServiceClass current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "service_class",
                id != null ? id.toString() : null,
                action,
                AuditModule.SERVICE_CLASS,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Класс обслуживания создан";
            case UPDATE -> "Класс обслуживания обновлен";
            case DELETE -> "Класс обслуживания удален";
            default -> "Действие выполнено над классом обслуживания";
        };
    }
}
