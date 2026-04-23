package com.toir.service;
import com.toir.entity.LaborEntry;
import com.toir.repository.LaborEntryRepository;

import com.toir.exception.RestException;
import com.toir.dto.laborentry.LaborEntryDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class LaborEntryService {

    private final LaborEntryRepository repository;

    public LaborEntryService(LaborEntryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<LaborEntryDto> findByWorkOrder(UUID workOrderId) {
        return repository.findAllByWorkOrderIdOrderByWorkDateAsc(workOrderId).stream()
                .map(LaborEntryDto::from).toList();
    }

    public LaborEntryDto create(UUID workOrderId, LaborEntryDto r) {
        LaborEntry e = new LaborEntry();
        e.setWorkOrderId(workOrderId);
        apply(e, r);
        return LaborEntryDto.from(repository.save(e));
    }

    public LaborEntryDto update(UUID id, LaborEntryDto r) {
        LaborEntry e = repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Labor entry not found: " + id));
        apply(e, r);
        return LaborEntryDto.from(e);
    }

    public void delete(UUID id) {
        repository.deleteById(id);
    }

    private void apply(LaborEntry e, LaborEntryDto r) {
        e.setUserId(r.userId());
        e.setContractorName(r.contractorName());
        e.setWorkDate(r.workDate());
        e.setHours(r.hours());
        e.setRate(r.rate());
        e.setDescription(r.description());
    }
}
