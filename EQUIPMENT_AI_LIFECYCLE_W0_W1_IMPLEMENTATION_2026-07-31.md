# Equipment AI Lifecycle W0/W1 Implementation Report

## 1. Executive summary

W0 and W1 were implemented for the Equipment AI lifecycle foundation. The
backend now has a durable canonical business specification, immutable
`EquipmentLifecycleContext` v1 records, a mandatory bounded context policy, an
internal read-only assembler, deterministic SHA-256 content identity,
structured data-quality and source-watermark metadata, JSON Schema, a
synthetic example, and focused tests.

No controller, migration, export, model call, model result, Kafka/outbox flow,
or frontend change was added.

## 2. Exact repository baseline

- Backend: `/home/tenzorsoft/Desktop/TOIR/toir-backend`
- Audited branch: `codex/equipment-spare-parts-lifecycle-w2-w5`
- Audited full commit:
  `813872f6d95f101c6583f51add5206c3bdc00de6`
- Audited upstream:
  `origin/codex/equipment-spare-parts-lifecycle-w2-w5`
- Audited branch state at the safety gate: ahead 8, behind 0
- Pre-existing untracked file preserved and not committed:
  `docs/superpowers/specs/2026-07-30-ppr-end-to-end-lifecycle-design.md`

The target branch was created directly from the exact audited commit. No pull,
merge, rebase, reset, stash, or force-push was used.

## 3. Target branch and final HEAD

- Target branch: `codex/equipment-ai-lifecycle-w0-w1`
- W0/W1 implementation HEAD at report generation:
  `2bb83467eca078b65b963087921511809918d58a`
- Upstream after the implementation push:
  `origin/codex/equipment-ai-lifecycle-w0-w1`
- Ahead/behind after that push: 0/0

This report is committed as a documentation closeout after the implementation
commit. A Git commit cannot contain its own final object ID because changing
the file changes that ID; the exact report-bearing final HEAD is therefore
recorded in the final delivery response and verified against the remote ref.

## 4. W0 decisions encoded

The canonical specification records the approved rules and separates each from
code-proven sources, implementation mappings, and unresolved gaps. It encodes:

- completion-anchor precedence with terminal Work Order fallback;
- PPR planning provenance without duplicate actual execution;
- Defect as failure, Repair Request as demand, and condition as signal;
- unknown recurrence when normalized grouping is not proven;
- canonical installation/removal evidence;
- operational status remaining separate from future advisory prediction;
- `100 = healthy` and `0 = critical` as future output direction only;
- explicit RUL units without assuming months;
- null versus zero, UTC and business-date semantics;
- source units, UZS cost convention, and no mixed-currency inference;
- advisory-only behavior and data minimization;
- availability, reliability, ordering, deduplication, soft-delete, legacy,
  bounds, fingerprint, and watermark semantics.

## 5. W1 architecture

`EquipmentLifecycleContextAssembler` is an internal Spring service with:

- `assemble(UUID equipmentId, Instant asOf, EquipmentLifecycleContextPolicy policy)`;
- `@Transactional(readOnly = true)`;
- canonical soft-delete-aware Equipment lookup and `RestException.notFound`;
- injected UTC `Clock`;
- one caller-provided `asOf` reused across reads;
- current-state, history, planning, component, cost, and document mappers;
- allowlisted scalar DTO output rather than JPA entity exposure;
- section metadata and watermarks derived from returned rows only;
- a fingerprint-free context followed by immutable fingerprint attachment.

Consistency is labeled
`TRANSACTION_READ_ONLY_DEFAULT_ISOLATION`, with
`historicalSnapshot=false`; the implementation does not claim stronger
snapshot semantics than the transaction provides.

## 6. Added contract types

