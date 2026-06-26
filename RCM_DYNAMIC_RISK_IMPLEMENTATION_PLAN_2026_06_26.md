# RCM Dynamic Equipment Risk Implementation Plan

Date: 2026-06-26

## Purpose

The current RCM risk score is only partly dynamic. Probability is influenced by open defects and MTBF, but consequence is driven by static criticality-class fields such as `safetyImpact`, `productionImpact`, `ecologicalImpact`, and `energyImpact`. Those values are not backed by real project modules, so explanations can look more precise than the system actually is.

The target model is a fully evidence-based risk score where every risk contribution can be traced to data the backend already has, and every explanation clearly says why the equipment received its risk score.

## Current Behavior

Current RCM risk is calculated in `RcmService`.

```text
consequence = safetyImpact + productionImpact + ecologicalImpact + energyImpact
probability = derived from open defects or MTBF
riskScore = min(100, consequence * probability)
```

Current problems:

- The impact fields are static configuration, not dynamic evidence.
- Ecological, energy, safety, and production impacts imply modules that do not currently exist.
- Explanations show calculation components but do not clearly explain the real reason behind the score.
- If risk is caused by defects, MTBF, overdue work, downtime, or status, the explanation should say that directly.

## Business Goal

For each equipment item, the system should answer:

```text
Why is this equipment risky right now?
```

The answer must be based on observable backend data:

- Open defects
- Defect severity
- Recurring defects
- MTBF
- MTTR
- Downtime events
- Equipment status
- Overdue maintenance tasks
- Open repair requests
- Inspection or calibration issues, where available
- Criticality class or equipment category as a business weight, not as fake module-derived impact

## Proposed Risk Model

The new model should calculate risk from dynamic risk factors.

```text
riskScore = min(100, consequenceScore * probabilityScore)
```

The formula can stay familiar, but both sides should be explainable from real evidence.

## Dynamic Probability Score

Probability should answer:

```text
How likely is a failure or operational issue soon?
```

Suggested rules:

| Evidence | Score impact | Explanation intent |
|---|---:|---|
| 5+ open defects | probability = 5 | Very high probability due to many active defects |
| 3-4 open defects | probability = 4 | High probability due to multiple active defects |
| 1-2 open defects | probability = 3 | Medium probability due to active defects |
| Low MTBF, below 2000 hours | probability at least 3 | Failures occur too frequently |
| MTBF between 2000 and 4000 hours | probability at least 2 | Reliability is moderate |
| Recurring defect pattern | +1, capped at 5 | Same issue repeats |
| Overdue critical maintenance | +1, capped at 5 | Preventive action is late |
| No active signal | probability = 1 | Baseline probability |

Important rule:

If multiple probability signals exist, the explanation should list them all, but mark the strongest signal as the primary reason.

Example:

```text
Probability is 4 because 3 open defects are active. MTBF is also below 4000 hours, which supports elevated probability.
```

## Dynamic Consequence Score

Consequence should answer:

```text
How serious would the impact be if this equipment fails?
```

Suggested dynamic consequence factors:

| Evidence | Score impact | Explanation intent |
|---|---:|---|
| Criticality level CRITICAL | +5 | Business-critical equipment |
| Criticality level HIGH | +4 | High-importance equipment |
| Criticality level MEDIUM | +2 | Moderate importance |
| Criticality level LOW | +1 | Lower importance |
| Recent downtime over threshold | +1 to +3 | Failures have caused real downtime |
| High MTTR | +1 to +2 | Recovery takes long |
| Open emergency/high repair request | +1 to +2 | Active severe repair demand |
| Equipment status UNDER_REPAIR / OUT_OF_SERVICE | +2 | Equipment is already unavailable or degraded |
| Missed inspection/calibration issue | +1 | Compliance/reliability attention needed |

This replaces the static impact fields in the RCM explanation. The static fields can be deprecated later, but first they should stop being presented as calculated evidence.

## Suggested Scoring Example

Example equipment:

- Criticality level: HIGH
- Open defects: 3
- MTBF: 3500 hours
- MTTR: 10 hours
- Recent downtime: 12 hours

Calculation:

```text
probability = 4
reason: 3 open defects

consequence = 4 + 2 + 1 = 7
reason: high criticality, recent downtime, long recovery time

riskScore = min(100, 7 * 4) = 28
```

Explanation:

```text
Risk is 28/100 because 3 open defects make failure probability high. Consequence is elevated because the equipment has high criticality, recent downtime, and long recovery time.
```

## Explanation Design

The explanation should be structured, not just a single translated sentence.

Recommended response shape:

```json
{
  "locale": "en",
  "summary": "Risk is 28/100 because 3 open defects make failure probability high.",
  "formula": "min(100, consequence × probability)",
  "reasons": [
    {
      "code": "OPEN_DEFECTS_HIGH",
      "category": "PROBABILITY",
      "label": "Open defects",
      "value": 3,
      "effect": "Probability set to 4",
      "severity": "HIGH"
    },
    {
      "code": "HIGH_CRITICALITY",
      "category": "CONSEQUENCE",
      "label": "Criticality",
      "value": "HIGH",
      "effect": "Consequence increased by 4",
      "severity": "HIGH"
    }
  ],
  "steps": [
    {
      "label": "Consequence",
      "value": 7
    },
    {
      "label": "Probability",
      "value": 4
    },
    {
      "label": "Final risk",
      "value": 28,
      "unit": "/100"
    }
  ]
}
```

The frontend can render:

- summary as the main explanation
- reasons as a detailed evidence list
- steps as the calculation breakdown

## Reason Codes

Use stable reason codes so the frontend does not parse human text.

Probability reason codes:

| Code | Meaning |
|---|---|
| `OPEN_DEFECTS_CRITICAL` | 5+ open defects |
| `OPEN_DEFECTS_HIGH` | 3-4 open defects |
| `OPEN_DEFECTS_MEDIUM` | 1-2 open defects |
| `LOW_MTBF_HIGH` | MTBF below 2000 hours |
| `LOW_MTBF_MEDIUM` | MTBF below 4000 hours |
| `RECURRING_DEFECTS` | repeated defect pattern found |
| `OVERDUE_MAINTENANCE` | overdue maintenance increases probability |
| `BASELINE_PROBABILITY` | no active probability signal |

Consequence reason codes:

| Code | Meaning |
|---|---|
| `CRITICAL_EQUIPMENT` | criticality level is CRITICAL |
| `HIGH_CRITICALITY` | criticality level is HIGH |
| `RECENT_DOWNTIME` | downtime recently affected equipment |
| `HIGH_MTTR` | recovery time is high |
| `OPEN_HIGH_REPAIR_REQUEST` | high-priority repair request is open |
| `EQUIPMENT_UNAVAILABLE` | status indicates unavailable/degraded equipment |
| `INSPECTION_OR_CALIBRATION_ISSUE` | inspection/calibration signal exists |

## Multilingual Explanation Text

The system should support Uzbek, Russian, and English.

Do not hardcode only one large sentence. Use localized templates per reason code.

### English

Summary examples:

```text
Risk is {riskScore}/100 because {primaryReason}.
Risk is {riskScore}/100 because several active signals increase probability and consequence.
```

Reason templates:

| Code | English template |
|---|---|
| `OPEN_DEFECTS_HIGH` | `{count} open defects were observed, so probability is high.` |
| `LOW_MTBF_HIGH` | `MTBF is {mtbfHours} hours, below the {threshold} hour threshold.` |
| `BASELINE_PROBABILITY` | `No active defect or reliability signal was found, so baseline probability is used.` |
| `HIGH_CRITICALITY` | `The equipment has high criticality, increasing consequence.` |
| `RECENT_DOWNTIME` | `Recent downtime of {downtimeHours} hours increases consequence.` |
| `HIGH_MTTR` | `MTTR is {mttrHours} hours, so recovery is considered difficult.` |

### Uzbek

Summary examples:

```text
Xavf {riskScore}/100, chunki {primaryReason}.
Xavf {riskScore}/100, chunki bir nechta faol belgilar ehtimollik va oqibatni oshirmoqda.
```

Reason templates:

| Code | Uzbek template |
|---|---|
| `OPEN_DEFECTS_HIGH` | `{count} ta ochiq nuqson aniqlandi, shuning uchun ehtimollik yuqori.` |
| `LOW_MTBF_HIGH` | `MTBF {mtbfHours} soat, bu {threshold} soat chegarasidan past.` |
| `BASELINE_PROBABILITY` | `Faol nuqson yoki ishonchlilik signali topilmadi, shuning uchun bazaviy ehtimollik ishlatiladi.` |
| `HIGH_CRITICALITY` | `Uskuna yuqori kritik darajaga ega, bu oqibatni oshiradi.` |
| `RECENT_DOWNTIME` | `So'nggi {downtimeHours} soatlik to'xtash oqibatni oshiradi.` |
| `HIGH_MTTR` | `MTTR {mttrHours} soat, shuning uchun tiklash murakkab deb baholanadi.` |

### Russian

Summary examples:

```text
Риск {riskScore}/100, потому что {primaryReason}.
Риск {riskScore}/100, потому что несколько активных факторов повышают вероятность и последствие.
```

Reason templates:

| Code | Russian template |
|---|---|
| `OPEN_DEFECTS_HIGH` | `Обнаружено {count} открытых дефекта, поэтому вероятность высокая.` |
| `LOW_MTBF_HIGH` | `MTBF составляет {mtbfHours} ч, что ниже порога {threshold} ч.` |
| `BASELINE_PROBABILITY` | `Активные дефекты или сигналы надежности не найдены, поэтому используется базовая вероятность.` |
| `HIGH_CRITICALITY` | `Оборудование имеет высокую критичность, что повышает последствие.` |
| `RECENT_DOWNTIME` | `Недавний простой {downtimeHours} ч повышает последствие.` |
| `HIGH_MTTR` | `MTTR составляет {mttrHours} ч, поэтому восстановление считается сложным.` |

