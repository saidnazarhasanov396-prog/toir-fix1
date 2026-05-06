package com.toir.service;

import com.toir.dto.criticalityclass.CriticalityClassDto;
import com.toir.entity.equipment.CriticalityClass;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.CriticalityClassRepository;
import com.toir.util.AuditBuilderService;
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


    @Transactional(readOnly = true)
    public List<CriticalityClassDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(CriticalityClassDto::from).toList();
    }

    @Transactional(readOnly = true)
    public CriticalityClassDto findById(UUID id) {
        return CriticalityClassDto.from(getOrThrow(id));
    }

    @Transactional()
    public CriticalityClassDto create(CriticalityClassDto r) {
        CriticalityClass e = new CriticalityClass();
        e.setCode(nextCode());
        apply(e, r);
        CriticalityClass saved = repository.save(e);

        auditBuilderService.log(
                "criticality_class",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.CRITICALITY_CLASS,
                "Класс критичности создан",
                null,
                saved
        );


        return CriticalityClassDto.from(saved);
    }

    @Transactional
    public CriticalityClassDto update(UUID id, CriticalityClassDto r) {
        CriticalityClass e = getOrThrow(id);
        apply(e, r);

        CriticalityClass saved = repository.save(e);

        auditBuilderService.log(
                "criticality_class",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.CRITICALITY_CLASS,
                "Класс критичности обновлен",
                e,
                saved
        );


        return CriticalityClassDto.from(e);
    }

    @Transactional
    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        CriticalityClass saved = repository.save(entity);

        auditBuilderService.log(
                "criticality_class",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.CRITICALITY_CLASS,
                "Класс критичности удален",
                saved,
                null
        );
    }

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
}
