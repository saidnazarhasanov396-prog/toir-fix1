package com.toir.service;
import com.toir.entity.ServiceClass;
import com.toir.repository.ServiceClassRepository;

import com.toir.exception.RestException;
import com.toir.dto.serviceclass.ServiceClassDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ServiceClassService {

    private final ServiceClassRepository repository;


    @Transactional(readOnly = true)
    public List<ServiceClassDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(ServiceClassDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ServiceClassDto findById(UUID id) {
        return ServiceClassDto.from(getOrThrow(id));
    }

    public ServiceClassDto create(ServiceClassDto r) {
        ServiceClass e = new ServiceClass();
        e.setCode(nextCode());
        e.setName(r.name()); e.setDescription(r.description());
        return ServiceClassDto.from(repository.save(e));
    }

    public ServiceClassDto update(UUID id, ServiceClassDto r) {
        ServiceClass e = getOrThrow(id);
        e.setName(r.name()); e.setDescription(r.description());
        return ServiceClassDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity); }

    private ServiceClass getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Service class not found: " + id));
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "SC-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("SC", year, sequence);
        while (repository.existsByCodeAndIsDeletedFalse(code)) {
            sequence++;
            code = formatCode("SC", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }
}
