# PPR Equipment Yearly Calendar Finalization Audit — 2026-07-29

## Scope and method

- Backend starting point: `origin/main` at `26f3370d43195454d4a768d9e0cf0ead74e9441a`.
- Frontend starting point: `origin/main` at `ae362686f902e74bd1160fb604916dd11c989e52`.
- Both repositories were clean, so the finalization branch was created directly from `origin/main`; no stash, cleanup, reset, or destructive worktree operation was used.
- The requested `PPR_EQUIPMENT_YEARLY_CALENDAR_AUDIT_2026-07-29.md` and `PPR_EQUIPMENT_YEARLY_CALENDAR_W1_W2_IMPLEMENTATION.md` were absent from `main`. Requirements were reconstructed from the merged implementation and the available approval-first specifications at `docs/superpowers/specs/2026-07-28-annual-maintenance-approval-first-design.md:20-31,543-578,781-793` and `docs/superpowers/specs/2026-07-28-ppr-schedule-window-and-test-gate-design.md:47-103`.
- Verification was static only, as required: `rg`, `git diff --check`, and `git status --short`. Tests, builds, compiler, application startup, typecheck, Testcontainers, and Docker builds were not run.

## W0–W9 verdict

| Work item | Expected scope | Present | Correct | Missing/broken evidence | Required action |
|---|---|---:|---:|---|---|
| W0 | Approved business rules | Yes | Yes | The two named calendar documents were absent; approval-first atomic/idempotent behavior is documented in `annual-maintenance-approval-first-design.md:20-31,543-578`, while schedule/deadline rules are at `ppr-schedule-window-and-test-gate-design.md:47-103`. | Reconstructed and recorded here; no blind copy from an old branch. |
| W1 | Backend DTO/query foundation | Yes | Yes | DTO/filter defaults and limits are in `PprEquipmentCalendarFilter:10-63`; deterministic page IDs and bounded metadata/occurrence queries are in `PprEquipmentCalendarJdbcRepository:35-117`; response grouping/order/display resolution is in `PprEquipmentCalendarService:89-142,178-241`. The diagnostics SQL used risky nested text-block concatenation at `PprEquipmentCalendarJdbcRepository:129-162`. | Replaced the four nested delimiters with one Java 21 text block and `%1$s` formatting. |
| W2 | Calendar endpoint | Yes | Yes | Direct endpoint, required year, repeated enum binding, defaults and RBAC are in `PprEquipmentCalendarController:23-81`; plan visibility/data scope and year overlap are in `PprEquipmentCalendarService:64-87,145-175`. | No further action. |
| W3 | Frontend types/API hook | Yes | Yes | Field-for-field contract is in `src/types/api.ts:3301-3425`; direct endpoint, repeated enum parameters and abort signal are in `src/lib/api.ts:1235-1265`; active-only query gating and complete query key are in `use-ppr-equipment-calendar.ts:15-29` and `ppr-equipment-calendar-state.ts:252-264`. | No further action. |
| W4 | URL-backed tabs | Yes | Yes | Existing selection accepted legacy/invalid URLs but did not make the default tasks URL canonical. Canonical parsing and normalization now live in `ppr-equipment-calendar-state.ts:107-163`, are applied with replace navigation in `ppr-plan-detail-page.tsx:401-415`, and are specified in `ppr-equipment-calendar-state.test.ts:14-71`. | Added pure normalization; canonical URLs are `?tab=tasks` and `?tab=equipment-calendar`, unrelated parameters are preserved. |
| W5 | Equipment × 12 months matrix | Yes | Yes | Twelve month keys, three-chip cap, desktop sticky matrix, and mobile equipment accordion are in `ppr-equipment-calendar-matrix.tsx:15-29,106-133,258-369`. Server-side equipment paging is in `PprEquipmentCalendarJdbcRepository:35-59` and `PprEquipmentCalendarPanel:261-277`. | No further action. |
| W6 | Occurrence details/navigation | Yes | Yes | Backend display code/name and planned-date fallback are in `PprEquipmentCalendarService:178-241`; details, start/end, deadline, source and null-safe task navigation are in `ppr-equipment-calendar-matrix.tsx:136-230`. | No further action. |
| W7 | Filters/pagination/performance | Yes | Yes | URL defaults/reset/stale-page handling are in `ppr-equipment-calendar-state.ts:166-243`; debounced, permission-gated, size-20 reference lookups and snapshot filter clearing are in `PprEquipmentCalendarPanel:53-127`; current-page-only occurrence loading is in `PprEquipmentCalendarService:89-97`. | No further action. |
| W8 | Localization/RBAC/tests | Yes | Yes | Endpoint permission is `PprEquipmentCalendarController:28-39`; scope enforcement is `PprEquipmentCalendarService:70-87,145-162`; RU/UZ/EN calendar namespaces are present and the missing active-equipment labels are now at `en.json:22812`, `ru.json:22849`, and `uz.json:22818`. Calendar tests use installed Vitest/React server-render conventions; no committed test imports `@testing-library/react`. | Added the `ACTIVE` label in all three calendar namespaces and added focused URL/materialization specifications (NOT RUN). |
| W9 | Manual-verification readiness | Yes | Yes (static) | Runtime, compiler, build and responsive screenshots remain intentionally unverified under the task policy. Static Review A and B found no remaining P1/P2 defect after correction. | Execute the ordered manual checklist in the delivery report. |

