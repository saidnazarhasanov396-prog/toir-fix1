package com.toir.service.contactor;
import com.toir.entity.contractors.ContractorContract;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.contarctor.ContractorContractRepository;

import com.toir.exception.RestException;
import com.toir.dto.contractorcontract.ContractorContractDto;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractorContractService {

    private final ContractorContractRepository repository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<ContractorContractDto> findByContractor(UUID contractorId) {
        return repository.findAllByContractorIdAndIsDeletedFalse(contractorId).stream().map(ContractorContractDto::from).toList();
    }

    @Transactional
    public ContractorContractDto create(ContractorContractDto r) {
        if (repository.existsByNumberAndIsDeletedFalse(r.number())) {
            throw RestException.conflict("Contract number already exists: " + r.number());
        }
        ContractorContract c = new ContractorContract();
        apply(c, r);
        ContractorContract saved = repository.save(c);

        auditBuilderService.log(
                "contractor_contract",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.CONTRACTOR_CONTRACT,
                "Договор подрядчика создан" ,
                null,
                saved
        );
        return ContractorContractDto.from(saved);
    }

    @Transactional
    public ContractorContractDto update(UUID id, ContractorContractDto r) {
        ContractorContract c = getOrThrow(id);
        apply(c, r);

        ContractorContract save = repository.save(c);
        auditBuilderService.log(
                "contractor_contract",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.CONTRACTOR_CONTRACT,
                "Договор подрядчика обновлен" ,
                c,
                save
        );

        return ContractorContractDto.from(c);
    }

    @Transactional
    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        ContractorContract saved = repository.save(entity);

        auditBuilderService.log(
                "contractor_contract",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.CONTRACTOR_CONTRACT,
                "Договор подрядчика удален" ,
                entity,
                null
        );
    }

    private ContractorContract getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Contract not found: " + id));
    }

    private void apply(ContractorContract c, ContractorContractDto r) {
        c.setContractorId(r.contractorId());
        c.setNumber(r.number());
        c.setSubject(r.subject());
        c.setStartDate(r.startDate());
        c.setEndDate(r.endDate());
        c.setAmount(r.amount());
        if (r.status() != null) c.setStatus(r.status());
    }
}
