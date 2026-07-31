package com.toir.service.equipmentlifecycleexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.config.EquipmentLifecycleExportProperties;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextPolicy;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextV1;
import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportMembership;
import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportPart;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportMembershipRepository;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportPartRepository;
import com.toir.service.equipmentlifecycle.EquipmentLifecycleContextAssembler;
import com.toir.service.equipmentlifecycleexport.EquipmentLifecycleExportLeaseService.LeaseClaim;
import com.toir.service.equipmentlifecycleexport.EquipmentLifecycleExportLeaseService.OwnedJob;
import com.toir.service.equipmentlifecycleexport.EquipmentLifecycleExportLeaseService.PartCheckpoint;
import com.toir.service.equipmentlifecycleexport.storage.EquipmentLifecycleExportObjectKeys;
import com.toir.service.equipmentlifecycleexport.storage.EquipmentLifecycleExportStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "toir.ai.equipment-lifecycle.export", name = "enabled", havingValue = "true")
@Slf4j
public class EquipmentLifecycleExportWorker {
    private final EquipmentLifecycleExportLeaseService leaseService;
    private final EquipmentLifecycleExportSelectionService selectionService;
    private final EquipmentLifecycleExportMembershipRepository membershipRepository;
    private final EquipmentLifecycleExportPartRepository partRepository;
    private final EquipmentLifecycleContextAssembler assembler;
    private final EquipmentLifecycleExportProfileResolver profileResolver;
    private final EquipmentLifecycleExportStorage storage;
    private final EquipmentLifecycleExportObjectKeys keys;
    private final EquipmentLifecycleExportFinalizer finalizer;
    private final EquipmentLifecycleNdjsonPartWriter partWriter;
    private final EquipmentLifecycleExportProperties properties;

    public EquipmentLifecycleExportWorker(
            EquipmentLifecycleExportLeaseService leaseService,
            EquipmentLifecycleExportSelectionService selectionService,
            EquipmentLifecycleExportMembershipRepository membershipRepository,
            EquipmentLifecycleExportPartRepository partRepository,
            EquipmentLifecycleContextAssembler assembler,
            EquipmentLifecycleExportProfileResolver profileResolver,
            EquipmentLifecycleExportStorage storage,
            EquipmentLifecycleExportFinalizer finalizer,
            EquipmentLifecycleExportProperties properties,
            ObjectMapper objectMapper
    ) {
        this.leaseService = leaseService;
        this.selectionService = selectionService;
        this.membershipRepository = membershipRepository;
        this.partRepository = partRepository;
        this.assembler = assembler;
        this.profileResolver = profileResolver;
        this.storage = storage;
        this.keys = new EquipmentLifecycleExportObjectKeys(properties.getS3().getPrefix());
        this.finalizer = finalizer;
        this.properties = properties;
        this.partWriter = new EquipmentLifecycleNdjsonPartWriter(objectMapper, properties.getMaximumPartBytes());
    }

    public void process(UUID jobId, String workerId) {
        UUID token = null;
        UUID currentEquipmentId = null;
        try {
            LeaseClaim claim = leaseService.claim(jobId, workerId);
            token = claim.fencingToken();
            if (!claim.selectionFrozen()) {
                selectionService.freeze(jobId, token);
            }
            OwnedJob job = leaseService.ownedJob(jobId, token);
            EquipmentLifecycleContextPolicy policy = profileResolver.restore(job.resolvedPolicyJson());
            List<EquipmentLifecycleExportPart> committed = partRepository
                    .findAllByJobIdOrderByPartNumberAsc(jobId);
            validateResume(job, token, committed);
            int partNumber = committed.size();
            long lastOrdinal = job.lastCompletedOrdinal();

            while (lastOrdinal + 1 < job.selectedCount()) {
                if (!leaseService.continueProcessing(jobId, token)) {
                    return;
                }
                List<EquipmentLifecycleExportMembership> members = membershipRepository
                        .findByJobIdAndOrdinalGreaterThanOrderByOrdinalAsc(
                                jobId,
                                lastOrdinal,
                                PageRequest.of(0, properties.getCheckpointRecords())
                        );
                if (members.isEmpty()) {
                    throw new IllegalStateException("Frozen membership contains a missing ordinal");
                }
                long firstOrdinal = lastOrdinal + 1;
                List<EquipmentLifecycleContextV1> contexts = new ArrayList<>(members.size());
                for (EquipmentLifecycleExportMembership member : members) {
                    if (member.getOrdinal() != firstOrdinal + contexts.size()) {
                        throw new IllegalStateException("Frozen membership ordinal sequence is invalid");
                    }
                    if (!leaseService.continueProcessing(jobId, token)) {
                        return;
                    }
                    currentEquipmentId = member.getEquipmentId();
                    contexts.add(assembler.assemble(member.getEquipmentId(), job.asOf(), policy));
                }
                EquipmentLifecycleNdjsonPartWriter.PartBytes partBytes = partWriter.write(contexts);
                long partLastOrdinal = members.get(members.size() - 1).getOrdinal();
                String key = keys.part(jobId, token, partNumber, firstOrdinal, partLastOrdinal);
                byte[] bytes = partBytes.bytes();
                EquipmentLifecycleExportStorage.ObjectMetadata stored = storage.putImmutable(
                        key,
                        new ByteArrayInputStream(bytes),
                        bytes.length,
                        partBytes.sha256(),
                        "application/x-ndjson"
                );
                leaseService.checkpoint(jobId, token, new PartCheckpoint(
                        partNumber,
                        firstOrdinal,
                        partLastOrdinal,
                        partBytes.recordCount(),
                        stored.key(),
                        stored.size(),
                        stored.sha256()
                ));
                partNumber++;
                lastOrdinal = partLastOrdinal;
                currentEquipmentId = null;
            }
            finalizer.finalizeExport(jobId, token);
        } catch (Exception exception) {
            log.error("Equipment lifecycle dataset export worker failed: jobId={}, equipmentId={}",
                    jobId, currentEquipmentId, exception);
            if (token != null) {
                String code = failureCode(exception, currentEquipmentId);
                leaseService.fail(jobId, token, code, failureSummary(exception), currentEquipmentId,
                        resumeAllowed(code));
            }
        }
    }

