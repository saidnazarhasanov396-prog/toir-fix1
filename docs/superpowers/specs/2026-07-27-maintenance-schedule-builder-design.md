# Maintenance Schedule Builder Design

## Goal

Add a stateless annual maintenance schedule preview and convert the same calculated occurrences into ordinary `PprTask` records without duplicating scheduling logic or changing the maintenance due-event lifecycle.

The same delivery also prevents ordinary workers from seeing `DRAFT` and `GENERATED` PPR plans or tasks belonging to those plans. A mechanic is a user with `PPR_PLAN_GENERATE` or `PPR_PLAN_APPROVE`; `SYSTEM_ADMIN` and `*` remain unrestricted.

Saved preview runs are outside this delivery.

## Current-System Findings

- `PprGeneratorService` currently creates one task per plan/regulation/equipment signature. It schedules that task at `plan.startDate` and does not expand occurrences through the full plan range.
- `PprPlanService.create` immediately invokes generation. The explicit `/ppr-plans/{id}/generate` call is therefore already an idempotent repeat for current clients.
- Current PPR screens send `pprType`, `scheduleType`, and `frequency`. The new schedule-builder contract sends dates, equipment scope, department, and `anchorMode`, and expects different maintenance kinds and periodicities in one plan.
- `MaintenanceDueCalculationService` already exposes `nextCalendarDueAt`, current meter state, and a structured missing-meter blocking code.
- `EquipmentMaintenanceEffectiveRuleResolver` is the canonical source of inherited, overridden, disabled, and individual effective rules.

## Considered Approaches

### 1. Shared occurrence engine with an explicit builder mode — selected

Persist nullable `anchorMode` on `PprPlan`. An explicitly supplied value activates schedule-builder generation; omitted values preserve the legacy generator path used by current screens. A new pure calculation service resolves equipment and effective rules, expands occurrences, and returns an internal result consumed by both preview and PPR generation.

This provides preview/generation parity while avoiding a broad behavioral change for existing plan creation.

### 2. Replace all PPR generation with horizon expansion

Make every calendar PPR plan expand every matching rule across its full date range and treat omitted `anchorMode` as `CURRENT`.

This is conceptually uniform, but it changes task counts and dates for all existing consumers and conflicts with the requirement to preserve current endpoints.

### 3. Add a preview-only calculator

Leave `PprGeneratorService` unchanged and calculate annual occurrences only for `/maintenance-schedule/preview`.

This is the smallest edit, but preview and committed tasks would disagree. It directly violates the consistency requirement and is rejected.

## API Contract

### `POST /api/v1/maintenance-schedule/preview`

Requires `PPR_PLAN_GENERATE`, `PPR_PLAN_CREATE`, `SYSTEM_ADMIN`, or `*`.

Request:

```json
{
  "fromDate": "2026-01-01",
  "toDate": "2026-12-31",
  "scopeType": "EQUIPMENT",
  "equipmentIds": ["uuid"],
  "equipmentTypeIds": [],
  "departmentId": "uuid",
  "anchorMode": "CURRENT"
}
```

Validation:

- `fromDate`, `toDate`, `scopeType`, and `anchorMode` are required.
- `fromDate` must not be after `toDate`.
- `EQUIPMENT` requires at least one `equipmentId` and rejects `equipmentTypeIds`.
- `EQUIPMENT_TYPE` requires at least one `equipmentTypeId` and rejects `equipmentIds`.
- Duplicate identifiers are rejected.
- Department scope is enforced with the existing `ScopeAccessService`.

Response items contain the requested fields plus nullable `equipmentMaintenanceRuleId` so individual rules without a base regulation remain representable. `regulationId` is nullable for those individual rules.

Items are sorted by equipment code, equipment id, planned date, rule name, regulation id, and individual rule id for deterministic responses.

Summary semantics:

- `equipmentCount`: distinct equipment with at least one returned occurrence.
- `totalOccurrences`: item count.
- `missingMetersCount`: distinct effective-rule/equipment pairs that configure a meter trigger but have no active meter.
- `unmatchedCount`: selected operational equipment that produces no schedulable occurrence in the requested window.

The endpoint is read-only and never writes preview data.

### PPR plan create/update/detail

Add optional `anchorMode` to `PprPlanRequest`, `PprPlanDto`, and `ppr_plans.anchor_mode`.

