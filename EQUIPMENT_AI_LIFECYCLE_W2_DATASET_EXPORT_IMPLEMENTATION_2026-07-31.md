# Equipment AI Lifecycle W2 Dataset Export Implementation Report

## 1. Executive summary

Two results must be kept separate:

1. **W2 application code:** implemented, statically inspected, committed, and
   pushed. It provides a default-disabled, persistent, asynchronous, bounded,
   resumable Equipment lifecycle dataset export using private MinIO/S3-compatible
   object storage and immutable checkpoint parts.
2. **Production MinIO readiness:** **BLOCKED / NOT VERIFIED**. Production
   activation remains prohibited until the DevOps evidence listed in section 13
   is supplied.

The implementation produces UTF-8 NDJSON containing exact W1
`EquipmentLifecycleContextV1` records, the canonical W1 JSON Schema, a versioned
manifest, and deterministic `SHA256SUMS`. It adds no model call, model-result
persistence, Kafka/outbox behavior, W3-W7 feature, or frontend change.

## 2. Exact source branch and baseline commit

- Source branch: `codex/equipment-ai-lifecycle-w0-w1`
- Exact source commit:
  `6c44a64780abc62357cbb7d3a1e9a9b9a548a2c0`
- Isolated recovery checkout:
  `/tmp/toir-w2-recovery.Gxr7HQ`
- Recovery checkout was created from the exact source commit with independent
  Git metadata; no pull, merge, rebase, reset, stash, or force-push was used.

## 3. Target branch and final HEAD

- Target branch: `codex/equipment-ai-lifecycle-w2-dataset-export`
- Functional W2 HEAD:
  `db78cadca7f23e1fbbd97c7ec8bf74292d9424f3`
- Functional upstream after push:
  `origin/codex/equipment-ai-lifecycle-w2-dataset-export`
- Functional local/remote divergence after push: ahead 0, behind 0.

This report is a documentation closeout after the functional commit. A Git
commit cannot embed its own object ID because doing so changes that ID. The
exact report-bearing final HEAD and matching remote ref are therefore recorded
in the final delivery response.

## 4. W1 contract reused

W2 reuses the implemented W1 `EquipmentLifecycleContextAssembler` and calls
`assemble(equipmentId, asOf, persistedPolicy)` one Equipment at a time. It does
not introduce a reduced export DTO or expose entities. Each line retains W1
schema version `1.0`, context fingerprint, section ordering, null/unit/currency
semantics, data-quality metadata, truncation metadata, and source watermarks.

The W1 assembler, canonical mapper, context DTO, policy, fingerprint algorithm,
schema, and existing repository projections were not redesigned.

## 5. Export architecture

The flow is:

`POST create -> short job transaction -> after-commit bounded dispatch ->`
`lease claim -> frozen membership -> W1 assembly -> immutable part upload ->`
`fenced DB checkpoint -> streaming finalization -> atomic COMPLETED publication`.

Correctness-critical state is stored in PostgreSQL. Artifact bytes remain in
private object storage. The executor is dedicated and bounded. Storage I/O and
W1 assembly do not occur while a broad job-row lock is held.

## 6. Database migration and constraints

`V20260731_1__equipment_lifecycle_dataset_export.sql` adds:

- export jobs with creator, creator-scoped idempotency key, normalized request
  fingerprint/snapshot, immutable authorization scope, `asOf`, schema/manifest
  versions, resolved policy/fingerprint, state, progress, lease, safe failure,
  retention, cleanup claim, timestamps, and optimistic version;
- frozen membership with unique `(job_id, equipment_id)` and
  `(job_id, ordinal)`;
- immutable committed parts with part number, ordinal interval, record count,
  object size, SHA-256, and fencing token;
- final artifact metadata with allowlisted type, fixed filename/media type,
  size, and SHA-256.

The migration adds status, expiry, lease, cleanup, and ordinal indexes plus
non-negative and enum-name checks. It stores no NDJSON, exported JSON line,
binary artifact, credential, token, or Equipment foreign key that could cascade
away frozen membership. The version did not collide with the baseline migration
set during static inspection.

## 7. Authorization and permission decision

