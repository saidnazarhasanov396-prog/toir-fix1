package com.toir.service.equipmentfleetlifecycle;

import com.toir.dto.equipmentfleetlifecycle.EquipmentFleetLifecycleV1;
import com.toir.security.ScopeAccessService;
import com.toir.service.equipmentfleetlifecycle.EquipmentFleetLifecycleBatchLoader.Batch;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EquipmentFleetLifecycleStreamService {

    private static final int BATCH_SIZE = 100;

    private final ScopeAccessService scopeAccessService;
    private final EquipmentFleetLifecycleBatchLoader loader;

    public EquipmentFleetLifecycleStreamService(
            ScopeAccessService scopeAccessService,
            EquipmentFleetLifecycleBatchLoader loader) {
        this.scopeAccessService = scopeAccessService;
        this.loader = loader;
    }

    public PreparedFleetStream prepare(Instant generatedAt, String correlationId) {
        Instant normalizedGeneratedAt = Objects.requireNonNull(generatedAt, "generatedAt")
                .truncatedTo(ChronoUnit.MICROS);
        Objects.requireNonNull(correlationId, "correlationId");
        if (correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        boolean scopeAdmin = scopeAccessService.isScopeAdmin();
        UUID scopeDepartmentId = scopeAdmin
                ? null
                : scopeAccessService.currentDepartmentIdOrNull();
        boolean denyAll = !scopeAdmin && scopeDepartmentId == null;
        Batch firstBatch = loader.load(
                scopeDepartmentId, denyAll, null, normalizedGeneratedAt, BATCH_SIZE);
        return new PreparedFleetStream(
                scopeDepartmentId, denyAll, normalizedGeneratedAt, correlationId, firstBatch);
    }

    public long stream(PreparedFleetStream prepared, ItemSink sink) throws IOException {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(sink, "sink");
        long accepted = 0L;
        Batch batch = prepared.firstBatch();
        while (true) {
            for (EquipmentFleetLifecycleV1.Line line : batch.lines()) {
                sink.accept(line);
                accepted++;
            }
            if (!batch.hasMore()) {
                return accepted;
            }
            batch = loader.load(
                    prepared.scopeDepartmentId(),
                    prepared.denyAll(),
                    batch.lastEquipmentId(),
                    prepared.generatedAt(),
                    BATCH_SIZE);
        }
    }

    public record PreparedFleetStream(
            UUID scopeDepartmentId,
            boolean denyAll,
            Instant generatedAt,
            String correlationId,
            Batch firstBatch) {
        public PreparedFleetStream {
            Objects.requireNonNull(generatedAt, "generatedAt");
            Objects.requireNonNull(correlationId, "correlationId");
            Objects.requireNonNull(firstBatch, "firstBatch");
        }
    }

    @FunctionalInterface
    public interface ItemSink {
        void accept(EquipmentFleetLifecycleV1.Line line) throws IOException;
    }
}
