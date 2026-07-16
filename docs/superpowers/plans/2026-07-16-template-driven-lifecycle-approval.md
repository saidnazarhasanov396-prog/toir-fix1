# Template-Driven Lifecycle Approval Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace fixed Repair Campaign and Planned Shutdown approval routes with exact active-template creation and immutable persisted runtime-step execution.

**Architecture:** One focused `LifecycleApprovalRoutePolicy` validates templates and persisted runtime routes only for `REPAIR_CAMPAIGN / APPROVE` and `PLANNED_SHUTDOWN / APPROVE`. Domain services lock the domain row first, `ApprovalService` then locks target/action, compatible pending requests are reused without reading templates, and new requests snapshot one validated exact active template into `approval_steps`. Final step decisions and domain finalization share one REQUIRED transaction.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring Data JPA, PostgreSQL 16/Flyway, JUnit 5, Mockito, AssertJ, Testcontainers, React 19, TypeScript 6, TanStack Query, Vitest, Yarn 4.

## Global Constraints

- Canonical backend specification: `docs/superpowers/specs/2026-07-16-template-driven-lifecycle-approval-design.md` at commit `c8d512c4`.
- Frontend base at planning time: `toir-front` commit `2a5d745a`.
- Apply the new policy only to `REPAIR_CAMPAIGN / APPROVE` and `PLANNED_SHUTDOWN / APPROVE`.
- Do not add ApprovalRequest template provenance columns.
- Do not add a universal approval policy registry.
- Do not rewrite historical ApprovalRequest or approval-step data.
- Do not hardcode route counts or business role lists.
- Do not include or modify unrelated Warehouse work.
- Do not add N+1 optimization or broad refactoring.
- Existing pending approvals execute only their persisted `approval_steps`; current templates are not read for reuse, actionability, preparation, or finalization.
- Template-only changes do not cancel pending approvals; approval-scope changes do.
- Requesters cannot approve or reject; one actor can approve at most one step; reject does not inherit that repeated-approved-actor restriction.
- `SYSTEM_ADMIN` wildcard authority cannot replace an unrelated configured lifecycle step role.
- Domain row lock always precedes approval target/action lock.
- Domain services and `ApprovalService` use one Spring `REQUIRED` transaction; do not use `REQUIRES_NEW`.
- Every behavior change starts with a focused failing test and a verified RED result.
- Use Java 21 for every Maven command.
- Execute implementation in isolated worktrees because the current backend checkout contains unrelated Warehouse changes.

## Repository and File Map

Backend additions:

- `docs/runbooks/lifecycle-approval-template-uniqueness-preflight.md`: executable rollout diagnostic and administrator remediation.
- `src/main/resources/db/migration/V20260716_1__unique_active_lifecycle_approval_templates.sql`: duplicate preflight and partial unique index.
- `src/main/java/com/toir/service/approval/LifecycleApprovalRoutePolicy.java`: neutral template/runtime/decision/completion policy.
- `src/main/java/com/toir/service/approval/LifecycleRouteResolution.java`: frozen route or typed resolution failure.
- `src/main/java/com/toir/service/approval/LifecycleApprovalStartPlan.java`: reusable pending request or frozen new route.
- `src/test/java/com/toir/migration/LifecycleApprovalTemplateUniquenessMigrationContractTest.java`: SQL contract.
- `src/test/java/com/toir/migration/LifecycleApprovalTemplateUniquenessMigrationPostgresTest.java`: real PostgreSQL migration behavior.
- `src/test/java/com/toir/integration/LifecycleApprovalFinalizationRollbackIntegrationTest.java`: real transaction rollback.
- `src/test/java/com/toir/integration/LifecycleApprovalConcurrencyIntegrationTest.java`: request and template concurrency.

Backend focused modifications:

- `ApprovalTemplateRepository`, `ApprovalRequestRepository`, `ApprovalRouteResolver`, `DefaultApprovalRouteResolver`.
- `ApprovalRuleService`, `ApprovalService`, `ApprovalScopeService`.
- `RepairCampaignApprovalPolicy`, `RepairCampaignService`, `RepairCampaignMutationImpactService`.
- `PlannedShutdownService`.
- Remove fixed-route uses from `RepairCampaignApprovalRouteValidator` and `PlannedShutdownApprovalRouteValidator`; delete them after all callers use the shared policy.
- Corresponding focused unit, controller, security, migration, and integration tests.

Frontend additions:

- `src/modules/hr/libs/approvals/runtime-route.ts`: immutable runtime-route integrity/view model.
- `src/modules/hr/libs/approvals/approval-rule-form.ts`: lossless role/explicit-step editor mapping.
- Focused tests beside those helpers.

Frontend focused modifications:

- `src/components/ui/approval-progress-bar.tsx`, `approval-section.tsx`, and their tests.
- Repair Campaign and Planned Shutdown detail pages only where they pass runtime DTO/status to the shared UI.
- `src/modules/hr/pages/approval-rules-page.tsx` and focused tests.
- Repair Campaign and Planned Shutdown error guidance and EN/RU/UZ locale files.

---

### Task 1: Database Diagnostic, Administrator Remediation, and Uniqueness Migration

**Files:**
- Create: `docs/runbooks/lifecycle-approval-template-uniqueness-preflight.md`
- Create: `src/main/resources/db/migration/V20260716_1__unique_active_lifecycle_approval_templates.sql`
- Create: `src/test/java/com/toir/migration/LifecycleApprovalTemplateUniquenessMigrationContractTest.java`
- Create: `src/test/java/com/toir/migration/LifecycleApprovalTemplateUniquenessMigrationPostgresTest.java`

**Interfaces:**
- Produces: database invariant “at most one active, non-deleted exact lifecycle template” using `COALESCE(action_type, 'APPROVE')`.
- Produces: operator-visible `MULTIPLE_ACTIVE_TEMPLATES target=% action=% count=%` migration failure with concrete SQL values substituted by PostgreSQL.
- Preserves: every existing template row and all unrelated target types.

- [ ] **Step 1: Write the failing SQL contract test**

```java
class LifecycleApprovalTemplateUniquenessMigrationContractTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260716_1__unique_active_lifecycle_approval_templates.sql");

    @Test
    void migrationUsesIdenticalNormalizationAndNeverRewritesTemplates() throws IOException {
        String sql = Files.readString(MIGRATION);
        assertThat(count(sql, "COALESCE(action_type, 'APPROVE')")).isGreaterThanOrEqualTo(2);
        assertThat(sql).contains(
                "MULTIPLE_ACTIVE_TEMPLATES",
                "REPAIR_CAMPAIGN",
                "PLANNED_SHUTDOWN",
                "CREATE UNIQUE INDEX",
                "active = true",
                "is_deleted = false");
        assertThat(sql).doesNotContain(
                "UPDATE approval_templates",
                "DELETE FROM approval_templates",
                "INSERT INTO approval_templates");
    }

    private static int count(String text, String needle) {
        return text.split(Pattern.quote(needle), -1).length - 1;
    }
}
```

- [ ] **Step 2: Run the contract test and verify RED**

Run:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=LifecycleApprovalTemplateUniquenessMigrationContractTest test
```

Expected: FAIL because the migration file does not exist.

- [ ] **Step 3: Add the executable rollout runbook**

Put this exact diagnostic in the runbook:

```sql
WITH duplicate_groups AS (
    SELECT target_type,
           COALESCE(action_type, 'APPROVE') AS normalized_action,
           COUNT(*) AS duplicate_count
    FROM approval_templates
    WHERE active = true
      AND is_deleted = false
      AND target_type IN ('REPAIR_CAMPAIGN', 'PLANNED_SHUTDOWN')
      AND COALESCE(action_type, 'APPROVE') = 'APPROVE'
    GROUP BY target_type, COALESCE(action_type, 'APPROVE')
    HAVING COUNT(*) > 1
)
SELECT target_type, normalized_action, duplicate_count
FROM duplicate_groups
ORDER BY target_type, normalized_action;
```

Add the row-level diagnostic:

```sql
SELECT id, code, name, target_type,
       COALESCE(action_type, 'APPROVE') AS normalized_action,
       active, is_deleted, created_at, updated_at
FROM approval_templates
WHERE active = true
  AND is_deleted = false
  AND target_type IN ('REPAIR_CAMPAIGN', 'PLANNED_SHUTDOWN')
  AND COALESCE(action_type, 'APPROVE') = 'APPROVE'
ORDER BY target_type, normalized_action, updated_at DESC, id DESC;
```

Document the required administrator action: select the approved canonical template ID for each duplicate group, record that decision in the rollout ticket, put reviewed `keep_id,deactivate_id` pairs in `lifecycle-template-remediation.csv`, explicitly deactivate only those losing IDs, and rerun the first diagnostic until it returns zero rows. Use this ID-bound psql transaction, never “latest wins” logic:

```sql
BEGIN;
CREATE TEMP TABLE approved_lifecycle_template_remediation (
    keep_id uuid NOT NULL,
    deactivate_id uuid PRIMARY KEY,
    CHECK (keep_id <> deactivate_id)
) ON COMMIT DROP;
\copy approved_lifecycle_template_remediation(keep_id, deactivate_id) FROM 'lifecycle-template-remediation.csv' CSV HEADER

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM approved_lifecycle_template_remediation remediation
        LEFT JOIN approval_templates keep_template ON keep_template.id = remediation.keep_id
        LEFT JOIN approval_templates losing_template ON losing_template.id = remediation.deactivate_id
        WHERE keep_template.id IS NULL
           OR losing_template.id IS NULL
           OR keep_template.target_type <> losing_template.target_type
           OR COALESCE(keep_template.action_type, 'APPROVE')
              <> COALESCE(losing_template.action_type, 'APPROVE')
    ) THEN
        RAISE EXCEPTION 'Invalid lifecycle template remediation CSV';
    END IF;