- `EquipmentLifecycleContextV1`
- `EquipmentLifecycleContextPolicy`
- `EquipmentLifecycleSection`
- `EquipmentLifecycleDataQuality`
- typed nested records for Equipment, passport, hierarchy, attributes,
  lifecycle, meters, maintenance, planning, Work Orders, Defects, Repair
  Requests, inspections, conditions, components, warranty, costs, environment,
  documents, section metadata, quality issues, and watermarks
- `EquipmentLifecycleFingerprintService`
- `EquipmentLifecycleCanonicalMapper`
- `EquipmentLifecycleContextAssembler`
- `EquipmentLifecycleClockConfiguration`

`schemaVersion` is exactly `"1.0"`. `contextFingerprint` is opaque and is not a
numeric or sortable data version.

## 7. Section-by-section source mapping

| Context section | State | Code-proven source and qualification |
|---|---|---|
| equipment | Implemented from a reliable source | Current non-deleted `equipment`; optional safe `equipment_passports` fields |
| hierarchy | Implemented but optional | Bounded `equipment_nodes`; current Equipment parent ID also remains in core |
| technicalAttributes | Implemented but optional | Bounded typed values plus batched non-deleted definitions; raw JSON excluded |
| lifecycleHistory | Implemented from reliable dedicated sources | Bounded status and location history; free notes and actors excluded |
| meterHistory | Implemented from a reliable source when unit is known | Bounded meter readings plus batched meter type/unit lookup |
| maintenanceSummary | Implemented as derived | Derived only from returned canonical completion events and terminal Work Orders |
| maintenanceHistory | Implemented with canonical precedence | Completion anchors first; COMPLETED/CLOSED Work Orders only as fallback |
| plannedMaintenance | Implemented but optional | Bounded PPR tasks and maintenance due events with separate provenance |
| workOrders | Implemented from a reliable operational source | Bounded Work Orders; unrestricted title/result/notes and performers excluded |
| defects | Event implemented; recurrence exposed with reliability warning | Bounded normalized Defects; stored recurrence count is not mapped |
| repairRequests | Implemented as a separate repair-demand source | Bounded requests; reporter and supplier contacts excluded |
| inspections | Implemented but optional | Direct Equipment-linked checkpoint/result/round projection only |
| conditionMeasurements | Implemented but optional | Bounded structured readings with unit, thresholds, severity, and event time |
| installedComponents | Implemented from the canonical source | Active `spare_part_installations` only; issue/reservation is not installation |
| componentReplacementHistory | Implemented but optional | Removed/replaced installation rows and proven replacement lineage |
| warranty | Implemented but optional current state | Allowlisted Equipment warranty fields; no dedicated history is claimed |
| costSummary | Implemented as a bounded derived view | Directly linked Actual Costs only, grouped by status, explicitly labeled UZS |
| environmentContext | Implemented as partial current state | Department/responsibility/location/warehouse and controlled outside-state fields |
| documentMetadata | Implemented but requires parsing | Safe Technical Document metadata only; no file ID, content, actor, or URL |
| dataQuality | Implemented as deterministic assembler output | Safe issue codes, severity, summaries, source IDs, and missing critical fields |
| sourceWatermarks | Implemented from returned rows | Count, truncation, coverage, max returned source time, and reliability |
| AI result, health score, RUL output, lifecycle prediction | Deferred | W1 intentionally contains model inputs only |

An included source with no returned rows is distinguishable from a
policy-omitted section through availability metadata.

## 8. Canonical precedence and deduplication rules

- A returned completion anchor emits the canonical maintenance event.
- A bounded batch query identifies anchors for candidate Work Orders even when
  the anchor falls outside a truncated returned slice.
- Linked Work Order fallback is suppressed and emits
  `DUPLICATE_SOURCE_SUPPRESSED`.
- Only COMPLETED/CLOSED Work Orders are fallback completion evidence.
- Missing `completedAt` remains null and emits
  `MISSING_ACTUAL_COMPLETION_TIME`.
- PPR tasks remain planning provenance and never independently create
  maintenance-history execution.
