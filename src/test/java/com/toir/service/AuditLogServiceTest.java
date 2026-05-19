package com.toir.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.audit.AuditLogResponseDto;
import com.toir.entity.AuditLog;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.AuditLogRepository;
import com.toir.repository.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    AuditLogRepository repository;

    @Mock
    UserRepository userRepository;

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(repository, new ObjectMapper(), userRepository);
    }

    @Test
    void auditLogWithNullUserIdShouldReturnResponseWithout500() {
        AuditLog log = auditLog(null);
        when(repository.findAllByIsDeletedFalseOrderByCreatedAtDesc(
                any(), any(), any(), any(), any(), any(Pageable.class)))
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
                any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.empty());

        Page<AuditLogResponseDto> result = service.find(0, 20, null, null, null, null, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().user()).isNull();
        verify(userRepository).findByIdAndIsDeletedFalse(userId);
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
}