END $$;

UPDATE approval_templates template
SET active = false, updated_at = now()
FROM approved_lifecycle_template_remediation remediation
WHERE template.id = remediation.deactivate_id;
COMMIT;
```

State that the CSV must contain only administrator-approved decisions attached to the rollout ticket and migration rollout must not begin until the duplicate diagnostic is empty.

- [ ] **Step 4: Add the forward-only migration**

```sql
DO $$
DECLARE duplicate_row record;
BEGIN
    FOR duplicate_row IN
        SELECT target_type,
               COALESCE(action_type, 'APPROVE') AS normalized_action,
               COUNT(*) AS duplicate_count
        FROM approval_templates
        WHERE active = true
          AND is_deleted = false
          AND target_type IN ('REPAIR_CAMPAIGN', 'PLANNED_SHUTDOWN')
          AND COALESCE(action_type, 'APPROVE') = 'APPROVE'
        GROUP BY target_type, COALESCE(action_type, 'APPROVE')
        HAVING COUNT(*) > 1
    LOOP
        RAISE EXCEPTION
            'MULTIPLE_ACTIVE_TEMPLATES target=% action=% count=%',
            duplicate_row.target_type,
            duplicate_row.normalized_action,
            duplicate_row.duplicate_count;
    END LOOP;
END $$;

CREATE UNIQUE INDEX uq_active_lifecycle_approval_template
    ON approval_templates (target_type, COALESCE(action_type, 'APPROVE'))
    WHERE active = true
      AND is_deleted = false
      AND target_type IN ('REPAIR_CAMPAIGN', 'PLANNED_SHUTDOWN')
      AND COALESCE(action_type, 'APPROVE') = 'APPROVE';
```

- [ ] **Step 5: Add real PostgreSQL migration tests and verify RED before completing the migration**

Use `PostgreSQLContainer<>("postgres:16-alpine")`, migrate to `20260715.1`, seed rows through JDBC, then migrate to latest. Implement separate tests for:

Use these exact test names and assertions:

| Test | Fixture | Assertion |
|---|---|---|
| `cleanMigrationSucceedsAndDoesNotRewriteRows` | one active lifecycle template; snapshot every column | Flyway succeeds and the post-migration row map equals the snapshot |
| `nullAndApproveActionsShareOneNormalizedKey` | drop fixture `action_type` NOT NULL; seed active NULL and APPROVE rows | Flyway throws with target, `APPROVE`, and count `2` |
| `duplicateActiveLifecycleTemplatesFailWithTargetActionAndCount` | two active Repair Campaign rows | exception contains `MULTIPLE_ACTIVE_TEMPLATES target=REPAIR_CAMPAIGN action=APPROVE count=2` |
| `inactiveAndDeletedDuplicatesAreAllowed` | one active, one inactive, one deleted per lifecycle target | Flyway succeeds |
| `unrelatedTargetsAreUnaffected` | two active WORK_ORDER templates | Flyway succeeds and both remain active |
| `duplicateActivationIsRejectedAfterMigration` | one active and one inactive exact lifecycle template | activating the second throws SQLState `23505` |
| `concurrentActivationLeavesAtMostOneActiveTemplate` | two inactive rows updated in separate connections after one latch | exactly one commit succeeds and active count is one |

Before the migration exists, run the PostgreSQL class and confirm at least the duplicate-activation assertion fails because no unique index exists.

- [ ] **Step 6: Run migration tests and verify GREEN**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=LifecycleApprovalTemplateUniquenessMigrationContractTest,LifecycleApprovalTemplateUniquenessMigrationPostgresTest test
```

Expected: PASS; Docker-backed tests execute rather than skip.

- [ ] **Step 7: Commit the migration slice**

```bash
git add docs/runbooks/lifecycle-approval-template-uniqueness-preflight.md \
  src/main/resources/db/migration/V20260716_1__unique_active_lifecycle_approval_templates.sql \
  src/test/java/com/toir/migration/LifecycleApprovalTemplateUniquenessMigrationContractTest.java \
  src/test/java/com/toir/migration/LifecycleApprovalTemplateUniquenessMigrationPostgresTest.java
git commit -m "feat: enforce unique lifecycle approval templates"
```

---

### Task 2: Shared Lifecycle Template and Runtime Policy

**Files:**
- Create: `src/main/java/com/toir/service/approval/LifecycleApprovalRoutePolicy.java`
- Create: `src/test/java/com/toir/service/approval/LifecycleApprovalRoutePolicyTest.java`

**Interfaces:**
- Produces: `boolean supports(ApprovalTargetType, ApprovalActionType)`.
- Produces: `ValidationResult validateTemplate(ApprovalTemplate)`.
- Produces: `ValidationResult validateRuntime(ApprovalRequest)`.
- Produces: `ValidationResult validateDecision(ApprovalRequest, ApprovalStep, UUID, ApprovalDecision, boolean)`.
- Produces: `ValidationResult validateCompletion(ApprovalRequest)`.
- Produces: immutable `List<RouteStep> orderedSteps()` in successful template results.

- [ ] **Step 1: Write parameterized failing policy tests**

Use real entity objects, not mocks. Define fixture helpers `template(RouteStep...)`, `pendingRequest(RouteStep...)`, `approvedStep(order, actor)`, and `pendingStep(order, assignment)` in the test class, then cover these exact neutral reasons:

```java
enum Reason {
    VALID, UNSUPPORTED_TARGET_ACTION, NO_ACTIVE_TEMPLATE,
    MULTIPLE_ACTIVE_TEMPLATES, EMPTY_ROUTE, NONPOSITIVE_ORDER,
    DUPLICATE_ORDER, NONCONTIGUOUS_ORDER, INVALID_ASSIGNMENT,
    DUPLICATE_EXPLICIT_APPROVER, REQUEST_STATUS_INCONSISTENT,
    CURRENT_STEP_INVALID, PRIOR_STEP_INCOMPLETE, DECISION_ACTOR_MISSING,
    REQUESTER_DECISION, REPEATED_APPROVING_ACTOR, ACTOR_INELIGIBLE,
    CURRENT_STEP_ONLY, RUNTIME_INCOMPLETE
}
```

Tests must include:

```java
@ParameterizedTest
@ValueSource(ints = {1, 2, 7, 11})
void acceptsAnyContiguousTemplateLength(int count) {
    ApprovalTemplate template = roleTemplate(count, "APPROVER");
    ValidationResult result = policy.validateTemplate(template);
    assertThat(result.valid()).isTrue();
    assertThat(result.orderedSteps()).extracting(RouteStep::order)
            .containsExactlyElementsOf(IntStream.rangeClosed(1, count).boxed().toList());
}

@Test
void blocksSecondApproveBySameActorButAllowsEligibleReject() {
    UUID requester = UUID.randomUUID();
    UUID actor = UUID.randomUUID();
    ApprovalRequest request = pendingRequest(requester,
            approvedStep(1, actor), pendingRoleStep(2, "APPROVER"));
    ApprovalStep current = request.getSteps().get(1);
    assertThat(policy.validateDecision(request, current, actor,
            ApprovalDecision.APPROVED, true).reason()).isEqualTo(REPEATED_APPROVING_ACTOR);
    assertThat(policy.validateDecision(request, current, actor,
            ApprovalDecision.REJECTED, true).reason()).isEqualTo(VALID);
}
```

- [ ] **Step 2: Run the policy test and verify RED**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=LifecycleApprovalRoutePolicyTest test
```

Expected: compilation failure because `LifecycleApprovalRoutePolicy` does not exist.

- [ ] **Step 3: Implement the focused policy API**

```java
@Component
public final class LifecycleApprovalRoutePolicy {
    private static final Set<ApprovalTargetType> TARGETS = EnumSet.of(
            ApprovalTargetType.REPAIR_CAMPAIGN,
            ApprovalTargetType.PLANNED_SHUTDOWN);

    public record RouteStep(int order, UUID approverId, String approverRole) {}
    public record ValidationResult(boolean valid, Reason reason, List<RouteStep> orderedSteps) {
        public ValidationResult {
            orderedSteps = orderedSteps == null ? List.of() : List.copyOf(orderedSteps);
        }
        static ValidationResult valid(List<RouteStep> steps) {
            return new ValidationResult(true, Reason.VALID, steps);
        }
        static ValidationResult invalid(Reason reason) {
            return new ValidationResult(false, reason, List.of());
        }
    }

    public boolean supports(ApprovalTargetType target, ApprovalActionType action) {
        return TARGETS.contains(target) && effectiveAction(action) == ApprovalActionType.APPROVE;
    }

    public ValidationResult validateTemplate(ApprovalTemplate template) {
        if (template == null || !supports(template.getTargetType(), template.getActionType())) {
            return ValidationResult.invalid(Reason.UNSUPPORTED_TARGET_ACTION);
        }
        List<RouteStep> route = template.getSteps().stream()
                .filter(step -> step != null && !step.isDeleted())
                .map(step -> new RouteStep(step.getStepOrder(), step.getApproverId(), step.getApproverRole()))
                .sorted(Comparator.comparingInt(RouteStep::order))
                .toList();
        return validateOrdered(route, true);
    }

