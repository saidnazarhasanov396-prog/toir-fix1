# RCM Auto-Plan Preview and Confirm Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace direct RCM task creation with a no-write forecast that explains candidates, reasons, duplicates, and conflicts, followed by explicit stale-safe confirmation.

**Architecture:** One deterministic backend classifier builds both preview and confirmation from the same inputs. Preview returns a canonical fingerprint; confirmation recomputes under transaction and refuses changed input, while a stable task source key plus a partial unique index provides idempotency under retries and concurrency. React presents the preview in a dialog and calls confirm only from its explicit primary action.

**Tech Stack:** Java 21, Spring Boot/JPA/PostgreSQL/Flyway, JUnit 5/Mockito/MockMvc, React 19, TypeScript 6, TanStack Query, Radix Dialog, Vitest/Testing Library, i18next.

## Global Constraints

- Preview is read-only: no task, audit, snapshot, or other persistence writes.
- Decisions are exactly `CREATE`, `DUPLICATE`, `CONFLICT`, and `SKIP`.
- Confirmation creates only `CREATE` rows and requires an unchanged preview fingerprint.
- Duplicate safety must hold under concurrent confirmation, not only in Java checks.
- Ambiguous regulation selection is a conflict; code must not silently take the first row.
- Task codes cannot use `System.currentTimeMillis()` as identity.
- Preview and confirm enforce both analytics visibility and PPR task creation access.
- New UI strings must exist in `ru`, `uz`, and `en`.
- Use TDD and preserve unrelated dirty-worktree changes.

---

## File Structure

### Backend

- Create DTOs under `src/main/java/com/toir/dto/rcm/autoplan/` for preview, row,
  decision, conflict, confirm request, and result.
- Create `src/main/java/com/toir/service/RcmAutoPlanPreviewService.java`: deterministic
  classification and fingerprinting.
- Refactor `src/main/java/com/toir/service/RcmAutoPlannerService.java`: confirm only,
  using preview classification rather than a separate loop.
- Modify `src/main/java/com/toir/controller/RcmController.java`: preview and confirm endpoints.
- Modify `src/main/java/com/toir/entity/PprTask.java` and
  `src/main/java/com/toir/repository/PprTaskRepository.java`: RCM source metadata/lookups.
- Create `src/main/resources/db/migration/V20260731_4__rcm_task_source_idempotency.sql`.
- Test in `RcmAutoPlanPreviewServiceTest`, `RcmAutoPlannerServiceTest`,
  `RcmControllerContractTest`, `RcmAutoPlanSecurityTest`, and
  `RcmTaskSourceMigrationContractTest`.

### Frontend

- Modify `src/types/api.ts` and `src/lib/api.ts` for preview/confirm contracts.
- Create `src/modules/equipment/components/rcm/rcm-auto-plan-dialog.tsx` and test.
- Modify `src/modules/equipment/components/rcm/rcm-toolbar.tsx` and
  `src/modules/equipment/pages/rcm-page.tsx` and add a page-flow test.
- Modify `src/i18n/locales/{ru,uz,en}.json`.

---

### Task 1: Define deterministic preview decisions

**Interfaces:**
- Produces: `RcmAutoPlanDecision`, `RcmAutoPlanPreviewRow`,
  `RcmAutoPlanPreviewResponse`, and `RcmAutoPlanPreviewService.preview(threshold, planId)`.
- A preview row carries equipment, risk/explanation, plan/regulation, proposed dates,
  priority, decision, existing task reference, and conflict codes.

- [ ] **Step 1: Write failing classification tests**

Create one test each for `CREATE`, missing equipment/type (`SKIP`), no regulation
(`CONFLICT`), multiple eligible regulations (`CONFLICT`), missing plan (`CONFLICT`),
matching active RCM task (`DUPLICATE`), and overlapping active non-RCM task
(`CONFLICT`). Assert candidate ordering by equipment UUID/code is stable.

- [ ] **Step 2: Run the service test and verify RED**

Run: `./mvnw -Dtest=RcmAutoPlanPreviewServiceTest test`
Expected: FAIL because preview DTOs/service do not exist.

- [ ] **Step 3: Add immutable DTOs and one classifier**

Use stable reason/conflict codes, not pretranslated backend prose. Reuse
`EquipmentRiskScore.reasons()` for “why”. Resolve proposed schedule/priority with
pure helpers shared by preview and confirm.

- [ ] **Step 4: Add deterministic fingerprinting**

