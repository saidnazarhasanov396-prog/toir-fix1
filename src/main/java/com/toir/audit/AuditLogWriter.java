package com.toir.audit;

import com.toir.entity.AuditLog;
import com.toir.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AuditLogWriter {

    private final AuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(AuditLogWriteCommand command) {
        AuditLog entry = new AuditLog();
        entry.setUserId(command.userId());
        entry.setModule(command.module());
        entry.setEntityType(command.entityType());
        entry.setEntityId(command.entityId());
        entry.setAction(command.action());
        entry.setMessage(command.message());
        entry.setIpAddress(command.ipAddress());
        entry.setUserAgent(command.userAgent());
        entry.setDiffJson(command.diffJson());
        entry.setPreviousSnapshot(command.previousSnapshot());
        entry.setCurrentSnapshot(command.currentSnapshot());
        entry.setReason(command.reason());
        entry.setSource(command.source());
        entry.setRequestMethod(command.requestMethod());
        entry.setRequestPath(command.requestPath());
        entry.setCorrelationId(command.correlationId());
        entry.setCreatedAt(Instant.now().atZone(ZoneId.of("Asia/Tashkent")).toInstant());
        repository.save(entry);
    }
}