## Known regression audit

| Risk | Result and evidence |
|---|---|
| Illegal Java text-block construction | Fixed in `PprEquipmentCalendarJdbcRepository.findDiagnostics`, lines `129-162`; all four predicates now use `%1$s` and a single `.formatted(matchingRow)` call. |
| Ambiguous filter constructor | Already fixed on `main`: wrapper constructor delegates with `Objects.requireNonNull(year).intValue()` at `PprEquipmentCalendarFilter:35-58`. |
| Missing materialization repository contract | Already present and plan-bound at `PprTaskRepository:348-356`; the empty snapshot is rejected before the call at `MaintenanceScheduleMaterializationService:59-78`. |
| Unsupported AssertJ API | Already fixed with the boolean assertion at `MaintenanceScheduleCalculationItemRepositoryContractTest:15-28`. |
| Spring content-factory bean | Exactly one production `@Component` exists at `MaintenanceScheduleCalculationContentFactory:9-14`; it has no fields, is stateless, and is constructor-injected into `MaintenanceScheduleMaterializationService`. Direct construction exists only in unit tests. |
| Missing frontend test dependency | No `*.test.ts`/`*.test.tsx` imports `@testing-library/react`; calendar component tests follow the installed `react-dom/server` + Vitest convention. |

## Corrective findings resolved

### P1 — diagnostics text-block compilation risk

`PprEquipmentCalendarJdbcRepository.findDiagnostics` embedded text-block delimiters inside four expressions. This was replaced by valid Java 21 text-block formatting at `PprEquipmentCalendarJdbcRepository:129-162`, preserving the same SQL predicate and parameters.

### P1 — materialization retry and partial recovery

`MaintenanceScheduleMaterializationService.finalizeApproval` treated any partial source-task set as inconsistent and accepted an exact retry only while the plan remained `APPROVED`. It now:

- verifies the requested snapshot and plan-bound source IDs (`MaintenanceScheduleMaterializationService:55-78`);
- rejects soft-deleted, null, unexpected, or duplicate source links (`88-97`);
- creates only missing tasks and records the complete snapshot count (`104-120`);
- treats exact retries in `APPROVED`, `IN_PROGRESS`, `CLOSED`, or `CANCELLED` as idempotent (`171-195`).

Focused specifications are in `MaintenanceScheduleMaterializationServiceTest:142-170,216-252` and were NOT RUN. The existing schema already provides plan-bound source-item integrity constraints in `V20260728_5_1__annual_maintenance_calculation_snapshot_integrity.sql:1-44`; no migration was required.

### P2 — non-canonical/default tab URLs

`normalizePprPlanDetailSearchParams` now converts absent, invalid, unauthorized, legacy, and task-deep-link states to a stable canonical tab while preserving unrelated query parameters (`ppr-equipment-calendar-state.ts:119-163`). The page applies normalization with replace navigation (`ppr-plan-detail-page.tsx:409-415`).

### P2 — active equipment status exposed as a raw enum

The matrix always resolves an equipment status for its tooltip (`ppr-equipment-calendar-matrix.tsx:58-94`), but the calendar namespace lacked `ACTIVE`. RU/UZ/EN labels were added at the cited locale lines.

## Backend deep-audit conclusions

