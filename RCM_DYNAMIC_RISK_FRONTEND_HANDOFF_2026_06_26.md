# RCM Dynamic Risk Frontend Handoff

Date: 2026-06-26

## Summary

Backend RCM risk scoring has been changed from partly static scoring to evidence-backed dynamic scoring.

The existing `/api/v1/rcm/risk-scores` endpoint is still the integration point. Existing numeric fields remain available for compatibility:

- `consequence`
- `probability`
- `probabilityPercent`
- `riskScore`
- `openDefects`
- `mtbfHours`
- `mttrHours`
- `criticalityClass`
- `criticalityClassName`
- `repairPriority`

New structured fields were added:

- top-level `reasons`
- nested `explanation.reasons`
- nested `explanation.steps`
- localized `explanation.summary`

Frontend should stop relying on old explanation step labels such as `Safety impact`, `Production impact`, `Ecological impact`, and `Energy impact`. Those static impact fields are no longer used in RCM scoring or explanation.

## Endpoint

```http
GET /api/v1/rcm/risk-scores
```

Existing query parameters still apply:

```text
top
page
size
sortBy
sortDir
lang
```

The controller also respects `Accept-Language` when `lang` is not provided.

Supported locales:

- `en`
- `uz`
- `ru`

Unsupported or missing locale falls back to `en`.

## Response Shape

The endpoint returns a Spring page. Each row has this shape:

```json
{
  "equipmentId": "00000000-0000-0000-0000-000000000001",
  "equipmentCode": "EQ-100",
  "equipmentName": "Compressor",
  "criticalityClass": "CRIT-HIGH",
  "criticalityClassName": "High criticality",
  "consequence": 7,
  "probability": 4,
  "riskScore": 28,
  "repairPriority": 2,
  "openDefects": 3,
  "mtbfHours": 3500.0,
  "mttrHours": 10.0,
  "probabilityPercent": 80,
  "reasons": [
    {
      "code": "OPEN_DEFECTS_HIGH",
      "category": "PROBABILITY",
      "label": "Open defects",
      "value": 3,
      "effect": "Probability set to 4",
      "severity": "HIGH"
    }
  ],
  "explanation": {
    "locale": "en",
    "formula": "min(100, consequence × probability)",
    "summary": "Risk is 28/100 because 3 open defects were observed, so probability is high.",
    "reasons": [
      {
        "code": "OPEN_DEFECTS_HIGH",
        "category": "PROBABILITY",
        "label": "Open defects",
        "value": 3,
        "effect": "Probability set to 4",
        "severity": "HIGH"
      }
    ],
    "steps": [
      {
        "label": "Consequence",
        "value": 7,
        "unit": null
      },
      {
        "label": "Probability",
        "value": 4,
        "unit": null
      },
      {
        "label": "Final risk",
        "value": 28,
        "unit": "/100"
      }
    ]
  }
}
```

`reasons` and `explanation.reasons` currently contain the same localized reason list. Use either one consistently. Recommended: use `explanation.reasons` inside the risk explanation UI, and use top-level `reasons` only when rendering compact risk badges or table decorations without opening the explanation object.

## Frontend Type Updates

Update the RCM risk score type to include:

```ts
export type RcmRiskReasonCategory = 'PROBABILITY' | 'CONSEQUENCE'

export type RcmRiskSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export type RcmRiskReasonCode =
  | 'OPEN_DEFECTS_CRITICAL'
  | 'OPEN_DEFECTS_HIGH'
  | 'OPEN_DEFECTS_MEDIUM'
  | 'LOW_MTBF_HIGH'
  | 'LOW_MTBF_MEDIUM'
  | 'RECURRING_DEFECTS'
  | 'OVERDUE_MAINTENANCE'
  | 'BASELINE_PROBABILITY'
  | 'CRITICAL_EQUIPMENT'
  | 'HIGH_CRITICALITY'
  | 'MEDIUM_CRITICALITY'
  | 'LOW_CRITICALITY'
  | 'RECENT_DOWNTIME'
  | 'HIGH_MTTR'
  | 'OPEN_HIGH_REPAIR_REQUEST'
  | 'EQUIPMENT_UNAVAILABLE'
  | 'INSPECTION_OR_CALIBRATION_ISSUE'

export interface RcmRiskReason {
  code: RcmRiskReasonCode
  category: RcmRiskReasonCategory
  label: string
  value: unknown
  effect: string
  severity: RcmRiskSeverity
}

export interface RcmRiskCalculationStep {
  label: string
  value: number
  unit?: string | null
}

export interface RcmRiskExplanation {
  locale: 'en' | 'uz' | 'ru'
  formula: string
  summary: string
  reasons: RcmRiskReason[]
  steps: RcmRiskCalculationStep[]
}

export interface EquipmentRiskScore {
  equipmentId: string
  equipmentCode: string
  equipmentName: string
  criticalityClass?: string | null
  criticalityClassName?: string | null
  consequence: number
  probability: number
  riskScore: number
  probabilityPercent: number
  repairPriority?: number | null
  openDefects: number
  mtbfHours: number
  mttrHours: number
  reasons: RcmRiskReason[]
  explanation?: RcmRiskExplanation | null
}
```

## Rendering Requirements

### Table View

Keep current numeric table columns, but update the explanation affordance:

- Show `riskScore` as the primary score.
- Show `probabilityPercent` only as a display alias for `probability * 20`.
- Keep sorting by existing backend fields: `riskScore`, `consequence`, `probability`, `openDefects`, `mtbfHours`, `mttrHours`.
- Do not calculate consequence on the frontend.
- Do not derive reason labels from `code`; use backend-provided localized `label`.
- Do not parse `summary` to infer state.

Recommended table additions:

- A compact primary reason chip from `explanation.reasons[0]`.
- A reason count badge such as `3 signals` if more than one reason exists.
- A category split indicator: probability reasons vs consequence reasons.

### Detail or Tooltip View

Render:

1. `explanation.summary` as the main text.
2. `explanation.reasons` as an evidence list.
3. `explanation.steps` as the formula breakdown.
4. `explanation.formula` as small secondary text.

Suggested layout:

```text
Risk is 28/100 because 3 open defects were observed, so probability is high.

Evidence
- [HIGH] Open defects: 3
  Probability set to 4
- [HIGH] Criticality: HIGH
  Consequence increased by 4

Calculation
Consequence: 7
Probability: 4
Final risk: 28/100
Formula: min(100, consequence × probability)
```

### Severity Styling

Use `severity` for visual emphasis:

```text
LOW      neutral / muted
MEDIUM   warning-light
HIGH     warning / orange
CRITICAL danger / red
```

Use `category` for grouping:

```text
PROBABILITY  "Why failure or operational issue is likely"
CONSEQUENCE  "Why impact would be serious"
```

Do not use severity alone to calculate the risk band. Risk band should still be based on `riskScore` unless product changes that rule.

### Localization

Frontend should pass the current UI locale:

```http
GET /api/v1/rcm/risk-scores?lang=uz
```

or set:

```http
Accept-Language: uz
```

Backend localizes:

- `explanation.summary`
- `explanation.formula`
- `reason.label`
- `reason.effect`
- `steps[].label`
- step units where applicable

Frontend should not translate reason codes itself for normal display. Keep frontend translations only for static UI labels such as section headings, column names, and button text.

## Scoring Signals Now Represented

Probability reasons:

| Code | Meaning |
|---|---|
| `OPEN_DEFECTS_CRITICAL` | 5 or more open defects |
| `OPEN_DEFECTS_HIGH` | 3-4 open defects |
| `OPEN_DEFECTS_MEDIUM` | 1-2 open defects |
| `LOW_MTBF_HIGH` | MTBF below 2000 hours |
| `LOW_MTBF_MEDIUM` | MTBF below 4000 hours |
| `RECURRING_DEFECTS` | Open recurring defects exist |
| `OVERDUE_MAINTENANCE` | Open overdue maintenance exists |
| `BASELINE_PROBABILITY` | No active probability signal |

