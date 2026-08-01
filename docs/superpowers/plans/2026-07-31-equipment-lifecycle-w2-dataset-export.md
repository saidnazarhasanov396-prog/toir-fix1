# Equipment Lifecycle W2 Dataset Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a secure, persistent, resumable W1 Equipment lifecycle dataset export backed by private MinIO/S3-compatible immutable checkpoint objects.

**Architecture:** A dedicated permission protects a default-disabled REST module. A persistent job freezes bounded Equipment membership and a resolved W1 policy, while a bounded executor processes ordinal batches under database lease fencing. An export-specific MinIO adapter stores immutable staged parts and private final artifacts; database publication happens only after HEAD/checksum verification.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring Data JPA, PostgreSQL/Flyway, Jackson, MinIO Java SDK 8.6, Spring Security, JUnit 5/Mockito/MockMvc.

## Global Constraints

- Start from `6c44a64780abc62357cbb7d3a1e9a9b9a548a2c0` on `codex/equipment-ai-lifecycle-w2-dataset-export`.
- Preserve the W1 `EquipmentLifecycleContextV1`, schema version `1.0`, assembler, fingerprint, watermarks, bounds, and semantics.
- Feature disabled by default; dedicated S3 credentials have no defaults and are never logged or returned.
- Do not use legacy `S3Service`, create buckets, change policy/ACL, expose URLs/keys, or invent production infrastructure.
- No model call/results, Kafka/outbox, frontend, or W3-W7 changes.
- Write tests before corresponding production code, but do not execute tests, Maven, builds, compilation, or application startup per user instruction.
- Allowed verification is static only; final verification text is `NOT RUN — skipped by user instruction.`

---

### Task 1: Persistence, migration, and immutable domain state

**Files:**
- Create: `src/main/resources/db/migration/V20260731_1__equipment_lifecycle_dataset_export.sql`
- Create: `src/main/java/com/toir/entity/equipmentlifecycleexport/*.java`
- Create: `src/main/java/com/toir/enums/equipmentlifecycleexport/*.java`
- Create: `src/main/java/com/toir/repository/equipmentlifecycleexport/*.java`
- Test: `src/test/java/com/toir/migration/EquipmentLifecycleDatasetExportMigrationContractTest.java`
- Test: `src/test/java/com/toir/repository/equipmentlifecycleexport/EquipmentLifecycleExportRepositoryContractTest.java`

**Interfaces:**
- Produces persistent job, membership, part, and artifact models; row-locking repositories; state and artifact enums.

- [ ] Write migration/repository contract tests for unique idempotency, membership/ordinal, part/artifact constraints, lease/expiry indexes, and no Equipment cascade FK.
- [ ] Add the Flyway migration with named string states, JSONB policy/request, non-negative checks, unique keys, and bounded lookup indexes.
- [ ] Add focused JPA entities with UUID identities, UTC instants, optimistic job versioning, no artifact bytes, and no user/entity relationships.
- [ ] Add repositories for row-locked jobs, bounded membership reads, committed part/artifact reads, and cleanup candidates.
- [ ] Statically inspect SQL/entity column parity and migration version uniqueness.

### Task 2: Configuration, profile resolution, permission, and fingerprints

**Files:**
- Create: `src/main/java/com/toir/config/EquipmentLifecycleExportProperties.java`
- Create: `src/main/java/com/toir/config/EquipmentLifecycleExportConfiguration.java`
- Create: `src/main/java/com/toir/service/equipmentlifecycleexport/EquipmentLifecycleExportProfileResolver.java`
- Create: `src/main/java/com/toir/service/equipmentlifecycleexport/EquipmentLifecycleExportFingerprintService.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/com/toir/security/PermissionConstants.java`
- Modify: `src/main/java/com/toir/enums/AuditModule.java`
- Test: `src/test/java/com/toir/config/EquipmentLifecycleExportPropertiesTest.java`
- Test: `src/test/java/com/toir/service/equipmentlifecycleexport/EquipmentLifecycleExportProfileResolverTest.java`

**Interfaces:**
- Produces `resolve(String, Instant)` returning a fully bounded W1 policy and deterministic request/policy SHA-256 helpers.

- [ ] Write tests for disabled defaults, mandatory enabled settings, bounded executor/profile values, allowlisted profiles, and absence of credential defaults.
- [ ] Add environment-backed properties, conditional validation, a concurrency-one bounded executor, and documented conservative limits.
- [ ] Add the dedicated permission and audit module without granting it to ordinary roles.
- [ ] Add `standard-v1` resolver with an explicit positive limit for every W1 section, UTC history/asOf windows, and no client-supplied arbitrary limits.
- [ ] Add deterministic SHA-256 fingerprints over normalized creator-independent requests and immutable resolved policies.

### Task 3: Export-specific private MinIO storage