- API/DTO: direct response, ISO Java date/time serialization, numeric labor hours, nullable classifier fields, deleted marker, zero-based paging, default 25/max 100, and all twelve month buckets are represented by the controller/filter/DTO/service cited above.
- Query: the repository pages distinct accessible equipment first, then loads metadata and occurrences for only that page (`PprEquipmentCalendarJdbcRepository:35-117`). Task and snapshot source SQL are selected exclusively by `findOccurrences:113-117`, preventing preview/materialized duplication. Stable ordering is at lines `54-58` and `PprEquipmentCalendarService:197-207`.
- Authoritative source: approval-first + not materialized + current revision selects `CALCULATION_SNAPSHOT`; every other mode selects `PPR_TASK` (`PprEquipmentCalendarAuthoritativeSourceResolver:13-24`). Selection does not use a display status.
- Placement: snapshot/linked planned date is preferred; manual/legacy scheduled start is the fallback; due date is returned only as deadline (`PprEquipmentCalendarService:210-241`). Rows use the start month.
- Security: method RBAC, plan visibility, plan department scope and canonical equipment predicates are server-side. Diagnostics return counts only. Manual task binding uses `PprEquipmentAccessPolicy.requireManualTaskEquipment` from the plan service path.
- Materialization: plan lock, exact revision/hash, plan-bound links, missing-only creation, soft-delete conflict and later-lifecycle idempotency are statically covered after the fix.
- Migration: existing applied migration contains the required source-item/plan integrity; no schema change was made.

## Frontend deep-audit conclusions

- Contract/API: frontend types at `src/types/api.ts:3301-3425` match the direct backend response, including stringified month keys, nullable task/snapshot IDs, source revision, dates, diagnostics, deletion and page metadata. API serialization appends each enum separately (`src/lib/api.ts:1260-1264`).
- Request lifecycle: the hook receives `enabled: active` (`PprEquipmentCalendarPanel:62`), inactive tab content returns `null` through the shared Tabs primitive, and per-task material items are empty in calendar mode (`ppr-equipment-calendar-state.ts:245-250`). The query key includes the complete normalized filter object (`252-264`).
- Matrix/accessibility: exactly twelve columns, sticky equipment, long-name wrapping, deleted/historical markers, backend display values, 3 + N rendering, keyboard buttons, responsive accordion and dialog are at `ppr-equipment-calendar-matrix.tsx:15-29,58-133,235-395`. A null `taskId` renders no link (`165-195`).
- Filters: bounded/debounced/permission-gated lookups, page reset, page recovery, backend page-size limits and snapshot-only lifecycle-filter clearing are implemented at the cited panel/state lines.
- Localization/dependencies: RU/UZ/EN calendar keys and runtime `Intl.DateTimeFormat(i18n.language)` month/date formatting are present. Production calendar code does not hardcode maintenance codes. No new package was introduced.

## Static Review A — backend/syntax/contract/security

- Reviewed Java syntax and imports, text blocks, constructor delegation, repository contracts, Spring bean registration, source resolver, DTO assembly, SQL parameter guards, RBAC/data scope, and materialization consistency.
- `git diff --check`: PASS (no output).
- Remaining P1 findings: 0.
- Remaining P2 findings: 0.

## Static Review B — frontend/lifecycle/accessibility/dependencies

- Reviewed URL normalization, active-tab mounting, query enablement, per-task fan-out prevention, abort signal, deterministic query key, permission gates, responsive matrix, dialog/task links, translations and package/import compatibility.
- `rg` found no `@testing-library/react` import in committed frontend tests and no hardcoded maintenance codes in production calendar files (test fixtures intentionally contain sample codes).
- `git diff --check`: PASS (no output).
- Remaining P1 findings: 0.
- Remaining P2 findings: 0.

## Verification and remaining P3 work

- Backend tests: **NOT RUN**.
- Frontend tests: **NOT RUN**.
- Backend build/compiler/startup: **NOT RUN**.
- Frontend build/typecheck/Docker build: **NOT RUN**.
- Testcontainers: **NOT RUN**.
- Remaining P3 items: external backend startup; frontend Docker build/typecheck; runtime validation of legacy, snapshot, multi-occurrence, cancelled-filter, pagination and limited-permission scenarios; RU/UZ/EN desktop/mobile screenshots.