Consequence reasons:

| Code | Meaning |
|---|---|
| `CRITICAL_EQUIPMENT` | Criticality level is `CRITICAL` |
| `HIGH_CRITICALITY` | Criticality level is `HIGH` |
| `MEDIUM_CRITICALITY` | Criticality level is `MEDIUM` |
| `LOW_CRITICALITY` | Criticality level is `LOW` |
| `RECENT_DOWNTIME` | Downtime exists in the recent evidence window |
| `HIGH_MTTR` | Recovery time is high |
| `OPEN_HIGH_REPAIR_REQUEST` | Open high/critical/emergency repair requests exist |
| `EQUIPMENT_UNAVAILABLE` | Equipment status is `IN_REPAIR` or `OUT_OF_SERVICE` |
| `INSPECTION_OR_CALIBRATION_ISSUE` | Reserved for future inspection/calibration evidence |

## Compatibility Notes

The following old behavior changed:

- `consequence` is no longer `safetyImpact + productionImpact + ecologicalImpact + energyImpact`.
- Old static explanation steps are removed from RCM explanations.
- A criticality class still matters, but through `CriticalityLevel`, not through fake module impact fields.

The following behavior remains compatible:

- Endpoint path is unchanged.
- Pagination shape is unchanged.
- Sorting parameters are unchanged.
- Numeric fields remain present.
- `probabilityPercent` remains present.

## Frontend Implementation Checklist

- Update API types for `EquipmentRiskScore`, `RcmRiskExplanation`, and `RcmRiskReason`.
- Update RCM table/detail components to read `explanation.summary`.
- Replace any UI that expects static impact steps with `explanation.steps`.
- Add a grouped reason list by `category`.
- Style reason chips by `severity`.
- Ensure `lang` or `Accept-Language` is sent with the current UI locale.
- Add fallback rendering when `explanation` is null:
  - Show numeric `riskScore`, `consequence`, and `probability`.
  - Hide the reason list.
  - Show a generic localized unavailable message from frontend i18n.
- Add tests for English, Uzbek, and Russian response rendering.
- Add tests that numeric values do not change when locale changes.
- Add tests that frontend never parses `summary` to determine reason state.

## Suggested QA Cases

1. Equipment with 3 open defects:
   - `probability = 4`
   - reason includes `OPEN_DEFECTS_HIGH`
   - summary mentions open defects in selected language

2. Equipment with no open defects and healthy MTBF:
   - `probability = 1`
   - reason includes `BASELINE_PROBABILITY`

3. Equipment with high criticality:
   - consequence includes criticality reason
   - reason includes `HIGH_CRITICALITY`

4. Equipment with recent downtime and high MTTR:
   - reasons include `RECENT_DOWNTIME` and `HIGH_MTTR`
   - calculation steps show consequence, probability, final risk

5. Locale switch:
   - `lang=en`, `lang=uz`, `lang=ru` change labels/effects/summary
   - `riskScore`, `probability`, `consequence`, and reason `code` values stay unchanged

## Backend Validation Already Run

Focused RCM tests:

```text
./mvnw -Dtest=EquipmentRiskScoringServiceTest,RcmServiceTest test
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
```

Affected backend tests:

```text
./mvnw -Dtest=EquipmentRiskScoringServiceTest,RcmServiceTest,RcmControllerContractTest,OperationalIssueScannerServiceTest,DashboardLifecycleServiceTest,MaintenanceAdvisorTest,ReportsPbacScopeTest test
Tests run: 48, Failures: 0, Errors: 0, Skipped: 0
```

Full backend suite note:

```text
./mvnw test
Tests run: 3067, Failures: 0, Errors: 62, Skipped: 12
```

The full suite compiled and reached test execution, but repository/DataJpa tests require PostgreSQL on `localhost:5433`. That database was not available in the local environment.
