package com.toir.service.equipmentlifecycleexport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.toir.config.EquipmentLifecycleExportProperties;
import com.toir.dto.equipmentlifecycleexport.EquipmentLifecycleExportManifestV1;
import com.toir.dto.equipmentlifecycleexport.EquipmentLifecycleExportManifestV1.Artifact;
import com.toir.dto.equipmentlifecycleexport.EquipmentLifecycleExportManifestV1.Consistency;
import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportPart;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportArtifactType;
import com.toir.exception.RestException;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportPartRepository;
import com.toir.service.equipmentlifecycleexport.EquipmentLifecycleExportLeaseService.FinalArtifact;
import com.toir.service.equipmentlifecycleexport.EquipmentLifecycleExportLeaseService.OwnedJob;
import com.toir.service.equipmentlifecycleexport.storage.EquipmentLifecycleExportObjectKeys;
import com.toir.service.equipmentlifecycleexport.storage.EquipmentLifecycleExportStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "toir.ai.equipment-lifecycle.export", name = "enabled", havingValue = "true")
public class EquipmentLifecycleExportFinalizer {
    private static final String CONSISTENCY_MODEL =
            "FIXED_AS_OF_WITH_FROZEN_EQUIPMENT_SELECTION_AND_READ_COMMITTED_SOURCE_READS";
    private static final int HEARTBEAT_BYTES = 8 * 1024 * 1024;

    private final EquipmentLifecycleExportPartRepository partRepository;
    private final EquipmentLifecycleExportLeaseService leaseService;
    private final EquipmentLifecycleExportStorage storage;
    private final EquipmentLifecycleExportObjectKeys keys;
    private final EquipmentLifecycleExportProperties properties;
    private final ObjectMapper objectMapper;

