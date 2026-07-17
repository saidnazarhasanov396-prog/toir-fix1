# Repair Campaign Approval Policy Versioning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the mandatory seven-discipline Repair Campaign approval route configurable only through compatible versioned templates, snapshot route provenance on approvals, and allow an auditable `SYSTEM_ADMIN` to approve all seven ordered steps.

**Architecture:** `RepairCampaignApprovalRouteValidator` remains the canonical policy authority. `DefaultApprovalRouteResolver` returns template-backed steps together with template provenance, `ApprovalService` snapshots and revalidates that provenance, and `ApprovalScopeService` distinguishes ordinary one-step actors from an explicitly audited multi-step `SYSTEM_ADMIN` override. A forward-only migration preserves completed history and cancels pending approvals whose governing policy cannot be proven.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring Data JPA, PostgreSQL/Flyway, JUnit 5, Mockito, AssertJ.

## Global Constraints

- Do not modify frontend code.
- Repair Campaign lifecycle approval always has the exact seven canonical role steps in order.
- `REPAIR_CAMPAIGN_SEVEN_DISCIPLINE_V1` is immutable once persisted.
- Ordinary actors need the exact step role or `REPAIR_CAMPAIGN_APPROVE`, cannot be the requester, and may approve only one discipline step.
- `SYSTEM_ADMIN` may approve all seven ordered steps but cannot approve their own request.
- Repeated admin decisions require server-derived override evidence and a nonblank reason.
- Completed historical approvals are never rewritten with inferred provenance.
- Pending approvals with obsolete or unprovable policy provenance are cancelled without deleting steps or history.
- Every behavior change starts with a failing focused test.
- Use Java 21 for all test and build commands.

---

### Task 1: Versioned Approval Schema and Entity Mapping

**Files:**
- Create: `src/main/resources/db/migration/V20260716_1__version_repair_campaign_approval_policy.sql`
- Modify: `src/main/java/com/toir/entity/ApprovalTemplate.java`
- Modify: `src/main/java/com/toir/entity/ApprovalRequest.java`
- Modify: `src/main/java/com/toir/entity/ApprovalStep.java`
- Modify: `src/main/java/com/toir/dto/approval/ApprovalRequestDto.java`
- Modify: `src/main/java/com/toir/dto/approval/ApprovalStepDto.java`
- Test: `src/test/java/com/toir/migration/RepairCampaignApprovalRouteMigrationContractTest.java`

**Interfaces:**
- Produces: `ApprovalTemplate.version`, `ApprovalRequest.approvalTemplateId`, `approvalTemplateVersion`, `approvalRouteHash`, `approvalPolicyVersion`, and `ApprovalStep.sodOverride`, `sodOverrideAuthority`, `sodOverrideReason`.
- Preserves: nullable provenance for historical terminal approvals and existing approval DTO constructors.

- [ ] **Step 1: Write the failing migration contract test**

Add assertions that the new migration contains the exact columns, the policy-upgrade cancellation reason, preserved history insertion, deterministic duplicate-template deactivation, and a partial unique index for active Repair Campaign `APPROVE` templates:

```java
@Test
void policyVersionMigrationAddsProvenanceOverrideAndIdempotentPendingRepair() throws IOException {
    String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V20260716_1__version_repair_campaign_approval_policy.sql"));

    assertThat(sql).contains(
            "approval_template_id",
            "approval_template_version",
            "approval_route_hash",
            "approval_policy_version",
            "sod_override",
            "sod_override_authority",
            "sod_override_reason",
            "REPAIR_CAMPAIGN_APPROVAL_POLICY_UPGRADED",
            "uq_active_repair_campaign_approve_template");
    assertThat(sql).contains("INSERT INTO approval_history");
    assertThat(sql).doesNotContain("DELETE FROM approval_requests", "DELETE FROM approval_steps");
}
```

- [ ] **Step 2: Run the migration contract test and verify RED**

Run:

```bash
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp/codex-home \
  -v "$PWD:/workspace" -v "$HOME/.m2:/tmp/codex-home/.m2" -w /workspace \
  eclipse-temurin:21-jdk ./mvnw -Dtest=RepairCampaignApprovalRouteMigrationContractTest test
```

Expected: FAIL because `V20260716_1__version_repair_campaign_approval_policy.sql` does not exist.

- [ ] **Step 3: Add the forward-only migration**