**Files:**
- Create: `src/main/java/com/toir/service/equipmentlifecycleexport/storage/EquipmentLifecycleExportStorage.java`
- Create: `src/main/java/com/toir/service/equipmentlifecycleexport/storage/EquipmentLifecycleExportObjectKeys.java`
- Create: `src/main/java/com/toir/service/equipmentlifecycleexport/storage/MinioEquipmentLifecycleExportStorage.java`
- Test: `src/test/java/com/toir/service/equipmentlifecycleexport/storage/EquipmentLifecycleExportObjectKeysTest.java`
- Test: `src/test/java/com/toir/service/equipmentlifecycleexport/storage/MinioEquipmentLifecycleExportStorageTest.java`

**Interfaces:**
- Produces immutable `put`, `head`, `open`, bounded `list`, and idempotent `delete` operations over server-generated logical keys.

- [ ] Write tests that reject traversal/caller-style keys and prove deterministic token-specific part and allowlisted final keys.
- [ ] Write adapter tests for streaming put/get, immutable identical retry, checksum/size mismatch, bounded listing, and idempotent deletion.
- [ ] Implement key generation and prefix normalization without bucket creation, ACL/policy mutation, public URLs, or legacy `S3Service` calls.
- [ ] Implement streaming MinIO operations with SHA-256 object metadata, HEAD verification, bounded multipart part size, and sanitized exceptions/logging.

### Task 4: Frozen selection and idempotent creation

**Files:**
- Create: `src/main/java/com/toir/dto/equipmentlifecycleexport/EquipmentLifecycleExportRequests.java`
- Create: `src/main/java/com/toir/service/equipmentlifecycleexport/EquipmentLifecycleExportSelectionService.java`
- Create: `src/main/java/com/toir/service/equipmentlifecycleexport/EquipmentLifecycleExportJobService.java`
- Modify: `src/main/java/com/toir/repository/equipment/EquipmentRepository.java`
- Test: `src/test/java/com/toir/service/equipmentlifecycleexport/EquipmentLifecycleExportSelectionServiceTest.java`
- Test: `src/test/java/com/toir/service/equipmentlifecycleexport/EquipmentLifecycleExportJobServiceTest.java`

**Interfaces:**
- Produces `create`, `freeze`, `resume`, `cancel`, `find`, and paginated list operations; frozen membership is consumed by the worker.

- [ ] Write tests for explicit scope, sorted deduplication, soft-delete rejection, keyset all-scope, max/empty rejection, interrupted preparation, and frozen resume.
- [ ] Write idempotency tests for same/different requests, concurrent uniqueness recovery, active-request reuse, fixed asOf, persisted resolved policy, and state conflicts.
- [ ] Add a UUID keyset projection query and bounded selection materialization with unique ordinals and freeze-last semantics.
- [ ] Implement transactional creation and lifecycle commands, after-commit dispatch, safe failure DTO fields, and audit calls.

### Task 5: NDJSON parts, leases, resume, and worker

**Files:**
- Create: `src/main/java/com/toir/service/equipmentlifecycleexport/EquipmentLifecycleExportLeaseService.java`
- Create: `src/main/java/com/toir/service/equipmentlifecycleexport/EquipmentLifecycleNdjsonPartWriter.java`
- Create: `src/main/java/com/toir/service/equipmentlifecycleexport/EquipmentLifecycleExportWorker.java`
- Create: `src/main/java/com/toir/service/equipmentlifecycleexport/EquipmentLifecycleExportDispatcher.java`
- Test: `src/test/java/com/toir/service/equipmentlifecycleexport/EquipmentLifecycleNdjsonPartWriterTest.java`
- Test: `src/test/java/com/toir/service/equipmentlifecycleexport/EquipmentLifecycleExportWorkerTest.java`
- Test: `src/test/java/com/toir/service/equipmentlifecycleexport/EquipmentLifecycleExportLeaseServiceTest.java`

**Interfaces:**
- Consumes frozen ordinal membership and W1 `assemble(UUID, Instant, EquipmentLifecycleContextPolicy)`.
- Produces committed immutable part metadata and fenced progress.

- [ ] Write NDJSON tests for exact W1 DTO, UTF-8/LF/no-BOM/no-wrapper/no-pretty output, bounded bytes, and serialization fail-before-publication.
- [ ] Write lease tests proving exclusive claim, heartbeat, expiry takeover, stale-token checkpoint rejection, and cooperative cancellation.
- [ ] Write worker tests proving storage verification precedes DB checkpoint, committed-only resume, contiguous ordinals, fixed asOf/policy, no duplicate/missing lines, and fail-fast safe failures.
- [ ] Implement bounded part serialization, attempt-token staging uploads, HEAD validation, then short fenced checkpoint transactions.
- [ ] Implement committed-part revalidation and resume, dedicated after-commit dispatch, and no correctness-critical in-memory state.