Canonicalize threshold, resolved plan ID, and sorted row fields that affect creation;
hash UTF-8 canonical bytes with SHA-256 lowercase hex. Exclude translated labels and
`calculatedAt` so language or clock display does not make an unchanged preview stale.

- [ ] **Step 5: Assert preview has no writes**

Verify `taskRepository.save`, `saveAll`, and `auditBuilderService.log` are never
called in every preview test.

- [ ] **Step 6: Run tests and verify GREEN**

Run: `./mvnw -Dtest=RcmAutoPlanPreviewServiceTest test`
Expected: PASS with zero failures.

- [ ] **Step 7: Commit preview classification**

```bash
git add src/main/java/com/toir/dto/rcm/autoplan \
  src/main/java/com/toir/service/RcmAutoPlanPreviewService.java \
  src/test/java/com/toir/service/RcmAutoPlanPreviewServiceTest.java
git commit -m "feat: classify RCM auto-plan preview"
```

### Task 2: Add persistent RCM source idempotency

**Interfaces:**
- Produces: nullable `PprTask.sourceType` and `PprTask.sourceKey` for legacy safety.
- Produces: repository lookup for active tasks by source key and overlapping
  equipment/regulation/window.

- [ ] **Step 1: Write the failing migration contract test**

Assert migration `V20260731_2` adds `source_type`, `source_key`, a check keeping both
null or both populated, and a partial unique index equivalent to:

```sql
create unique index uq_ppr_tasks_active_rcm_source_key
on ppr_tasks (source_key)
where is_deleted = false and source_type = 'RCM_AUTO_PLAN';
```

- [ ] **Step 2: Run migration test and verify RED**

Run: `./mvnw -Dtest=RcmTaskSourceMigrationContractTest test`
Expected: FAIL because the migration does not exist.

- [ ] **Step 3: Add the migration and entity fields**

Use length-bounded columns and enum/string validation consistent with existing
entities. Do not backfill legacy tasks as RCM tasks based only on title/code text.

- [ ] **Step 4: Add repository contract tests**

Test active duplicate lookup, deleted/cancelled exclusion, overlap boundaries, and
that two concurrent inserts of one active source key cannot both commit.

- [ ] **Step 5: Implement repository methods and verify GREEN**

Run: `./mvnw -Dtest=RcmTaskSourceMigrationContractTest,PprTaskRepositoryMaterializationContractTest test`
Expected: PASS with zero failures.

- [ ] **Step 6: Commit persistence protection**

```bash
git add src/main/resources/db/migration/V20260731_4__rcm_task_source_idempotency.sql \
  src/main/java/com/toir/entity/PprTask.java \
  src/main/java/com/toir/repository/PprTaskRepository.java \
  src/test/java/com/toir/migration/RcmTaskSourceMigrationContractTest.java \
  src/test/java/com/toir/repository/PprTaskRepositoryMaterializationContractTest.java
git commit -m "feat: guard RCM task source idempotency"
```

### Task 3: Expose the read-only preview API

**Interfaces:**
- Produces: `POST /api/v1/rcm/auto-plan/preview?riskThreshold=30&planId=...`.
- Consumes: `RcmAutoPlanPreviewService.preview(int, UUID)`.

- [ ] **Step 1: Add failing MockMvc contract tests**

Assert summary counts, target plan, fingerprint, timestamps, all candidate equipment,
risk reasons, decisions, duplicate existing task, conflict codes, and threshold
validation. Verify service receives optional `planId` exactly.

- [ ] **Step 2: Run the controller test and verify RED**

Run: `./mvnw -Dtest=RcmControllerContractTest test`
Expected: FAIL with 404 for the preview endpoint.

- [ ] **Step 3: Add the endpoint and validation**

Accept thresholds in `1..100`. Return the standard 400 envelope otherwise. Keep the
legacy `/auto-plan` endpoint temporarily callable only if compatibility is required,
but remove it from the frontend and mark it deprecated; it must delegate through
the new confirmation safety path before release.

- [ ] **Step 4: Add method-security tests**

Prove analytics-only users cannot preview creation, properly authorized PPR users can,
and department-scoped users never receive out-of-scope equipment.

- [ ] **Step 5: Run contracts/security and verify GREEN**

Run: `./mvnw -Dtest=RcmControllerContractTest,RcmAutoPlanSecurityTest test`
Expected: PASS with zero failures.

- [ ] **Step 6: Commit preview API**

