package com.toir.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.audit.AuditLogWriteScheduler;
import com.toir.dto.audit.AuditLogResponseDto;
import com.toir.dto.audit.AuditLogUserSummary;
import com.toir.entity.AuditLog;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.UserStatus;
import com.toir.repository.AuditLogRepository;
import com.toir.repository.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    AuditLogRepository repository;

    @Mock
    UserRepository userRepository;

    @Mock
    AuditLogWriteScheduler writeScheduler;

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(repository, new ObjectMapper(), userRepository, writeScheduler);
    }

    @Test
    void auditLogWithNullUserIdShouldReturnResponseWithout500() {
        AuditLog log = auditLog(null);
        when(repository.findAllByIsDeletedFalseOrderByCreatedAtDesc(
                any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        Page<AuditLogResponseDto> result = service.find(0, 20, null, null, null, null, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().user()).isNull();
        verifyNoInteractions(userRepository);
    }

    @Test
    void auditLogWithUnresolvedUserIdShouldReturnResponseWithout500() {
        UUID userId = UUID.randomUUID();
        AuditLog log = auditLog(userId);
        when(repository.findAllByIsDeletedFalseOrderByCreatedAtDesc(
                any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));
        when(userRepository.findAuditLogUserSummariesByIdIn(anyCollection())).thenReturn(List.of());

        Page<AuditLogResponseDto> result = service.find(0, 20, null, null, null, null, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().user()).isNull();
        verify(userRepository).findAuditLogUserSummariesByIdIn(anyCollection());
        verify(userRepository, never()).findByIdAndIsDeletedFalse(userId);
    }

    @Test
    void auditLogWithUserWhoseDepartmentReferenceIsMissingShouldReturnUserWithNullDepartment() {
        UUID userId = UUID.randomUUID();
        UUID missingDepartmentId = UUID.fromString("63f42751-41dd-4125-a1f2-7ab3068264dd");
        AuditLog log = auditLog(userId);
        when(repository.findAllByIsDeletedFalseOrderByCreatedAtDesc(
                any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));
        when(userRepository.findAuditLogUserSummariesByIdIn(anyCollection()))
                .thenReturn(List.of(userSummary(userId, missingDepartmentId, null)));

        Page<AuditLogResponseDto> result = service.find(0, 20, null, null, null, null, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().user()).isNotNull();
        assertThat(result.getContent().getFirst().user().department()).isNull();
        verify(userRepository, never()).findByIdAndIsDeletedFalse(userId);
    }

    @Test
    void auditLogListingResolvesUsersInOneBulkLookup() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        when(repository.findAllByIsDeletedFalseOrderByCreatedAtDesc(
                any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(auditLog(firstUserId), auditLog(secondUserId))));
        when(userRepository.findAuditLogUserSummariesByIdIn(anyCollection()))
                .thenReturn(List.of(userSummary(firstUserId, UUID.randomUUID(), "Maintenance")));

        Page<AuditLogResponseDto> result = service.find(0, 20, null, null, null, null, null);

        assertThat(result.getContent()).hasSize(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(userRepository).findAuditLogUserSummariesByIdIn(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(firstUserId, secondUserId);
        verify(userRepository, never()).findByIdAndIsDeletedFalse(any());
    }

    @Test
    void auditLogListingPassesModuleFilterToRepository() {
        when(repository.findAllByIsDeletedFalseOrderByCreatedAtDesc(
                any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.find(0, 20, AuditModule.WORK_ORDER, AuditAction.UPDATE, null, null, null, null);

        verify(repository).findAllByIsDeletedFalseOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq("WORK_ORDER"),
                org.mockito.ArgumentMatchers.eq("UPDATE"),
                any(),
                any(),
                any(),
                any(),
                any(Pageable.class)
        );
    }

    private AuditLog auditLog(UUID userId) {
        AuditLog log = new AuditLog();
        ReflectionTestUtils.setField(log, "id", UUID.randomUUID());
        log.setUserId(userId);
        log.setModule(AuditModule.WORK_ORDER);
        log.setEntityType("work_order");
        log.setEntityId(UUID.randomUUID().toString());
        log.setAction(AuditAction.CREATE);
        log.setMessage("created");
        log.setCreatedAt(Instant.now());
        return log;
    }

    private AuditLogUserSummary userSummary(UUID userId, UUID departmentId, String departmentName) {
        return new AuditLogUserSummary(
                userId,
                "orphaned.department.user",
                "orphaned.department.user@example.com",
                "Orphaned Department User",
                "Technician",
                null,
                UserStatus.ACTIVE,
                null,
                departmentId,
                departmentName == null ? null : "DEP",
                departmentName,
                null,
                null,
                null
        );
    }
}
