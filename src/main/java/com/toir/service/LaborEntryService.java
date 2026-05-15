package com.toir.service;

import com.toir.dto.laborentry.LaborEntryDto;
import com.toir.entity.LaborEntry;
import com.toir.entity.users.User;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.LaborEntryRepository;
import com.toir.repository.users.UserRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LaborEntryService {

    private final LaborEntryRepository repository;
    private final UserRepository userRepository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<LaborEntryDto> findByWorkOrder(UUID workOrderId) {
        List<LaborEntry> entries = repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrderId);
        if (entries.isEmpty()) {
            return List.of();
        }

        Set<UUID> userIds = entries.stream()
                .map(LaborEntry::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<UUID, LaborEntryDto.UserRef> userRefById = loadUserRefs(userIds);

        return entries.stream()
                .map(entry -> LaborEntryDto.from(entry, userRefById.get(entry.getUserId())))
                .toList();
    }

    @Transactional
    public LaborEntryDto create(UUID workOrderId, LaborEntryDto r) {
        LaborEntry e = new LaborEntry();
        e.setWorkOrderId(workOrderId);
        apply(e, r);
        LaborEntry saved = repository.save(e);

        auditBuilderService.log(
                "labor_entry",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.LABOR_ENTRY,
                "Запись трудозатрат обновлена",
                e,
                saved
        );

        return LaborEntryDto.from(saved, toUserRef(saved.getUserId()));
    }

    @Transactional
    public LaborEntryDto update(UUID id, LaborEntryDto r) {
        LaborEntry e = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Labor entry not found: " + id));
        apply(e, r);

        LaborEntry saved = repository.save(e);

        auditBuilderService.log(
                "labor_entry",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.LABOR_ENTRY,
                "Запись трудозатрат создана",
                null,
                saved
        );

        return LaborEntryDto.from(e, toUserRef(saved.getUserId()));
    }

    @Transactional
    public void delete(UUID id) {
        var entity = repository.findByIdAndIsDeletedFalse(id).orElseThrow();
        entity.setDeleted(true);
        LaborEntry saved = repository.save(entity);

        auditBuilderService.log(
                "labor_entry",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.LABOR_ENTRY,
                "Запись трудозатрат удалена",
                saved,
                null
        );

    }

    private void apply(LaborEntry e, LaborEntryDto r) {
        e.setUserId(r.userId());
        e.setContractorName(r.contractorName());
        e.setWorkDate(r.workDate());
        e.setHours(r.hours());
        e.setRate(r.rate());
        e.setDescription(r.description());
    }

    private Map<UUID, LaborEntryDto.UserRef> loadUserRefs(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userRepository.findAllByIdInAndIsDeletedFalse(userIds).stream()
                .collect(Collectors.toMap(User::getId, LaborEntryDto.UserRef::from, (a, b) -> a));
    }

    private LaborEntryDto.UserRef toUserRef(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findByIdAndIsDeletedFalse(userId)
                .map(LaborEntryDto.UserRef::from)
                .orElse(null);
    }
}
