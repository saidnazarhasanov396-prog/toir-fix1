package com.toir.service;
import com.toir.entity.ContractorContract;
import com.toir.repository.ContractorContractRepository;

import com.toir.exception.RestException;
import com.toir.dto.contractorcontract.ContractorContractDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractorContractService {

    private final ContractorContractRepository repository;

    @Transactional(readOnly = true)
    public List<ContractorContractDto> findByContractor(UUID contractorId) {
        return repository.findAllByContractorId(contractorId).stream().map(ContractorContractDto::from).toList();
    }

    public ContractorContractDto create(ContractorContractDto r) {
        if (repository.existsByNumber(r.number())) {
            throw RestException.conflict("Contract number already exists: " + r.number());
        }
        ContractorContract c = new ContractorContract();
        apply(c, r);
        return ContractorContractDto.from(repository.save(c));
    }

    public ContractorContractDto update(UUID id, ContractorContractDto r) {
        ContractorContract c = getOrThrow(id);
        apply(c, r);
        return ContractorContractDto.from(c);
    }

    public void delete(UUID id) { repository.delete(getOrThrow(id)); }

    private ContractorContract getOrThrow(UUID id) {
        return repository.findById(id)
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