```bash
git add src/main/java/com/toir/controller/RcmController.java \
  src/test/java/com/toir/controller/RcmControllerContractTest.java \
  src/test/java/com/toir/security/RcmAutoPlanSecurityTest.java
git commit -m "feat: expose RCM auto-plan preview"
```

### Task 4: Confirm only an unchanged preview

**Interfaces:**
- Produces: `RcmAutoPlanConfirmRequest(riskThreshold, planId, previewFingerprint)`.
- Produces: `RcmAutoPlannerService.confirm(request)` and
  `POST /api/v1/rcm/auto-plan/confirm`.

- [ ] **Step 1: Write failing stale-preview and success tests**

Assert a matching fingerprint creates only `CREATE` rows; changed risk, plan,
regulation, duplicate state, or candidate membership throws HTTP 409 code
`RCM_PREVIEW_STALE`; zero-creatable confirmation writes nothing.

- [ ] **Step 2: Write failing idempotency/concurrency tests**

Confirm twice with refreshed fingerprints and assert the second call reports existing
duplicates and creates zero. Run two confirm transactions for the same source keys
and assert only one set persists.

- [ ] **Step 3: Run the service tests and verify RED**

Run: `./mvnw -Dtest=RcmAutoPlannerServiceTest test`
Expected: FAIL because confirmation does not exist and current `generate` writes directly.

- [ ] **Step 4: Refactor generation to consume preview rows**

Inside one transaction, recompute preview, compare fingerprint using constant-time
comparison, create only `CREATE` rows, set stable RCM source metadata, generate
collision-safe task codes, and audit each inserted task. Translate a unique-index
race into the existing duplicate result or a stable 409 response.

- [ ] **Step 5: Add confirm controller tests**

Assert request-body binding, successful result, 400 validation, 403 access denial,
404 explicit plan, and 409 stale code. Ensure no GET endpoint performs creation.

- [ ] **Step 6: Run service/controller tests and verify GREEN**

Run: `./mvnw -Dtest=RcmAutoPlanPreviewServiceTest,RcmAutoPlannerServiceTest,RcmControllerContractTest,RcmAutoPlanSecurityTest test`
Expected: PASS with zero failures.

- [ ] **Step 7: Commit confirmation**

```bash
git add src/main/java/com/toir/service/RcmAutoPlannerService.java \
  src/main/java/com/toir/controller/RcmController.java \
  src/main/java/com/toir/dto/rcm/autoplan \
  src/test/java/com/toir/service/RcmAutoPlannerServiceTest.java \
  src/test/java/com/toir/controller/RcmControllerContractTest.java
git commit -m "feat: confirm stable RCM auto-plan previews"
```

### Task 5: Add typed frontend preview/confirm clients

**Interfaces:**
- Produces: `RcmAutoPlanPreview`, `RcmAutoPlanPreviewRow`,
  `RcmAutoPlanConfirmRequest`, `api.previewRcmAutoPlan`, and
  `api.confirmRcmAutoPlan`.

- [ ] **Step 1: Write failing API request tests**

Assert preview POST sends threshold/optional plan as query parameters with no body,
confirm POST sends JSON including the exact fingerprint, and neither client calls the
legacy direct endpoint.

- [ ] **Step 2: Run the API tests and verify RED**

Run: `yarn test src/lib/api-metric-explanations.test.ts`
Expected: FAIL because the new methods do not exist.

- [ ] **Step 3: Add exact TypeScript contracts and clients**

Model decision as a string union and conflict/reason codes without `any`. Retain the
existing translated RCM risk reasons. Remove `triggerRcmAutoPlan` only after all
consumers move in Task 7.

- [ ] **Step 4: Run API tests and verify GREEN**

Run: `yarn test src/lib/api-metric-explanations.test.ts`
Expected: PASS with zero failures.

- [ ] **Step 5: Commit API types**

```bash
git add src/types/api.ts src/lib/api.ts src/lib/api-metric-explanations.test.ts
git commit -m "feat: add RCM preview and confirm clients"
```

### Task 6: Build the RCM preview dialog

**Interfaces:**
- Produces: `<RcmAutoPlanDialog preview open pending onConfirm onRefresh onClose />`.
- Consumes: preview DTOs from Task 5; never performs API calls itself.

- [ ] **Step 1: Write failing dialog tests**

Assert the dialog shows creatable/duplicate/conflict/skip counts; plan identity; every
equipment; risk score and expandable reasons; regulation/schedule/priority; existing
task links; conflict labels; and `Создать N задач`. Assert confirm is disabled at
zero, Escape/close causes no mutation, and pending state prevents double submit.

