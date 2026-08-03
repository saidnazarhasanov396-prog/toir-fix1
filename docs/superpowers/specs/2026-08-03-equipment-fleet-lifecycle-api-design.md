# Equipment Fleet Lifecycle JSON API Design

## Objective and boundary

Add one synchronous read endpoint that returns the lifecycle data requested for
the complete Equipment fleet as a streamed JSONL/NDJSON response:

`GET /api/v1/equipment/lifecycle-context`

There are no request filters, pagination parameters, or history limits in v1.
The response covers every non-deleted Equipment visible in the caller's data
scope. It contains Equipment identity/current state, every non-deleted meter and
its latest reading, every reliably completed repair, the reason for each repair,
and the last meter reading at or before each repair completion time. The endpoint
does not call an AI model, persist predictions, or replace the existing
single-Equipment lifecycle context or asynchronous dataset export APIs.

## Authorization and scope

The endpoint requires `EQUIPMENT_READ`, `SYSTEM_ADMIN`, or wildcard authority,
matching the existing lifecycle-context read API. A scope administrator receives
the entire non-deleted fleet. Other callers receive all non-deleted Equipment
whose effective scope department, `coalesce(responsible_department_id,
department_id)`, equals the caller's department. A caller without a resolvable
department receives an empty fleet rather than unscoped data.

The payload excludes user IDs, employee/contact details, meter notes and device
IDs, and Repair Request reporter/assignee and supplier contact fields. This is a
read-only endpoint and does not create audit-domain or Equipment-domain records.

## Response contract

The media type is `application/x-ndjson`. Every non-empty UTF-8 line is one
complete `EquipmentFleetLifecycleLineV1` JSON object and ends with LF. There is
no array wrapper, manifest line, summary line, or `equipmentCount`. An empty
visible fleet produces a successful empty response body.

Every line has `schemaVersion` fixed to `1.0`, the response-wide `generatedAt`,
`consistency` fixed to `FIXED_AS_OF_READ_COMMITTED_BATCHES`, plus `equipment`,
`meters`, `lastRepair`, `repairs`, and `dataQuality`. Repeating the metadata
makes every line independently parseable and traceable. Equipment lines are
ordered by Equipment UUID ascending.

Lists are always JSON arrays and are never `null`. `lastRepair` is `null` when
there is no qualifying repair; otherwise it equals the last element of
`repairs`. Repairs are ordered by `(completedAt ASC, workOrderId ASC)`. Meters
are ordered by `(meterType ASC, name ASC, meterId ASC)`.

`equipment` exposes stable IDs and safe current-state fields: ID, code, name,
inventory/technical/serial numbers, model, type, physical and responsible
department, location, status, category, criticality class, manufacturer,
commissioning date, arrival date, operation start date, and expected-lifetime
settings.

Every non-deleted Equipment meter is included, including inactive meters. A
meter exposes ID, name, type, unit, active/primary flags, rollover value, cached
current value and cached last-read time, plus `latestReading`. The latest reading
is the non-deleted reading for that meter with `readAt <= generatedAt`, selected
by `(readAt DESC, id DESC)`. It includes reading ID, value, delta, time, source,
context, and linked Repair Request, Work Order, and Defect IDs. It is `null` when
the meter has no qualifying reading.

## Canonical repair semantics

A Work Order is a confirmed repair only when all these conditions hold:

- `is_deleted = false`;
- `work_type = REPAIR`;
- `status` is `COMPLETED` or `CLOSED`;
- `completed_at` is not null and is not after `generatedAt`.

Each repair exposes Work Order ID, number, title, Work Order type, work type,
status, priority, linked node/Defect/Repair Request/PPR/due-event IDs, planned
and actual timestamps, summary, result, closure notes, derived duration minutes,
reason, and `meterReadingsAtRepair`.

Reason precedence is deterministic:

1. A linked non-deleted Defect belonging to the same Equipment supplies
   description, failure reason, and root cause (`source = DEFECT`).
2. Otherwise, a linked non-deleted Repair Request belonging to the same
   Equipment supplies its description (`source = REPAIR_REQUEST`).
3. Otherwise `reason` is `null`; Work Order summary/result/closure notes remain
   separate fields and are not relabeled as a failure cause.

Broken or cross-Equipment links are not followed and produce a data-quality
issue. Defect and Repair Request text is normalized and bounded to 512 characters
before serialization, matching the existing lifecycle context's text-safety
limit.