The migration must:

```sql
ALTER TABLE approval_templates
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE approval_requests
    ADD COLUMN approval_template_id uuid,
    ADD COLUMN approval_template_version bigint,
    ADD COLUMN approval_route_hash varchar(64),
    ADD COLUMN approval_policy_version varchar(100);

ALTER TABLE approval_requests
    ADD CONSTRAINT fk_approval_request_template
    FOREIGN KEY (approval_template_id) REFERENCES approval_templates(id) ON DELETE SET NULL;

ALTER TABLE approval_steps
    ADD COLUMN sod_override boolean NOT NULL DEFAULT false,
    ADD COLUMN sod_override_authority varchar(100),
    ADD COLUMN sod_override_reason text;
```

Before creating the partial unique index, rank active non-deleted `REPAIR_CAMPAIGN/APPROVE` templates by `created_at DESC, id DESC` and deactivate every row after the first. Create the index only for that target/action pair.

Cancel active pending Repair Campaign lifecycle approvals whose provenance columns are null using `REPAIR_CAMPAIGN_APPROVAL_POLICY_UPGRADED`; insert one `PENDING -> CANCELLED` history row from the update `RETURNING` result. Do not alter terminal approvals or delete any steps/history.

- [ ] **Step 4: Map the new entity and DTO fields**

Add `@Version Long version` to `ApprovalTemplate`. Add nullable provenance fields to `ApprovalRequest`. Add override evidence fields to `ApprovalStep`:

```java
@Column(name = "sod_override", nullable = false)
private boolean sodOverride;

@Column(name = "sod_override_authority")
private String sodOverrideAuthority;

@Column(name = "sod_override_reason", columnDefinition = "text")
private String sodOverrideReason;
```

Expose the same evidence in `ApprovalRequestDto` and `ApprovalStepDto`, while retaining compatibility constructors used by existing tests.

- [ ] **Step 5: Run focused mapping and migration tests**

Run:

```bash
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp/codex-home \
  -v "$PWD:/workspace" -v "$HOME/.m2:/tmp/codex-home/.m2" -w /workspace \
  eclipse-temurin:21-jdk ./mvnw -Dtest=RepairCampaignApprovalRouteMigrationContractTest,ApprovalServiceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit the schema slice**

```bash
git add src/main/resources/db/migration/V20260716_1__version_repair_campaign_approval_policy.sql \
  src/main/java/com/toir/entity/ApprovalTemplate.java \
  src/main/java/com/toir/entity/ApprovalRequest.java \
  src/main/java/com/toir/entity/ApprovalStep.java \
  src/main/java/com/toir/dto/approval/ApprovalRequestDto.java \
  src/main/java/com/toir/dto/approval/ApprovalStepDto.java \
  src/test/java/com/toir/migration/RepairCampaignApprovalRouteMigrationContractTest.java
git commit -m "feat: version repair campaign approval evidence"
```

---

### Task 2: Canonical Template Validation and Single Active Template

**Files:**
- Modify: `src/main/java/com/toir/service/approval/ApprovalRuleService.java`
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignApprovalRouteValidator.java`
- Test: `src/test/java/com/toir/service/approval/ApprovalRuleServiceTest.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignApprovalRouteValidatorTest.java`

**Interfaces:**
- Consumes: `RepairCampaignApprovalRouteValidator.validateInputs(List<CreateApprovalRequest.StepInput>)`.
- Produces: Repair Campaign-specific create/update/activation rejection with `REPAIR_CAMPAIGN_APPROVAL_ROUTE_NOT_CONFIGURED`.

- [ ] **Step 1: Add failing rule-service tests**

Cover canonical acceptance and invalid active/inactive behavior:

```java
@Test
void activeRepairCampaignRuleMustMatchCanonicalPolicy() {
    ApprovalRuleDto invalid = rule(
            ApprovalTargetType.REPAIR_CAMPAIGN,
            ApprovalActionType.APPROVE,
            List.of(new ApprovalRuleDto.Step(1, "SYSTEM_ADMIN")),
            true);

    assertThatThrownBy(() -> service.create(invalid))
            .isInstanceOf(RestException.class)
            .hasMessageContaining("REPAIR_CAMPAIGN_APPROVAL_ROUTE_NOT_CONFIGURED");
}

@Test
void inactiveLegacyRepairCampaignRuleMayBeStoredButCannotBeActivated() {
    ApprovalTemplate legacy = persistedInactiveSystemAdminTemplate();
    when(templateRepository.findByIdAndIsDeletedFalse(legacy.getId())).thenReturn(Optional.of(legacy));

    assertThatThrownBy(() -> service.update(legacy.getId(), canonicalFlagOnly(false, true)))
            .isInstanceOf(RestException.class)
            .hasMessageContaining("REPAIR_CAMPAIGN_APPROVAL_ROUTE_NOT_CONFIGURED");
}
```