    public EquipmentLifecycleExportFinalizer(
            EquipmentLifecycleExportPartRepository partRepository,
            EquipmentLifecycleExportLeaseService leaseService,
            EquipmentLifecycleExportStorage storage,
            EquipmentLifecycleExportProperties properties,
            ObjectMapper objectMapper
    ) {
        this.partRepository = partRepository;
        this.leaseService = leaseService;
        this.storage = storage;
        this.keys = new EquipmentLifecycleExportObjectKeys(properties.getS3().getPrefix());
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void finalizeExport(UUID jobId, UUID token) {
        OwnedJob job = leaseService.ownedJob(jobId, token);
        List<EquipmentLifecycleExportPart> parts = partRepository.findAllByJobIdOrderByPartNumberAsc(jobId);
        long datasetSize = validateParts(job, token, parts);
        Instant completionTime = leaseService.beginFinalizing(jobId, token);

        DigestResult datasetDigest = digest(new HeartbeatingInputStream(
                new SequentialPartInputStream(storage, parts), jobId, token));
        if (datasetDigest.size() != datasetSize) {
            throw RestException.conflict("Dataset export part sizes changed during finalization");
        }
        String datasetKey = keys.finalArtifact(jobId, token, EquipmentLifecycleExportArtifactType.DATASET);
        EquipmentLifecycleExportStorage.ObjectMetadata dataset = storage.putImmutable(
                datasetKey,
                new HeartbeatingInputStream(new SequentialPartInputStream(storage, parts), jobId, token),
                datasetSize,
                datasetDigest.sha256(),
                EquipmentLifecycleExportArtifactType.DATASET.getMediaType()
        );

        byte[] schemaBytes = canonicalSchema();
        String schemaSha = EquipmentLifecycleExportStorage.sha256(schemaBytes);
        String schemaKey = keys.finalArtifact(jobId, token, EquipmentLifecycleExportArtifactType.SCHEMA);
        EquipmentLifecycleExportStorage.ObjectMetadata schema = storage.putImmutable(
                schemaKey, new ByteArrayInputStream(schemaBytes), schemaBytes.length, schemaSha,
                EquipmentLifecycleExportArtifactType.SCHEMA.getMediaType());

        byte[] manifestBytes = manifestBytes(job, completionTime, dataset, schema);
        String manifestSha = EquipmentLifecycleExportStorage.sha256(manifestBytes);
        String manifestKey = keys.finalArtifact(jobId, token, EquipmentLifecycleExportArtifactType.MANIFEST);
        EquipmentLifecycleExportStorage.ObjectMetadata manifest = storage.putImmutable(
                manifestKey, new ByteArrayInputStream(manifestBytes), manifestBytes.length, manifestSha,
                EquipmentLifecycleExportArtifactType.MANIFEST.getMediaType());

        byte[] sumsBytes = checksumBytes(dataset.sha256(), schema.sha256(), manifest.sha256());
        String sumsSha = EquipmentLifecycleExportStorage.sha256(sumsBytes);
        String sumsKey = keys.finalArtifact(jobId, token, EquipmentLifecycleExportArtifactType.CHECKSUMS);
        EquipmentLifecycleExportStorage.ObjectMetadata sums = storage.putImmutable(
                sumsKey, new ByteArrayInputStream(sumsBytes), sumsBytes.length, sumsSha,
                EquipmentLifecycleExportArtifactType.CHECKSUMS.getMediaType());

        List<FinalArtifact> artifacts = List.of(
                finalArtifact(EquipmentLifecycleExportArtifactType.DATASET, dataset),
                finalArtifact(EquipmentLifecycleExportArtifactType.SCHEMA, schema),
                finalArtifact(EquipmentLifecycleExportArtifactType.MANIFEST, manifest),
                finalArtifact(EquipmentLifecycleExportArtifactType.CHECKSUMS, sums)
        );
        for (FinalArtifact artifact : artifacts) {
            EquipmentLifecycleExportStorage.ObjectMetadata verified = storage.head(artifact.objectKey())
                    .orElseThrow(() -> RestException.conflict("Final dataset export artifact is missing"));
            if (verified.size() != artifact.objectSize() || !verified.sha256().equals(artifact.sha256())) {
                throw RestException.conflict("Final dataset export artifact integrity mismatch");
            }
        }
        if (!leaseService.continueProcessing(jobId, token)) {
            return;
        }
        leaseService.complete(jobId, token, artifacts);
    }

    private long validateParts(OwnedJob job, UUID token, List<EquipmentLifecycleExportPart> parts) {
        if (!job.selectionFrozen() || job.selectedCount() <= 0 || parts.isEmpty()) {
            throw RestException.conflict("Frozen dataset export parts are incomplete");
        }
        long expectedOrdinal = 0;
        long records = 0;
        long bytes = 0;
        int expectedPart = 0;
        for (EquipmentLifecycleExportPart part : parts) {
            if (part.getPartNumber() != expectedPart++ || part.getFirstOrdinal() != expectedOrdinal
                    || part.getLastOrdinal() != part.getFirstOrdinal() + part.getRecordCount() - 1) {
                throw RestException.conflict("Dataset export parts contain a missing or overlapping ordinal range");
            }
            EquipmentLifecycleExportStorage.ObjectMetadata object = storage.head(part.getObjectKey())
                    .orElseThrow(() -> RestException.conflict("Committed dataset export part is missing"));
            if (object.size() != part.getObjectSize() || !object.sha256().equals(part.getSha256())) {
                throw RestException.conflict("Committed dataset export part failed checksum validation");
            }
            expectedOrdinal = part.getLastOrdinal() + 1;
            records += part.getRecordCount();
            bytes = Math.addExact(bytes, part.getObjectSize());
            if (expectedPart % 20 == 0 && !leaseService.continueProcessing(job.jobId(), token)) {
                throw RestException.conflict("Dataset export was cancelled during finalization");
            }
        }
        if (records != job.selectedCount() || records != job.completedCount()
                || expectedOrdinal - 1 != job.lastCompletedOrdinal()) {
            throw RestException.conflict("Dataset export record count does not match frozen membership");
        }
        return bytes;
    }

    private byte[] canonicalSchema() {
        try (InputStream input = new ClassPathResource(
                "ai/equipment-lifecycle/equipment-lifecycle-context-v1.schema.json").getInputStream()) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Canonical W1 schema resource is unavailable", exception);
        }
    }

