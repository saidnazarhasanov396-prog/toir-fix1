package com.toir.service;
import com.toir.entity.equipment.CriticalityClass;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.CriticalityClassRepository;

import com.toir.exception.RestException;
import com.toir.dto.criticalityclass.CriticalityClassDto;
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
public class CriticalityClassService {

    private final CriticalityClassRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<CriticalityClassDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(CriticalityClassDto::from).toList();
    }

    @Transactional(readOnly = true)
    public CriticalityClassDto findById(UUID id) {
        return CriticalityClassDto.from(getOrThrow(id));
    }

    public CriticalityClassDto create(CriticalityClassDto r) {
        CriticalityClass e = new CriticalityClass();
        e.setCode(nextCode());
        apply(e, r);
        CriticalityClass saved = repository.save(e);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return CriticalityClassDto.from(saved);
    }

    public CriticalityClassDto update(UUID id, CriticalityClassDto r) {
        CriticalityClass e = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(e);
        apply(e, r);
        audit(AuditAction.UPDATE, e.getId(), oldJson, e);
        return CriticalityClassDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        CriticalityClass saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null); }

    private CriticalityClass getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Criticality class not found: " + id));
    }

    private void apply(CriticalityClass e, CriticalityClassDto r) {
        e.setName(r.name());
        e.setNameEn(r.nameEn());
        e.setNameUz(r.nameUz());
        e.setLevel(r.level());
        e.setDescription(r.description());
        e.setSafetyImpact(r.safetyImpact());
        e.setProductionImpact(r.productionImpact());
        e.setEcologicalImpact(r.ecologicalImpact());
        e.setEnergyImpact(r.energyImpact());
        e.setFailureConsequence(r.failureConsequence());
        e.setRepairPriority(r.repairPriority());
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "CRIT-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("CRIT", year, sequence);
        while (repository.existsByCodeAndIsDeletedFalse(code)) {
            sequence++;
            code = formatCode("CRIT", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }

    private void audit(AuditAction action, UUID id, String oldJson, CriticalityClass current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "criticality_class",
                id != null ? id.toString() : null,
                action,
                AuditModule.CRITICALITY_CLASS,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Класс критичности создан";
            case UPDATE -> "Класс критичности обновлен";
            case DELETE -> "Класс критичности удален";
            default -> "Действие выполнено над классом критичности";
        };
    }
}