    public ValidationResult validateRuntime(ApprovalRequest request) {
        if (request == null || !supports(request.getTargetType(), request.getActionType())) {
            return ValidationResult.invalid(Reason.UNSUPPORTED_TARGET_ACTION);
        }
        List<RouteStep> route = activeRuntimeSteps(request).stream()
                .map(step -> new RouteStep(step.getStepNumber(), step.getApproverId(), step.getApproverRole()))
                .toList();
        ValidationResult structure = validateOrdered(route, false);
        return structure.valid() ? validateRuntimeState(request) : structure;
    }
    public ValidationResult validateDecision(ApprovalRequest request, ApprovalStep step, UUID actor,
                                             ApprovalDecision outcome, boolean assignmentSatisfied) {
        ValidationResult runtime = validateRuntime(request);
        if (!runtime.valid()) return runtime;
        if (step == null || step.getStepNumber() != request.getCurrentStep())
            return ValidationResult.invalid(Reason.CURRENT_STEP_ONLY);
        if (!assignmentSatisfied) return ValidationResult.invalid(Reason.ACTOR_INELIGIBLE);
        if (Objects.equals(actor, request.getRequesterId()))
            return ValidationResult.invalid(Reason.REQUESTER_DECISION);
        if (outcome == ApprovalDecision.APPROVED && approvedActors(request).contains(actor))
            return ValidationResult.invalid(Reason.REPEATED_APPROVING_ACTOR);
        return ValidationResult.valid(runtime.orderedSteps());
    }

    public ValidationResult validateCompletion(ApprovalRequest request) {
        ValidationResult runtime = validateRuntime(request);
        if (!runtime.valid()) return runtime;
        return completionEvidenceIsValid(request)
                ? ValidationResult.valid(runtime.orderedSteps())
                : ValidationResult.invalid(Reason.RUNTIME_INCOMPLETE);
    }
}
```

Implement `validateOrdered(route, rejectDuplicateExplicitIds)`, `activeRuntimeSteps`, `validateRuntimeState`, `approvedActors`, and `completionEvidenceIsValid` as private methods in the same class. Normalize blank roles to null, enforce assignment XOR and contiguous `1..N`, and reject duplicate explicit IDs for templates. Sort copied lists only. For runtime `APPROVED`, require all steps approved; for `PENDING`, require a valid pending current step and approved prior steps; terminal rejected/cancelled routes remain structurally readable but non-actionable.

- [ ] **Step 4: Run the policy tests and verify GREEN**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=LifecycleApprovalRoutePolicyTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the shared policy**

```bash
git add src/main/java/com/toir/service/approval/LifecycleApprovalRoutePolicy.java \
  src/test/java/com/toir/service/approval/LifecycleApprovalRoutePolicyTest.java
git commit -m "feat: add lifecycle approval route policy"
```

---

### Task 3: Exact Active Template Resolution and Frozen Routes

**Files:**
- Create: `src/main/java/com/toir/service/approval/LifecycleRouteResolution.java`
- Modify: `src/main/java/com/toir/service/approval/ApprovalRouteResolver.java`
- Modify: `src/main/java/com/toir/service/approval/DefaultApprovalRouteResolver.java`
- Modify: `src/main/java/com/toir/repository/ApprovalTemplateRepository.java`
- Modify: `src/test/java/com/toir/service/approval/DefaultApprovalRouteResolverTest.java`

**Interfaces:**
- Produces: `LifecycleRouteResolution resolveLifecycleRoute(ApprovalTargetType, ApprovalActionType)`.
- Produces: a copied immutable `List<CreateApprovalRequest.StepInput>` only when one exact active template is valid.
- Preserves: existing `resolveRoute(ApprovalRequest)` behavior for unrelated targets.

- [ ] **Step 1: Add failing resolver tests**

Use these exact resolver assertions:

| Test | Assertion |
|---|---|
| `noExactTemplateReturnsNoActiveTemplate` | `resolved=false`, reason `NO_ACTIVE_TEMPLATE`, empty steps |
| `multipleExactTemplatesReturnAmbiguousWithoutChoosingLatest` | reason `MULTIPLE_ACTIVE_TEMPLATES`; neither template is converted |
| `oneTemplateFreezesOrderedRoleAndExplicitAssignments` | inputs are ordered `1..N` and retain assignment XOR |
| `malformedTemplateReturnsPolicyReason` | reason `NONCONTIGUOUS_ORDER` |
| `lifecycleResolutionNeverCallsTargetOnlyOrPermissionFallback` | Mockito verifies the target-only repository method is never invoked |
| `unrelatedTargetKeepsExistingGenericResolution` | existing WORK_ORDER fallback result remains unchanged |

- [ ] **Step 2: Run resolver tests and verify RED**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=DefaultApprovalRouteResolverTest test
```

Expected: FAIL because typed lifecycle resolution and all-exact-template loading do not exist.

- [ ] **Step 3: Add the typed resolution record and repository query**

```java
public record LifecycleRouteResolution(
        List<CreateApprovalRequest.StepInput> steps,
        LifecycleApprovalRoutePolicy.Reason reason
) {
    public LifecycleRouteResolution {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
    public boolean resolved() { return reason == LifecycleApprovalRoutePolicy.Reason.VALID; }
}
```

Add an `@EntityGraph(attributePaths = "steps")` repository method returning every exact active template with deterministic `ORDER BY createdAt DESC, id DESC`; do not use `findFirst...` for lifecycle resolution.

- [ ] **Step 4: Implement exact lifecycle resolution**

Add to `ApprovalRouteResolver`:

```java
LifecycleRouteResolution resolveLifecycleRoute(
        ApprovalTargetType targetType,
        ApprovalActionType actionType);
```

In `DefaultApprovalRouteResolver`, return `NO_ACTIVE_TEMPLATE` for zero, `MULTIPLE_ACTIVE_TEMPLATES` for more than one, or validate the sole template through `LifecycleApprovalRoutePolicy`. Convert its ordered `RouteStep` values without changing role/explicit assignments:

```java
new CreateApprovalRequest.StepInput(step.approverId(), step.approverRole())
```

Keep generic `resolveRoute` for unrelated targets and route lifecycle callers through the typed method.

- [ ] **Step 5: Run resolver and policy tests**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=DefaultApprovalRouteResolverTest,LifecycleApprovalRoutePolicyTest test
```

Expected: PASS.

- [ ] **Step 6: Commit the resolver slice**

```bash
git add src/main/java/com/toir/service/approval/LifecycleRouteResolution.java \
  src/main/java/com/toir/service/approval/ApprovalRouteResolver.java \
  src/main/java/com/toir/service/approval/DefaultApprovalRouteResolver.java \
  src/main/java/com/toir/repository/ApprovalTemplateRepository.java \
  src/test/java/com/toir/service/approval/DefaultApprovalRouteResolverTest.java
git commit -m "feat: resolve exact lifecycle approval templates"
```

---

### Task 4: Variable Template Administration and Controlled Cardinality Conflicts

**Files:**
- Modify: `src/main/java/com/toir/service/approval/ApprovalRuleService.java`
- Modify: `src/test/java/com/toir/service/approval/ApprovalRuleServiceTest.java`
- Modify: `src/test/java/com/toir/controller/ApprovalRulesControllerTest.java`

**Interfaces:**
- Consumes: `LifecycleApprovalRoutePolicy.validateTemplate` semantics.
- Produces: variable `1..N` lifecycle template create/update/activation.
- Produces: controlled `409 MULTIPLE_ACTIVE_TEMPLATES` on service or unique-index conflict.
- Preserves: explicit approver ID and role assignment XOR.

- [ ] **Step 1: Add failing template service tests**

Implement one parameterized `activeLifecycleTemplateAllowsVariableStepCounts` test for `{1,2,7,9}` that captures the saved template and asserts the same count/order. Add named tests asserting: one `SYSTEM_ADMIN` role step saves; repeated roles save; a USER step retains `approverId` and null role; duplicate explicit IDs and assignment XOR map to `APPROVAL_TEMPLATE_STEPS_INVALID`; a second active exact template and the named unique-index violation map to `MULTIPLE_ACTIVE_TEMPLATES`; WORK_ORDER keeps its existing behavior.

- [ ] **Step 2: Run template tests and verify RED**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=ApprovalRuleServiceTest,ApprovalRulesControllerTest test
```

Expected: FAIL because the service currently requires role-only steps and does not apply shared lifecycle validation/cardinality mapping.

- [ ] **Step 3: Make request-to-entity mapping lossless**

Use the DTO’s `approverType`, `approverId`, and `approverRole` without forcing roles:

```java
step.setApproverId(stepRequest.approverType() == ApprovalRuleDto.ApproverType.USER
        ? stepRequest.approverId() : null);
step.setApproverRole(stepRequest.approverType() == ApprovalRuleDto.ApproverType.ROLE
        ? normalizedRole(stepRequest.approverRole()) : null);
```

Validate exactly one assignment and contiguous request order before changing persisted rows. For a lifecycle active result, build a detached candidate template, run the shared policy, and map any invalid result to `APPROVAL_TEMPLATE_STEPS_INVALID`.

- [ ] **Step 4: Enforce service cardinality and map the DB backstop**