## Backend Implementation Plan

### Phase 1: Introduce structured risk model

Add DTOs:

```java
RiskExplanationDto
RiskReasonDto
RiskCalculationStepDto
RiskReasonCode
RiskReasonCategory
RiskSeverity
```

Suggested fields:

```java
record RiskReasonDto(
    String code,
    String category,
    String label,
    Object value,
    String effect,
    String severity
) {}
```

### Phase 2: Build dynamic evidence collector

Create a service such as:

```text
EquipmentRiskEvidenceService
```

It should collect per equipment:

- open defects count
- defect severities
- recurrence count
- latest reliability metric
- recent downtime totals
- open repair request count/severity
- overdue maintenance count
- equipment status
- criticality level
- inspection/calibration flags where available

This service should return a normalized evidence object, not a final score.

### Phase 3: Create dynamic risk calculator

Create:

```text
EquipmentRiskScoringService
```

Responsibilities:

- calculate probability
- calculate consequence
- calculate final risk
- emit reason codes and raw values
- avoid localized text

This service should be deterministic and easy to unit test.

### Phase 4: Create explanation renderer

Create or refactor:

```text
MetricExplanationService
```

Responsibilities:

- accept reason codes and values
- resolve locale from `lang` or `Accept-Language`
- render summary, reason labels, reason effects, and formula text
- support `en`, `uz`, `ru`

Scoring logic should not be inside this renderer.

### Phase 5: Update RCM service

Refactor `RcmService.computeAll()` to:

1. Load equipment.
2. Collect evidence.
3. Score evidence.
4. Render explanation.
5. Return `EquipmentRiskScore`.

The service should no longer calculate consequence from `safetyImpact + productionImpact + ecologicalImpact + energyImpact`.

### Phase 6: API response changes

Extend `EquipmentRiskScore`:

```java
int consequenceScore
int probabilityScore
int riskScore
RiskExplanationDto explanation
List<RiskReasonDto> reasons
```

Compatibility option:

- Keep old `consequence` and `probability` fields for now.
- Add clearer aliases later if frontend agrees.

### Phase 7: Deprecate static impact fields

Do not delete database columns immediately.

Recommended sequence:

1. Stop using static impact fields in RCM scoring.
2. Stop showing them in RCM explanations.
3. Keep them on criticality class screens only if the business still wants manual criticality configuration.
4. Later migration can remove or rename them if no longer needed.

## Testing Plan

Unit tests should cover:

- 5+ open defects -> critical probability reason
- 3-4 open defects -> high probability reason
- 1-2 open defects -> medium probability reason
- no defects but low MTBF -> MTBF probability reason
- no defects and healthy MTBF -> baseline probability reason
- high criticality -> consequence reason
- recent downtime -> consequence reason
- high MTTR -> consequence reason
- multiple reasons -> primary reason selected correctly
- explanation renders in English
- explanation renders in Uzbek
- explanation renders in Russian
- unsupported locale falls back to English

Contract tests should verify:

- `/api/v1/rcm/risk-scores` returns structured reasons
- reason codes are stable
- localized labels change with `lang`
- numeric values do not change with language

## Example Final API Output

```json
{
  "equipmentId": "00000000-0000-0000-0000-000000000001",
  "equipmentCode": "EQ-100",
  "equipmentName": "Compressor",
  "consequence": 7,
  "probability": 4,
  "riskScore": 28,
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
      },
      {
        "code": "HIGH_CRITICALITY",
        "category": "CONSEQUENCE",
        "label": "Criticality",
        "value": "HIGH",
        "effect": "Consequence increased by 4",
        "severity": "HIGH"
      }
    ],
    "steps": [
      {
        "label": "Consequence",
        "value": 7
      },
      {
        "label": "Probability",
        "value": 4
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

## Open Business Decisions

These must be decided before implementation:

1. Should criticality level be a consequence factor, or should consequence be only historical/dynamic data?
2. What downtime period should count: last 30 days, 90 days, or 12 months?
3. What MTTR threshold is considered high?
4. Should overdue maintenance increase probability, consequence, or both?
5. Should repair request priority affect risk independently of defects?
6. Should calibration/inspection issues affect all equipment or only specific equipment categories?
7. What risk bands should the frontend show: low/medium/high/critical?

## Recommended First Version

For a practical first implementation, use:

Probability:

- open defects
- recurring defects
- MTBF
- overdue maintenance

Consequence:

- criticality level
- recent downtime
- MTTR
- current equipment status

Explanation:

- structured reason codes
- summary in `en`, `uz`, `ru`
- stable numeric steps

This gives dynamic, understandable risk without inventing ecological or energy modules that do not exist.
