package com.toir.service.contactor;

import com.toir.dto.contractor.ContractorDto;
import com.toir.dto.contractor.ContractorRequest;
import com.toir.entity.contractors.Contractor;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.contarctor.ContractorRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.CodeGenerationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractorService {

    private final ContractorRepository repository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<ContractorDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(ContractorDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ContractorDto findById(UUID id) {
        return ContractorDto.from(getOrThrow(id));
    }

    @Transactional
    public ContractorDto create(ContractorRequest request) {
        CodeGenerationUtils.rejectClientProvidedCode(request.code());
        Contractor entity = new Contractor();
        entity.setCode(nextCode());
        apply(entity, request);
        Contractor saved = repository.save(entity);

        auditBuilderService.log(
                "contractor",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.CONTRACTOR,
                "Подрядчик создан" ,
                null,
                saved
        );

        return ContractorDto.from(saved);
    }

    @Transactional
    public ContractorDto update(UUID id, ContractorRequest request) {
        CodeGenerationUtils.rejectClientProvidedCode(request.code());
        Contractor entity = getOrThrow(id);

        apply(entity, request);
        Contractor save = repository.save(entity);

        auditBuilderService.log(
                "contractor",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.CONTRACTOR,
                "Подрядчик обновлен",
                entity,
                save
        );
        return ContractorDto.from(entity);
    }

    @Transactional
    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        Contractor saved = repository.save(entity);

        auditBuilderService.log(
                "contractor",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.CONTRACTOR,
                "Подрядчик удален",
                entity,
                null
        );

    }

    private Contractor getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Contractor not found: " + id));
    }

    private void apply(Contractor entity, ContractorRequest request) {
        entity.setName(request.name());
        entity.setTaxNumber(request.taxNumber());
        entity.setContactPerson(request.contactPerson());
        entity.setPhone(request.phone());
        entity.setEmail(request.email());
        entity.setSpecialization(request.specialization());
        if (request.status() != null) entity.setStatus(request.status());
    }

    private String nextCode() {
        String prefix = "CTR-" + java.time.Year.now().getValue() + "-";
        return CodeGenerationUtils.nextYearSequenceCode(
                "CTR",
                () -> repository.maxSequenceByCodePrefix(prefix),
                repository::existsByCodeAndIsDeletedFalse
        );
    }

}
