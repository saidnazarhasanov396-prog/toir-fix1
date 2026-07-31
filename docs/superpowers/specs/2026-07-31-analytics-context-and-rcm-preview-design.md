# Analytics Context and RCM Preview Design

**Date:** 2026-07-31
**Status:** Approved direction
**Repositories:** `toir-backend`, `toir-frontend`

## 1. Purpose

Make every analytics value understandable and make RCM task generation safe:

1. one page-level period selector controls all analytics cards, charts, and tables;
2. every analytics block exposes the effective period, department scope, data sources,
   and calculation time;
3. RCM auto-planning shows a complete forecast before it writes any PPR tasks;
4. tasks are created only after explicit user confirmation and stale previews or
   duplicates cannot create conflicting work.

## 2. Confirmed Product Decisions

- The analytics selector has exactly three values:
  `LAST_7_DAYS`, `LAST_30_DAYS`, and `ALL_TIME`.
- User-facing labels are “7 days”, “30 days”, and “All time”; rolling ranges avoid
  ambiguity around partial calendar weeks and months.
- The selected value is stored in the page URL and defaults to `LAST_30_DAYS` for a
  first visit. Existing bookmarked URLs without the parameter receive that default.
- One selector controls the whole downtime/reliability analytics page. Individual
  cards do not have independent period controls.
- Compact context is visible beside every KPI group and chart/table title. An info
  tooltip expands it to period boundaries, department, sources, and updated time.
- The backend is authoritative for both filtering and returned context metadata.
- The user's authorization scope remains authoritative. The frontend cannot request
  data outside the current user's permitted department scope.
- RCM auto-planning is a preview/confirm workflow. Opening the preview never writes
  tasks, audit records, or snapshots.

## 3. Considered Approaches

### 3.1 Frontend-only filtering

Rejected. The current UI receives paginated and top-N datasets, so client filtering
would calculate incomplete totals and allow different widgets to disagree.

### 3.2 Backend query-time filtering with context metadata

Selected. Each analytics endpoint accepts the same period and applies it before
aggregation. Responses carry the effective context used for that exact result.
This fits the current transactional application and requires no separate analytics
storage.

### 3.3 Persisted analytical snapshots

Deferred. Snapshots could support historical BI and very large datasets, but they
introduce refresh jobs, retention rules, and eventual-consistency semantics that
are unnecessary for the requested page.

## 4. Analytics Period Contract

### 4.1 Request

Every endpoint used by the downtime/reliability page accepts:

```text
period=LAST_7_DAYS | LAST_30_DAYS | ALL_TIME
```

The backend resolves one `asOf` from an injected clock. For rolling periods:

```text
LAST_7_DAYS  => [asOf - 7 days, asOf]
LAST_30_DAYS => [asOf - 30 days, asOf]
ALL_TIME     => [unbounded, asOf]
```

Range comparisons use the application business timezone when a source stores local
dates and UTC instants when it stores instants. The response always returns UTC ISO
timestamps and the business timezone identifier.

Unknown period values return HTTP 400 with the standard error envelope.

### 4.2 Shared response context

All analytics responses include an `analyticsContext` object:

```json
{
  "period": "LAST_30_DAYS",
  "from": "2026-07-01T10:00:00Z",
  "to": "2026-07-31T10:00:00Z",
  "timezone": "Asia/Tashkent",
  "scope": {
    "type": "DEPARTMENT",
    "departmentId": "...",
    "departmentName": "Цех № 1"
  },
  "calculatedAt": "2026-07-31T10:00:00Z",
  "sources": ["DOWNTIME_EVENTS", "WORK_ORDERS"]
}
```

For unrestricted users, scope type is `ALL_AUTHORIZED` and department fields are
null. `ALL_TIME` has a null `from`. Sources are stable codes; frontend translations
provide the display names.

Each response reports only sources used by that response. The page must not invent
source names client-side.

### 4.3 Source timestamps and interval behavior

- downtime events are included when their interval overlaps the selected range;
  duration is clipped to the overlap, and open events end at `asOf`;
- repair requests use `detectedAt`, falling back to `createdAt`;
- defects use `detectedAt` when available, otherwise `createdAt`;
- work-order creation and type shares use `createdAt`; closed-work-order and repair
  duration metrics use `actualCompletionAt`;
- PPR completion uses `actualCompletionAt` for completed tasks and `dueDate` for due
  or overdue tasks;
- reliability history uses `metricDate` inside the selected range; current MTBF,
  MTTR, and availability are recalculated from in-range operational events rather
  than taking an unrelated all-time snapshot.

An empty period produces zero totals and empty series, never silently falls back to
all-time data.

## 5. Analytics UI

The period selector lives in `PageToolbar` and updates a `period` URL parameter.
Changing it resets affected pagination and causes all analytics queries on the page
to refetch with the same period in both their query keys and request parameters.

Context presentation has two layers:

