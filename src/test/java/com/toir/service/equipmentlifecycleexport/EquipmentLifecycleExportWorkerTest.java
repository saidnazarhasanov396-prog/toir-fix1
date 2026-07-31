package com.toir.service.equipmentlifecycleexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.toir.config.EquipmentLifecycleExportProperties;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextPolicy;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextV1;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleDataQuality.Availability;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleSection;
import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportMembership;
import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportPart;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportScopeMode;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportStatus;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportMembershipRepository;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportPartRepository;
import com.toir.service.equipmentlifecycle.EquipmentLifecycleContextAssembler;
import com.toir.service.equipmentlifecycleexport.storage.EquipmentLifecycleExportStorage;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EquipmentLifecycleExportWorkerTest {

    @Test
    void storageObjectIsVerifiedBeforeDatabaseCheckpointAndW1GetsFixedPolicyAndAsOf() {
        Fixture fixture = fixture(false);

        fixture.worker.process(fixture.jobId, "worker-a");

        InOrder order = inOrder(fixture.storage, fixture.leases);
        order.verify(fixture.storage).putImmutable(any(), any(), anyLong(), any(), eq("application/x-ndjson"));
        order.verify(fixture.leases).checkpoint(eq(fixture.jobId), eq(fixture.token), any());
        verify(fixture.assembler).assemble(fixture.equipmentId, fixture.asOf, fixture.policy);
        verify(fixture.finalizer).finalizeExport(fixture.jobId, fixture.token);
    }

    @Test
    void resumeTrustsCommittedPartMetadataAndDoesNotRebuildOrRewriteItsRecord() {
        Fixture fixture = fixture(true);

        fixture.worker.process(fixture.jobId, "worker-a");

        verify(fixture.storage).head("exports/staging/committed-part");
        verify(fixture.storage, never()).list(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(fixture.assembler, never()).assemble(any(), any(), any());
        verify(fixture.leases, never()).checkpoint(any(), any(), any());
        verify(fixture.finalizer).finalizeExport(fixture.jobId, fixture.token);
    }

    @Test
    void assemblyFailurePublishesNoErrorLineAndPreservesAResumableCheckpointState() {
        Fixture fixture = fixture(false);
        when(fixture.assembler.assemble(fixture.equipmentId, fixture.asOf, fixture.policy))
                .thenThrow(new IllegalStateException("source failure"));

        fixture.worker.process(fixture.jobId, "worker-a");

        verify(fixture.storage, never()).putImmutable(any(), any(), anyLong(), any(), any());
        verify(fixture.leases).fail(
                eq(fixture.jobId), eq(fixture.token), eq("EQUIPMENT_ASSEMBLY_FAILED"), any(),
                eq(fixture.equipmentId), eq(true));
        verify(fixture.finalizer, never()).finalizeExport(any(), any());
    }

    @Test
    void cooperativeCancellationStopsBeforeTheNextRecordOrPartialArtifact() {
        Fixture fixture = fixture(false);
        when(fixture.leases.continueProcessing(fixture.jobId, fixture.token)).thenReturn(false);

        fixture.worker.process(fixture.jobId, "worker-a");

        verify(fixture.assembler, never()).assemble(any(), any(), any());
        verify(fixture.storage, never()).putImmutable(any(), any(), anyLong(), any(), any());
        verify(fixture.finalizer, never()).finalizeExport(any(), any());
    }

    @Test
    void missingCommittedObjectMakesResumeNonResumableInsteadOfRestartingFromZero() {
        Fixture fixture = fixture(true);
        when(fixture.storage.head("exports/staging/committed-part")).thenReturn(Optional.empty());

        fixture.worker.process(fixture.jobId, "worker-a");

        verify(fixture.leases).fail(
                eq(fixture.jobId), eq(fixture.token), eq("CHECKPOINT_CORRUPT"), any(), any(), eq(false));
        verify(fixture.assembler, never()).assemble(any(), any(), any());
        verify(fixture.finalizer, never()).finalizeExport(any(), any());
    }

    private Fixture fixture(boolean resumed) {
        UUID jobId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Instant asOf = Instant.parse("2026-07-31T10:00:00Z");
        EquipmentLifecycleContextPolicy policy = boundedPolicy(asOf);
        EquipmentLifecycleExportLeaseService leases = mock(EquipmentLifecycleExportLeaseService.class);
        when(leases.claim(jobId, "worker-a")).thenReturn(new EquipmentLifecycleExportLeaseService.LeaseClaim(
                jobId, token, EquipmentLifecycleExportStatus.RUNNING, true));
        when(leases.continueProcessing(jobId, token)).thenReturn(true);
        when(leases.ownedJob(jobId, token)).thenReturn(new EquipmentLifecycleExportLeaseService.OwnedJob(
                jobId, asOf, "{}", "a".repeat(64), "b".repeat(64), "standard-v1",
                EquipmentLifecycleExportScopeMode.EXPLICIT_IDS, true, 1,
                resumed ? 1 : 0, resumed ? 0 : -1, null, asOf, asOf));
        EquipmentLifecycleExportSelectionService selection = mock(EquipmentLifecycleExportSelectionService.class);
        EquipmentLifecycleExportMembershipRepository memberships = mock(EquipmentLifecycleExportMembershipRepository.class);
        EquipmentLifecycleExportMembership member = new EquipmentLifecycleExportMembership();
        member.setEquipmentId(equipmentId);
        member.setOrdinal(0);
        when(memberships.findByJobIdAndOrdinalGreaterThanOrderByOrdinalAsc(eq(jobId), eq(-1L), any()))
                .thenReturn(List.of(member));
        EquipmentLifecycleExportPartRepository parts = mock(EquipmentLifecycleExportPartRepository.class);
        EquipmentLifecycleExportStorage storage = mock(EquipmentLifecycleExportStorage.class);
        if (resumed) {
            EquipmentLifecycleExportPart part = new EquipmentLifecycleExportPart();
            part.setPartNumber(0);
            part.setFirstOrdinal(0);
            part.setLastOrdinal(0);
            part.setRecordCount(1);
            part.setObjectKey("exports/staging/committed-part");
            part.setObjectSize(10);
            part.setSha256("c".repeat(64));
            when(parts.findAllByJobIdOrderByPartNumberAsc(jobId)).thenReturn(List.of(part));
            when(storage.head(part.getObjectKey())).thenReturn(Optional.of(
                    new EquipmentLifecycleExportStorage.ObjectMetadata(
                            part.getObjectKey(), part.getObjectSize(), part.getSha256())));
        } else {
            when(parts.findAllByJobIdOrderByPartNumberAsc(jobId)).thenReturn(List.of());
            when(storage.putImmutable(any(), any(), anyLong(), any(), any())).thenAnswer(invocation ->
                    new EquipmentLifecycleExportStorage.ObjectMetadata(
                            invocation.getArgument(0), invocation.getArgument(2), invocation.getArgument(3)));
        }
        EquipmentLifecycleContextAssembler assembler = mock(EquipmentLifecycleContextAssembler.class);
        when(assembler.assemble(equipmentId, asOf, policy)).thenReturn(context(equipmentId, asOf));
        EquipmentLifecycleExportProfileResolver profiles = mock(EquipmentLifecycleExportProfileResolver.class);
        when(profiles.restore("{}")).thenReturn(policy);
        EquipmentLifecycleExportFinalizer finalizer = mock(EquipmentLifecycleExportFinalizer.class);
        EquipmentLifecycleExportProperties properties = new EquipmentLifecycleExportProperties();
        properties.getS3().setPrefix("exports/");
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        EquipmentLifecycleExportWorker worker = new EquipmentLifecycleExportWorker(
                leases, selection, memberships, parts, assembler, profiles, storage, finalizer, properties, mapper);
        return new Fixture(worker, leases, storage, assembler, finalizer, jobId, token, equipmentId, asOf, policy);
    }

    private EquipmentLifecycleContextPolicy boundedPolicy(Instant asOf) {
        EnumSet<EquipmentLifecycleSection> sections = EnumSet.allOf(EquipmentLifecycleSection.class);
        Map<EquipmentLifecycleSection, Integer> limits = new EnumMap<>(EquipmentLifecycleSection.class);
        sections.forEach(section -> limits.put(section, 1));
        return new EquipmentLifecycleContextPolicy(asOf.minus(Duration.ofDays(1)), Duration.ofDays(1),
                sections, limits, EquipmentLifecycleContextPolicy.MeasurementGranularity.RAW);
    }

    private EquipmentLifecycleContextV1 context(UUID id, Instant at) {
        var metadata = EquipmentLifecycleContextV1.SectionMetadata.available(
                Availability.AVAILABLE_AND_POPULATED, List.of("equipment"), 1, false, at, at, at);
        var equipment = new EquipmentLifecycleContextV1.EquipmentCore(
                id, "EQ-1", "Pump", "INV-1", null, null, null, 2020, UUID.randomUUID(),
                null, null, null, null, null, null, null, null, "ACTIVE", null, null, null,
                null, null, null, null, null, null, null, null);
        return EquipmentLifecycleContextV1.empty(at, at,
                new EquipmentLifecycleContextV1.ConsistencyMetadata("READ_COMMITTED", false),
                new EquipmentLifecycleContextV1.ValueSection<>(metadata, equipment),
                new EquipmentLifecycleContextV1.ItemsSection<>(metadata, List.of()))
                .withFingerprint("d".repeat(64));
    }

    private record Fixture(
            EquipmentLifecycleExportWorker worker,
            EquipmentLifecycleExportLeaseService leases,
            EquipmentLifecycleExportStorage storage,
            EquipmentLifecycleContextAssembler assembler,
            EquipmentLifecycleExportFinalizer finalizer,
            UUID jobId,
            UUID token,
            UUID equipmentId,
            Instant asOf,
            EquipmentLifecycleContextPolicy policy
    ) {}
}