Add parameterized cases for 5 steps, 8 steps, duplicate role, wrong order, unexpected role, and approver ID.

- [ ] **Step 2: Run focused tests and verify RED**

```bash
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp/codex-home \
  -v "$PWD:/workspace" -v "$HOME/.m2:/tmp/codex-home/.m2" -w /workspace \
  eclipse-temurin:21-jdk ./mvnw -Dtest=ApprovalRuleServiceTest,RepairCampaignApprovalRouteValidatorTest test
```

Expected: FAIL because `ApprovalRuleService` performs only generic validation.

- [ ] **Step 3: Inject and apply the canonical validator**

Change the constructor dependency and add a focused guard:

```java
private void validateRepairCampaignPolicy(ApprovalRuleDto request) {
    if (request.targetType() != ApprovalTargetType.REPAIR_CAMPAIGN
            || effectiveActionType(request.actionType()) != ApprovalActionType.APPROVE
            || !request.active()) {
        return;
    }
    List<CreateApprovalRequest.StepInput> inputs = request.steps().stream()
            .sorted(Comparator.comparingInt(ApprovalRuleDto.Step::order))
            .map(step -> new CreateApprovalRequest.StepInput(null, normalizedRole(step.approverRole())))
            .toList();
    if (!repairCampaignRouteValidator.validateInputs(inputs).valid()) {
        throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_ROUTE_NOT_CONFIGURED");
    }
}
```

Call this after generic validation and before changing/deactivating persisted templates. Preserve inactive legacy records, but apply the guard whenever the resulting rule is active.

- [ ] **Step 4: Run focused tests and verify GREEN**

```bash
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp/codex-home \
  -v "$PWD:/workspace" -v "$HOME/.m2:/tmp/codex-home/.m2" -w /workspace \
  eclipse-temurin:21-jdk ./mvnw -Dtest=ApprovalRuleServiceTest,RepairCampaignApprovalRouteValidatorTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the template-policy slice**

```bash
git add src/main/java/com/toir/service/approval/ApprovalRuleService.java \
  src/main/java/com/toir/service/repair/RepairCampaignApprovalRouteValidator.java \
  src/test/java/com/toir/service/approval/ApprovalRuleServiceTest.java \
  src/test/java/com/toir/service/repair/RepairCampaignApprovalRouteValidatorTest.java
git commit -m "feat: validate repair campaign approval templates"
```

---

### Task 3: Resolve and Snapshot Template Provenance

**Files:**
- Create: `src/main/java/com/toir/service/approval/ResolvedApprovalRoute.java`
- Modify: `src/main/java/com/toir/service/approval/ApprovalRouteResolver.java`
- Modify: `src/main/java/com/toir/service/approval/DefaultApprovalRouteResolver.java`
- Modify: `src/main/java/com/toir/service/ApprovalService.java`
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignApprovalRouteValidator.java`
- Test: `src/test/java/com/toir/service/approval/DefaultApprovalRouteResolverTest.java`
- Test: `src/test/java/com/toir/service/ApprovalServiceTest.java`

**Interfaces:**
- Produces: `ResolvedApprovalRoute(List<StepInput> steps, UUID templateId, Long templateVersion)`.
- Produces: `RepairCampaignApprovalRouteValidator.POLICY_VERSION` and deterministic `routeHash`/provenance validation.

- [ ] **Step 1: Add failing resolver and service tests**

```java
@Test
void repairCampaignResolutionReturnsCanonicalTemplateProvenance() {
    ApprovalTemplate template = canonicalTemplate();
    template.setVersion(4L);
    when(templateRepository.findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
            ApprovalTargetType.REPAIR_CAMPAIGN, ApprovalActionType.APPROVE))
            .thenReturn(Optional.of(template));

    ResolvedApprovalRoute route = resolver.resolve(request(ApprovalTargetType.REPAIR_CAMPAIGN));

    assertThat(route.templateId()).isEqualTo(template.getId());
    assertThat(route.templateVersion()).isEqualTo(4L);
    assertThat(route.steps()).hasSize(7);
}
```

