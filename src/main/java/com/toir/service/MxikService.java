package com.toir.service;

import com.toir.dto.mxik.MxikDto;
import com.toir.dto.mxik.MxikNameCodeCountProjection;
import com.toir.dto.mxik.MxikNameCountProjection;
import com.toir.entity.Mxik;
import com.toir.exception.RestException;
import com.toir.repository.MxikRepository;
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

    @Transactional(readOnly = true)
    public Page<MxikDto> findAll(String search, int page, int size) {
        return repository.search(searchPattern(search), PaginationUtils.pageRequest(page, size))
                .map(MxikDto::from);
    }

    @Transactional(readOnly = true)
    public MxikDto findById(UUID id) {
        return MxikDto.from(getOrThrow(id));
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

    private String searchPattern(String search) {
        String token = trimToNull(search);
        return token == null ? null : "%" + token.toLowerCase(Locale.ROOT) + "%";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