### Task 6: Manifest, canonical schema, checksums, and atomic completion

**Files:**
- Create: `src/main/java/com/toir/dto/equipmentlifecycleexport/EquipmentLifecycleExportManifestV1.java`
- Create: `src/main/java/com/toir/service/equipmentlifecycleexport/EquipmentLifecycleExportFinalizer.java`
- Create: `docs/ai/equipment-lifecycle/equipment-lifecycle-export-manifest-v1.schema.json`
- Create: `docs/ai/equipment-lifecycle/equipment-lifecycle-export-manifest-v1.example.json`
- Modify: `pom.xml`
- Test: `src/test/java/com/toir/service/equipmentlifecycleexport/EquipmentLifecycleExportFinalizerTest.java`
- Test: `src/test/java/com/toir/service/equipmentlifecycleexport/EquipmentLifecycleExportArtifactContractTest.java`

**Interfaces:**
- Produces four verified private immutable final artifacts and atomically publishes their DB metadata with `COMPLETED`.

- [ ] Write tests for streaming ordered concatenation, missing/overlap rejection, record-count mismatch, checksums, deterministic `SHA256SUMS`, and invisible-before-completion artifacts.
- [ ] Package the canonical W1 schema from `docs/` as a single Maven resource source and add exact-byte contract coverage.
- [ ] Implement two-pass streaming dataset hash/upload, schema copy, synthetic-safe versioned manifest, and checksum file in non-circular order.
- [ ] Re-HEAD every final artifact and perform one short lease-fenced metadata insert plus `COMPLETED` transition only after all invariants hold.
- [ ] Validate the manifest schema/example and canonical schema JSON with an available lightweight parser.

### Task 7: Privileged API and secure streaming download

**Files:**
- Create: `src/main/java/com/toir/dto/equipmentlifecycleexport/EquipmentLifecycleExportResponses.java`
- Create: `src/main/java/com/toir/controller/EquipmentLifecycleDatasetExportController.java`
- Test: `src/test/java/com/toir/security/RbacEquipmentLifecycleDatasetExportSecurityTest.java`
- Test: `src/test/java/com/toir/controller/EquipmentLifecycleDatasetExportControllerTest.java`

**Interfaces:**
- Exposes create/list/status/resume/cancel/artifact-list/download under `/api/v1/ai/equipment-lifecycle/dataset-exports`.

- [ ] Write MockMvc security tests covering unauthenticated, ordinary equipment reader, dedicated permission, system admin, and wildcard across every route.
- [ ] Write controller tests for 202 creation/resume, idempotent cancellation, allowlisted artifacts, completed/non-expired gating, expiry, fixed download headers, and streaming Resource response.
- [ ] Implement DTO mapping without keys/paths/contacts/idempotency values and a controller-wide restrictive `@PreAuthorize` expression.
- [ ] Implement current-authorization download with fixed filename/content type, content length, SHA ETag, private no-store/no-transform, and nosniff.

### Task 8: Retention, cleanup, and audit

**Files:**
- Create: `src/main/java/com/toir/service/equipmentlifecycleexport/EquipmentLifecycleExportRetentionService.java`
- Test: `src/test/java/com/toir/service/equipmentlifecycleexport/EquipmentLifecycleExportRetentionServiceTest.java`

**Interfaces:**
- Produces bounded multi-instance-safe cleanup claims and idempotent final/staging deletion.

- [ ] Write tests for expiry-before-I/O, immediate download prohibition, bounded claims/listing, retry after delete failure, completed staging cleanup, failed/cancelled retention, and active-job safety.
- [ ] Implement row-locked skip-locked claims, claim expiry, bounded object deletion, incomplete cleanup retry, and injected-clock scheduling.
- [ ] Record safe create/resume/cancel/download/expiry audit events without payloads, keys, paths, credentials, or idempotency keys.

### Task 9: Static security review, report, commit, and push

**Files:**
- Create: `EQUIPMENT_AI_LIFECYCLE_W2_DATASET_EXPORT_IMPLEMENTATION_2026-07-31.md`

**Interfaces:**
- Produces the required 30-section evidence report and the single W2 commit on the requested branch.

- [ ] Inspect all changed files and use `rg` to confirm no model/Kafka/frontend/public URL/raw key/credential leakage and no legacy `S3Service` dependency.
- [ ] Parse all new JSON documents with the available lightweight parser.
- [ ] Run `git diff --check`, review full diff/status, verify frontend unchanged, and re-hash the preserved untracked document.
- [ ] Write the 30-section report separating application-code status from blocked/unverified production MinIO readiness and state exactly `NOT RUN — skipped by user instruction.`
- [ ] Commit only W2 files as `Add resumable equipment lifecycle dataset export`.
- [ ] Push only `codex/equipment-ai-lifecycle-w2-dataset-export` without merge or force-push and record the result.