Before activation, load all other active exact templates and reject nonempty results with `MULTIPLE_ACTIVE_TEMPLATES`. Retain the partial unique index as the concurrency backstop. In `DataIntegrityViolationException` handling, map constraint `uq_active_lifecycle_approval_template` to the same controlled conflict.

- [ ] **Step 5: Run template tests and verify GREEN**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=ApprovalRuleServiceTest,ApprovalRulesControllerTest,LifecycleApprovalRoutePolicyTest test
```

Expected: PASS.

- [ ] **Step 6: Commit the administration slice**

```bash
git add src/main/java/com/toir/service/approval/ApprovalRuleService.java \
  src/test/java/com/toir/service/approval/ApprovalRuleServiceTest.java \
  src/test/java/com/toir/controller/ApprovalRulesControllerTest.java
git commit -m "feat: allow variable lifecycle approval templates"
```

---

### Task 5: Runtime Start Planning and Template-Independent Pending Reuse

**Files:**
- Create: `src/main/java/com/toir/service/approval/LifecycleApprovalStartPlan.java`
- Modify: `src/main/java/com/toir/service/ApprovalService.java`
- Modify: `src/main/java/com/toir/repository/ApprovalRequestRepository.java`
- Modify: `src/test/java/com/toir/service/ApprovalServiceTest.java`

**Interfaces:**
- Produces: `LifecycleApprovalStartPlan planLifecycleApproval(target, targetId, action, domainPending, currentPayload)`.
- Produces: `ApprovalRequestDto materializeLifecycleApproval(plan, requesterId, title, description, payloadAfterFlush)`.
- Guarantees: pending reuse is decided before template resolution and does not compare with current templates.

- [ ] **Step 1: Add failing planning/reuse tests**

Add tests named `compatiblePendingIsReusedWhenNoActiveTemplateExists`, `compatiblePendingIsReusedWhenMultipleTemplatesExist`, `editedOrDeactivatedTemplateDoesNotChangePendingSteps`, `newRequestResolvesAndFreezesTemplateExactlyOnce`, `pendingRequestInReadinessDomainStateIsConflict`, `malformedPendingRuntimeRouteIsRouteStaleNotTemplateMismatch`, and `duplicatePendingRequestsAreCancelledExceptOneCompatibleNewest`. Reuse returns the same request ID and `verifyNoInteractions(routeResolver)` for zero/multiple/current-template-change cases; every original step ID/order/assignment is unchanged; new creation calls `resolveLifecycleRoute` exactly once and materialization performs no resolver call; readiness plus pending returns `REQUEST_STATUS_INCONSISTENT`; malformed persisted data maps to route-stale; deterministic duplicate handling preserves the newest compatible request and cancels every other pending request with history.

- [ ] **Step 2: Run focused ApprovalService tests and verify RED**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=ApprovalServiceTest test
```

Expected: FAIL because the plan/materialize API does not exist and existing reuse runs fixed-route validation.

- [ ] **Step 3: Add the immutable start plan**

```java
public record LifecycleApprovalStartPlan(
        ApprovalTargetType targetType,
        UUID targetId,
        ApprovalActionType actionType,
        ApprovalRequest reusableRequest,
        List<CreateApprovalRequest.StepInput> frozenSteps,
        LifecycleApprovalRoutePolicy.Reason failure
) {
    public LifecycleApprovalStartPlan {
        frozenSteps = frozenSteps == null ? List.of() : List.copyOf(frozenSteps);
    }
    public boolean reusable() { return reusableRequest != null; }
    public boolean creatable() { return failure == LifecycleApprovalRoutePolicy.Reason.VALID && !reusable(); }
}
```

- [ ] **Step 4: Implement plan-before-materialize orchestration**

`planLifecycleApproval` must:

1. require a supported lifecycle pair;
2. acquire the existing PostgreSQL advisory target/action transaction lock;
3. load pending requests ordered `created_at DESC, id DESC`;
4. if a pending request exists while `domainPending=false`, return `REQUEST_STATUS_INCONSISTENT`;
5. when `domainPending=true`, reuse the first pending request whose payload equals `currentPayload` and whose persisted runtime route validates;
6. cancel only real duplicate or scope-stale pending requests, preserving steps/history;
7. call `resolveLifecycleRoute` only when no reusable request exists.

`materializeLifecycleApproval` must return the reusable request unchanged or create one request from `frozenSteps` and caller-supplied post-flush payload. It must not call the resolver.

- [ ] **Step 5: Replace fixed route-stale checks for lifecycle requests**

Route staleness for the two pairs becomes:

```java
private boolean lifecycleRouteStale(ApprovalRequest request) {
    return lifecyclePolicy.supports(effectiveTargetType(request), request.getActionType())
            && !lifecyclePolicy.validateRuntime(request).valid();
}
```

Do not consult `RepairCampaignApprovalRouteValidator`, `PlannedShutdownApprovalRouteValidator`, or current templates.

- [ ] **Step 6: Run focused tests and verify GREEN**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=ApprovalServiceTest,DefaultApprovalRouteResolverTest,LifecycleApprovalRoutePolicyTest test
```

Expected: PASS.

- [ ] **Step 7: Commit the planning/reuse slice**

```bash
git add src/main/java/com/toir/service/approval/LifecycleApprovalStartPlan.java \
  src/main/java/com/toir/service/ApprovalService.java \
  src/main/java/com/toir/repository/ApprovalRequestRepository.java \
  src/test/java/com/toir/service/ApprovalServiceTest.java
git commit -m "feat: preserve pending lifecycle approval snapshots"
```

---

### Task 6: Atomic Repair Campaign and Planned Shutdown Request Approval

**Files:**
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignApprovalPolicy.java`
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignService.java`
- Modify: `src/main/java/com/toir/service/PlannedShutdownService.java`
- Modify: `src/test/java/com/toir/service/RepairCampaignServiceTest.java`
- Modify: `src/test/java/com/toir/service/PlannedShutdownServiceTest.java`

**Interfaces:**
- Consumes: Task 5 plan/materialize API.
- Guarantees: domain row lock → approval target/action lock.
- Guarantees: template resolution/validation occurs before first domain mutation for a new request.

- [ ] **Step 1: Add failing request-order and rollback tests**

For Repair Campaign, use Mockito `InOrder` to prove:

```java
inOrder.verify(repository).findLockedByIdAndIsDeletedFalse(campaignId);
inOrder.verify(approvalService).planLifecycleApproval(
        eq(ApprovalTargetType.REPAIR_CAMPAIGN), eq(campaignId),
        eq(ApprovalActionType.APPROVE), eq(false), isNull());
inOrder.verify(repository).saveAndFlush(domain);
inOrder.verify(approvalService).materializeLifecycleApproval(
        eq(plan), any(UUID.class), anyString(), any(), anyString());
```

Use the Planned Shutdown repository’s `findByIdAndIsDeletedFalseForUpdate(shutdownId)` in its equivalent `InOrder` assertion. Add tests for zero template, invalid template, and multiple templates asserting the domain entity remains in its readiness status with null approval snapshot and no runtime save. Add a pending-state compatible reuse test that does not require a template.

- [ ] **Step 2: Run both domain service tests and verify RED**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=RepairCampaignServiceTest,PlannedShutdownServiceTest test
```

Expected: FAIL because current services mutate/flush before route resolution and call the generic start API.

- [ ] **Step 3: Refactor Repair Campaign request ordering**

After `getLockedOrThrow`, version/scope checks, and prospective hash calculation:

```java
LifecycleApprovalStartPlan plan = approvalService.planLifecycleApproval(
        ApprovalTargetType.REPAIR_CAMPAIGN,
        campaign.getId(),
        ApprovalActionType.APPROVE,
        campaign.getStatus() == RepairCampaignStatus.PENDING_APPROVAL,
        campaign.getStatus() == RepairCampaignStatus.PENDING_APPROVAL
                ? RepairCampaignApprovalPolicy.payload(campaign) : null);
approvalPolicy.requireStartPlan(plan);
if (plan.reusable()) return toDto(campaign);

approvalPolicy.prepareRequest(campaign, expectedScopeVersion);
RepairCampaign saved = repository.saveAndFlush(campaign);
approvalService.materializeLifecycleApproval(
        plan, requireApprovalRequester(), approvalTitle(saved), comment,
        RepairCampaignApprovalPolicy.payload(saved));
```

Add `requireApprovalRequester()` in `RepairCampaignService`; it returns `scopeAccessService.currentUserIdOrNull()` or throws `Authenticated requester is required`. Keep this in one default REQUIRED transaction. `requireStartPlan` maps neutral reasons to the three approved template errors.

- [ ] **Step 4: Refactor Planned Shutdown request ordering**

Calculate the prospective scope hash without mutating the entity, plan first, then write approved window/scope/status, `saveAndFlush`, and materialize using the frozen route and final payload. Allow consistent `PENDING_APPROVAL` calls to reuse; reject a pending request found in readiness state as a conflict.

- [ ] **Step 5: Run request tests and verify GREEN**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=RepairCampaignServiceTest,PlannedShutdownServiceTest,ApprovalServiceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit atomic request orchestration**

```bash
git add src/main/java/com/toir/service/repair/RepairCampaignApprovalPolicy.java \
  src/main/java/com/toir/service/repair/RepairCampaignService.java \
  src/main/java/com/toir/service/PlannedShutdownService.java \
  src/test/java/com/toir/service/RepairCampaignServiceTest.java \
  src/test/java/com/toir/service/PlannedShutdownServiceTest.java
git commit -m "feat: create lifecycle approvals atomically"
```

