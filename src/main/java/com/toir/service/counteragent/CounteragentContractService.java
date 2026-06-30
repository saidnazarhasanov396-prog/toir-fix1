package com.toir.service.counteragent;

import com.toir.dto.counteragent.CounteragentContractDto;
import com.toir.entity.contractors.ContractorContract;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.contarctor.ContractorContractRepository;
import com.toir.service.CounteragentService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CounteragentContractService {

    private final ContractorContractRepository repository;
    private final CounteragentService counteragentService;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<CounteragentContractDto> findByCounteragent(UUID counteragentId) {
        return repository.findAllByCounteragentIdAndIsDeletedFalse(counteragentId)
                .stream()
                .map(CounteragentContractDto::from)
                .toList();
    }

    @Transactional
    public CounteragentContractDto create(CounteragentContractDto request) {
        counteragentService.load(request.counteragentId());
        if (repository.existsByNumberAndIsDeletedFalse(request.number())) {
            throw RestException.conflict("Counteragent contract number already exists: " + request.number());
        }
        ContractorContract contract = new ContractorContract();
        apply(contract, request);
        ContractorContract saved = repository.save(contract);
        auditBuilderService.log(
                "counteragent_contract",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.COUNTERAGENT_CONTRACT,
                "Counteragent contract created",
                null,
                saved
        );
        return CounteragentContractDto.from(saved);
    }

    @Transactional
    public CounteragentContractDto update(UUID id, CounteragentContractDto request) {
        counteragentService.load(request.counteragentId());
        ContractorContract contract = getOrThrow(id);
        apply(contract, request);
        ContractorContract saved = repository.save(contract);
        auditBuilderService.log(
                "counteragent_contract",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.COUNTERAGENT_CONTRACT,
                "Counteragent contract updated",
                contract,
                saved
        );
        return CounteragentContractDto.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        ContractorContract contract = getOrThrow(id);
        contract.setDeleted(true);
        ContractorContract saved = repository.save(contract);
        auditBuilderService.log(
                "counteragent_contract",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.COUNTERAGENT_CONTRACT,
                "Counteragent contract deleted",
                contract,
                null
        );
    }

    private ContractorContract getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Counteragent contract not found: " + id));
    }

    private void apply(ContractorContract contract, CounteragentContractDto request) {
        contract.setCounteragentId(request.counteragentId());
        contract.setNumber(request.number());
        contract.setSubject(request.subject());
        contract.setStartDate(request.startDate());
        contract.setEndDate(request.endDate());
        contract.setAmount(request.amount());
        if (request.status() != null) {
            contract.setStatus(request.status());
        }
    }
}