    private void validateResume(OwnedJob job, UUID token, List<EquipmentLifecycleExportPart> parts) {
        long expectedOrdinal = 0;
        long records = 0;
        int expectedPart = 0;
        for (EquipmentLifecycleExportPart part : parts) {
            if (part.getPartNumber() != expectedPart++ || part.getFirstOrdinal() != expectedOrdinal
                    || part.getLastOrdinal() != part.getFirstOrdinal() + part.getRecordCount() - 1) {
                throw new CheckpointIntegrityException("Committed export parts contain missing or overlapping ordinals");
            }
            EquipmentLifecycleExportStorage.ObjectMetadata object = storage.head(part.getObjectKey())
                    .orElseThrow(() -> new CheckpointIntegrityException("Committed export part is missing"));
            if (object.size() != part.getObjectSize() || !object.sha256().equals(part.getSha256())) {
                throw new CheckpointIntegrityException("Committed export part failed HEAD checksum validation");
            }
            expectedOrdinal = part.getLastOrdinal() + 1;
            records += part.getRecordCount();
            if (expectedPart % 20 == 0 && !leaseService.continueProcessing(job.jobId(), token)) {
                throw new IllegalStateException("Dataset export cancelled while validating resume state");
            }
        }
        if (records != job.completedCount()
                || (records == 0 && job.lastCompletedOrdinal() != -1)
                || (records > 0 && expectedOrdinal - 1 != job.lastCompletedOrdinal())) {
                throw new CheckpointIntegrityException("Persisted export checkpoint does not match committed part metadata");
        }
    }

    private static String failureCode(Exception exception, UUID equipmentId) {
        if (exception instanceof CheckpointIntegrityException) {
            return "CHECKPOINT_CORRUPT";
        }
        if (exception instanceof EquipmentLifecycleNdjsonPartWriter.SerializationException) {
            return "SERIALIZATION_FAILED";
        }
        if (exception instanceof EquipmentLifecycleExportStorage.StorageException) {
            return "STORAGE_INTEGRITY_FAILED";
        }
        if (exception instanceof EquipmentLifecycleNdjsonPartWriter.PartTooLargeException) {
            return "CHECKPOINT_PART_LIMIT_EXCEEDED";
        }
        if (equipmentId != null) {
            return "EQUIPMENT_ASSEMBLY_FAILED";
        }
        if (exception instanceof com.toir.exception.RestException) {
            return "SELECTION_INVALID";
        }
        return "EXPORT_PROCESSING_FAILED";
    }

    private static boolean resumeAllowed(String code) {
        return !"CHECKPOINT_CORRUPT".equals(code)
                && !"CHECKPOINT_PART_LIMIT_EXCEEDED".equals(code)
                && !"SERIALIZATION_FAILED".equals(code)
                && !"SELECTION_INVALID".equals(code);
    }

    private static String failureSummary(Exception exception) {
        if (exception instanceof EquipmentLifecycleNdjsonPartWriter.PartTooLargeException) {
            return "A bounded checkpoint part exceeded the configured byte limit";
        }
        if (exception instanceof EquipmentLifecycleExportStorage.StorageException) {
            return "Private export storage integrity verification failed";
        }
        return "Dataset export processing failed; the last committed checkpoint was preserved";
    }

    private static final class CheckpointIntegrityException extends RuntimeException {
        private CheckpointIntegrityException(String message) {
            super(message);
        }
    }
}