In `ApprovalServiceTest`, capture the saved request and assert template ID/version, policy version, and a stable 64-character route hash. Add a test proving a changed template version makes a pending approval non-reusable.

- [ ] **Step 2: Run focused tests and verify RED**

```bash
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp/codex-home \
  -v "$PWD:/workspace" -v "$HOME/.m2:/tmp/codex-home/.m2" -w /workspace \
  eclipse-temurin:21-jdk ./mvnw -Dtest=DefaultApprovalRouteResolverTest,ApprovalServiceTest test
```

Expected: FAIL because route provenance is not returned or persisted.

- [ ] **Step 3: Introduce the resolved-route contract**

```java
public record ResolvedApprovalRoute(
        List<CreateApprovalRequest.StepInput> steps,
        UUID templateId,
        Long templateVersion
) {
    public ResolvedApprovalRoute {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public static ResolvedApprovalRoute empty() {
        return new ResolvedApprovalRoute(List.of(), null, null);
    }
}
```

Change `ApprovalRouteResolver.resolveRoute` to `ResolvedApprovalRoute resolve(ApprovalRequest request)` and update all callers/tests. Template routes return provenance; permission fallbacks return null provenance.

- [ ] **Step 4: Add deterministic policy/hash support**

In `RepairCampaignApprovalRouteValidator` add:

```java
public static final String POLICY_VERSION = "REPAIR_CAMPAIGN_SEVEN_DISCIPLINE_V1";

public String routeHash(List<CreateApprovalRequest.StepInput> inputs) {
    String normalized = IntStream.range(0, inputs.size())
            .mapToObj(index -> (index + 1) + ":" + Objects.toString(inputs.get(index).approverRole(), "")
                    + ":" + Objects.toString(inputs.get(index).approverId(), ""))
            .collect(Collectors.joining("|", "REPAIR_CAMPAIGN|APPROVE|" + POLICY_VERSION + "|", ""));
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(normalized.getBytes(StandardCharsets.UTF_8)));
}
```

Wrap checked digest failure as an illegal state because SHA-256 is mandatory in the JDK.

- [ ] **Step 5: Persist and validate provenance in `ApprovalService`**

For Repair Campaign creation:

```java
ResolvedApprovalRoute resolved = routeResolver.resolve(request);
List<StepInput> effectiveSteps = normalizeStepInputs(resolved.steps());
request.setApprovalTemplateId(resolved.templateId());
request.setApprovalTemplateVersion(resolved.templateVersion());
request.setApprovalPolicyVersion(RepairCampaignApprovalRouteValidator.POLICY_VERSION);
request.setApprovalRouteHash(repairCampaignApprovalRouteValidator.routeHash(effectiveSteps));
```

Require non-null template ID/version for Repair Campaign lifecycle approvals. Reuse/actionability requires current scope payload, canonical persisted steps, matching policy version/hash, and the current selected template ID/version. Generic approval targets preserve their existing behavior.

- [ ] **Step 6: Run focused tests and verify GREEN**

```bash
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp/codex-home \
  -v "$PWD:/workspace" -v "$HOME/.m2:/tmp/codex-home/.m2" -w /workspace \
  eclipse-temurin:21-jdk ./mvnw -Dtest=DefaultApprovalRouteResolverTest,ApprovalServiceTest,RepairCampaignApprovalRouteValidatorTest test
```

Expected: PASS.

- [ ] **Step 7: Commit the provenance slice**

```bash
git add src/main/java/com/toir/service/approval/ResolvedApprovalRoute.java \
  src/main/java/com/toir/service/approval/ApprovalRouteResolver.java \
  src/main/java/com/toir/service/approval/DefaultApprovalRouteResolver.java \
  src/main/java/com/toir/service/ApprovalService.java \
  src/main/java/com/toir/service/repair/RepairCampaignApprovalRouteValidator.java \
  src/test/java/com/toir/service/approval/DefaultApprovalRouteResolverTest.java \
  src/test/java/com/toir/service/ApprovalServiceTest.java
git commit -m "feat: snapshot repair campaign approval provenance"
```

---

### Task 4: Permission Eligibility and Audited SYSTEM_ADMIN Override