    private byte[] manifestBytes(
            OwnedJob job,
            Instant completionTime,
            EquipmentLifecycleExportStorage.ObjectMetadata dataset,
            EquipmentLifecycleExportStorage.ObjectMetadata schema
    ) {
        try {
            JsonNode policy = objectMapper.readTree(job.resolvedPolicyJson());
            EquipmentLifecycleExportManifestV1 manifest = new EquipmentLifecycleExportManifestV1(
                    "1.0",
                    job.jobId(),
                    "EquipmentLifecycleContextV1",
                    "1.0",
                    "NDJSON",
                    "UTF-8",
                    "LF",
                    job.createdAt(),
                    job.startedAt(),
                    completionTime,
                    job.asOf(),
                    job.contextProfile(),
                    policy,
                    job.requestFingerprint(),
                    job.policyFingerprint(),
                    job.selectionMode(),
                    job.selectedCount(),
                    job.completedCount(),
                    dataset.size(),
                    List.of(
                            new Artifact(EquipmentLifecycleExportArtifactType.DATASET.getFilename(),
                                    EquipmentLifecycleExportArtifactType.DATASET.getMediaType(), dataset.size(), dataset.sha256()),
                            new Artifact(EquipmentLifecycleExportArtifactType.SCHEMA.getFilename(),
                                    EquipmentLifecycleExportArtifactType.SCHEMA.getMediaType(), schema.size(), schema.sha256())
                    ),
                    "W1_DETERMINISTIC_CANONICAL_JSON_SHA256",
                    "FROZEN_MEMBERSHIP_ORDINAL_ASC",
                    Map.of(
                            "recordLevelDataQualityPreserved", true,
                            "recordLevelSectionTruncationMetadataPreserved", true
                    ),
                    new Consistency(
                            CONSISTENCY_MODEL,
                            true,
                            false,
                            "READ_COMMITTED",
                            true,
                            "Each W1 record retains its context fingerprint and source watermarks"
                    ),
                    completionTime.plus(properties.getCompletedRetention()),
                    List.of(
                            "No single MVCC snapshot spans this export",
                            "Source rows may change while a long export is running"
                    )
            );
            byte[] json = objectMapper.writer().without(SerializationFeature.INDENT_OUTPUT)
                    .writeValueAsBytes(manifest);
            byte[] result = java.util.Arrays.copyOf(json, json.length + 1);
            result[result.length - 1] = '\n';
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("Dataset export manifest generation failed", exception);
        }
    }

    static byte[] checksumBytes(String datasetSha, String schemaSha, String manifestSha) {
        String value = datasetSha + "  " + EquipmentLifecycleExportArtifactType.DATASET.getFilename() + "\n"
                + schemaSha + "  " + EquipmentLifecycleExportArtifactType.SCHEMA.getFilename() + "\n"
                + manifestSha + "  " + EquipmentLifecycleExportArtifactType.MANIFEST.getFilename() + "\n";
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static FinalArtifact finalArtifact(
            EquipmentLifecycleExportArtifactType type,
            EquipmentLifecycleExportStorage.ObjectMetadata metadata
    ) {
        return new FinalArtifact(type, metadata.key(), metadata.size(), metadata.sha256());
    }

    private static DigestResult digest(InputStream input) {
        try (InputStream source = input) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            long size = 0;
            int read;
            while ((read = source.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                size += read;
            }
            return new DigestResult(size, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException exception) {
            throw new EquipmentLifecycleExportStorage.StorageException("Export stream read failed", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record DigestResult(long size, String sha256) {}

    private static final class SequentialPartInputStream extends InputStream {
        private final EquipmentLifecycleExportStorage storage;
        private final List<EquipmentLifecycleExportPart> parts;
        private int index;
        private EquipmentLifecycleExportStorage.StoredObject current;

        private SequentialPartInputStream(
                EquipmentLifecycleExportStorage storage,
                List<EquipmentLifecycleExportPart> parts
        ) {
            this.storage = storage;
            this.parts = List.copyOf(parts);
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int read = read(one, 0, 1);
            return read == -1 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            while (true) {
                if (current == null && index < parts.size()) {
                    EquipmentLifecycleExportPart part = parts.get(index++);
                    current = storage.open(part.getObjectKey());
                    if (current.metadata().size() != part.getObjectSize()
                            || !current.metadata().sha256().equals(part.getSha256())) {
                        close();
                        throw new IOException("Committed export part integrity changed");
                    }
                }
                if (current == null) {
                    return -1;
                }
                int read = current.input().read(bytes, offset, length);
                if (read != -1) {
                    return read;
                }
                current.close();
                current = null;
            }
        }

        @Override
        public void close() throws IOException {
            if (current != null) {
                current.close();
                current = null;
            }
        }
    }

    private final class HeartbeatingInputStream extends InputStream {
        private final InputStream delegate;
        private final UUID jobId;
        private final UUID token;
        private int bytesUntilHeartbeat = HEARTBEAT_BYTES;

        private HeartbeatingInputStream(InputStream delegate, UUID jobId, UUID token) {
            this.delegate = delegate;
            this.jobId = jobId;
            this.token = token;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int read = read(one, 0, 1);
            return read == -1 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = delegate.read(bytes, offset, length);
            if (read > 0) {
                bytesUntilHeartbeat -= read;
                if (bytesUntilHeartbeat <= 0) {
                    if (!leaseService.continueProcessing(jobId, token)) {
                        throw new IOException("Dataset export was cancelled");
                    }
                    bytesUntilHeartbeat = HEARTBEAT_BYTES;
                }
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