All routes require `SYSTEM_ADMIN`, wildcard `*`, or the dedicated
`EQUIPMENT_LIFECYCLE_DATASET_EXPORT` authority. The dedicated authority is not
granted to ordinary Equipment readers. System administrators already use the
repository's wildcard convention; custom roles may be assigned the dedicated
string through the existing role-permission model.

Non-global exporters must have a valid department ID. Their immutable
authorization scope is stored as the department UUID and applied to explicit
and all-authorized Equipment selection using the existing responsible-department
fallback rule. Non-global list/status/resume/cancel/artifact operations are also
creator-scoped; cross-owner access returns not found. Every download re-enters
method authorization and owner/global checks. The worker uses persisted scope
and does not depend on an asynchronous `SecurityContext`.

## 8. Export request and profile semantics

Creation requires an `Idempotency-Key`, named profile, and explicit scope mode:
`EXPLICIT_IDS` or `ALL_AUTHORIZED`. Explicit IDs are normalized, deduplicated,
and sorted. `ALL_AUTHORIZED` rejects supplied IDs. The server-enforced maximum
cannot be bypassed by the request.

The allowlisted `standard-v1` profile resolves all W1 sections with explicit
history, future-horizon, section-row, and measurement-granularity bounds. The
fully resolved JSON policy, policy SHA-256, normalized request JSON, and request
SHA-256 are stored at creation. Resume restores those persisted values rather
than current configuration. Parallel creator/idempotency races are stopped by a
database unique constraint and exposed as a sanitized conflict.

## 9. Equipment selection and freezing strategy

Selection uses only UUID projections. Explicit selection checks active,
authorized IDs in configured batches. All-authorized selection uses stable UUID
keyset pagination, not offset pagination or entity `findAll`. Soft-deleted and
out-of-department Equipment are excluded.

Membership rows receive contiguous zero-based ordinals and unique Equipment IDs.
The selection transaction clears any non-frozen preparation rows, rematerializes
them, persists the count, and marks `selection_frozen` only at the end. Empty or
oversized selections fail explicitly. Ordinary resume returns the existing
frozen membership and never rebuilds it.

## 10. Job states and transitions

Persistent states are `QUEUED`, `PREPARING`, `RUNNING`, `FINALIZING`,
`COMPLETED`, `CANCEL_REQUESTED`, `CANCELLED`, `FAILED`, and `EXPIRED`.

The successful path is the required ordered path. Queued/failed jobs may cancel
immediately; active jobs use cooperative cancellation. Completed or expired jobs
cannot be cancelled or resumed. Failed jobs expose only bounded safe failure
fields and an explicit `resumeAllowed` value. Artifact descriptors and bytes are
unavailable before `COMPLETED`; expiry gates access before physical cleanup.

## 11. Async executor, lease, and concurrency strategy

Creation dispatches only in transaction `afterCommit`. A dedicated
`ThreadPoolTaskExecutor` defaults to concurrency one and a queue of two, both
configurable and bounded. Queue rejection leaves the persistent job manually
resumable.

Workers claim the row with a pessimistic lock and receive a new UUID fencing
token. Active leases reject a second worker; expired leases may be taken over
with a different token. Heartbeats extend the lease at record, resume-validation,
and streaming-finalization boundaries. Checkpoint, finalization, completion, and
failure publication validate the current token; checkpoint/completion also
validate unexpired lease ownership. A stale worker cannot publish its objects or
state.

## 12. Checkpoint and resume algorithm

Each configured record batch is serialized into one bounded byte array, assigned
a deterministic part number and contiguous ordinal interval, and uploaded under
`staging/<job>/attempt-<fencing-token>/parts/...`. The adapter HEAD-validates its
stored size and SHA-256 metadata before a short fenced transaction inserts the
part row and advances `completed_count` and `last_completed_ordinal`.

Resume trusts only committed DB part rows. It re-HEADs every committed object,
checks size/SHA-256, consecutive part numbers, non-overlapping contiguous
ordinals, record counts, and agreement with job progress. Missing or corrupt
committed objects make the checkpoint non-resumable. Orphan/uncommitted objects
are ignored because resume never lists storage as checkpoint authority. A new
lease uses a new attempt prefix, so a stale worker cannot overwrite active parts.

## 13. Storage abstraction and deployment assumptions

