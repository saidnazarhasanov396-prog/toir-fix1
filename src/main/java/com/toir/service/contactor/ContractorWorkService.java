package com.toir.service.contactor;

import com.toir.dto.contractorwork.ContractorWorkDto;
import com.toir.entity.contractors.ContractorWork;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.ContractorWorkStatus;
import com.toir.exception.RestException;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractorWorkService {

    private final ContractorWorkRepository repository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<ContractorWorkDto> findByContractor(UUID contractorId) {
        return repository.findAllByContractorIdAndIsDeletedFalse(contractorId).stream().map(ContractorWorkDto::from).toList();
    }

    @Transactional
    public ContractorWorkDto create(ContractorWorkDto r) {
        ContractorWork w = new ContractorWork();
        w.setContractorId(r.contractorId());
        w.setWorkOrderId(r.workOrderId());
        w.setDescription(r.description());
        w.setCreatedById(r.createdById());
        ContractorWork saved = repository.save(w);

        auditBuilderService.log(
                "contractor_work",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.CONTRACTOR_WORK,
                "Работа подрядчика создана",
                null,
                saved
        );

        return ContractorWorkDto.from(saved);
    }

    @Transactional
    public ContractorWorkDto start(UUID id) {
        ContractorWork w = getOrThrow(id);

        w.setStatus(ContractorWorkStatus.IN_PROGRESS);
        w.setStartedAt(Instant.now());
        ContractorWork saved = repository.save(w);

        auditBuilderService.log(
                "contractor_work",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.CONTRACTOR_WORK,
                "Работа подрядчика обновлена",
                w,
                saved
        );

        return ContractorWorkDto.from(w);
    }

    @Transactional
    public ContractorWorkDto complete(UUID id, String result, Double cost) {
        ContractorWork w = getOrThrow(id);
        if (result == null || result.isBlank()) {
            throw RestException.badRequest("Result is required to complete work");
        }

        w.setStatus(ContractorWorkStatus.COMPLETED);
        w.setCompletedAt(Instant.now());
        w.setResult(result);
        w.setCost(cost);

        ContractorWork save = repository.save(w);

        auditBuilderService.log(
                "contractor_work",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.CONTRACTOR_WORK,
                "Работа подрядчика обновлена",
                w,
                save
        );

        return ContractorWorkDto.from(w);
    }

    @Transactional
    public ContractorWorkDto accept(UUID id, UUID acceptedById, String comment) {
        ContractorWork w = getOrThrow(id);
        if (w.getStatus() != ContractorWorkStatus.COMPLETED) {
            throw RestException.badRequest("Only completed works can be accepted");
        }

        w.setStatus(ContractorWorkStatus.ACCEPTED);
        w.setAcceptedById(acceptedById);
        w.setAcceptedAt(Instant.now());
        w.setAcceptanceComment(comment);


        ContractorWork saved = repository.save(w);

        auditBuilderService.log(
                "contractor_work",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.CONTRACTOR_WORK,
                "Работа подрядчика обновлена",
                w,
                saved
        );
        return ContractorWorkDto.from(w);
    }

    private ContractorWork getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Contractor work not found: " + id));
    }


}