1. a compact visible line or badge, such as `30 days · Цех № 1`;
2. an adjacent info icon whose tooltip shows exact dates, department, translated
   source names, and `Updated 31.07.2026, 15:00`.

The context component is shared by toolbar statistics, overview tiles, chart cards,
RCA/predictive sections, and table section headers. It consumes backend metadata and
does not reconstruct dates locally. Loading skeletons reserve its space, and missing
metadata is rendered as “Context unavailable” rather than a fabricated value.

All new strings exist in Russian, Uzbek, and English. The selected period is
shareable and survives reload/back navigation through the URL.

## 6. RCM Preview and Confirmation

### 6.1 Preview request

Replace the direct auto-plan button behavior with:

```text
POST /api/v1/rcm/auto-plan/preview?riskThreshold=30&planId=<optional>
```

The response contains:

- `previewFingerprint`, `calculatedAt`, and optional expiry time;
- resolved target plan identity;
- candidate count, creatable count, duplicate count, conflict count, and skipped
  count;
- one row per candidate with equipment ID/code/name, risk score, translated risk
  reasons, selected regulation, proposed schedule and priority;
- decision `CREATE`, `DUPLICATE`, `CONFLICT`, or `SKIP`;
- existing task identity for duplicates;
- stable conflict/reason codes plus safe display parameters.

Preview uses the same authorization scope as the RCM list and PPR creation command.
It produces no database mutations.

### 6.2 Classification rules

- missing equipment or equipment type: `SKIP`;
- no active regulation: `CONFLICT`;
- more than one equally eligible regulation: `CONFLICT` instead of silently taking
  the first row;
- no target plan: `CONFLICT`;
- an existing active RCM task with the same source key: `DUPLICATE`;
- an overlapping active non-RCM task for the same equipment/regulation: `CONFLICT`;
- otherwise: `CREATE`.

Duplicate and conflict checks ignore deleted or terminally cancelled tasks.

### 6.3 Confirmation

The preview dialog presents summary cards and an equipment table. The user can
expand “Why” to see the risk evidence, and can inspect duplicate/conflict details.
The primary action reads `Create N tasks`; it is disabled when `N = 0`.

Confirmation calls:

```text
POST /api/v1/rcm/auto-plan/confirm
{
  "riskThreshold": 30,
  "planId": "...",
  "previewFingerprint": "..."
}
```

The server recomputes the preview in the same transaction. If its fingerprint has
changed, it returns HTTP 409 `RCM_PREVIEW_STALE`; the UI keeps the dialog open and
offers to refresh. If unchanged, only `CREATE` rows are inserted. Duplicates,
conflicts, and skips are never inserted.

### 6.4 Idempotency and concurrency

Every generated RCM task has a stable source type and source key derived from target
plan, equipment, regulation, and planning cycle. A partial unique database index on
active RCM source keys is the final concurrency guard. Confirmation uses the same
classification/building code as preview, deterministic ordering, transactional
inserts, and the existing audit service. Repeating an already successful confirm
returns zero new tasks and the existing duplicate results rather than creating more
rows.

Task codes use the existing code-generation facility or a collision-safe sequence;
`System.currentTimeMillis()` is not used as identity.

## 7. Error Handling and Access

- Analytics read endpoints retain `ANALYTICS_READ` and PBAC department scoping.
- Preview requires analytics read plus the permission needed to create PPR tasks.
- Confirm requires the PPR task creation permission and validates access to the
  resolved plan and every equipment row.
- HTTP 400 covers invalid period/threshold; 403 covers permission or scope denial;
  404 covers an explicitly requested plan; 409 covers stale preview or a concurrent
  source-key conflict.
- The frontend uses the standard toast/error components. It never uses `alert()`.
- A failed confirmation leaves the preview visible and does not claim tasks were
  created.

## 8. Testing

Backend tests cover period parsing and range resolution, per-source timestamp
filtering, clipped downtime intervals, department scope metadata, empty periods,
context contracts for every page endpoint, and a fixed clock.

RCM backend tests cover no-write preview, every classification, reason payloads,
fingerprint determinism, stale preview rejection, repeated confirmation,
concurrent confirmation, unique-index migration, permissions, and audit creation.

Frontend tests cover URL/default period state, synchronized query parameters and
query keys, pagination reset, visible context badges, tooltip contents, all three
locales, preview summary/table, explicit confirmation, zero-creatable state, stale
refresh, mutation error handling, and success invalidation/navigation.

Final verification includes targeted backend/frontend tests, full frontend test
suite, lint, i18n parity, TypeScript/Vite build, backend compilation/tests relevant
to touched modules, and migration contract tests.

## 9. Non-goals

- Arbitrary custom date ranges.
- Independent periods per card or chart.
- A new data warehouse or persisted analytics snapshot pipeline.
- Editing RCM candidates inside the preview.
- Automatically resolving ambiguous regulations or plan conflicts.
- Creating tasks before explicit confirmation.
