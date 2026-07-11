package com.toir.service.plannedshutdown;

import com.toir.entity.PlannedShutdown;
import com.toir.entity.plannedshutdown.*;
import com.toir.enums.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlannedShutdownApprovalScopeHasherTest {
    private final PlannedShutdownApprovalScopeHasher hasher = new PlannedShutdownApprovalScopeHasher();

    @Test
    void hashIsIndependentOfCollectionAndDisplayOrder() {
        Fixture f = fixture();
        String first = hasher.hash(f.root, List.of(f.assetA, f.assetB), List.of(f.workA, f.workB),
                List.of(f.readinessA, f.readinessB), List.of(f.isolationA, f.isolationB));
        f.assetA.setOrderNumber(99); f.workA.setOrderNumber(99); f.readinessA.setOrderNumber(99);
        String reordered = hasher.hash(f.root, List.of(f.assetB, f.assetA), List.of(f.workB, f.workA),
                List.of(f.readinessB, f.readinessA), List.of(f.isolationB, f.isolationA));
        assertThat(reordered).isEqualTo(first);
    }

    @Test
    void operationalRequirementChangeChangesHashButCompletionEvidenceDoesNot() {
        Fixture f = fixture();
        String first = hash(f);
        f.readinessA.setEvidence("photo"); f.readinessA.setStatus(PlannedShutdownItemStatus.PASSED);
        f.isolationA.setAppliedAt(Instant.now());
        assertThat(hash(f)).isEqualTo(first);
        f.workA.setRequiresIsolation(true);
        assertThat(hash(f)).isNotEqualTo(first);
    }

    private String hash(Fixture f) {
        return hasher.hash(f.root, List.of(f.assetA, f.assetB), List.of(f.workA, f.workB),
                List.of(f.readinessA, f.readinessB), List.of(f.isolationA, f.isolationB));
    }

    private Fixture fixture() {
        PlannedShutdown root = new PlannedShutdown(); root.setId(UUID.randomUUID()); root.setCode("PS-1");
        root.setName("Annual"); root.setShutdownType("PLANNED"); root.setDepartmentId(UUID.randomUUID());
        root.setResponsibleEmployeeId(UUID.randomUUID()); root.setPlannedStartAt(Instant.parse("2026-08-01T00:00:00Z"));
        root.setPlannedEndAt(Instant.parse("2026-08-02T00:00:00Z")); root.setReason("Maintenance");
        PlannedShutdownAsset a = asset(UUID.randomUUID(), 0); PlannedShutdownAsset b = asset(UUID.randomUUID(), 1);
        PlannedShutdownWorkItem wa = work(UUID.randomUUID(), a.getEquipmentId(), 0);
        PlannedShutdownWorkItem wb = work(UUID.randomUUID(), b.getEquipmentId(), 1);
        PlannedShutdownReadinessItem ra = readiness("HSE", 0); PlannedShutdownReadinessItem rb = readiness("OPS", 1);
        PlannedShutdownIsolationPoint ia = isolation(a.getEquipmentId(), "L-1", 0);
        PlannedShutdownIsolationPoint ib = isolation(b.getEquipmentId(), "L-2", 1);
        return new Fixture(root, a, b, wa, wb, ra, rb, ia, ib);
    }

    private PlannedShutdownAsset asset(UUID equipment, int order) {
        var a = new PlannedShutdownAsset(); a.setEquipmentId(equipment); a.setDisposition(PlannedShutdownAssetDisposition.STOPPED);
        a.setOrderNumber(order); return a;
    }
    private PlannedShutdownWorkItem work(UUID id, UUID equipment, int order) {
        var w = new PlannedShutdownWorkItem(); w.setId(id); w.setSourceType(PlannedShutdownWorkItemSourceType.MANUAL);
        w.setEquipmentId(equipment); w.setTitle("Inspect"); w.setPriority(PriorityLevel.HIGH); w.setRequiresShutdown(true);
        w.setOrderNumber(order); return w;
    }
    private PlannedShutdownReadinessItem readiness(String key, int order) {
        var r = new PlannedShutdownReadinessItem(); r.setReadinessKey(key); r.setTitle(key); r.setSeverity(PlannedShutdownReadinessSeverity.CRITICAL);
        r.setOrderNumber(order); return r;
    }
    private PlannedShutdownIsolationPoint isolation(UUID equipment, String tag, int order) {
        var i = new PlannedShutdownIsolationPoint(); i.setEquipmentId(equipment); i.setIsolationMethod("LOTO");
        i.setLockTagIdentifier(tag); i.setResponsibleEmployeeId(UUID.randomUUID()); i.setOrderNumber(order); return i;
    }
    private record Fixture(PlannedShutdown root, PlannedShutdownAsset assetA, PlannedShutdownAsset assetB,
            PlannedShutdownWorkItem workA, PlannedShutdownWorkItem workB,
            PlannedShutdownReadinessItem readinessA, PlannedShutdownReadinessItem readinessB,
            PlannedShutdownIsolationPoint isolationA, PlannedShutdownIsolationPoint isolationB) {}
}
