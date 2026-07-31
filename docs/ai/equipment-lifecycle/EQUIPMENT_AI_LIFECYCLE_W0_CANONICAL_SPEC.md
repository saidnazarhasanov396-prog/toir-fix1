# Equipment AI Lifecycle W0 Canonical Specification

**Status:** approved for W1 implementation
**Version:** 1.0
**Date:** 2026-07-31
**Audited baseline:** `813872f6d95f101c6583f51add5206c3bdc00de6`

## 1. Scope

This specification defines canonical terminology, source precedence, null and
time semantics, data minimization, and content identity for
`EquipmentLifecycleContext` v1.

W1 provides an immutable backend contract and an internal read-only assembler
for one Equipment item. It does not provide an HTTP endpoint, dataset export,
model call, model result, persistence, Kafka, outbox, scheduler, or frontend.

The context is advisory-model input. It does not mutate or reinterpret TOIR
operational state.

## 2. How to read the rules

Each rule distinguishes:

- **Approved rule:** the business decision implemented by W1.
- **Code-proven source:** the audited TOIR source.
- **Implementation mapping:** the v1 representation.
- **Unresolved gap:** a limitation that must remain visible rather than being
  filled with an invented value.

Schema presence does not prove production population. A source can therefore
be available but optional or unreliable.

## 3. Canonical maintenance completion

### Approved rule

1. `MaintenanceCompletionAnchor` is the strongest maintenance-execution
   evidence.
2. A `COMPLETED` or `CLOSED` Work Order is fallback completion evidence.
3. `CLOSED` is administrative. It is not an execution timestamp.
4. Actual completion time comes from `MaintenanceCompletionAnchor.performedAt`
   or the linked Work Order's canonical `completedAt`.
5. Missing actual completion time remains null and creates a data-quality
   warning.
6. `CANCELLED`, suspended, draft, planned, approved, or in-progress work is not
   successful maintenance.

### Code-proven source

- `maintenance_completion_anchors.performed_at`
- `maintenance_completion_anchors.work_order_id`
- `work_orders.status`
- `work_orders.completed_at`
- regulation, Equipment-rule, PPR-task, Repair Request, and due-event lineage
  on `MaintenanceCompletionAnchor`

### Implementation mapping

Maintenance history is ordered by `(actualCompletionAt, sourceType, sourceId)`,
with null event times ordered last.

An anchor produces one canonical `MAINTENANCE_COMPLETION_ANCHOR` event. A
completed/closed Work Order produces a `WORK_ORDER` fallback event only when no
returned anchor references that Work Order. The event retains all proven source
IDs.

### Unresolved gap

Historical rows can claim terminal status while lacking `completedAt`.
Administrative close overwrites `WorkOrder.completedAt` in the current write
flow, so W1 cannot recover an earlier technical completion timestamp unless an
anchor exists.

## 4. PPR and Work Order precedence

### Approved rule

If a PPR task has a linked Work Order, the Work Order/anchor is canonical for
actual execution. Direct PPR completion must not create a second maintenance
completion event.

### Code-proven source

- `ppr_tasks.source_calculation_item_id`
- `ppr_tasks.maintenance_due_event_id`
- `work_orders.ppr_task_id`
- `work_orders.maintenance_due_event_id`
- `maintenance_completion_anchors.ppr_task_id`
- `maintenance_completion_anchors.work_order_id`

### Implementation mapping

The planned-maintenance section retains PPR task and due-event provenance.
Linked Work Order ID is included when present. PPR status never independently
creates an item in `maintenanceHistory`.

Duplicate or conflicting PPR/Work Order/anchor sources create deterministic
quality issues. W1 does not silently manufacture a merged source record.

### Unresolved gap

Legacy/manual and approval-first plan paths coexist. A PPR task can be completed
directly without the stronger Work Order completion side effects.

## 5. Failure and condition semantics

### Approved rule

- A Defect is the canonical failure event.
- A Repair Request is repair demand, not a second failure.
- A condition alarm or threshold breach is a condition signal, not a failure,
  even when it later leads to a Defect.
- Linked records remain in their own sections and are not counted twice.