`EquipmentLifecycleExportStorage` is a narrow export-only abstraction with
immutable put, HEAD, streaming open, bounded list, and idempotent delete. The
MinIO/S3-compatible adapter creates its own dedicated client from environment
configuration. It never calls the legacy `S3Service`, creates a bucket, changes
bucket policy, sets ACLs, creates public/signed URLs, or returns physical keys in
ordinary DTOs. Keys are server-generated beneath a validated relative prefix;
absolute paths, backslashes, dot segments, control characters, and unmanaged
prefixes are rejected. No filesystem or node-local fallback exists.

The feature is disabled by default. When enabled, endpoint, bucket, prefix,
access key, and secret key are mandatory; optional region and TLS trust-store
settings are environment-backed. Credential values have no W2 defaults and are
not logged.

**PRODUCTION MINIO READINESS: BLOCKED / NOT VERIFIED.** DevOps must provide and
prove all of the following before enabling the feature:

- a private durable bucket and exact runtime endpoint, bucket, and export prefix;
- protected/masked and rotated access/secret credentials or secret-manager
  references accessible by every backend instance;
- prefix-scoped least-privilege permission for put, get/HEAD, list, delete, and
  the multipart operations required by the MinIO SDK;
- public-access-block/bucket-policy evidence prohibiting anonymous reads;
- streaming and multipart capability with configured part-size compatibility;
- network/DNS/TLS reachability from every backend instance;
- durable MinIO cluster persistence, replication/failure-domain, retention, and
  container-restart evidence.

The existing generic repository MinIO configuration contains source-stored
credentials. Their values are intentionally not reproduced here. They are a
separate production security blocker: migrate them to protected/masked secrets
or a secret manager and rotate them. W2 neither copies those values nor rewrites
the unrelated generic storage subsystem.

## 14. NDJSON format

The dataset filename is `equipment-lifecycle-context-v1.ndjson`. It is compact
UTF-8, no BOM, one complete W1 JSON object per line, LF terminated, with no array
wrapper, blank line, pretty printing, or second business envelope. Export order
is frozen ordinal order. Each accepted line is checked for W1 schema version,
Equipment ID, and context fingerprint before part publication. Serialization or
part-size failure returns no partial visible line.

## 15. Manifest contract

The versioned manifest includes job ID, record/schema/manifest versions, format,
encoding, line ending, lifecycle timestamps, fixed `asOf`, profile and complete
resolved policy, request/policy fingerprints, selection mode and counts, dataset
bytes, dataset/schema artifact metadata, context-version algorithm, ordering,
data-quality/truncation preservation, expiry, and explicit consistency details.

It contains no contacts, users/employees, tokens, credentials, storage keys,
paths, URLs, comments, payloads, or stack traces. Application/source version is
omitted because the running build has no proven build metadata source.

## 16. Schema-artifact strategy

The canonical W1 schema remains
`docs/ai/equipment-lifecycle/equipment-lifecycle-context-v1.schema.json`.
Maven resources package that exact file into the runtime classpath from its
single authoritative source; no divergent second schema copy is maintained.
W2 adds a separate manifest v1 schema and synthetic example.

## 17. Checksum and atomic-finalization strategy

Finalization validates every committed part, streams parts in ordinal order to
calculate the dataset SHA-256/size, and streams them again into an immutable
token-specific final object. It then stores the exact schema, generates the
manifest containing dataset/schema metadata, and writes deterministic lowercase
`SHA256SUMS` entries for dataset, schema, and manifest only.

Every final object is re-HEADed for size and SHA-256. A final lease heartbeat and
fencing check precedes one short transaction that inserts all four artifact rows
and moves the job to `COMPLETED`. S3 copy/delete is not modeled as atomic rename;
objects may physically exist before publication but are unreachable through the
API until the atomic DB transition. Count, ordinal, size, or checksum mismatch
blocks completion and download.

## 18. API endpoints and permissions

All endpoints are beneath
`/api/v1/ai/equipment-lifecycle/dataset-exports` and share the restrictive
authorization expression:

- `POST /` — create, `202 Accepted`, creator-scoped idempotency;
- `GET /` — bounded pagination and optional status filter;
- `GET /{exportId}` — state/progress/safe metadata;
- `POST /{exportId}/resume` — `202`, resumable or expired-lease recovery;
- `POST /{exportId}/cancel` — `202`, idempotent/cooperative cancellation;
- `GET /{exportId}/artifacts` — completed, non-expired metadata only;
- `GET /{exportId}/artifacts/{artifactType}` — allowlisted enum download.

