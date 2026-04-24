package com.toir.service;
import com.toir.entity.EscalationEvent;
import com.toir.enums.EscalationStatus;
import com.toir.repository.EscalationEventRepository;

import com.toir.exception.RestException;
import com.toir.dto.escalation.EscalationEventDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class EscalationEventService {

    private final EscalationEventRepository repository;

    public EscalationEventService(EscalationEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<EscalationEventDto> findOpen() {
        return repository.findAllByStatusOrderByRaisedAtDesc(EscalationStatus.OPEN).stream()
                .map(EscalationEventDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EscalationEventDto> findAll() {
        return repository.findAll().stream().map(EscalationEventDto::from).toList();
    }

    public EscalationEventDto raise(EscalationEventDto r) {
        EscalationEvent e = new EscalationEvent();
        e.setSlaRuleId(r.slaRuleId());
        e.setEntityType(r.entityType());
        e.setEntityId(r.entityId());
        e.setTriggerType(r.triggerType());
        e.setNotes(r.notes());
        return EscalationEventDto.from(repository.save(e));
    }

    public EscalationEventDto acknowledge(UUID id, UUID userId, String notes) {
        EscalationEvent e = getOrThrow(id);
        if (e.getStatus() != EscalationStatus.OPEN) {
            throw RestException.badRequest("Only OPEN escalations can be acknowledged");
        }
        e.setStatus(EscalationStatus.ACKNOWLEDGED);
        e.setAcknowledgedAt(Instant.now());
        e.setAcknowledgedById(userId);
        if (notes != null) e.setNotes(notes);
        return EscalationEventDto.from(e);
    }

    public EscalationEventDto resolve(UUID id, UUID userId, String notes) {
        EscalationEvent e = getOrThrow(id);
        if (e.getStatus() == EscalationStatus.RESOLVED || e.getStatus() == EscalationStatus.CANCELLED) {
            throw RestException.badRequest("Escalation already closed");
        }
        e.setStatus(EscalationStatus.RESOLVED);
        e.setResolvedAt(Instant.now());
        e.setResolvedById(userId);
        if (notes != null) e.setNotes(notes);
        return EscalationEventDto.from(e);
    }

    private EscalationEvent getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Escalation event not found: " + id));
    }
}