- [ ] **Step 2: Run the component test and verify RED**

Run: `yarn test rcm-auto-plan-dialog`
Expected: FAIL because the component is missing.

- [ ] **Step 3: Implement the accessible dialog**

Use existing `Dialog`, `Button`, table/card, badge, and metric-explanation components.
Keep summary visible above a scrollable candidate list. Render decisions with text
and icon/color so meaning does not depend on color alone.

- [ ] **Step 4: Add exact translations in three locales**

Add preview title/description, summary labels, decision labels, conflict/reason code
labels, why/duplicate/existing-task text, refresh/stale/error messages, and create
button pluralization under `rcm.autoPlanPreview`.

- [ ] **Step 5: Run component and i18n checks GREEN**

Run: `yarn test rcm-auto-plan-dialog && yarn i18n:check`
Expected: both commands exit 0.

- [ ] **Step 6: Commit the dialog**

```bash
git add src/modules/equipment/components/rcm/rcm-auto-plan-dialog.tsx \
  src/modules/equipment/components/rcm/rcm-auto-plan-dialog.test.tsx \
  src/i18n/locales/ru.json src/i18n/locales/uz.json src/i18n/locales/en.json
git commit -m "feat: present RCM task forecast dialog"
```

### Task 7: Replace direct auto-plan with preview then confirm

**Interfaces:**
- Consumes: API clients from Task 5 and dialog from Task 6.
- Produces: toolbar opens preview; page owns preview/confirm mutations and stale refresh.

- [ ] **Step 1: Write a failing page-flow test**

Click auto-plan and assert only preview is called. Confirm in the opened dialog and
assert confirm receives the preview fingerprint. Simulate 409 and assert no success
banner, dialog remains open, and refresh action requests a new preview. On success,
assert RCM scores and PPR task queries are invalidated.

- [ ] **Step 2: Run the page-flow test and verify RED**

Run: `yarn test rcm-page`
Expected: FAIL because the toolbar still calls direct mutation.

- [ ] **Step 3: Refactor toolbar to emit intent only**

Replace the `UseMutationResult` prop with `onAutoPlan`, `previewPending`, and disabled
state. Keep the toolbar unaware of dialog and response details.

- [ ] **Step 4: Add preview/confirm mutations to `RcmPage`**

Store only the latest preview response. Open dialog on preview success; close only on
explicit close or successful confirmation. Handle 409 separately with refresh; use
the standard toast for all errors and success.

- [ ] **Step 5: Remove legacy frontend direct call and result banner**

Delete `triggerRcmAutoPlan` and the old automatic result banner after `rg` confirms
no consumers remain. The confirmed result may be shown in a toast containing created,
duplicate, conflict, and skipped counts.

- [ ] **Step 6: Run all RCM frontend tests and verify GREEN**

Run: `yarn test rcm`
Expected: PASS with zero failures.

- [ ] **Step 7: Commit page integration**

```bash
git add src/modules/equipment/pages/rcm-page.tsx \
  src/modules/equipment/pages/rcm-page.test.tsx \
  src/modules/equipment/components/rcm/rcm-toolbar.tsx \
  src/lib/api.ts
git commit -m "feat: require confirmation for RCM auto-plan"
```

### Task 8: Cross-repository verification

- [ ] **Step 1: Run backend RCM and migration verification**

Run: `./mvnw -Dtest=RcmAutoPlanPreviewServiceTest,RcmAutoPlannerServiceTest,RcmControllerContractTest,RcmAutoPlanSecurityTest,RcmTaskSourceMigrationContractTest,PprTaskRepositoryMaterializationContractTest test`
Expected: exit 0 and zero failures.

- [ ] **Step 2: Run the full frontend quality gate**

Run: `yarn test && yarn lint && yarn i18n:check && yarn build`
Expected: every command exits 0 with no test, lint, locale, type, or build failures.

- [ ] **Step 3: Inspect intended diffs only**

Run `git status --short`, `git diff --check`, and `git diff --stat` in both repositories.
Confirm no pre-existing equipment-export or audit changes are staged accidentally.

- [ ] **Step 4: Perform manual acceptance**

Open `/rcm`; verify clicking auto-plan creates nothing until confirmation, all
candidates/reasons/duplicates/conflicts are visible, cancel writes nothing, confirm
creates the shown count, repeating does not duplicate, and changing data before
confirm produces a refreshable stale-preview message.
