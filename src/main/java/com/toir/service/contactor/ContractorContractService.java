package com.toir.service.contactor;
import com.toir.entity.contractors.ContractorContract;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.contarctor.ContractorContractRepository;

import com.toir.exception.RestException;
import com.toir.dto.contractorcontract.ContractorContractDto;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ContractorContractService {

    private final ContractorContractRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;

    @Transactional(readOnly = true)
    public List<ContractorContractDto> findByContractor(UUID contractorId) {
        return repository.findAllByContractorIdAndIsDeletedFalse(contractorId).stream().map(ContractorContractDto::from).toList();
    }

    public ContractorContractDto create(ContractorContractDto r) {
        if (repository.existsByNumberAndIsDeletedFalse(r.number())) {
            throw RestException.conflict("Contract number already exists: " + r.number());
        }
        ContractorContract c = new ContractorContract();
        apply(c, r);
        ContractorContract saved = repository.save(c);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return ContractorContractDto.from(saved);
    }

    public ContractorContractDto update(UUID id, ContractorContractDto r) {
        ContractorContract c = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(c);
        apply(c, r);
        audit(AuditAction.UPDATE, c.getId(), oldJson, c);
        return ContractorContractDto.from(c);
    }

    public void delete(UUID id) {
        var entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        ContractorContract saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null);
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

    private void audit(AuditAction action, UUID id, String oldJson, ContractorContract current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "contractor_contract",
                id != null ? id.toString() : null,
                action,
                AuditModule.CONTRACTOR_CONTRACT,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Договор подрядчика создан";
            case UPDATE -> "Договор подрядчика обновлен";
            case DELETE -> "Договор подрядчика удален";
            default -> "Действие выполнено над договором подрядчика";
        };
    }
}