Create, resume, cancel, download, and expiry use the existing audit mechanism
without request bodies, idempotency keys, object keys, or artifact payloads.

## 19. Download security

Download rechecks current method authorization and owner/global scope, then
requires `COMPLETED` and `expiresAt > now`. Expired data returns 410-equivalent;
incomplete or integrity-invalid data returns conflict. The stored object HEAD
metadata must match DB size and SHA-256.

Responses stream an `InputStreamResource` and use fixed allowlisted filename and
media type, `Content-Length`, SHA-256 ETag, `Cache-Control: private, no-store,
no-transform`, safe attachment disposition, and
`X-Content-Type-Options: nosniff`. No byte-array download, arbitrary filename,
path parameter, Range claim, signed URL, or storage credential is exposed.

## 20. Retention and cleanup

`expiresAt` is derived from bounded configured retention at completion. The
cleanup query uses a bounded `FOR UPDATE SKIP LOCKED` claim with expiring claim
tokens, making multiple instances state-safe. A completed expired job becomes
`EXPIRED` before object I/O, so downloads stop immediately.

Cleanup deletes staging/orphan and final prefixes in bounded list batches and is
idempotent. Incomplete deletion clears the claim and retries later. Completed
staging parts are cleaned after publication. Failed/cancelled staging and any
unpublished final objects are cleaned after bounded retention, after which
resume is disabled. Expired artifact, part, and membership metadata is reduced
while the minimal job audit record remains. Active jobs are not age-deleted.

## 21. Query and load protection

Equipment selection uses UUID-only bounded batches and UUID keysets. Frozen
membership is read through a configured `PageRequest`. W1 policy contains a
positive limit for every section and a bounded history window. The worker calls
the assembler one Equipment at a time. Checkpoint byte arrays are capped; the
complete Equipment list and complete dataset are never held in heap. Final
checksums and concatenation use streaming I/O. Executor concurrency, queue,
selection batch, checkpoint records/bytes, multipart part size, lease, cleanup
batch, and retention are configurable and bounded.

Production values require representative load testing before activation.

## 22. Security and data-minimization review

Static review found no W2 model/Kafka/outbox behavior, legacy `S3Service` call,
bucket creation, policy/ACL mutation, public URL, signed URL, common pool,
caller-controlled storage key, context logging, or API storage-key field.
Ordinary Equipment read authority is insufficient. Frozen authorization scope
prevents request selection from exceeding current department scope. Worker and
API failure messages are bounded/sanitized; full diagnostics stay server-side
without context payload logging.

W2 does not add employee contact fields, credentials, authorization headers,
binary document data, attachment bodies, unrestricted comments/audits,
filesystem paths, or stack traces to dataset-specific DTOs or manifest.

## 23. Tests written

The recovered functional commit contains 65 focused `@Test` methods across
configuration, migration contracts, request/profile fingerprints, frozen
selection, idempotency/concurrent conflict, job authorization and expiry, lease
takeover/fencing/cancellation, NDJSON, worker checkpoint ordering/resume/failure,
finalization/checksums, storage keys and MinIO adapter behavior, manifest/schema
contracts, RBAC/controller headers, and bounded retention cleanup.

These tests were written but deliberately not executed under the user-mandated
verification restriction. No claim is made that they compile or pass.

## 24. Verification status

NOT RUN — skipped by user instruction.

Permitted static evidence performed:

- full staged file inventory and diff-stat review;
- repeated `git diff --check` with no reported whitespace errors;
- `rg` checks for forbidden storage behavior, W3-W7 scope, frontend/unrelated
  paths, environment-backed settings, fencing/checkpoint/finalization guards,
  and query bounds;
- lightweight parse of four W1/W2 JSON documents;
- manifest synthetic example validation against its JSON Schema;
- exact branch, baseline ancestry, remote-branch absence before first push,
  local/remote ref equality, and 0/0 divergence checks.

Maven, Gradle, JUnit, Testcontainers, npm, Vitest, compilation, build,
application startup, migration execution, MinIO runtime calls, and runtime
verification were not run.

## 25. Known limitations

