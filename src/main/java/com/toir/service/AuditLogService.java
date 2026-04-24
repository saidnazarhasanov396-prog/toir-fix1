package com.toir.service;
import com.toir.enums.AuditAction;
import com.toir.entity.AuditLog;
import com.toir.repository.AuditLogRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(UUID userId, String module, String entityType, String entityId,
                       AuditAction action, String message, String ip, String userAgent) {
        AuditLog entry = new AuditLog();
        entry.setUserId(userId);
        entry.setModule(module);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setMessage(message);
        entry.setIpAddress(ip);
        entry.setUserAgent(userAgent);
        repository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> find(int page, int size) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }
}
