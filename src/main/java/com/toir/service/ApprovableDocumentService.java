package com.toir.service;

import com.toir.dto.approval.ApprovableDocumentDto;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.repository.ApprovableDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ApprovableDocumentService {

    private final ApprovableDocumentRepository repository;

    @Transactional(readOnly = true)
    public Page<ApprovableDocumentDto> search(
            String search,
            ApprovalTargetType type,
            ApprovalStatus status,
            int page,
            int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);

        String searchPattern = StringUtils.hasText(search)
                ? "%" + search.trim().toLowerCase(Locale.ROOT) + "%"
                : null;

        List<ApprovableDocumentDto> all = repository.searchAll(
                searchPattern,
                type != null ? type.name() : null,
                status != null ? status.name() : null
        );

        int start = Math.min(safePage * safeSize, all.size());
        int end = Math.min(start + safeSize, all.size());

        return new PageImpl<>(
                all.subList(start, end),
                PageRequest.of(safePage, safeSize),
                all.size()
        );
    }
}