---

### Task 7: Current-Step Eligibility, Separation of Duty, and Authoritative Flags

**Files:**
- Modify: `src/main/java/com/toir/service/ApprovalService.java`
- Modify: `src/main/java/com/toir/service/ApprovalScopeService.java`
- Modify: `src/test/java/com/toir/service/ApprovalServiceTest.java`
- Modify: `src/test/java/com/toir/security/ApprovalPbacScopeTest.java`
- Modify: `src/test/java/com/toir/controller/ApprovalControllerTest.java`

**Interfaces:**
- Consumes: shared `validateDecision`.
- Produces: outcome-specific eligibility for approve and reject.
- Produces: backend `canApprove`, `canReject`, `canCancel` without lifecycle wildcard substitution.

- [ ] **Step 1: Add failing decision and DTO flag tests**

Add named tests for requester approve and reject (`403`), repeated-actor approve (`403`), eligible repeated-actor reject (`REJECTED`), wrong role and explicit ID (`403`), wildcard-only admin (`canApprove=false`), explicitly configured `SYSTEM_ADMIN` (`canApprove=true` when nonrequester and unused actor), separate cancellation permission, and unchanged unrelated-target wildcard behavior.

- [ ] **Step 2: Run decision/security tests and verify RED**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=ApprovalServiceTest,ApprovalPbacScopeTest,ApprovalControllerTest test
```

Expected: FAIL on generalized runtime-step SoD and outcome-specific reject behavior.

- [ ] **Step 3: Remove fixed-role SoD from ApprovalScopeService**

Delete Production/HSE and Repair Campaign discipline-list branches. Keep document-scope checks. Let `ApprovalService` compute exact assignment satisfaction and pass it with the outcome into the shared policy.

For lifecycle role steps, assignment satisfaction is exact role membership/authority only:

```java
private boolean lifecycleRoleSatisfied(UUID actorId, String configuredRole) {
    return isActiveUserWithRole(actorId, configuredRole)
            || (matchesAuthenticatedPrincipal(actorId)
                && currentAuthenticationHasRole(configuredRole));
}
```

Do not call a permission wildcard fallback for a different configured role.

- [ ] **Step 4: Make decision validation outcome-specific**

Before mutating a step:

```java
ValidationResult decision = lifecyclePolicy.validateDecision(
        request, current, actorId, outcome, assignmentSatisfied);
if (!decision.valid()) throw mapDecisionFailure(decision.reason());
```

Run the repeated-approved-actor check only for `ApprovalDecision.APPROVED`. Both approve and reject enforce requester exclusion, current step, prior-step consistency, and exact assignment.

- [ ] **Step 5: Compute authoritative actionability flags with the same functions**

Use the current authenticated actor and current domain-scope check to independently compute approve and reject. `canCancel` continues through cancellation permission/lifecycle logic. A malformed runtime route or non-pending request makes all actions false.

- [ ] **Step 6: Run decision/security tests and verify GREEN**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=ApprovalServiceTest,ApprovalPbacScopeTest,ApprovalControllerTest,LifecycleApprovalRoutePolicyTest test
```

Expected: PASS.

- [ ] **Step 7: Commit eligibility and actionability**

```bash
git add src/main/java/com/toir/service/ApprovalService.java \
  src/main/java/com/toir/service/ApprovalScopeService.java \
  src/test/java/com/toir/service/ApprovalServiceTest.java \
  src/test/java/com/toir/security/ApprovalPbacScopeTest.java \
  src/test/java/com/toir/controller/ApprovalControllerTest.java
git commit -m "feat: enforce lifecycle approval separation of duty"
```

---

### Task 8: Repair Campaign Runtime Completion and Atomic Finalization

**Files:**
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignApprovalPolicy.java`
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignService.java`
- Modify: `src/main/java/com/toir/service/approval/RepairCampaignApprovalHandler.java`
- Modify: `src/main/java/com/toir/service/ApprovalService.java`
- Modify: `src/test/java/com/toir/service/repair/RepairCampaignApprovalPolicyTest.java`
- Modify: `src/test/java/com/toir/service/RepairCampaignServiceTest.java`
- Modify: `src/test/java/com/toir/service/ApprovalServiceTest.java`

**Interfaces:**
- Consumes: `LifecycleApprovalRoutePolicy.validateCompletion`.
- Guarantees: final step → request APPROVED → domain APPROVED in one transaction.
- Removes: all seven-role/count/order completion assumptions.

- [ ] **Step 1: Add failing one-step, seven-step, and inconsistency tests**

Add named tests for one-step `SYSTEM_ADMIN`, seven-step, and arbitrary three-step finalization, each asserting campaign `APPROVED`. Add a test with `verifyNoInteractions(routeResolver)` during finalization. Add parameterized scope version/hash/payload mismatch cases asserting the existing exact 409 codes, plus malformed and incomplete persisted runtime cases asserting route-stale and incomplete codes.

- [ ] **Step 2: Run Repair Campaign tests and verify RED**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=RepairCampaignApprovalPolicyTest,RepairCampaignServiceTest,ApprovalServiceTest test
```

Expected: FAIL because the current policy requires the canonical seven-discipline route.

- [ ] **Step 3: Replace fixed completion with shared runtime completion**

Keep status, target/action, scope version, current hash, and payload checks. Replace `validateCompleted`/`completedSevenDisciplineRoute` behavior with:

```java
ValidationResult result = lifecyclePolicy.validateCompletion(request);
if (!result.valid()) {
    throw mapRuntimeCompletionFailure(result.reason());
}
```

Remove public constants and helper methods that imply a seven-role business contract.

- [ ] **Step 4: Enforce terminal ordering in ApprovalService**

For the final approved step, mark the step first, validate all persisted steps, mark request `APPROVED`, and then call the handler. Do not catch and convert lifecycle domain finalization exceptions to `FAILED`; rethrow them so the REQUIRED transaction rolls back. Keep existing non-lifecycle failure semantics unchanged.

- [ ] **Step 5: Run Repair Campaign tests and verify GREEN**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=RepairCampaignApprovalPolicyTest,RepairCampaignServiceTest,ApprovalServiceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit Repair Campaign finalization**

```bash
git add src/main/java/com/toir/service/repair/RepairCampaignApprovalPolicy.java \
  src/main/java/com/toir/service/repair/RepairCampaignService.java \
  src/main/java/com/toir/service/approval/RepairCampaignApprovalHandler.java \
  src/main/java/com/toir/service/ApprovalService.java \
  src/test/java/com/toir/service/repair/RepairCampaignApprovalPolicyTest.java \
  src/test/java/com/toir/service/RepairCampaignServiceTest.java \
  src/test/java/com/toir/service/ApprovalServiceTest.java
git commit -m "feat: finalize repair campaigns from runtime steps"
```

---

### Task 9: Planned Shutdown Generic Runtime Evidence, Finalization, and Preparation

**Files:**
- Modify: `src/main/java/com/toir/repository/ApprovalRequestRepository.java`
- Modify: `src/main/java/com/toir/service/PlannedShutdownService.java`
- Modify: `src/main/java/com/toir/service/approval/PlannedShutdownApprovalHandler.java`
- Modify: `src/test/java/com/toir/service/PlannedShutdownServiceTest.java`
- Modify: `src/test/java/com/toir/service/PlannedShutdownLifecycleServiceTest.java`

**Interfaces:**
- Produces: deterministic newest current-scope approved runtime evidence.
- Removes: Production/HSE-specific evidence and blocker requirements.

- [ ] **Step 1: Add failing generic-evidence tests**

Add named tests for Production/HSE two-step, arbitrary three-role, and one-step explicit routes, each asserting finalization and preparation. Seed ties to prove `created_at DESC, id DESC`; seed a newer other-scope approval and assert the current-scope request wins; assert malformed/incomplete current-scope evidence cannot prepare; verify the resolver is never called during preparation/finalization.

- [ ] **Step 2: Run Planned Shutdown tests and verify RED**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=PlannedShutdownServiceTest,PlannedShutdownLifecycleServiceTest test
```

Expected: FAIL because current evidence requires exact Production and HSE roles.

- [ ] **Step 3: Add deterministic approved-request loading**

Add a repository query ordered by `created_at DESC, id DESC` for exact target/action/status. In the service, filter by current scope payload and shared runtime completion; do not inspect template data.

- [ ] **Step 4: Replace role-specific evidence**

Replace `ApprovalEvidence`, `approvedSeparatedStep`, and Production/HSE blocker codes with a generic result:

```java
private record RuntimeApprovalEvidence(
        boolean currentScope,
        boolean structurallyValid,
        boolean complete,
        ApprovalRequest request) {}
```

`prepare` requires current scope, structural validity, and completion. `finalizeApprovalFromApprovalRequest` validates the supplied request directly against target, scope payload, and shared completion.

- [ ] **Step 5: Run Planned Shutdown tests and verify GREEN**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=PlannedShutdownServiceTest,PlannedShutdownLifecycleServiceTest,ApprovalServiceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit Planned Shutdown finalization**

