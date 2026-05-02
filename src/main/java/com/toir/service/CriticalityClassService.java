package com.toir.service;
import com.toir.entity.CriticalityClass;
import com.toir.repository.CriticalityClassRepository;

import com.toir.exception.RestException;
import com.toir.dto.criticalityclass.CriticalityClassDto;
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


    @Transactional(readOnly = true)
    public List<CriticalityClassDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(CriticalityClassDto::from).toList();
    }

    @Transactional(readOnly = true)
    public CriticalityClassDto findById(UUID id) {
        return CriticalityClassDto.from(getOrThrow(id));
    }

    public CriticalityClassDto create(CriticalityClassDto r) {
        CriticalityClass e = new CriticalityClass();
        e.setCode(nextCode());
        apply(e, r);
        return CriticalityClassDto.from(repository.save(e));
    }

    public CriticalityClassDto update(UUID id, CriticalityClassDto r) {
        CriticalityClass e = getOrThrow(id);
        apply(e, r);
        return CriticalityClassDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity); }

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
