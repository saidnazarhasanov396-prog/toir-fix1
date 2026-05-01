package com.toir.service;
import com.toir.entity.EquipmentType;
import com.toir.repository.EquipmentTypeRepository;

import com.toir.exception.RestException;
import com.toir.dto.equipmenttype.EquipmentTypeDto;
import com.toir.dto.equipmenttype.EquipmentTypeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class EquipmentTypeService {

    private final EquipmentTypeRepository repository;


    @Transactional(readOnly = true)
    public List<EquipmentTypeDto> findAll(String search, String category) {
        search = search == null ? null : "%" + search.toLowerCase() + "%";
        return com.toir.util.UpdatedAtSorter.descending(repository.findAllByIsDeletedFalseAndBySearchParam(search, category))
                .stream()
                .map(EquipmentTypeDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EquipmentTypeDto findById(UUID id) {
        return EquipmentTypeDto.from(getOrThrow(id));
    }

    public EquipmentType getEntityOrThrow(UUID id) {
        return getOrThrow(id);
    }

    public EquipmentTypeDto create(EquipmentTypeRequest request) {
        EquipmentType entity = new EquipmentType();
        entity.setCode(nextCode());
        apply(entity, request);
        return EquipmentTypeDto.from(repository.save(entity));
    }

    public EquipmentTypeDto update(UUID id, EquipmentTypeRequest request) {
        EquipmentType entity = getOrThrow(id);
        apply(entity, request);
        return EquipmentTypeDto.from(entity);
    }

    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity);
    }

    private EquipmentType getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment type not found: " + id));
    }

    private void apply(EquipmentType entity, EquipmentTypeRequest request) {
        entity.setName(request.name());
        entity.setCategory(request.category());
        entity.setDescription(request.description());
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "ET-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("ET", year, sequence);
        while (repository.existsByCode(code)) {
            sequence++;
            code = formatCode("ET", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }
}
