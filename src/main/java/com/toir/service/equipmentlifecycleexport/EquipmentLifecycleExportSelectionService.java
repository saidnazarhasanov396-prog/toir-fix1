package com.toir.service.equipmentlifecycleexport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.config.EquipmentLifecycleExportProperties;
import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportJob;
import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportMembership;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportScopeMode;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportStatus;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportJobRepository;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportMembershipRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "toir.ai.equipment-lifecycle.export", name = "enabled", havingValue = "true")
public class EquipmentLifecycleExportSelectionService {
    private final EquipmentLifecycleExportJobRepository jobRepository;
    private final EquipmentLifecycleExportMembershipRepository membershipRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentLifecycleExportProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EquipmentLifecycleExportSelectionService(
            EquipmentLifecycleExportJobRepository jobRepository,
            EquipmentLifecycleExportMembershipRepository membershipRepository,
            EquipmentRepository equipmentRepository,
            EquipmentLifecycleExportProperties properties,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Qualifier("equipmentLifecycleClock") Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.membershipRepository = membershipRepository;
        this.equipmentRepository = equipmentRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public EquipmentLifecycleExportJob freeze(UUID jobId, UUID fencingToken) {
        EquipmentLifecycleExportJob job = jobRepository.findForUpdate(jobId)
                .orElseThrow(() -> RestException.notFound("Dataset export job not found"));
        requireActiveLease(job, fencingToken, clock.instant());
        if (job.isSelectionFrozen()) {
            return job;
        }
        if (job.getStatus() != EquipmentLifecycleExportStatus.PREPARING) {
            throw RestException.conflict("Dataset export selection cannot be prepared in the current state");
        }

        membershipRepository.deleteAllByJobId(jobId);
        membershipRepository.flush();
        UUID scopeDepartmentId = scopeDepartmentId(job.getAuthorizationScope());
        long selected = job.getSelectionMode() == EquipmentLifecycleExportScopeMode.EXPLICIT_IDS
                ? materializeExplicit(job, scopeDepartmentId)
                : materializeAll(job, scopeDepartmentId);
        if (selected == 0) {
            throw RestException.badRequest("Dataset export selection is empty");
        }
        job.setSelectedCount(selected);
        job.setSelectionFrozen(true);
        job.setStatus(EquipmentLifecycleExportStatus.RUNNING);
        job.setUpdatedAt(clock.instant());
        return jobRepository.save(job);
    }

    private long materializeExplicit(EquipmentLifecycleExportJob job, UUID scopeDepartmentId) {
        List<UUID> ids = explicitIds(job.getRequestSnapshot());
        requireWithinMaximum(ids.size());
        long ordinal = 0;
        for (int from = 0; from < ids.size(); from += properties.getSelectionBatchSize()) {
            List<UUID> batch = ids.subList(from, Math.min(ids.size(), from + properties.getSelectionBatchSize()));
            Set<UUID> active = new HashSet<>(equipmentRepository.findActiveIdsForExport(batch, scopeDepartmentId));
            if (active.size() != batch.size()) {
                throw RestException.badRequest("One or more requested Equipment records are unavailable");
            }
            ordinal = persistBatch(job.getId(), batch, ordinal, clock.instant());
        }
        return ordinal;
    }

    private long materializeAll(EquipmentLifecycleExportJob job, UUID scopeDepartmentId) {
        UUID afterId = null;
        long ordinal = 0;
        while (true) {
            List<UUID> batch = equipmentRepository.findActiveIdsForExportAfter(
                    scopeDepartmentId,
                    afterId,
                    properties.getSelectionBatchSize()
            );
            if (batch.isEmpty()) {
                return ordinal;
            }
            if (ordinal + batch.size() > properties.getMaximumEquipment()) {
                throw RestException.badRequest("Dataset export exceeds the configured Equipment maximum");
            }
            ordinal = persistBatch(job.getId(), batch, ordinal, clock.instant());
            afterId = batch.get(batch.size() - 1);
            if (batch.size() < properties.getSelectionBatchSize()) {
                return ordinal;
            }
        }
    }

    private long persistBatch(UUID jobId, List<UUID> ids, long firstOrdinal, Instant createdAt) {
        List<EquipmentLifecycleExportMembership> rows = new ArrayList<>(ids.size());
        long ordinal = firstOrdinal;
        for (UUID equipmentId : ids) {
            EquipmentLifecycleExportMembership row = new EquipmentLifecycleExportMembership();
            row.setId(UUID.randomUUID());
            row.setJobId(jobId);
            row.setEquipmentId(equipmentId);
            row.setOrdinal(ordinal++);
            row.setCreatedAt(createdAt);
            rows.add(row);
        }
        membershipRepository.saveAllAndFlush(rows);
        return ordinal;
    }

    private List<UUID> explicitIds(String requestSnapshot) {
        try {
            JsonNode root = objectMapper.readTree(requestSnapshot);
            List<UUID> ids = new ArrayList<>();
            for (JsonNode node : root.path("equipmentIds")) {
                ids.add(UUID.fromString(node.asText()));
            }
            return ids.stream().distinct().sorted(Comparator.naturalOrder()).toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Persisted export request is invalid", exception);
        }
    }

    private UUID scopeDepartmentId(String authorizationScope) {
        try {
            JsonNode root = objectMapper.readTree(authorizationScope);
            if (root.path("global").asBoolean(false)) {
                return null;
            }
            return UUID.fromString(root.path("departmentId").asText());
        } catch (Exception exception) {
            throw new IllegalStateException("Persisted export authorization scope is invalid", exception);
        }
    }

    private void requireWithinMaximum(int size) {
        if (size == 0) {
            throw RestException.badRequest("Explicit Equipment scope must not be empty");
        }
        if (size > properties.getMaximumEquipment()) {
            throw RestException.badRequest("Dataset export exceeds the configured Equipment maximum");
        }
    }

    private static void requireActiveLease(EquipmentLifecycleExportJob job, UUID token, Instant now) {
        if (token == null || !token.equals(job.getLeaseToken()) || job.getLeaseExpiresAt() == null
                || !job.getLeaseExpiresAt().isAfter(now)) {
            throw RestException.conflict("Dataset export worker lease is no longer valid");
        }
    }
}