- Production MinIO/IAM/durability/reachability/multipart/retention readiness is
  blocked and unverified as detailed in section 13.
- No build, compilation, test, migration, or startup result is available.
- A long export is not one MVCC snapshot; committed records are not rewritten if
  later source data changes.
- Queue rejection or crash after commit and before dispatch requires manual
  resume; automatic W6 reconciliation is intentionally absent.
- HTTP Range download is not implemented.
- Final dataset hashing and upload use two streaming passes over committed parts
  to avoid retaining the dataset in memory.
- Runtime application/build version is omitted from the manifest because no
  reliable build metadata source was proven.

## 26. Consistency guarantees and non-guarantees

The declared model is
`FIXED_AS_OF_WITH_FROZEN_EQUIPMENT_SELECTION_AND_READ_COMMITTED_SOURCE_READS`.
W2 guarantees one persisted `asOf`, one persisted resolved policy, one frozen
ordered Equipment membership, immutable already-committed parts, exactly one
committed ordinal interval per part, and final record count equal to selected
count before publication.

It does not guarantee one database/MVCC snapshot over the full export. Source
rows may change while later Equipment records are assembled. Per-record W1
context fingerprints and source watermarks support traceability; already
checkpointed records are not silently rebuilt on resume.

## 27. Deferred W3-W7 work

No model client, prompt construction, inference, model-result DTO/table/history,
health/RUL calculation, Kafka, outbox, producer/consumer, DLQ, retry platform,
reconciliation, change trigger, or automated maintenance action was added.
Those capabilities remain deferred to W3-W7.

## 28. Files changed

The functional commit contains 56 W2 files:

- configuration: export properties/configuration and `application.yml` env map;
- API: controller, request/response DTOs, artifact/manifest DTOs;
- persistence: one Flyway migration, four entities, three enums, four export
  repositories, and two bounded Equipment ID queries;
- security/audit: one permission constant and one audit module;
- processing: dispatcher, job, selection, lease, worker, part writer, finalizer,
  profile/fingerprint, retention, storage abstraction/key builder/MinIO adapter;
- artifacts/docs: manifest schema/example, W2 design, execution plan, canonical
  W1 schema resource packaging in `pom.xml`;
- tests: configuration, migration, RBAC, controller, storage, selection,
  lifecycle, lease, worker, finalizer, retention, NDJSON, and artifact contracts.

No frontend, WorkOrder material-readiness, deployment, container, generic
`S3Service`, or production secret file is in the functional commit.

## 29. Commit and push result

Functional W2 commit:

`db78cadca7f23e1fbbd97c7ec8bf74292d9424f3 Add resumable equipment lifecycle dataset export`

Push result: succeeded as a new branch at
`origin/codex/equipment-ai-lifecycle-w2-dataset-export` without force. Local and
remote refs both resolved to the functional SHA with ahead 0 / behind 0. No
merge or merge request was created.

The report closeout commit and its exact matching remote-ref verification are
recorded in the final delivery response for the self-reference reason in
section 3.

## 30. Pre-existing untracked and unrelated files preserved

The source workspace was observed being changed by a parallel process. It
switched away from W2 and advanced another branch while adding unrelated
WorkOrder material-readiness changes. W2 recovery waited for a stable two-point
snapshot, proved that committed changes after the W2 baseline did not touch the
five W2 modified tracked files, verified the remote W2 branch was absent, and
then copied only the classified W2 set into the isolated exact-baseline clone.
Source/clone comparisons confirmed identical transferred W2 content.

The following shared-workspace files were not copied, staged, restored, stashed,
committed, or modified by W2 recovery:

- `src/test/java/com/toir/service/WorkOrderMaterialReadinessServiceTest.java`;
- `src/main/java/com/toir/service/WorkOrderMaterialReadinessService.java`;
- `src/main/java/com/toir/dto/workorder/WorkOrderMaterialReadinessRowDto.java`;
- `src/main/java/com/toir/enums/MaterialReadinessStatus.java`.

The W0/W1 report's pre-existing untracked document was also left unchanged and
uncommitted:

`docs/superpowers/specs/2026-07-30-ppr-end-to-end-lifecycle-design.md`

Its read-only SHA-256 at recovery review was
`16d36685d73d37de7221870fb41dfc6c1035b87ff5adee2df827953f61d09f10`.