- Explicit `CURRENT` or `RESET_TO_PLAN_START` marks a schedule-builder plan.
- Omitted/null keeps the legacy generation behavior for existing consumers.
- New schedule-builder clients must send the same explicit value used for preview.
- A builder request that omits `pprType` represents a mixed-kind plan; the stored `pprType` and `frequency` remain null and `scheduleType` is `CALENDAR`.
- Existing requests without `anchorMode` retain current defaults.

`POST /ppr-plans/{id}/generate` keeps its current request shape. It reads the stored mode.

## Occurrence Calculation

The calculator receives a validated date window, equipment scope, department, and anchor mode.

1. Load operational equipment matching the scope and department.
2. Resolve applicable active effective rules for every selected equipment item.
3. Skip `MANUAL` trigger rules and `HOUR` periodicity because a date-only annual schedule cannot represent them safely.
4. Calculate the current due state once per effective rule.
5. Count a configured missing meter from the structured blocking code, without suppressing an otherwise available calendar schedule.
6. Determine the first occurrence:
   - `CURRENT`: use `nextCalendarDueAt`, converted in the application clock zone.
   - `RESET_TO_PLAN_START`: add one full rule interval to `fromDate`.
7. For `CURRENT`, advance an overdue date by whole rule intervals until it reaches `fromDate`.
8. Add inclusive occurrences through `toDate`.

Supported calendar intervals are `DAY`, `WEEK`, `MONTH`, `QUARTER`, and `YEAR`. Calendar arithmetic uses `LocalDate.plus*`, preserving Java's end-of-month behavior.

The engine returns domain occurrences, diagnostics, and equipment matching information. The preview mapper only formats them. The generator maps the same occurrences to `PprTask`.

## Generation and Idempotency

For a plan with explicit `anchorMode`, `PprGeneratorService` builds an occurrence request from plan dates, department, and plan targets, then calls the shared calculator.

Each generated task uses:

- `scheduledStart`: occurrence date at 09:00.
- `scheduledEnd`: occurrence date plus the labor-duration day span, at 18:00, capped at plan end.
- `dueDate`: occurrence date at 18:00.
- regulation/rule/equipment/labor fields from the occurrence.

The duplicate signature becomes:

```text
plan + regulation + individual rule + equipment + planned date
```

This allows multiple cycles for the same rule while keeping repeat generation idempotent. Existing task codes remain valid; new occurrence codes retain the current prefix and sequence convention.

Plans without explicit `anchorMode` continue through the existing generator implementation.

`PprPlanService.create` continues auto-generation for compatibility. A frontend's subsequent explicit generate call safely creates zero duplicates.

## Visibility Policy

Add a focused `PprPlanVisibilityPolicy` that reads current authorities.

- Mechanics/admins may request any plan status.
- Other readers are limited to `APPROVED`, `IN_PROGRESS`, `CLOSED`, and `CANCELLED`.
- Requested statuses are intersected with the caller's allowed statuses.
- `GET /ppr-plans` supports singular `status` and repeated `statuses`; using both forms creates their union.
- `GET /ppr-plans/tasks` keeps the existing task `status` filter and additionally enforces allowed parent-plan statuses.

Repository queries enforce the final allowed plan statuses. Plan/task statistics use the same visibility set to avoid count leakage. Direct plan detail and plan-task collection reads return not found when the caller cannot view the parent plan.

Mutation permissions and the approval lifecycle remain unchanged.

## Error Handling

- Invalid preview payloads return the existing 400 error envelope.
- Unknown explicitly requested equipment identifiers produce a 400 response instead of silently returning a misleading empty preview.
- Department-scope violations use the existing access-denied behavior.
- A `CURRENT` rule without a calendar due date contributes to unmatched diagnostics and produces no occurrence.
- No arbitrary occurrence cap is needed for supported date units; `HOUR` is excluded to avoid an unbounded date-only expansion.

## Testing

- Unit-test interval expansion, inclusive boundaries, overdue anchor advancement, reset anchoring, missing meters, manual/hour exclusions, scope resolution, and deterministic output.
- Controller contract-test preview validation, authorization declaration, scope enforcement, and JSON shape.
- Generator lifecycle-test multiple occurrences, exact preview/generation dates, explicit builder mode, repeat idempotency, and unchanged legacy behavior.
- Migration contract-test the nullable enum-like column and constraint.
- Service/controller/repository tests status parameters and enforced worker visibility for plans, tasks, stats, and direct reads.
- Run targeted tests first, then the full non-container Maven suite that is viable in the local environment.