### Code-proven source

- `defects` with Equipment, node, Repair Request, status, detected/resolved
  timestamps, failure reason, and root cause
- `repair_requests` with Equipment, status, source, and operational timestamps
- `condition_readings` with parameter, value, unit, thresholds, severity, and
  recorded time

### Implementation mapping

Defects, Repair Requests, and condition measurements are separate arrays. IDs
link them where the schema proves a relationship. Descriptions, reporter data,
supplier contacts, and unrestricted notes are excluded.

### Unresolved gap

Condition-alarm auto-Defects do not persist the originating condition-reading
ID. Cross-section causal deduplication is therefore not always provable.

## 6. Repeated failures

### Approved rule

Stored `Defect.recurrenceCount` is not canonical by itself. Repeated failure may
be derived only from reliably normalized failure events.

### Code-proven source

Defect category, severity, failure reason, and root cause are free strings.
The audited write flow does not prove maintenance of `recurrenceCount`.

### Implementation mapping

W1 exposes `derivedRepeatedFailureCount=null` and
`recurrenceReliability=UNRELIABLE` for current Defect data. The stored counter
is not mapped as a canonical count. A section-level warning explains why.

### Unresolved gap

A normalized failure-mode dictionary relationship and an approved grouping
window/key do not yet exist.

## 7. Component lifecycle

### Approved rule

Only `SparePartInstallation` proves installation. Warehouse movement,
reservation, stock issue, or `RepairMaterialUsage` does not.

Removal and replacement come from canonical installation rows and their
removal/replacement fields.

### Code-proven source

- `spare_part_installations`
- applied life-rule snapshot and revision
- installation/removal Work Order IDs
- replacement links and correlation ID
- installed/removed timestamps
- status and lifecycle-evaluation state

### Implementation mapping

Active installation rows populate `installedComponents`. Removed/replaced rows
populate `componentReplacementHistory`. Actor IDs, serial/lot snapshots,
free-text reasons, and raw applied-rule JSON are excluded from v1.

### Unresolved gap

Pre-migration and externally managed components may not have canonical
installation rows. Component condition is not a separate structured domain.

## 8. Operational status and predictive lifecycle

### Approved rule

`EquipmentStatus` and other TOIR operational/technical statuses remain source
of truth. A future AI lifecycle stage is a separate advisory result.

### Implementation mapping

W1 copies the canonical Equipment status name. It contains no predictive stage
and writes no status.

### Unresolved gap

Predictive lifecycle-stage vocabulary is deferred to result-contract work.

## 9. Health score

### Approved rule

Future direction is `100 = healthy`, `0 = critical`. Missing data must not
become zero.

### Implementation mapping

W1 contains no calculated health score. It supplies nullable inputs and
coverage metadata only.

## 10. Remaining useful life

### Approved rule

RUL supports explicit units including `MONTH`, `HOUR`, `CYCLE`, and `KM`.
No universal month assumption is allowed.

### Implementation mapping

W1 does not calculate RUL. It preserves normative lifetime configuration,
meter type, meter unit, current readings, and component due provenance.

### Unresolved gap

Equipment-type-specific target selection and future model result semantics are
deferred.

## 11. Missing, null, zero, and collection semantics

- JSON null means unknown, unavailable, not recorded, or unreliable as
  qualified by section/issue metadata.
- Numeric zero is a real numeric zero and is never a substitute for null.
- Empty `items` with `AVAILABLE_AND_POPULATED` means the bounded source was
  queried successfully and no matching rows existed.
- An unavailable, unreliable, missing, parser-required, or out-of-scope source
  uses the matching availability value; it is not represented as a silently
  trustworthy empty source.
- Omitted optional sections are represented by metadata with
  `OUT_OF_SCOPE_FOR_V1` for that policy invocation.
- Truncation is explicit and includes the applied window and returned count.

## 12. Timestamp and timezone rules

- Contract instants are UTC ISO-8601 timestamps.
- Business dates remain ISO local dates and are not converted to midnight
  timestamps.
