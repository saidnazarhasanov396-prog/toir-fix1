package com.toir.service;
import com.toir.entity.ContractorWork;
import com.toir.enums.ContractorWorkStatus;
import com.toir.repository.ContractorWorkRepository;

import com.toir.exception.RestException;
import com.toir.dto.contractorwork.ContractorWorkDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ContractorWorkService {

    private final ContractorWorkRepository repository;

    @Transactional(readOnly = true)
    public List<ContractorWorkDto> findByContractor(UUID contractorId) {
        return repository.findAllByContractorIdAndIsDeletedFalse(contractorId).stream().map(ContractorWorkDto::from).toList();
    }

    public ContractorWorkDto create(ContractorWorkDto r) {
        ContractorWork w = new ContractorWork();
        w.setContractorId(r.contractorId());
        w.setWorkOrderId(r.workOrderId());
        w.setDescription(r.description());
        w.setCreatedById(r.createdById());
        return ContractorWorkDto.from(repository.save(w));
    }

    public ContractorWorkDto start(UUID id) {
        ContractorWork w = getOrThrow(id);
        w.setStatus(ContractorWorkStatus.IN_PROGRESS);
        w.setStartedAt(Instant.now());
        return ContractorWorkDto.from(w);
    }

    public ContractorWorkDto complete(UUID id, String result, Double cost) {
        ContractorWork w = getOrThrow(id);
        if (result == null || result.isBlank()) {
            throw RestException.badRequest("Result is required to complete work");
        }
        w.setStatus(ContractorWorkStatus.COMPLETED);
        w.setCompletedAt(Instant.now());
        w.setResult(result);
        w.setCost(cost);
        return ContractorWorkDto.from(w);
    }

    public ContractorWorkDto accept(UUID id, UUID acceptedById, String comment) {
        ContractorWork w = getOrThrow(id);
        if (w.getStatus() != ContractorWorkStatus.COMPLETED) {
            throw RestException.badRequest("Only completed works can be accepted");
        }
        w.setStatus(ContractorWorkStatus.ACCEPTED);
        w.setAcceptedById(acceptedById);
        w.setAcceptedAt(Instant.now());
        w.setAcceptanceComment(comment);
        return ContractorWorkDto.from(w);
    }

    private ContractorWork getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Contractor work not found: " + id));
    }
}