For every meter and repair, `meterReadingsAtRepair` contains the latest
non-deleted reading of that meter where `readAt <= repair.completedAt`, selected
by `(readAt DESC, id DESC)`. A row is still returned when no reading exists; its
reading ID, value, and time are null and its availability is `MISSING`. This
preserves the difference between an unconfigured meter and a configured meter
without historical evidence. The snapshot includes meter ID/type/unit so the AI
consumer does not need to join sections.

## Data quality

Each Equipment item has `dataQuality.complete` and an `issues` array. Stable
issue codes in v1 are:

- `NO_METERS`;
- `METER_WITHOUT_READING`;
- `NO_COMPLETED_REPAIRS`;
- `REPAIR_WITHOUT_REASON`;
- `REPAIR_METER_READING_MISSING`;
- `BROKEN_DEFECT_LINK`;
- `BROKEN_REPAIR_REQUEST_LINK`;
- `METER_CACHE_MISMATCH` when cached meter state differs from the selected
  latest reading.

`complete` is false when at least one issue is present. Issues contain only a
code, safe message, and optional related IDs; they never contain exception text,
SQL, personal data, or raw payloads.

## Query and streaming design

The controller returns `application/x-ndjson`. Each immutable Equipment line is
serialized to one compact byte array before any bytes for that line are written,
then the service writes the bytes and exactly one LF. It never pretty-prints,
writes blank lines, or wraps records in a JSON array. Equipment selection uses
UUID keyset batches in ascending order; the batch size is an internal, bounded
server constant and is not a request filter.

For each Equipment batch, dedicated read queries bulk-load meters, qualifying
repairs, their linked Defects and Repair Requests, current latest meter readings,
and per-repair meter snapshots. No query is issued once per Equipment or once per
repair. The `(meter_id, read_at)` index supports latest-reading selection; repair
queries use Equipment/status/work-type/completion predicates and require an
index review in implementation. JPA entities are mapped to immutable response
records before serialization and the persistence context is cleared between
batches.

`generatedAt` is a fixed read watermark, not a database-wide repeatable-read
snapshot. Source rows changed during a long response can therefore reflect
different commit moments, while no row newer than `generatedAt` is accepted for
time-bearing repair/reading facts. This consistency level is stated in the
response as `FIXED_AS_OF_READ_COMMITTED_BATCHES`.

## Errors and HTTP behavior

Authorization failures return the existing 403 error contract before streaming.
Preflight/query setup failures return the existing 5xx error contract.
Once response bytes have been sent, HTTP status cannot be changed safely; a
mid-stream database, mapping, serialization, or client-I/O failure aborts the
connection and is logged with a correlation ID. Because a line is serialized
before it is written, serialization failures do not emit a partial line. The
endpoint never appends an error or summary line because a consumer could mistake
it for Equipment data.

The response uses `Cache-Control: no-store, private`, `X-Content-Type-Options:
nosniff`, UTF-8 `application/x-ndjson`, and inline filename
`equipment-fleet-lifecycle-v1.jsonl`. Compression remains controlled by the
existing server configuration. The endpoint has no response cache and no write
transaction.

## Test strategy and acceptance criteria

Implementation follows test-first development. Unit/contract tests prove:

- endpoint path, authority, NDJSON media type, inline JSONL filename, security
  headers, and absence of request filters;
- all and only scope-visible non-deleted Equipment are emitted in stable order;
- inactive and active meters are returned and latest-reading tie-breaking is
  deterministic;
- only completed/closed `REPAIR` Work Orders with a completion time qualify;
- all qualifying repairs are chronological and `lastRepair` equals the last;
- Defect reason precedence, Repair Request fallback, broken-link protection, and
  missing-reason diagnostics;
- readings at repair never come from after completion and missing readings stay
  explicit;
- every non-empty line is independently valid compact JSON, has response
  metadata, and ends with one LF; there are no blank or non-Equipment lines;
- empty fleet, no meters, and no repairs serialize with the specified empty-body
  and null/array semantics;
- batch boundaries do not duplicate or skip Equipment and repository calls are
  batch-oriented;
- a simulated serialization or mid-stream failure terminates output without a
  partial or misleading error/summary line.

Repository integration tests validate the PostgreSQL latest-reading and repair
snapshot queries, including equal timestamps, soft deletion, cross-Equipment
links, and scope predicates. The feature is complete when focused tests and the
existing lifecycle-context/export regression tests pass and every line of a
representative multi-batch response is valid JSON matching schema version `1.0`.