- `generatedAt` is obtained from the injected application `Clock`.
- `asOf` is captured once by the caller/assembler and reused for all queries.
- Business event time and persistence/update time are distinct.
- No missing event time is replaced with `updatedAt`, close time, current time,
  or audit time.
- `LocalDateTime` planning fields are converted using the documented TOIR
  business zone `Asia/Tashkent`, then emitted as UTC instants.
- W1 uses a read-only Spring transaction with the configured database default
  isolation. Metadata labels this as a transaction-scoped read, not a
  guaranteed historical database snapshot.

## 13. Unit-of-measure rules

- Every measurement carries an explicit source unit.
- Unknown/blank units remain null and create `UNKNOWN_UNIT`.
- W1 does not silently convert units.
- Meter-reading units come from the referenced active or historical meter row.
- Condition and inspection units come from their measurement rows; expected
  units remain separate.
- Unit normalization/conversion is deferred until an approved canonical-unit
  policy exists.

## 14. Currency rules

- Every monetary value carries a currency.
- Existing Equipment cost presentation uses `UZS`; W1 labels included direct
  Actual Cost amounts `UZS` and identifies that convention as source
  provenance.
- Mixed currencies are never summed.
- Costs lacking a proven Equipment link or currency remain excluded or
  unreliable.
- Downtime monetary cost, acquisition cost, replacement cost, depreciation,
  and exchange-rate conversion are not inferred.

## 15. Advisory-only behavior

The context is read-only model input. It never:

- creates Work Orders, PPR tasks, Repair Requests, or Defects;
- changes Equipment or maintenance status;
- installs/removes components;
- schedules maintenance;
- persists model results;
- invokes an AI/model service.

## 16. Permitted and excluded data

### Permitted

- stable source IDs and controlled codes;
- Equipment identity, technical fields, canonical status, hierarchy, and
  organizational/location IDs;
- bounded lifecycle, meter, maintenance, PPR, Defect, Repair Request,
  inspection, condition, component, warranty, direct-cost, and safe document
  metadata;
- whitelisted technical strings: Equipment/name/model/manufacturer,
  attribute labels/controlled scalar values, Defect taxonomy strings,
  Work Order/PPR codes, and document title/type/revision.

### Excluded

- credentials, bearer tokens, secrets, authorization headers;
- employee/user names, phone numbers, email, personnel numbers, and full
  Employee/User objects;
- reporter, performer, uploader, reviewer, and component actor identity;
- IP address, user agent, correlation/request metadata, and broad audit
  snapshots;
- binary files, document bodies, image data, storage paths, signed/download
  URLs;
- Repair Request supplier contacts;
- unrestricted descriptions, comments, notes, results, rejection reasons,
  closure notes, and outside-taken-by text;
- unrelated organizational, campaign, finance, or user data.

## 17. Section availability and reliability

The contract uses these exact semantic states:

- `AVAILABLE_AND_POPULATED`
- `AVAILABLE_BUT_OPTIONAL`
- `AVAILABLE_BUT_UNRELIABLE`
- `DERIVABLE`
- `MISSING`
- `REQUIRES_DOCUMENT_PARSING`
- `OUT_OF_SCOPE_FOR_V1`

Availability describes source/contract semantics, not a guarantee that
production rows exist.

Every collection/value section carries metadata with availability, returned
count, truncation, coverage window, maximum returned source time, reliability,
and source names.

## 18. Ordering and deduplication

- All histories use stable ascending business time followed by source type and
  source UUID.
- Null business times sort last.
- Current collections use stable semantic keys followed by UUID.
- Entity UUID is the primary deduplication key.
- Proven domain correlation/lineage keys are used only for canonical
  precedence, never to merge ambiguous records silently.
- Status and location events remain separate event types.
- Anchors suppress linked Work Order fallback maintenance events.
- Linked PPR tasks never add a second actual-maintenance event.
- Active and removed component views come from one bounded canonical
  installation source and are split deterministically.

## 19. Soft-delete and legacy behavior

- Soft-deleted Equipment is not found through the assembler.
- All W1 repository queries exclude `is_deleted=true`.
- A source without historical reconstruction is current-state-as-read and is
  labeled accordingly.