- Defect, Repair Request, and condition records remain separate sections.
- Stored recurrence is not treated as a canonical count.
- Active and removed/replaced component views are split from one bounded
  installation source.
- Histories are stably ordered by business time, source type where applicable,
  and UUID.

## 9. Context-version/fingerprint strategy

The implementation hashes deterministic Jackson JSON with SHA-256 and returns
64 lowercase hexadecimal characters.

Included material covers schema version, `asOf`, a canonicalized applied
policy, consistency metadata, mapped sections, metadata, data quality, and
watermarks. Policy section names and bounds are sorted before hashing; map
entries and properties are serialized in stable order.

Excluded material is `generatedAt`, the fingerprint field itself, and all data
excluded from the DTO. Tests cover identical content with different
`generatedAt`, changed source values, collection insertion order, and hash
shape.

## 10. Query and bounds strategy

Every included high-cardinality section requires an explicit positive
caller-supplied limit. No unlimited default exists.

Sixteen dedicated lifecycle queries use `LIMIT :limitPlusOne`, soft-delete
filters, and deterministic ordering. The assembler trims to `limit` and sets
`truncated=true` when the sentinel row is returned. Definitions and meters are
batch-enriched from bounded ID sets. Anchor presence is checked in one bounded
ID query. No total-count query is used only for context metadata, and no N+1
User/Employee/file enrichment was introduced.

The policy also carries history start, future planning horizon, optional
section inclusion, and explicit `RAW` measurement granularity. W2 must select
and load-test production profile numbers.

## 11. Data-quality semantics

The exact availability states are:

- `AVAILABLE_AND_POPULATED`
- `AVAILABLE_BUT_OPTIONAL`
- `AVAILABLE_BUT_UNRELIABLE`
- `DERIVABLE`
- `MISSING`
- `REQUIRES_DOCUMENT_PARSING`
- `OUT_OF_SCOPE_FOR_V1`

Implemented issue codes include snapshot limitation, current state postdating
`asOf`, missing completion time, unknown unit, unreliable recurrence,
suppressed duplicate source, missing attribute definition, excluded raw JSON
attribute, section truncation, and partial bounded cost aggregation.

Issues contain controlled summaries rather than exception messages or stack
traces. Missing critical Equipment fields and safe affected source IDs are
separate structured fields.

## 12. Security/data-minimization decisions

The DTOs omit credentials, secrets, tokens, personal contacts, User/Employee
objects, reporters, performers, recorders, installers/removers, uploaders,
reviewers, unrestricted notes/comments/descriptions/results, binary contents,
file IDs, storage paths, and signed URLs.

Only lifecycle-relevant stable IDs, controlled status/type names, safe
technical text, explicit measurements, direct monetary summaries, and safe
document metadata are mapped. Technical attribute raw JSON and component rule
snapshots are not exposed.

## 13. Added repository/service queries

Bounded query methods were added for:

- Equipment nodes and attribute values;
- Equipment status and location history;
- meter and condition readings;
- completion anchors and anchored Work Order IDs;
- Work Orders;
- PPR tasks and maintenance due events;
- Defects and Repair Requests;
- spare-part installations;
- Technical Documents;
- direct Actual Costs;
- direct Equipment-linked inspection projection.

A batched non-deleted attribute-definition lookup was added for bounded
enrichment. The service layer adds the assembler, canonical mapper,
fingerprinter, and injected UTC Clock configuration.

## 14. Tests written

Focused tests were written for:

- mandatory limits, invalid windows, and defensive policy copies;
- anchor precedence and anchor-aware Work Order suppression;
- COMPLETED/CLOSED fallback and exclusion of CANCELLED work;
- null completion timestamp plus warning;
- PPR provenance without duplicate execution;
- unknown repeated failure rather than trusting the stored counter;
- installation/removal mapping and installation-only source behavior;
- deterministic ordering and duplicate suppression;
- limit-plus-one truncation and omitted-versus-empty metadata;
- canonical soft-delete-aware not-found behavior;
- null numeric and explicit UZS semantics;
- deterministic fingerprinting, relevant source change, volatile
  `generatedAt`, and caller collection insertion order;