**Files:**
- Modify: `src/main/java/com/toir/service/ApprovalScopeService.java`
- Modify: `src/main/java/com/toir/service/ApprovalService.java`
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignApprovalRouteValidator.java`
- Modify: `src/main/java/com/toir/dto/approval/ApprovalStepDto.java`
- Test: `src/test/java/com/toir/security/ApprovalPbacScopeTest.java`
- Test: `src/test/java/com/toir/service/ApprovalServiceTest.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignApprovalRouteValidatorTest.java`

**Interfaces:**
- Produces: ordinary eligibility through exact role or `REPAIR_CAMPAIGN_APPROVE`.
- Produces: repeated-actor `SYSTEM_ADMIN` evidence and terminal validation.

- [ ] **Step 1: Add failing PBAC and decision tests**

Add tests proving:

```java
@Test
void repairCampaignApprovePermissionCanActOnCurrentDisciplineStepButOnlyOnce() {
    authenticate(userId, "REPAIR_CAMPAIGN_APPROVE");
    scopeService.assertCanDecideApproval(approvalAtStep(1), currentStep(1), null);

    ApprovalRequest second = approvalAtStep(2);
    second.getSteps().getFirst().setDecision(ApprovalDecision.APPROVED);
    second.getSteps().getFirst().setDecidedById(userId);

    assertThatThrownBy(() -> scopeService.assertCanDecideApproval(second, currentStep(2), null))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("distinct actor");
}
```

Add an `ApprovalServiceTest` that authenticates one `SYSTEM_ADMIN`, approves all seven steps in order, supplies a reason on repeated steps, and asserts steps 2-7 contain `sodOverride=true`, authority `SYSTEM_ADMIN`, reason, actor, and timestamp. Add negative tests for admin requester and blank repeated-step reason.

- [ ] **Step 2: Run focused tests and verify RED**

```bash
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp/codex-home \
  -v "$PWD:/workspace" -v "$HOME/.m2:/tmp/codex-home/.m2" -w /workspace \
  eclipse-temurin:21-jdk ./mvnw -Dtest=ApprovalPbacScopeTest,ApprovalServiceTest,RepairCampaignApprovalRouteValidatorTest test
```

Expected: FAIL because ordinary permission eligibility and persisted admin override do not exist.

- [ ] **Step 3: Implement eligibility without weakening route checks**

In the Repair Campaign discipline path, accept an ordinary actor when either exact role matching succeeds or:

```java
securityAccessService.hasPermission(authentication, PermissionConstants.REPAIR_CAMPAIGN_APPROVE)
```

Keep requester exclusion. Skip the previous-actor rejection only when `securityAccessService.isSystemAdmin(authentication)` is true. Expose a focused `isCurrentPrincipalSystemAdmin()` method for decision evidence; do not trust request DTO fields.

- [ ] **Step 4: Persist override evidence atomically**

Before changing the current step, detect whether the authenticated admin actor already approved another discipline step. For a repeated admin actor:

```java
if (!StringUtils.hasText(decision.comment())) {
    throw RestException.badRequest("SYSTEM_ADMIN separation-of-duty override reason is required");
}
current.setSodOverride(true);
current.setSodOverrideAuthority("SYSTEM_ADMIN");
current.setSodOverrideReason(decision.comment().trim());
```

For ordinary or first-use actors, clear all override fields. When returning to an earlier step, clear override evidence together with decision actor/time/comment.

- [ ] **Step 5: Update terminal completion validation**

Group approved steps by `decidedById`. Distinct actors pass unchanged. For each repeated occurrence after the first, require `sodOverride=true`, authority exactly `SYSTEM_ADMIN`, and a nonblank reason. Always reject requester participation, regardless of override evidence.

- [ ] **Step 6: Run focused tests and verify GREEN**

```bash
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp/codex-home \
  -v "$PWD:/workspace" -v "$HOME/.m2:/tmp/codex-home/.m2" -w /workspace \
  eclipse-temurin:21-jdk ./mvnw -Dtest=ApprovalPbacScopeTest,ApprovalServiceTest,RepairCampaignApprovalPolicyTest,RepairCampaignApprovalRouteValidatorTest test