```bash
git add src/main/java/com/toir/repository/ApprovalRequestRepository.java \
  src/main/java/com/toir/service/PlannedShutdownService.java \
  src/main/java/com/toir/service/approval/PlannedShutdownApprovalHandler.java \
  src/test/java/com/toir/service/PlannedShutdownServiceTest.java \
  src/test/java/com/toir/service/PlannedShutdownLifecycleServiceTest.java
git commit -m "feat: finalize shutdowns from runtime approval steps"
```

---

### Task 10: Scope Mutation Cancellation, History Preservation, and Re-request

**Files:**
- Modify: `src/main/java/com/toir/service/ApprovalService.java`
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignMutationImpactService.java`
- Modify: `src/main/java/com/toir/service/PlannedShutdownService.java`
- Modify: `src/test/java/com/toir/service/repair/RepairCampaignMutationImpactServiceTest.java`
- Modify: `src/test/java/com/toir/service/PlannedShutdownServiceTest.java`
- Modify: `src/test/java/com/toir/service/ApprovalServiceTest.java`

**Interfaces:**
- Produces: `cancelPendingLifecycleApproval(target, targetId, reason)` after the caller holds the domain lock.
- Guarantees: cancellation and domain scope reset commit together.

- [ ] **Step 1: Add failing mutation tests**

Add named tests that assert Repair Campaign and Planned Shutdown scope mutations set the pending request to `CANCELLED`, leave step rows unchanged, add one cancellation history row, clear domain approval snapshot, and reset readiness. Assert no resolver/materializer call during mutation; an explicit later request snapshots the then-active template; a template-only edit never invokes cancellation.

- [ ] **Step 2: Run mutation tests and verify RED**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=RepairCampaignMutationImpactServiceTest,PlannedShutdownServiceTest,ApprovalServiceTest test
```

Expected: FAIL because Repair Campaign cancels directly without governance history and Planned Shutdown scope resets do not consistently cancel the pending request.

- [ ] **Step 3: Centralize lifecycle pending cancellation**

Implement in `ApprovalService`:

```java
@Transactional(propagation = Propagation.REQUIRED)
public void cancelPendingLifecycleApproval(
        ApprovalTargetType targetType, UUID targetId, String reason) {
    lockApprovalTargetAction(targetType.name(), targetId, ApprovalActionType.APPROVE);
    findPendingApprovals(targetType, targetType.name(), targetId, ApprovalActionType.APPROVE)
            .forEach(request -> cancelPreservingHistory(request, reason));
}
```

Do not clear steps, delete history, or resolve a template.

- [ ] **Step 4: Call cancellation after the domain row lock and before snapshot reset**

Repair Campaign mutation impact and Planned Shutdown approval-relevant mutation paths must call the method before clearing approval scope/version/hash and setting readiness status. Preserve the existing domain-specific readiness target.

- [ ] **Step 5: Run mutation tests and verify GREEN**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=RepairCampaignMutationImpactServiceTest,PlannedShutdownServiceTest,ApprovalServiceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit scope invalidation**

```bash
git add src/main/java/com/toir/service/ApprovalService.java \
  src/main/java/com/toir/service/repair/RepairCampaignMutationImpactService.java \
  src/main/java/com/toir/service/PlannedShutdownService.java \
  src/test/java/com/toir/service/repair/RepairCampaignMutationImpactServiceTest.java \
  src/test/java/com/toir/service/PlannedShutdownServiceTest.java \
  src/test/java/com/toir/service/ApprovalServiceTest.java
git commit -m "feat: invalidate lifecycle approvals on scope changes"
```

---

### Task 11: Real Transaction Rollback and Concurrency Integration Tests

**Files:**
- Create: `src/test/java/com/toir/integration/LifecycleApprovalFinalizationRollbackIntegrationTest.java`
- Create: `src/test/java/com/toir/integration/LifecycleApprovalConcurrencyIntegrationTest.java`

**Interfaces:**
- Verifies: actual Spring transaction rollback, not Mockito state.
- Verifies: database uniqueness and advisory locking under concurrency.

- [ ] **Step 1: Add the failing real rollback integration test**

Use `@SpringBootTest`, PostgreSQL Testcontainers, and real repositories. Seed a pending lifecycle request whose final step is pending. Add a test-only primary `ApprovalActionExecutor` that invokes the real Repair Campaign finalizer and then throws, proving rollback even after the domain entity was mutated:

```java
@TestConfiguration
static class FailingFinalizerConfiguration {
    @Bean
    @Primary
    ApprovalActionExecutor failingLifecycleExecutor(RepairCampaignService campaigns) {
        return request -> {
            campaigns.finalizeApprovalFromApprovalRequest(request);
            throw RestException.conflict("FORCED_DOMAIN_FINALIZATION_FAILURE");
        };
    }
}
```

```java
assertThatThrownBy(() -> approvalService.approveStep(requestId, finalStepId, decision))
        .hasMessageContaining("FORCED_DOMAIN_FINALIZATION_FAILURE");
entityManager.clear();
assertThat(entityManager.find(ApprovalStep.class, finalStepId).getDecision())
        .isEqualTo(ApprovalDecision.PENDING);
assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
        .isEqualTo(ApprovalStatus.PENDING);
assertThat(repairCampaignRepository.findById(campaignId).orElseThrow().getStatus())
        .isEqualTo(RepairCampaignStatus.PENDING_APPROVAL);
```

- [ ] **Step 2: Run the rollback test and verify RED**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=LifecycleApprovalFinalizationRollbackIntegrationTest test
```

Expected: FAIL if the finalizer exception is converted to `FAILED` or any final step/request/domain change commits.

- [ ] **Step 3: Make only the minimal transaction-boundary correction**

If Task 8 did not already make the test green, ensure lifecycle finalizer exceptions are propagated from `executeTerminalAction` and no lifecycle handler/method uses `REQUIRES_NEW`. Do not change unrelated target failure handling.

- [ ] **Step 4: Add concurrency integration tests**

Use two executor threads, a `CountDownLatch`, and independent transactions:

```java
@Test void concurrentRequestApprovalCreatesAtMostOnePendingRequest();
@Test void concurrentTemplateActivationLeavesAtMostOneActiveExactTemplate();
@Test void uniqueViolationNeverLeaksRawSqlException();
```

The first test asserts pending count is exactly one and both callers return the same request or one receives a controlled conflict. The second asserts active exact count is one. The third asserts the thrown application exception contains `MULTIPLE_ACTIVE_TEMPLATES` and is not a raw `DataIntegrityViolationException`.

- [ ] **Step 5: Run both integration tests and verify GREEN**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=LifecycleApprovalFinalizationRollbackIntegrationTest,LifecycleApprovalConcurrencyIntegrationTest test
```

Expected: PASS with PostgreSQL tests executed.

- [ ] **Step 6: Commit integration evidence**

```bash
git add src/test/java/com/toir/integration/LifecycleApprovalFinalizationRollbackIntegrationTest.java \
  src/test/java/com/toir/integration/LifecycleApprovalConcurrencyIntegrationTest.java
git commit -m "test: verify lifecycle approval transactions"
```

---

### Task 12: Remove Fixed Validators and Update Backend Regression Coverage

**Files:**
- Delete: `src/main/java/com/toir/service/repair/RepairCampaignApprovalRouteValidator.java`
- Delete: `src/main/java/com/toir/service/plannedshutdown/PlannedShutdownApprovalRouteValidator.java`
- Delete or replace: their fixed-route test classes.
- Create: `src/test/java/com/toir/service/approval/LifecycleApprovalFixedAssumptionSourceTest.java`
- Modify: `src/test/java/com/toir/service/approval/DefaultApprovalRouteResolverTest.java`
- Modify: `src/test/java/com/toir/service/ApprovalServiceTest.java`
- Modify: `src/test/java/com/toir/security/ApprovalPbacScopeTest.java`
- Modify: migration contract tests that assert fixed route data only where historical seed integrity still matters.

**Interfaces:**
- Ensures: no production reference to fixed role/count validators remains.
- Preserves: historical seed migrations; this task does not rewrite them.

- [ ] **Step 1: Add a failing source-contract test for removed assumptions**

Create `LifecycleApprovalFixedAssumptionSourceTest` to read the four changed production service files and reject these symbols/messages:

```java
assertThat(productionSources).doesNotContain(
        "REQUIRED_DISCIPLINE_ROLES",
        "REQUIRED_APPROVER_ROLES",
        "LEGACY_SYSTEM_ADMIN_ROUTE",
        "completedSevenDisciplineRoute",
        "APPROVAL_PRODUCTION_MISSING",
        "APPROVAL_HSE_MISSING");
```

- [ ] **Step 2: Run the source contract and verify RED**

Run the focused class with Java 21 and confirm it finds the current fixed assumptions.

- [ ] **Step 3: Delete obsolete validators and rewrite tests around shared behavior**

Move any still-valid structural assertion to `LifecycleApprovalRoutePolicyTest`. Keep migration tests that verify historical seed SQL, but remove assertions that runtime validity must always equal the seeded seven/two shapes.