- Defect/Repair Request/condition contract separation;
- privacy-sensitive field absence;
- JSON Schema/example version and synthetic-example shape.

## 15. Verification status

NOT RUN — skipped by user instruction.

Allowed static checks performed:

- JSON syntax inspection for schema and example;
- `rg` checks for bounded queries, provenance/quality codes, forbidden
  payload fields, mutation/controller/AI/Kafka additions, and scope;
- `git diff --check`;
- staged name/status and diff-stat inspection;
- backend branch, exact baseline ancestry, upstream, and ahead/behind checks;
- frontend status comparison with the audit baseline.

No statement that tests or the build passed is made.

## 16. Known limitations

- Current Equipment, warranty, environment, hierarchy, attribute definitions,
  and inspection checkpoint definitions are not historically reconstructable.
- Database default isolation does not guarantee a cross-transaction historical
  snapshot.
- Free-text Defect taxonomy cannot yet support reliable recurrence grouping.
- Direct PPR completion remains weaker legacy evidence and does not generate a
  canonical completion event in W1.
- PPR plan/calculation rows are represented through proven task snapshot IDs;
  full plan and calculation snapshot payloads are not loaded.
- Maintenance summary and cost totals are explicitly partial when their
  bounded sources truncate.
- Actual Costs have no per-row currency field; UZS follows the audited TOIR
  convention and no mixed-currency conversion is attempted.
- Document bodies require future governed parsing/OCR.
- No production limit profile or load measurement was invented.

## 17. Deferred W2 work

W2 remains responsible for versioned NDJSON, manifest, checksum, resumable
asynchronous export jobs, Equipment batch iteration, production bounds/load
testing, export authorization, and operational observability.

Model inference, result contracts/persistence, latest/history APIs, manual
recalculation, Kafka, outbox, retries, DLQ, reconciliation, and frontend UI are
also outside this implementation.

## 18. Files changed

Documentation and contract artifacts:

- `docs/ai/equipment-lifecycle/EQUIPMENT_AI_LIFECYCLE_W0_CANONICAL_SPEC.md`
- `docs/ai/equipment-lifecycle/equipment-lifecycle-context-v1.schema.json`
- `docs/ai/equipment-lifecycle/equipment-lifecycle-context-v1.example.json`
- `docs/superpowers/plans/2026-07-31-equipment-ai-lifecycle-w0-w1.md`
- this implementation report

Production types/services:

- four files under `src/main/java/com/toir/dto/equipmentlifecycle/`
- four files under `src/main/java/com/toir/service/equipmentlifecycle/`

Repository access:

- bounded additions in the existing Condition, Meter, PPR, Technical Document,
  Work Order, Actual Cost, Defect, Equipment attribute/node/history,
  Maintenance, Repair Request, and Spare Part Installation repositories
- two new inspection lifecycle projection/repository files

Tests:

- `EquipmentLifecycleContextPolicyTest`
- `EquipmentLifecycleCanonicalMappingTest`
- `EquipmentLifecycleContextAssemblerTest`
- `EquipmentLifecycleContractShapeTest`
- `EquipmentLifecycleFingerprintServiceTest`

No frontend or migration file changed.

## 19. Commit and push result

Functional W0/W1 commit:

`2bb83467eca078b65b963087921511809918d58a Add equipment lifecycle AI context foundation`

Push result: succeeded to
`origin/codex/equipment-ai-lifecycle-w0-w1` without force. The local and remote
branch were verified at ahead 0 / behind 0 immediately after the functional
push. No merge or merge request was created.

The report closeout commit and its matching remote-ref verification are
reported with the final delivery because, as noted in section 3, a commit
cannot embed its own object ID.