```

Expected: PASS, including one admin completing all seven steps.

- [ ] **Step 7: Commit the authorization slice**

```bash
git add src/main/java/com/toir/service/ApprovalScopeService.java \
  src/main/java/com/toir/service/ApprovalService.java \
  src/main/java/com/toir/service/repair/RepairCampaignApprovalRouteValidator.java \
  src/main/java/com/toir/dto/approval/ApprovalStepDto.java \
  src/test/java/com/toir/security/ApprovalPbacScopeTest.java \
  src/test/java/com/toir/service/ApprovalServiceTest.java \
  src/test/java/com/toir/service/repair/RepairCampaignApprovalRouteValidatorTest.java
git commit -m "feat: audit repair campaign admin approval overrides"
```

---

### Task 5: Policy Upgrade Re-request and Legacy Recovery

**Files:**
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignApprovalPolicy.java`
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignService.java`
- Modify: `src/main/java/com/toir/service/ApprovalService.java`
- Test: `src/test/java/com/toir/service/RepairCampaignServiceTest.java`
- Test: `src/test/java/com/toir/service/ApprovalServiceTest.java`
- Test: `src/test/java/com/toir/migration/RepairCampaignApprovalRouteMigrationPostgresTest.java`

**Interfaces:**
- Produces: transactional replacement of a pending approval when scope is still current but template/policy provenance changed.
- Preserves: existing stale-scope behavior when campaign facts changed.

- [ ] **Step 1: Add failing re-request tests**

Cover a campaign already in `PENDING_APPROVAL` with a current scope/hash and a cancelled or provenance-stale pending approval:

```java
@Test
void reRequestWithCurrentScopeReplacesPolicyUpgradedApproval() {
    RepairCampaign campaign = pendingCampaignWithCurrentSnapshot();
    when(repository.findLockedByIdAndIsDeletedFalse(campaign.getId())).thenReturn(Optional.of(campaign));

    service.requestApproval(campaign.getId(), campaign.getVersion(), campaign.getScopeVersion(), "policy upgrade");

    verify(approvalService).requestApproval(argThat(start ->
            start.targetType() == ApprovalTargetType.REPAIR_CAMPAIGN
                    && start.actionType() == ApprovalActionType.APPROVE));
    assertThat(campaign.getStatus()).isEqualTo(RepairCampaignStatus.PENDING_APPROVAL);
}
```

Add a negative test where the current hasher no longer matches `approvalScopeHash`; expect the existing scope-stale error and no approval creation.

- [ ] **Step 2: Run focused tests and verify RED**

```bash
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp/codex-home \
  -v "$PWD:/workspace" -v "$HOME/.m2:/tmp/codex-home/.m2" -w /workspace \
  eclipse-temurin:21-jdk ./mvnw -Dtest=RepairCampaignServiceTest,ApprovalServiceTest test
```

Expected: FAIL because `prepareRequest` accepts only `RESOURCE_CHECK`.

- [ ] **Step 3: Add the narrow pending re-request policy**

Allow `PENDING_APPROVAL` only when all existing campaign scope snapshot checks pass. Do not rewrite the snapshot or increment scope version in that path:

```java
if (campaign.getStatus() == RepairCampaignStatus.PENDING_APPROVAL) {
    if (campaign.getApprovalScopeHash() == null
            || !Objects.equals(campaign.getScopeVersion(), campaign.getApprovalScopeVersion())
            || !Objects.equals(campaign.getApprovalScopeHash(), scopeHasher.hash(campaign))) {
        throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_SCOPE_STALE");
    }
    return;
}
```

The existing `RESOURCE_CHECK` path continues to capture a new snapshot and enter `PENDING_APPROVAL`.

- [ ] **Step 4: Verify transactional cancellation/replacement**

Add a service test proving that old pending requests are cancelled with `REPAIR_CAMPAIGN_APPROVAL_POLICY_UPGRADED` or route-specific reason, exactly one new canonical approval is created, and only one pending approval remains. When template resolution fails, the transaction must not persist a partial campaign or approval state.

- [ ] **Step 5: Extend PostgreSQL migration verification**

Verify the new migration on the optional local PostgreSQL 17 chain:

```java
assertThat(scalar(statement, "SELECT count(*) FROM approval_requests "
        + "WHERE status='PENDING' AND target_type='REPAIR_CAMPAIGN' "
        + "AND approval_policy_version IS NULL")).isEqualTo(0L);
