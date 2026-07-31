# Equipment Lifecycle W2 Dataset Export Design

## Objective and boundary

W2 adds a privileged, persistent, asynchronous export of the exact W1
`EquipmentLifecycleContextV1` contract. It produces UTF-8 NDJSON, the canonical
W1 JSON Schema, a versioned manifest, and `SHA256SUMS`. It does not call a model,
persist model results, publish Kafka events, or implement W3-W7.

The feature is disabled by default. Application code may be deployed while
production activation remains prohibited until DevOps proves the private MinIO
bucket, rotated protected credentials, prefix-scoped IAM, public-access denial,
multipart support, cross-instance reachability, durable cluster storage, and
retention behavior.

## Request, authorization, and identity

Every endpoint requires the dedicated `EQUIPMENT_LIFECYCLE_DATASET_EXPORT`
authority, `SYSTEM_ADMIN`, or wildcard authority. The permission is not granted
to ordinary equipment readers. A job persists only the authenticated creator's
UUID and the normalized authorization scope; it never stores user/contact data.

Clients must choose either `EXPLICIT_IDS` or `ALL_AUTHORIZED` and the allowlisted
`standard-v1` profile. A required `Idempotency-Key` is scoped to creator UUID.
The same key and normalized request returns the existing job; a changed request
conflicts. An identical active request is also reused where safe.

## Frozen selection and context policy

Job creation captures one UTC `asOf` from the injected W1 clock and resolves the
named profile into a complete `EquipmentLifecycleContextPolicy`. The immutable
resolved policy JSON and its SHA-256 fingerprint are stored with the job and
reused on resume.

Selection excludes soft-deleted Equipment. Explicit IDs are deduplicated and
sorted. `ALL_AUTHORIZED` uses UUID keyset pagination. Membership rows contain a
stable zero-based ordinal and are unique by both `(job_id, equipment_id)` and
`(job_id, ordinal)`. A partially prepared selection is cleared and rebuilt;
`selection_frozen` changes only after the complete bounded selection succeeds.
Ordinary resume never rebuilds frozen membership.

## Persistent lifecycle and leasing

The state path is `QUEUED -> PREPARING -> RUNNING -> FINALIZING -> COMPLETED`.
Cancellation uses `CANCEL_REQUESTED -> CANCELLED`; failures use `FAILED`, and
expired completed exports use `EXPIRED`. A bounded dedicated executor dispatches
only after job-creation or resume transactions commit.

A worker claim stores owner, random fencing token, lease expiry, and heartbeat.
Short row-locked transactions validate that the current token owns an unexpired
lease before publishing a part checkpoint, changing finalization state, or
completing. An expired/stale worker may leave an orphan object but cannot publish
it into database checkpoint state.

## Private MinIO/S3-compatible storage

`EquipmentLifecycleExportStorage` is an export-only abstraction. The MinIO
adapter receives a dedicated client and configuration and never creates a
bucket, changes policy, sets ACLs, produces URLs, or calls the legacy `S3Service`.
Bucket and prefix are server configuration only. Logical keys are generated from
job IDs, fencing tokens, deterministic part numbers, and allowlisted artifact
types. The adapter rejects absolute keys, dot segments, backslashes, control
characters, and keys outside its configured prefix.

Each bounded NDJSON part is uploaded to an attempt-token-specific staging key as
an immutable object with a SHA-256 metadata value. A HEAD operation must confirm
its length and checksum before a short fenced transaction inserts the part row
and advances the checkpoint. Part rows store part number, ordinal range, record
count, object key, byte size, SHA-256, and fencing token. Resume trusts only these
committed rows, validates every object using HEAD metadata, and verifies
contiguous non-overlapping ordinals. Orphan objects are ignored and later removed
by bounded cleanup.

S3 copy/delete is not treated as atomic rename. Final dataset creation streams
committed parts in ordinal order into a private immutable final object. The
schema, manifest, and checksum file are uploaded similarly. Final object metadata
is persisted, but no object key appears in an API DTO.

## NDJSON and finalization

Each W1 context is serialized by the configured compact `ObjectMapper` as one
UTF-8 line ending in LF. There is no BOM, array wrapper, pretty printing, blank
line, or secondary business envelope. A bounded in-memory part buffer limits
both record count and byte size; a single context exceeding that cap fails
safely before checkpoint publication.

Finalization validates committed ordinal continuity, record count, part HEAD
metadata, final object sizes, and SHA-256 values. Order is dataset, canonical
schema, manifest, then `SHA256SUMS`. The checksum file lists the first three
artifacts using lowercase hex, two spaces, filename, and LF. A final short fenced
transaction inserts artifact metadata and changes to `COMPLETED`; ordinary APIs
expose artifacts only after that transition.

The manifest names the consistency model
`FIXED_AS_OF_WITH_FROZEN_EQUIPMENT_SELECTION_AND_READ_COMMITTED_SOURCE_READS`.
It states that membership is frozen, no single MVCC snapshot spans the export,
source rows may change during a long job, and W1 context fingerprints and source
watermarks provide per-record traceability.

## Downloads, failures, and cleanup

Downloads re-run current method authorization, accept only an artifact enum,
require non-expired `COMPLETED` state, and stream the private object. Responses
use fixed filenames, content length, SHA-based ETag, private/no-store headers,
and `nosniff`. No presigned URL or physical key is returned.

Assembly, serialization, storage, or integrity failure preserves the last valid
checkpoint and records only a bounded safe code/summary and Equipment UUID.
No error line is added to NDJSON. Cooperative cancellation is checked at record
and part boundaries.

A bounded scheduled cleanup atomically claims eligible jobs. Completed jobs are
made `EXPIRED` before deletion, immediately preventing download. Final and
staging deletion is idempotent and bounded; repeated claims continue incomplete
cleanup. Active jobs are never age-deleted. Audit records cover create, resume,
cancel, download, and expiry without request bodies, keys, or idempotency keys.

## Production activation blockers

The current repository does not prove production MinIO readiness. Existing
repository-stored generic MinIO credentials must be removed from source and
rotated by infrastructure owners, but rewriting that unrelated subsystem is not
part of W2. W2 uses only dedicated environment-backed settings with no credential
defaults. Production enablement remains prohibited until all readiness evidence
listed in the implementation report is supplied.