- [ ] **Step 4: Run the backend focused regression set**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=LifecycleApprovalRoutePolicyTest,DefaultApprovalRouteResolverTest,ApprovalRuleServiceTest,ApprovalServiceTest,RepairCampaignApprovalPolicyTest,RepairCampaignServiceTest,PlannedShutdownServiceTest,ApprovalPbacScopeTest test
```

Expected: PASS.

- [ ] **Step 5: Commit fixed-assumption removal**

```bash
git add -A src/main/java/com/toir/service/repair/RepairCampaignApprovalRouteValidator.java \
  src/main/java/com/toir/service/plannedshutdown/PlannedShutdownApprovalRouteValidator.java \
  src/test/java/com/toir/service/repair/RepairCampaignApprovalRouteValidatorTest.java \
  src/test/java/com/toir/service/plannedshutdown/PlannedShutdownApprovalRouteValidatorTest.java \
  src/test/java/com/toir/service/approval/LifecycleApprovalFixedAssumptionSourceTest.java \
  src/test/java/com/toir/service/approval \
  src/test/java/com/toir/service/ApprovalServiceTest.java \
  src/test/java/com/toir/security/ApprovalPbacScopeTest.java
git commit -m "refactor: remove fixed lifecycle approval routes"
```

---

### Task 13: Frontend Runtime Route Integrity and Dynamic Rendering

**Files:**
- Create: `toir-front/src/modules/hr/libs/approvals/runtime-route.ts`
- Create: `toir-front/src/modules/hr/libs/approvals/tests/runtime-route.test.ts`
- Modify: `toir-front/src/components/ui/approval-progress-bar.tsx`
- Modify: `toir-front/src/components/ui/approval-progress-bar.test.tsx`
- Modify: `toir-front/src/components/ui/approval-section.tsx`
- Modify: `toir-front/src/components/ui/approval-section.test.ts`
- Modify: `toir-front/src/modules/repairs/pages/repair-campaign-detail-page.tsx`
- Modify: `toir-front/src/modules/repairs/pages/planned-shutdown-detail-page.tsx`

**Interfaces:**
- Produces: `getRuntimeRouteView(request: ApprovalRequestRecord): RuntimeRouteView`.
- Guarantees: copied sorting, no fake steps/totals, terminal history non-actionable.

- [ ] **Step 1: Write failing parameterized runtime-route tests**

```typescript
it.each([1, 2, 7, 11])("accepts a contiguous %i-step runtime route", (count) => {
  const view = getRuntimeRouteView(approval({ steps: steps(count), totalSteps: count }));
  expect(view.integrity).toBe("VALID");
  expect(view.label).toBe(`1 / ${count}`);
});

it("does not mutate query-cache step arrays", () => {
  const input = Object.freeze([step(2), step(1)]);
  const view = getRuntimeRouteView(approval({ steps: input as ApprovalStepRecord[] }));
  expect(view.sortedSteps.map((item) => item.stepNumber)).toEqual([1, 2]);
  expect(input.map((item) => item.stepNumber)).toEqual([2, 1]);
});
it.each([
  [[], 0],
  [[step(1), step(3)], 2],
  [[step(1), step(1)], 2],
])("marks missing, gap, and duplicate routes unavailable", (route, total) => {
  expect(getRuntimeRouteView(approval({ steps: route, totalSteps: total })).integrity)
    .toBe("UNAVAILABLE");
});
it("rejects totalSteps different from steps.length", () => {
  expect(getRuntimeRouteView(approval({ steps: steps(2), totalSteps: 7 })).integrity)
    .toBe("UNAVAILABLE");
});
it("validates currentStep against PENDING status", () => {
  expect(getRuntimeRouteView(approval({ steps: steps(2), currentStep: 3 })).integrity)
    .toBe("UNAVAILABLE");
});
it("renders APPROVED as history without actionable current step", () => {
  const view = getRuntimeRouteView(approval({ status: "APPROVED", steps: approvedSteps(2) }));
  expect(view).toMatchObject({ integrity: "VALID", history: true, actionable: false });
});
it("keeps the old persisted route when a template fixture changes", () => {
  const request = approval({ steps: steps(2), totalSteps: 2 });
  const before = getRuntimeRouteView(request);
  const changedTemplate = steps(7);
  expect(changedTemplate).toHaveLength(7);
  expect(getRuntimeRouteView(request).sortedSteps).toEqual(before.sortedSteps);
});
```

- [ ] **Step 2: Run focused frontend tests and verify RED**

From `toir-front`:

```bash
yarn test src/modules/hr/libs/approvals/tests/runtime-route.test.ts \
  src/components/ui/approval-progress-bar.test.tsx \
  src/components/ui/approval-section.test.ts
```

Expected: FAIL because `getRuntimeRouteView` does not exist and inconsistent totals currently render.

- [ ] **Step 3: Implement the pure runtime view model**

```typescript
export type RuntimeRouteView = {
  integrity: "VALID" | "UNAVAILABLE";
  sortedSteps: ApprovalStepRecord[];
  currentStepId: string | null;
  currentStep: number | null;
  totalSteps: number | null;
  actionable: boolean;
  history: boolean;
};

export function getRuntimeRouteView(request: ApprovalRequestRecord): RuntimeRouteView {
  const sortedSteps = [...(request.steps ?? [])].sort((a, b) => a.stepNumber - b.stepNumber);
  const contiguous = sortedSteps.length > 0
    && sortedSteps.every((step, index) => step.stepNumber === index + 1);
  const totalConsistent = request.totalSteps == null || request.totalSteps === sortedSteps.length;
  // Validate PENDING current step and terminal history; return UNAVAILABLE on inconsistency.
}
```

- [ ] **Step 4: Route all shared rendering/actionability through the view model**

`ApprovalProgressSummary` receives status or a prepared view and renders unavailable for invalid data. `ApprovalSection` requires valid integrity, PENDING status, a runtime current step, local permission, and the exact backend flag for each action. Keep `allowCreate={false}` on both domain detail pages.

- [ ] **Step 5: Run focused tests and verify GREEN**

```bash
yarn test src/modules/hr/libs/approvals/tests/runtime-route.test.ts \
  src/components/ui/approval-progress-bar.test.tsx \
  src/components/ui/approval-section.test.ts \
  src/modules/repairs/pages/tests/repair-campaign-detail-security-contract.test.ts
```

Expected: PASS.

- [ ] **Step 6: Commit dynamic runtime rendering in the frontend repository**

```bash
git add src/modules/hr/libs/approvals/runtime-route.ts \
  src/modules/hr/libs/approvals/tests/runtime-route.test.ts \
  src/components/ui/approval-progress-bar.tsx \
  src/components/ui/approval-progress-bar.test.tsx \
  src/components/ui/approval-section.tsx \
  src/components/ui/approval-section.test.ts \
  src/modules/repairs/pages/repair-campaign-detail-page.tsx \
  src/modules/repairs/pages/planned-shutdown-detail-page.tsx
git commit -m "feat: render immutable lifecycle approval routes"
```

---

### Task 14: Frontend Template Preservation, Administrator Errors, and Localization

**Files:**
- Create: `toir-front/src/modules/hr/libs/approvals/approval-rule-form.ts`
- Create: `toir-front/src/modules/hr/libs/approvals/tests/approval-rule-form.test.ts`
- Modify: `toir-front/src/modules/hr/pages/approval-rules-page.tsx`
- Modify: `toir-front/src/modules/repairs/libs/repair-campaigns/error-guidance.ts`
- Modify: `toir-front/src/modules/repairs/libs/repair-campaigns/tests/repair-campaign-error-guidance.test.ts`
- Modify: `toir-front/src/modules/repairs/libs/planned-shutdowns/error-guidance.ts`
- Modify: `toir-front/src/modules/repairs/libs/planned-shutdowns/tests/planned-shutdown-error-guidance.test.ts`
- Modify: `toir-front/src/i18n/locales/en.json`
- Modify: `toir-front/src/i18n/locales/ru.json`
- Modify: `toir-front/src/i18n/locales/uz.json`

**Interfaces:**
- Produces: lossless template DTO → form → payload mapping for role steps.
- Produces: read-only protection when explicit approver steps are unsupported by the UI.
- Produces: EN/RU/UZ guidance for approved stable codes.

- [ ] **Step 1: Write failing lossless mapping and variable payload tests**

```typescript
it.each([1, 2, 7, 9])("emits a contiguous %i-role-step payload", (count) => {
  expect(toApprovalRulePayload(formWithRoles(count)).steps.map((step) => step.order))
    .toEqual(Array.from({ length: count }, (_, index) => index + 1));
});

it("preserves an existing explicit approver step without converting it to a role", () => {
  const form = toApprovalRuleForm(ruleWithExplicitUser("user-1"));
  expect(form.readOnlyBecauseExplicitApprover).toBe(true);
  expect(form.steps[0]).toMatchObject({ approverType: "USER", approverId: "user-1" });
});

it("does not emit an editable payload for a read-only explicit template", () => {
  expect(() => toApprovalRulePayload(explicitReadOnlyForm())).toThrow("EXPLICIT_APPROVER_EDITOR_UNSUPPORTED");
});
```

Add guidance tests for the four requested configuration errors and existing scope-stale/route-stale codes in both domain helpers.

- [ ] **Step 2: Run focused tests and verify RED**

```bash
yarn test src/modules/hr/libs/approvals/tests/approval-rule-form.test.ts \
  src/modules/repairs/libs/repair-campaigns/tests/repair-campaign-error-guidance.test.ts \
  src/modules/repairs/libs/planned-shutdowns/tests/planned-shutdown-error-guidance.test.ts
