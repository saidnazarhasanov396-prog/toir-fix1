# Backend Metric Explanations Frontend Handoff

Date: 2026-06-25

## Purpose

Backend now returns localized, dynamic explanations for calculated maintenance analytics. Frontend should display these backend-provided explanations instead of recreating calculation formulas in the UI.

Supported languages:

- `uz`
- `en`
- `ru`

Language selection:

- Preferred: pass `?lang=uz`, `?lang=en`, or `?lang=ru`.
- Alternative: send `Accept-Language: uz`, `Accept-Language: en`, or `Accept-Language: ru`.
- Fallback: unsupported or missing language resolves to English.

## Shared Response Shape

RCM risk score rows and reliability passport rows now use the same `explanation` object:

```json
{
  "explanation": {
    "locale": "uz",
    "formula": "min(100, oqibat × ehtimollik)",
    "summary": "Xavf 56/100, chunki oqibat 14 va ehtimollik 4.",
    "steps": [
      { "label": "Xavfsizlik ta'siri", "value": 4, "unit": null },
      { "label": "Yakuniy xavf", "value": 56, "unit": "/100" }
    ]
  }
}
```

Frontend rendering guidance:

- Show the existing metric value as before.
- Add an info icon next to calculated values that have `explanation`.
- On click or hover, show `summary`, `formula`, and the ordered `steps`.
- Append `unit` after `value` when `unit` is not null.
- Do not translate `summary`, `formula`, or `steps[].label` in frontend; backend already returns localized text.
- Do not recalculate values in frontend.
- Hide the explanation UI when `explanation` is null.

## RCM Risk Scores

Endpoint:

```http
GET /api/v1/rcm/risk-scores?lang=uz
```

Existing fields remain:

- `consequence`
- `probability`
- `riskScore`
- `openDefects`
- `mtbfHours`
- `mttrHours`

New field:

- `explanation`

Formula:

```text
riskScore = min(100, consequence × probability)
```

Example:

```json
{
  "equipmentCode": "EQ-2026-1001",
  "consequence": 14,
  "probability": 4,
  "riskScore": 56,
  "openDefects": 3,
  "explanation": {
    "locale": "uz",
    "formula": "min(100, oqibat × ehtimollik)",
    "summary": "Xavf 56/100, chunki oqibat 14 va ehtimollik 4.",
    "steps": [
      { "label": "Xavfsizlik ta'siri", "value": 4, "unit": null },
      { "label": "Ishlab chiqarish ta'siri", "value": 5, "unit": null },
      { "label": "Ekologik ta'sir", "value": 3, "unit": null },
      { "label": "Energiya ta'siri", "value": 2, "unit": null },
      { "label": "Oqibat jami", "value": 14, "unit": null },
      { "label": "Ochiq nuqsonlar", "value": 3, "unit": null },
      { "label": "Ehtimollik", "value": 4, "unit": null },
      { "label": "MTBF", "value": 0, "unit": "soat" },
      { "label": "MTTR", "value": 0, "unit": "soat" },
      { "label": "Yakuniy xavf", "value": 56, "unit": "/100" }
    ]
  }
}
```

Localized summary examples:

```json
{
  "uz": "Xavf 56/100, chunki oqibat 14 va ehtimollik 4.",
  "en": "Risk is 56/100 because consequence is 14 and probability is 4.",
  "ru": "Риск 56/100, потому что последствие равно 14, а вероятность 4."
}
```

Frontend TODO:

- Add `explanation?: MetricExplanation | null` to the RCM risk score type.
- Pass current app language as `lang` when loading RCM risk scores.
- Add an explanation icon next to `riskScore`.
- Render the explanation as a compact popover, tooltip, or drawer.

## Reliability Passport Availability

Endpoint:

```http
GET /api/v1/equipment/reliability-passport?lang=ru
```

Existing fields remain:

- `mtbfHours`
- `mttrHours`
- `availabilityPct`
- `totalDowntimeEvents`
- `totalDowntimeMinutes`
- `topRootCauses`

New field:

- `explanation`

Formula:

```text
availabilityPct = (observed time - downtime) / observed time × 100
```

Example:

```json
{
  "equipmentCode": "EQ-2026-0001",
  "availabilityPct": 99.98,
  "totalDowntimeMinutes": 120,
  "explanation": {
    "locale": "ru",
    "formula": "(наблюдаемое время - простой) / наблюдаемое время × 100",
    "summary": "Доступность 99.98%, потому что наблюдаемое время 8760 ч, простой 2 ч.",
    "steps": [
      { "label": "Наблюдаемое время", "value": 8760, "unit": "часы" },
      { "label": "Простой", "value": 2, "unit": "часы" },
      { "label": "Рабочее время", "value": 8758, "unit": "часы" },
      { "label": "Доступность", "value": 99.98, "unit": "%" }
    ]
  }
}
```

Localized summary examples:

```json
{
  "uz": "Mavjudlik 99.98%, chunki kuzatilgan vaqt 8760 soat va to'xtash vaqti 2 soat.",
  "en": "Availability is 99.98% because observed time was 8760 hours and downtime was 2 hours.",
  "ru": "Доступность 99.98%, потому что наблюдаемое время 8760 ч, простой 2 ч."
}
```

Frontend TODO:

- Add `explanation?: MetricExplanation | null` to the reliability passport row type.
- Pass current app language as `lang` when loading reliability passports.
- Add an explanation icon next to `availabilityPct`.
- Keep rendering the numeric availability value exactly as today.
- Render `summary`, `formula`, and `steps` from the backend response.

## Recommended TypeScript Types

```ts
export interface MetricExplanationStep {
  label: string;
  value: number;
  unit?: string | null;
}

export interface MetricExplanation {
  locale: "uz" | "en" | "ru" | string;
  formula: string;
  summary: string;
  steps: MetricExplanationStep[];
}
```

Add these optional fields to existing frontend response types:

```ts
export interface EquipmentRiskScoreRecord {
  explanation?: MetricExplanation | null;
}

export interface ReliabilityPassportSummary {
  explanation?: MetricExplanation | null;
}
```

## Notes

- Backend text is already localized and dynamic.
- Values in `steps` come from the same backend calculation that produced the metric.
- Frontend should not infer or regenerate the explanation.
- Stats endpoints remain numeric-only and do not need explanation UI.