```

Verify the cancellation history is present once and step rows remain.

- [ ] **Step 6: Run recovery tests and verify GREEN**

```bash
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp/codex-home \
  -v "$PWD:/workspace" -v "$HOME/.m2:/tmp/codex-home/.m2" -w /workspace \
  eclipse-temurin:21-jdk ./mvnw -Dtest=RepairCampaignServiceTest,ApprovalServiceTest,RepairCampaignApprovalRouteMigrationContractTest,RepairCampaignApprovalRouteMigrationPostgresTest test
```

Expected: PASS; PostgreSQL-gated cases may report assumptions only when the documented local PostgreSQL prerequisite is unavailable.

- [ ] **Step 7: Commit the recovery slice**

```bash
git add src/main/java/com/toir/service/repair/RepairCampaignApprovalPolicy.java \
  src/main/java/com/toir/service/repair/RepairCampaignService.java \
  src/main/java/com/toir/service/ApprovalService.java \
  src/test/java/com/toir/service/RepairCampaignServiceTest.java \
  src/test/java/com/toir/service/ApprovalServiceTest.java \
  src/test/java/com/toir/migration/RepairCampaignApprovalRouteMigrationPostgresTest.java
git commit -m "feat: re-request versioned repair campaign approvals"
```

---

### Task 6: Full Backend Verification and Publication

**Files:**
- Review every file changed by Tasks 1-5.
- Do not modify frontend files.

**Interfaces:**
- Consumes: all previous task outputs.
- Produces: a verified, committed, pushed `Codex_org` integration ready for the subsequent read-only audit.

- [ ] **Step 1: Run focused Repair Campaign approval tests on Java 21**

```bash
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp/codex-home \
  -v "$PWD:/workspace" -v "$HOME/.m2:/tmp/codex-home/.m2" -w /workspace \
  eclipse-temurin:21-jdk ./mvnw \
  -Dtest=RepairCampaignApprovalRouteValidatorTest,RepairCampaignApprovalPolicyTest,DefaultApprovalRouteResolverTest,ApprovalRuleServiceTest,ApprovalServiceTest,RepairCampaignServiceTest,ApprovalPbacScopeTest \
  test
```

Expected: PASS with no skipped unit tests.

- [ ] **Step 2: Run migration and repository tests**

```bash
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp/codex-home \
  -v "$PWD:/workspace" -v "$HOME/.m2:/tmp/codex-home/.m2" -w /workspace \
  eclipse-temurin:21-jdk ./mvnw \
  -Dtest=RepairCampaignApprovalRouteMigrationContractTest,RepairCampaignApprovalRouteMigrationPostgresTest,ApprovalRequestRepositoryQueryContractTest \
  test
```

Expected: PASS; any unavailable external PostgreSQL gate is reported explicitly.

- [ ] **Step 3: Run compile and package**

```bash
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp/codex-home \
  -v "$PWD:/workspace" -v "$HOME/.m2:/tmp/codex-home/.m2" -w /workspace \
  eclipse-temurin:21-jdk ./mvnw -DskipTests compile
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp/codex-home \
  -v "$PWD:/workspace" -v "$HOME/.m2:/tmp/codex-home/.m2" -w /workspace \
  eclipse-temurin:21-jdk ./mvnw -DskipTests package
```

Expected: both commands end with `BUILD SUCCESS`.

- [ ] **Step 4: Review the complete change set**

```bash
git diff --check
git status --short
git log --oneline --decorate -8
```

Confirm no frontend files, unrelated backend files, generated binaries, or local configuration are staged.

- [ ] **Step 5: Push `Codex_org`**

```bash
git push origin Codex_org
```

Expected: `origin/Codex_org` advances to the verified integration HEAD.

- [ ] **Step 6: Begin the queued read-only audit**

After publication, execute `/home/tenzorsoft/Downloads/Telegram Desktop/message (10).txt` exactly as a separate read-only backend/frontend/history audit. Do not mix audit findings with implementation changes.

## Plan Self-Review

- Schema, template lifecycle, provenance snapshot, deterministic hash, ordinary permission eligibility, requester exclusion, multi-step admin override, terminal evidence validation, legacy cancellation, re-request, concurrency, and Java 21 verification each have an owning task.
- The route remains exactly seven steps; only the actor separation rule has the approved, persisted `SYSTEM_ADMIN` exception.
- No task modifies frontend code.
- The queued full-stack audit begins only after integration is committed and pushed.