```

Expected: FAIL because lossless form mapping and new guidance are absent.

- [ ] **Step 3: Implement lossless rule form mapping and read-only UI**

```typescript
export type RuleFormStep = {
  id: string;
  order: number;
  approverType: "ROLE" | "USER";
  approverRole: string | null;
  approverId: string | null;
  approverName: string | null;
};
```

When any existing step is `USER`, preserve every field, show an administrator banner, and disable save/reorder/delete controls for the entire template. Do not filter the step out and do not convert it. New templates remain role-only and variable `1..N`.

Detect multiple active exact lifecycle templates in the fetched list, show `MULTIPLE_ACTIVE_TEMPLATES`, and do not silently hide either row.

- [ ] **Step 4: Add EN/RU/UZ guidance**

Map:

```text
REPAIR_CAMPAIGN_APPROVAL_TEMPLATE_NOT_CONFIGURED
PLANNED_SHUTDOWN_APPROVAL_TEMPLATE_NOT_CONFIGURED
APPROVAL_TEMPLATE_STEPS_INVALID
MULTIPLE_ACTIVE_TEMPLATES
```

Retain scope-stale mappings. Rewrite route-stale copy to say persisted runtime approval data is malformed/inconsistent. Remove Production/HSE-specific wording from Planned Shutdown approval guidance.

- [ ] **Step 5: Run focused tests and i18n validation**

```bash
yarn test src/modules/hr/libs/approvals/tests/approval-rule-form.test.ts \
  src/modules/repairs/libs/repair-campaigns/tests/repair-campaign-error-guidance.test.ts \
  src/modules/repairs/libs/planned-shutdowns/tests/planned-shutdown-error-guidance.test.ts
yarn i18n:check
```

Expected: focused tests PASS and `i18n validation passed.`

- [ ] **Step 6: Commit template UI and localization**

```bash
git add src/modules/hr/libs/approvals/approval-rule-form.ts \
  src/modules/hr/libs/approvals/tests/approval-rule-form.test.ts \
  src/modules/hr/pages/approval-rules-page.tsx \
  src/modules/repairs/libs/repair-campaigns/error-guidance.ts \
  src/modules/repairs/libs/repair-campaigns/tests/repair-campaign-error-guidance.test.ts \
  src/modules/repairs/libs/planned-shutdowns/error-guidance.ts \
  src/modules/repairs/libs/planned-shutdowns/tests/planned-shutdown-error-guidance.test.ts \
  src/i18n/locales/en.json src/i18n/locales/ru.json src/i18n/locales/uz.json
git commit -m "feat: preserve lifecycle approval template UI data"
```

---

### Task 15: Automated Verification and Required Report Evidence

**Files:**
- Modify only test/report files found necessary by the verification results.
- Do not modify Warehouse files.

**Interfaces:**
- Produces: fresh command output for every automated claim.
- Produces: backend and frontend commit hashes for the required report.

- [ ] **Step 1: Confirm intended repository scope before verification**

Backend:

```bash
git status --short
if git diff --name-only c8d512c4...HEAD | rg -qi 'Warehouse|warehouse'; then
  echo "Unrelated Warehouse path detected in lifecycle approval branch" >&2
  exit 1
fi
```

Frontend:

```bash
git status --short
```

Expected: each implementation worktree is clean after task commits; backend intended diff contains no Warehouse path.

- [ ] **Step 2: Run Java 21 focused unit and domain tests**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
java -version
./mvnw -Dtest=LifecycleApprovalRoutePolicyTest,DefaultApprovalRouteResolverTest,ApprovalRuleServiceTest,ApprovalServiceTest,RepairCampaignApprovalPolicyTest,RepairCampaignServiceTest,RepairCampaignMutationImpactServiceTest,PlannedShutdownServiceTest,PlannedShutdownLifecycleServiceTest,ApprovalPbacScopeTest,ApprovalControllerTest test
```

Expected: Java reports version 21 and all listed tests PASS.

- [ ] **Step 3: Run real PostgreSQL and transaction/concurrency tests**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -Dtest=LifecycleApprovalTemplateUniquenessMigrationContractTest,LifecycleApprovalTemplateUniquenessMigrationPostgresTest,LifecycleApprovalFinalizationRollbackIntegrationTest,LifecycleApprovalConcurrencyIntegrationTest test
```

Expected: all tests PASS and Testcontainers tests execute, not skip.

- [ ] **Step 4: Compile and package the backend**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -DskipTests compile
./mvnw package
```

Expected: both commands exit 0; package runs the repository’s normal test lifecycle unless explicitly configured otherwise.

- [ ] **Step 5: Run focused frontend tests**

```bash
yarn test src/modules/hr/libs/approvals/tests/runtime-route.test.ts \
  src/modules/hr/libs/approvals/tests/approval-rule-form.test.ts \
  src/components/ui/approval-progress-bar.test.tsx \
  src/components/ui/approval-section.test.ts \
  src/modules/repairs/libs/repair-campaigns/tests/repair-campaign-error-guidance.test.ts \
  src/modules/repairs/libs/planned-shutdowns/tests/planned-shutdown-error-guidance.test.ts \
  src/modules/repairs/pages/tests/repair-campaign-detail-security-contract.test.ts
```

Expected: PASS.

- [ ] **Step 6: Run TypeScript, changed-file ESLint, and i18n checks**

```bash
yarn exec tsc -b
yarn eslint \
  src/modules/hr/libs/approvals/runtime-route.ts \
  src/modules/hr/libs/approvals/tests/runtime-route.test.ts \
  src/modules/hr/libs/approvals/approval-rule-form.ts \
  src/modules/hr/libs/approvals/tests/approval-rule-form.test.ts \
  src/components/ui/approval-progress-bar.tsx \
  src/components/ui/approval-progress-bar.test.tsx \
  src/components/ui/approval-section.tsx \
  src/components/ui/approval-section.test.ts \
  src/modules/hr/pages/approval-rules-page.tsx \
  src/modules/repairs/pages/repair-campaign-detail-page.tsx \
  src/modules/repairs/pages/planned-shutdown-detail-page.tsx \
  src/modules/repairs/libs/repair-campaigns/error-guidance.ts \
  src/modules/repairs/libs/planned-shutdowns/error-guidance.ts
yarn i18n:check
```

Expected: every command exits 0.

- [ ] **Step 7: Run production build from the clean frontend worktree**

```bash
test -z "$(git status --porcelain)"
yarn build
```

Expected: worktree assertion and production build exit 0.

- [ ] **Step 8: Record commit hashes and executed evidence**

From the backend implementation worktree run:

```bash
git rev-parse HEAD
```

From the frontend implementation worktree run:

```bash
git rev-parse HEAD
```

Write the required report with these exact headings:

```markdown
## PM Contract Applied
## Fixed Route Assumptions Removed
## Template Resolution Flow
## Repair Campaign Runtime Approval Flow
## Planned Shutdown Runtime Approval Flow
## Finalization Changes
## Template Change Behavior
## Backend Files Changed
## Frontend Files Changed
## Tests Added or Updated
## Verification Results
## Migration or Data Impact
## Remaining Business Decisions
## Commit Hashes
```

List only commands actually executed as passed. Record skipped or blocked commands explicitly.

---

### Task 16: Manual UI Business-Flow Acceptance Before Release

**Files:**
- Create: `toir-front/docs/qa/template-driven-lifecycle-approval-manual-acceptance.md`

**Interfaces:**
- Consumes: deployed backend/frontend build and administrator-created test templates.
- Produces: dated manual evidence separate from automated verification.

- [ ] **Step 1: Create the manual checklist before testing**

```markdown
# Template-Driven Lifecycle Approval Manual Acceptance

- Environment/build IDs:
- Tester:
- Date/time:
- Backend commit:
- Frontend commit:

## Repair Campaign
- [ ] One-step SYSTEM_ADMIN template creates and displays 1 / 1.
- [ ] Requester cannot approve or reject.
- [ ] Eligible SYSTEM_ADMIN completes the step and campaign finalizes.
- [ ] Seven-step template creates and displays 1 / 7 in configured order.
- [ ] One actor cannot approve a second step.
- [ ] Changing the template leaves an existing pending route unchanged.
- [ ] Scope mutation cancels pending approval and preserves history.
- [ ] Explicit re-request uses the new active template.

## Planned Shutdown
- [ ] Production/HSE template creates and displays 1 / 2.
- [ ] An arbitrary valid N-step template creates and displays 1 / N.
- [ ] Completion permits preparation without Production/HSE hardcoding.
- [ ] Another-scope historical approval does not satisfy preparation.
- [ ] Template change leaves the pending runtime route unchanged.

## Template Administration
- [ ] Variable role steps can be added, ordered, saved, and activated.
- [ ] Multiple active configuration shows a controlled administrator error.
- [ ] Existing explicit-approver templates are read-only and lose no data.

## Failure Guidance
- [ ] Missing template guidance is correct in EN/RU/UZ.
- [ ] Invalid steps and multiple-active guidance is correct in EN/RU/UZ.
- [ ] Scope-stale and runtime-route-stale guidance is actionable.
```

- [ ] **Step 2: Execute the checklist against the release candidate**

Record PASS/FAIL, screenshots or request IDs, and defect links for every item. Do not mark release accepted while any required item is unexecuted or failed.

- [ ] **Step 3: Commit only the completed acceptance record**

```bash
git add docs/qa/template-driven-lifecycle-approval-manual-acceptance.md
git commit -m "docs: record lifecycle approval acceptance"
```

Expected: the file contains environment, tester, date, commit hashes, and evidence; unchecked template text is not committed as release evidence.
