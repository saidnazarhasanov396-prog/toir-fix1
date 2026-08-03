package com.toir.service.equipmentfleetlifecycle;

import com.toir.dto.equipmentfleetlifecycle.EquipmentFleetLifecycleV1;
import com.toir.repository.equipmentfleetlifecycle.EquipmentFleetLifecycleQueryRepository;
import com.toir.repository.equipmentfleetlifecycle.EquipmentFleetLifecycleQueryRepository.EquipmentRow;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentFleetLifecycleBatchLoader {

    private final EquipmentFleetLifecycleQueryRepository repository;
    private final EquipmentFleetLifecycleBatchAssembler assembler;

    public EquipmentFleetLifecycleBatchLoader(
            EquipmentFleetLifecycleQueryRepository repository,
            EquipmentFleetLifecycleBatchAssembler assembler) {
        this.repository = repository;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public Batch load(
            UUID scopeDepartmentId,
            boolean denyAll,
            UUID afterExclusive,
            Instant asOf,
            int limit) {
        List<EquipmentRow> selected = repository.findEquipmentBatch(
                scopeDepartmentId, denyAll, afterExclusive, limit + 1);
        if (selected.isEmpty()) {
            return new Batch(List.of(), null, false);
        }

        boolean hasMore = selected.size() > limit;
        List<EquipmentRow> equipment = List.copyOf(
                selected.subList(0, Math.min(selected.size(), limit)));
        List<UUID> equipmentIds = equipment.stream().map(EquipmentRow::id).toList();
        List<EquipmentFleetLifecycleV1.Line> lines = assembler.assemble(
                equipment,
                repository.findMeters(equipmentIds),
                repository.findLatestReadings(equipmentIds, asOf),
                repository.findRepairs(equipmentIds, asOf),
                repository.findRepairMeterSnapshots(equipmentIds, asOf),
                asOf);
        UUID lastEquipmentId = lines.isEmpty()
                ? null
                : lines.get(lines.size() - 1).equipment().id();
        return new Batch(lines, lastEquipmentId, hasMore);
    }

    public record Batch(
            List<EquipmentFleetLifecycleV1.Line> lines,
            UUID lastEquipmentId,
            boolean hasMore) {
        public Batch {
            lines = List.copyOf(lines);
        }
    }
}
