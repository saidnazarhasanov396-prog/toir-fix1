# Equipment Passport and Lifetime Correctness Design

## Goal

Prevent equipment from being created without the global identity and ownership data required for safe operation, make passport completeness truthful when no type-specific attributes are configured, calculate remaining resource from actual consumption, and explain every lifetime status to the user.

## Scope

This change covers the equipment create/update API contract, equipment read DTO, equipment passport completeness, lifetime resource calculations, the equipment detail UI, and related Russian, English, and Uzbek translations and tests.

It does not expose the full `EquipmentLifecycleContextV1`, change lifecycle dataset exports, redesign equipment-type attribute administration, or migrate unrelated legacy duration fields.

## Global Required Fields

Every equipment record requires these fields regardless of equipment type:

- `criticalityClassId`
- `locationId`
- `commissionedAt`
- `responsibleId`

The backend is authoritative. Creation fails with a structured bad-request response when any field is absent. Updates may not clear a required field. A legacy record that already lacks a required value remains readable, but an update must supply all missing global values before it can succeed.

The frontend marks the same fields as required, validates them before submission, and presents localized field-level guidance. Frontend validation is usability support and does not replace backend enforcement.

## Passport Completeness

Passport completeness combines two sources:

1. The four global required fields above.
2. Dynamic attribute definitions marked required for the equipment type.

All missing global and dynamic fields are critical. Completeness is true only when every applicable field has a value. An equipment type with no required dynamic definitions therefore reports `4/4` when its global fields are filled and never reports a misleading `0/0` complete state.

The existing completeness DTO remains the primary contract, but its counts and missing-field list include global fields. Missing fields use stable keys and user-facing labels so the frontend can render the same card without inventing business rules.

## Lifetime Resource Calculation

The canonical meter calculation is:

```text
target = baseline + limit
remaining = target - current
consumedPercent = ((current - baseline) / limit) * 100
remainingDays = floor(max(remaining, 0) / averageDailyUsage)
```

`remainingDays` is unknown when the limit, current reading, or positive average daily usage is unavailable. It must not fall back to `limit / averageDailyUsage`, because that treats already consumed resource as unused.

The backend derives the returned remaining days from the same resolved meter snapshot used for `current`, `target`, and `remaining`. The frontend uses the server-provided remaining value and applies the same formula only as a display fallback when structured days are absent. It never estimates days from the full limit.

Invalid configurations are rejected when the server receives them: limit must be positive, warning percent must be in its supported range, average daily usage must be positive when supplied, and baseline/current/target relationships must produce a coherent snapshot. A depleted resource is represented as zero remaining days; raw remaining resource may stay negative to explain how far the limit was exceeded.

## Explainable Lifetime Status

The existing status values remain compatible:

- `NORMAL`
- `EXPIRING_SOON`
- `EXPIRED`
- `UNKNOWN`

The read DTO adds structured status evidence rather than backend-generated prose. Each evidence item identifies its source (`CALENDAR` or `METER`), reason code, severity, and the relevant structured values such as end date, current reading, target, remaining value, warning threshold, and unit.

The overall status is the most severe applicable evidence. When calendar and meter evidence have equal severity, both are returned. When one source determines a more severe overall status, both may still be returned, but the determining evidence is explicitly marked. With insufficient data, the response explains which inputs are missing rather than presenting an unexplained `UNKNOWN`.

The frontend localizes reason codes and formats dates, numbers, and units. Example explanations include “Resource exhausted: current 10,100 cycles, target 10,000 cycles” and “Warning: 600 cycles remain; threshold 1,000 cycles.”

## Components and Data Flow

Backend responsibilities:

- Centralize global required-field validation in equipment domain/service code and reuse it from create and update paths, including vehicle-backed equipment paths where applicable.
- Centralize passport completeness construction so global fields and type-required attributes are counted consistently.
- Centralize lifetime snapshot/status calculation so DTO values, remaining days, and status evidence cannot diverge.
- Keep the existing API fields during migration and add structured evidence additively.

Frontend responsibilities:

- Mirror required-field validation in equipment forms.
- Render the completeness DTO as returned by the backend, including the configured `4/4` baseline.
- Render remaining days from remaining resource, not full limit.
- Display localized status explanations next to the lifetime badge/card.

## Error Handling and Compatibility

Validation errors use stable error codes and field names so all three frontend locales can map them without parsing English text. Existing equipment remains readable. Existing clients that ignore the additive explanation fields continue to work. The legacy persisted `daysOfResourceRemaining` field is not treated as authoritative when a fresh resolved meter snapshot is available.

No database migration is required unless implementation inspection shows the new structured evidence must be persisted; the preferred design derives it at read time.

## Testing

Implementation follows test-driven development.

Backend tests cover:

- create rejection for each missing global field;
- update rejection when a global field is cleared or a legacy incomplete record is not repaired;
- completeness counts with zero and multiple type-required attributes;
- remaining days using remaining resource, depleted resource, and missing inputs;
- calendar-only, meter-only, combined, warning, expired, normal, and unknown status evidence;
- vehicle-backed equipment using the same rules where it shares the equipment contract.

Frontend tests cover:

- required form fields and localized validation;
- `4/4` and incomplete global passport states;
- remaining-day display based on remaining resource;
- localized status explanation rendering.

Verification includes focused backend and frontend tests, the complete affected test suites, frontend lint, TypeScript build, and locale parity check.

## Acceptance Criteria

- No equipment can be created without all four global required fields.
- An update cannot remove any global required field, and a legacy incomplete record must be repaired before another update succeeds.
- Passport completeness never labels `0/0` as complete; with no dynamic requirements it uses the four global requirements.
- Remaining resource days subtract consumed resource and stay consistent with the displayed current, target, and remaining values.
- Every lifetime status shown in the equipment UI has a localized, data-backed explanation.
- Backend remains the single source of truth, and relevant automated checks pass.