- Legacy/manual planning or document sources retain a source/legacy marker and
  an issue where reliability differs.
- W1 does not backfill, rewrite, delete, or upgrade legacy data.

## 20. Context policy and bounds

The caller must provide:

- history start;
- future planning horizon;
- included optional sections;
- a positive maximum row count for every included high-cardinality section;
- measurement granularity.

There are no hidden unlimited defaults. Repository reads request `limit + 1`
where practical, return at most `limit`, and set `truncated=true` when the
sentinel row exists. W1 avoids full-table counts used only for metadata.

Numeric W2 export profiles are intentionally deferred until load testing.

## 21. Context fingerprint

`schemaVersion` is `"1.0"` and is not a data-change version.

W1 uses an opaque lowercase SHA-256 fingerprint of deterministic canonical JSON.
The fingerprint:

- is for equality, idempotency, and future stale-result checks;
- is not numeric and is not sortable;
- includes schema version, `asOf`, applied context policy, consistency
  metadata, mapped sections, section metadata, quality issues, and returned
  source watermarks;
- excludes `generatedAt` and the fingerprint field itself;
- excludes credentials and all data excluded from the context;
- uses stable property/map ordering and assembler-sorted arrays;
- changes when any included canonical value, bound, availability, issue, or
  watermark changes.

Identical canonical input with identical `asOf` and policy produces the same
fingerprint. A different `generatedAt` alone does not.

## 22. Source watermarks

Watermarks are computed from already returned bounded rows. They do not trigger
full-table scans.

Each watermark identifies:

- source/module;
- maximum returned event/update time when available;
- returned count;
- truncation;
- coverage start/end;
- source reliability.

A watermark is not a claim that no later row exists outside the captured
transaction.

## 23. Code-proven section mapping

| Context section | Source | W1 reliability |
|---|---|---|
| equipment | `equipment`, `equipment_passports` | reliable core; optional passport/lifetime fields |
| hierarchy | `equipment.parent_id`, `equipment_nodes` | optional, bounded |
| technicalAttributes | attribute definition/value tables | optional; typed values only |
| lifecycleHistory | status and location history | reliable dedicated events; bounded |
| meterHistory | meter and reading tables | reliable when unit known; bounded |
| maintenanceSummary/history | completion anchors, Work Orders | canonical precedence; bounded |
| plannedMaintenance | PPR tasks and maintenance due events | optional/legacy-aware; bounded |
| workOrders | Work Orders | reliable operational facts; bounded |
| defects | Defects | event reliable; recurrence/taxonomy normalization unreliable |
| repairRequests | Repair Requests | separate repair-demand facts; bounded |
| inspections | direct Equipment checkpoints and round results | optional; mutable-definition warning |
| conditionMeasurements | condition readings | reliable structured signals; bounded |
| installedComponents | active installation records | canonical installation source; bounded |
| componentReplacementHistory | removed/replaced installation records | canonical when populated; bounded |
| warranty | Equipment warranty fields | optional current state; no dedicated history |
| costSummary | directly linked Actual Costs | optional; UZS convention; indirect costs excluded |
| environmentContext | Equipment placement/org IDs | partial current state |
| documentMetadata | technical document metadata | optional; body requires parsing |
| dataQuality | assembler-derived | deterministic |
| sourceWatermarks | returned bounded rows | transaction-limited |

## 24. Unresolved questions deferred beyond W1

1. Predictive lifecycle-stage vocabulary and thresholds.
2. Model result persistence and stale-result selection.
3. Normalized failure-mode dictionary and recurrence grouping window.
4. Historical snapshots for core, hierarchy, criticality, warranty, and
   mutable inspection checkpoints.
5. Canonical cross-domain unit conversion.
6. Multi-currency storage and exchange-rate policy.
7. Acquisition, replacement, depreciation, and downtime monetary cost.
8. Document/OCR feature governance.
9. W2 history limits, batching, NDJSON, manifest, checksum, and resume profile.
10. Kafka/outbox triggers, coalescing, retries, DLQ, and reconciliation.
11. Production population, coverage thresholds, and model sufficiency rules.
