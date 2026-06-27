package com.toir.service;

import com.toir.dto.mxik.MxikDto;
import com.toir.dto.mxik.MxikNameCodeCountProjection;
import com.toir.dto.mxik.MxikNameCountProjection;
import com.toir.dto.mxik.MxikRequest;
import com.toir.entity.Mxik;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.MxikRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MxikService {

    private final MxikRepository repository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public Page<MxikDto> findAll(String search, int page, int size) {
        return repository.search(searchPattern(search), PaginationUtils.pageRequest(page, size))
                .map(MxikDto::from);
    }

    @Transactional(readOnly = true)
    public MxikDto findById(UUID id) {
        return MxikDto.from(getOrThrow(id));
    }

    @Transactional
    public MxikDto create(MxikRequest request) {
        String kod = normalizeKod(request.kod());
        if (repository.existsActiveByKod(kod)) {
            throw RestException.conflict("MXIK already exists: kod=" + kod);
        }

        Mxik entity = new Mxik();
        apply(entity, request, kod);
        Mxik saved = repository.save(entity);

        auditBuilderService.log(
                "mxik",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.SPARE_PART,
                "MXIK created",
                null,
                saved
        );

        return MxikDto.from(saved);
    }

    @Transactional
    public MxikDto update(UUID id, MxikRequest request) {
        Mxik entity = getOrThrow(id);
        String kod = normalizeKod(request.kod());
        if (repository.existsActiveByKodAndIdNot(kod, id)) {
            throw RestException.conflict("MXIK already exists: kod=" + kod);
        }

        apply(entity, request, kod);
        Mxik saved = repository.save(entity);

        auditBuilderService.log(
                "mxik",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.SPARE_PART,
                "MXIK updated",
                entity,
                saved
        );

        return MxikDto.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Mxik entity = getOrThrow(id);
        entity.setDeleted(true);
        Mxik saved = repository.save(entity);

        auditBuilderService.log(
                "mxik",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.SPARE_PART,
                "MXIK deleted",
                saved,
                null
        );
    }

    @Transactional(readOnly = true)
    public List<MxikNameCountProjection> listGroupCounts() {
        return repository.findGroupCounts();
    }

    @Transactional(readOnly = true)
    public List<MxikNameCountProjection> listSubPositionCounts(String groupName) {
        String normalized = trimToNull(groupName);
        if (normalized == null) {
            throw RestException.badRequest("groupName is required");
        }
        return repository.findSubPositionCounts(normalized);
    }

    @Transactional(readOnly = true)
    public Page<MxikNameCodeCountProjection> listMxikCounts(String positionName, int page, int size) {
        String normalized = trimToNull(positionName);
        if (normalized == null) {
            throw RestException.badRequest("positionName is required");
        }
        return repository.findMxikCounts(normalized, PageRequest.of(Math.max(page, 0), Math.max(size, 1)));
    }

    private Mxik getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("MXIK not found: " + id));
    }

    private void apply(Mxik entity, MxikRequest request, String kod) {
        entity.setName(requiredTrim(request.name(), "name"));
        entity.setKod(kod);
        entity.setType(requiredTrim(request.type(), "type").toUpperCase(Locale.ROOT));
        entity.setGroupName(trimToNull(request.groupName()));
        entity.setPositionName(trimToNull(request.positionName()));
    }

    private String normalizeKod(String value) {
        String token = requiredTrim(value, "kod");
        return token.toUpperCase(Locale.ROOT);
    }

    private String searchPattern(String search) {
        String token = trimToNull(search);
        return token == null ? null : "%" + token.toLowerCase(Locale.ROOT) + "%";
    }

    private String requiredTrim(String value, String fieldName) {
        String token = trimToNull(value);
        if (token == null) {
            throw RestException.badRequest(fieldName + " is required");
        }
        return token;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
