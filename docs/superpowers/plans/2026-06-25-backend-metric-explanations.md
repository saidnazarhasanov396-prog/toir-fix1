# Backend Metric Explanations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Return dynamic, localized Uzbek/English/Russian explanations for calculated RCM risk and reliability passport availability metrics.

**Architecture:** Keep calculations in the existing backend services and add a small DTO/localization layer that formats formulas, summaries, and calculation steps from the computed values. RCM and reliability responses will carry both the existing numeric fields and an `explanation` object so frontend can render without recalculating.

**Tech Stack:** Java 21, Spring Boot 3, records for DTOs, JUnit 5, Mockito, AssertJ.

---

### Task 1: Explanation DTO Contract

**Files:**
- Create: `src/main/java/com/toir/dto/analytics/MetricExplanationDto.java`
- Create: `src/main/java/com/toir/dto/analytics/MetricExplanationStepDto.java`

- [x] **Step 1: Write DTO records**

Create immutable records with fields: `locale`, `formula`, `summary`, `steps`, and step fields `label`, `value`, `unit`.

- [x] **Step 2: Keep JSON shape frontend-friendly**

Use plain strings and numbers only; no server-only enums in response payloads.

### Task 2: Localized Explanation Service

**Files:**
- Create: `src/main/java/com/toir/service/MetricExplanationService.java`
- Test: `src/test/java/com/toir/service/RcmServiceTest.java`
- Test: `src/test/java/com/toir/service/ReliabilityPassportServiceTest.java`

- [x] **Step 1: Add failing RCM test**

Assert that `computeAll("uz")` returns an RCM risk explanation whose formula, summary, and step labels are Uzbek and whose values match the actual risk calculation.

- [x] **Step 2: Add failing reliability test**

Assert that `passport(equipmentId, "ru")` returns an availability explanation whose formula, summary, and step values match observed hours, downtime hours, operating hours, and availability.

- [x] **Step 3: Implement localization**

Add supported locales `uz`, `en`, `ru`; normalize unknown or blank values to English. Generate RCM and availability explanations from numeric inputs.

### Task 3: RCM Service Wiring

**Files:**
- Modify: `src/main/java/com/toir/dto/rcm/EquipmentRiskScore.java`
- Modify: `src/main/java/com/toir/service/RcmService.java`
- Modify: `src/main/java/com/toir/controller/RcmController.java`

- [x] **Step 1: Add `explanation` field**

Extend `EquipmentRiskScore` with `MetricExplanationDto explanation`.

- [x] **Step 2: Add language-aware service overloads**

Keep existing `computeAll()` and `topN()` defaults for compatibility, and add `computeAll(String lang)` / `topN(int n, String lang)`.

- [x] **Step 3: Accept `lang` query param**

Add optional `lang` to `/api/v1/rcm/risk-scores`; default remains English.

### Task 4: Reliability Passport Wiring

**Files:**
- Modify: `src/main/java/com/toir/controller/ReliabilityPassportController.java`
- Modify: `src/main/java/com/toir/service/ReliabilityPassportService.java`
- Modify: `src/main/java/com/toir/service/ReliabilityDowntimeCalculator.java`

- [x] **Step 1: Add `explanation` field**

Extend `ReliabilityPassport` with `MetricExplanationDto explanation`.

- [x] **Step 2: Expose calculation internals**

Add `observedHours` and `operatingHours` to `EquipmentReliability`, alongside existing downtime and availability values.

- [x] **Step 3: Add language-aware service overloads**

Keep existing methods as English defaults and add `lang` overloads for list and detail methods. Stats stay numeric-only because they do not return calculated row explanations.

- [x] **Step 4: Accept `lang` query param**

Add optional `lang` to `/equipment/reliability-passport`, `/equipment/reliability-passports/stats`, and detail usage when needed.

### Task 5: Frontend Report

**Files:**
- Create: `../toir-front/docs/backend-metric-explanations-frontend-report.md`

- [x] **Step 1: Document payload shape**

Explain the new `explanation` object, locale selection, fallback rules, and rendering guidance.

- [x] **Step 2: Document fields per endpoint**

Cover RCM risk score and reliability passport availability separately with sample Uzbek, English, and Russian localized outputs.

### Task 6: Verification and Git

**Commands:**
- `./mvnw -Dtest=RcmServiceTest,ReliabilityPassportServiceTest test`
- `git pull --rebase --autostash`
- `git add <scoped files only>`
- `git commit -m "feat: add localized metric explanations"`
- `git push origin Codex_org`

- [x] **Step 1: Run targeted backend tests**

Expected: selected tests pass.

- [ ] **Step 2: Pull/rebase both repos**

Expected: local branch is rebased and pre-existing unrelated edits are preserved.

- [ ] **Step 3: Commit and push scoped changes**

Expected: backend commit contains implementation/tests/plan, frontend commit contains the Markdown report.
