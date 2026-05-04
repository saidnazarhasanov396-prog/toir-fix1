package com.toir.service.contactor;
import com.toir.entity.contractors.Contractor;
import com.toir.repository.contarctor.ContractorRepository;

import com.toir.exception.RestException;
import com.toir.dto.contractor.ContractorDto;
import com.toir.dto.contractor.ContractorRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ContractorService {

    private final ContractorRepository repository;

    @Transactional(readOnly = true)
    public List<ContractorDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(ContractorDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ContractorDto findById(UUID id) {
        return ContractorDto.from(getOrThrow(id));
    }

    public ContractorDto create(ContractorRequest request) {
        if (repository.existsByCodeAndIsDeletedFalse(request.code())) {
            throw RestException.conflict("Contractor code already exists: " + request.code());
        }
        Contractor entity = new Contractor();
        apply(entity, request);
        return ContractorDto.from(repository.save(entity));
    }

    public ContractorDto update(UUID id, ContractorRequest request) {
        Contractor entity = getOrThrow(id);
        apply(entity, request);
        return ContractorDto.from(entity);
    }

    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity);
    }

    private Contractor getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Contractor not found: " + id));
    }

    private void apply(Contractor entity, ContractorRequest request) {
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setTaxNumber(request.taxNumber());
        entity.setContactPerson(request.contactPerson());
        entity.setPhone(request.phone());
        entity.setEmail(request.email());
        entity.setSpecialization(request.specialization());
        if (request.status() != null) entity.setStatus(request.status());
    }
}
