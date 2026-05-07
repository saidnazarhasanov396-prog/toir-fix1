package com.toir.service.defects;

import com.toir.dto.defect.DefectDto;
import com.toir.dto.defect.DefectRequest;
import com.toir.dto.defect.DefectResponse;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.DefectStatus;
import com.toir.exception.RestException;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefectService {

    private final DefectRepository repository;
    private final EquipmentRepository equipmentRepository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<DefectResponse> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .map(DefectDto::from)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<DefectResponse> search(UUID equipmentId, int page, int size, String search) {
        var pageable = PaginationUtils.pageRequest(page, size);
        return repository.searchPaginated(
                equipmentId,
                search,
                pageable
        ).map(DefectDto::from)
         .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DefectResponse findById(UUID id) {
        return toResponse(DefectDto.from(getOrThrow(id)));
    }

    @Transactional(readOnly = true)
    public List<DefectResponse> findByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId).stream()
                .map(DefectDto::from)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DefectResponse create(DefectRequest request) {
        Defect entity = new Defect();
        entity.setCode(nextCode());
        apply(entity, request);
        Defect saved = repository.save(entity);

        auditBuilderService.log(
                "defect",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.DEFECT,
                "Дефект создан",
                null,
                saved
        );

        return toResponse(DefectDto.from(saved));
    }

    @Transactional
    public DefectResponse update(UUID id, DefectRequest request) {
        Defect entity = getOrThrow(id);
        apply(entity, request);

        Defect save = repository.save(entity);

        auditBuilderService.log(
                "defect",
                id != null ? id.toString() : null,
                AuditAction.UPDATE,
                AuditModule.DEFECT,
                "Дефект обновлен",
                entity,
                save
        );

        return toResponse(DefectDto.from(entity));
    }

    @Transactional
    public DefectResponse resolve(UUID id) {
        Defect entity = getOrThrow(id);
        entity.setStatus(DefectStatus.RESOLVED);
        entity.setResolvedAt(Instant.now());

        Defect saved = repository.save(entity);

        auditBuilderService.log(
                "defect",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.DEFECT,
                "Дефект устранен",
                entity,
                saved
        );
        return toResponse(DefectDto.from(saved));
    }

    @Transactional
    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        Defect saved = repository.save(entity);

        auditBuilderService.log(
                "defect",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.DEFECT,
                "Дефект удален",
                saved,
                null
        );
    }

    private Defect getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Defect not found: " + id));
    }

    private void apply(Defect entity, DefectRequest request) {
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setEquipmentId(request.equipmentId());
        entity.setRequestId(request.requestId());
        entity.setWorkOrderId(request.workOrderId());
        entity.setCategory(request.category());
        entity.setSeverity(request.severity());
        entity.setFailureReason(request.failureReason());
        entity.setRootCause(request.rootCause());
    }

    private DefectResponse toResponse(DefectDto dto) {
        return DefectResponse.from(dto, findEquipmentName(dto.equipmentId()));
    }

    private String findEquipmentName(UUID equipmentId) {
        if (equipmentId == null) {
            return null;
        }
        return equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .map(Equipment::getName)
                .orElse(null);
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "DEF-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("DEF", year, sequence);
        while (repository.existsByCode(code)) {
            sequence++;
            code = formatCode("DEF", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }
}
