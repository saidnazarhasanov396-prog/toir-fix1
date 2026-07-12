# Active Spare-Part Rule Rebinding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebind affected active spare-part installations to the currently effective life-rule revision after rule creation, revision, or deactivation while preserving trustworthy installation-time meter baselines and immediately updating due events.

**Architecture:** Add a focused `SparePartRuleRebindingService` between rule mutation and the existing lifecycle evaluator. It selects affected active installations, resolves the post-mutation winning rule, rewrites the applied-rule snapshot, reuses or reconstructs installation-time baselines, resolves the old rule cycle, and delegates calculation to `SparePartLifecycleEvaluationService` in the same transaction.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring Data JPA, PostgreSQL, Jackson, JUnit 5, Mockito, AssertJ, Maven.

## Global Constraints

- Preserve the physical installation baseline; a rule change never resets already consumed part life.
- Never substitute zero or the current meter value when an installation-time baseline is unavailable.
- Rebind active installations only; completed, replaced, removed, and deleted history stays immutable.
- Preserve rule precedence: `NODE_SLOT`, `NODE`, `EQUIPMENT`, then `CATALOG`.
- Rule mutation, rebinding, evaluation, and due-event transition run in one transaction.
- Keep public API response shapes unchanged and do not modify Equipment or Vehicle design-lifetime fields.
- Follow TDD: every production change is preceded by a focused failing test and observed RED result.
- Preserve unrelated worktree changes; do not reset, clean, or broadly rewrite the repository.

---

## Planned File Structure

- Create `src/main/java/com/toir/service/sparepartlifecycle/SparePartRuleRebindingService.java`: candidate selection, rule snapshot replacement, baseline reconciliation, and reevaluation orchestration.
- Create `src/test/java/com/toir/service/sparepartlifecycle/SparePartRuleRebindingServiceTest.java`: focused behavior tests for rebinding, scope filtering, baseline retention/reconstruction, fallback, and evaluation error.
- Modify `src/main/java/com/toir/service/sparepartlifecycle/SparePartLifeRuleService.java`: invoke rebinding after create/revise/deactivate mutations.
- Modify `src/test/java/com/toir/service/sparepartlifecycle/SparePartLifeRuleServiceTest.java`: verify mutation hooks and transaction-facing arguments.
- Modify `src/main/java/com/toir/service/sparepartlifecycle/SparePartDueEventService.java`: resolve open events belonging to the superseded rule cycle.
- Modify `src/test/java/com/toir/service/sparepartlifecycle/SparePartDueEventServiceTest.java`: prove old cycles resolve before a new cycle is evaluated.
- Modify `src/main/java/com/toir/entity/sparepartlifecycle/SparePartInstallation.java`: allow applied rule ID, revision, and JSON snapshot to be updated for active installations.
- Modify `src/main/java/com/toir/repository/sparepartlifecycle/SparePartInstallationRepository.java`: load active installations for a spare part in stable order.
- Modify `src/main/java/com/toir/repository/MeterReadingRepository.java`: load the latest trustworthy reading at or before installation time.
- Modify `src/main/java/com/toir/service/sparepartlifecycle/SparePartLifecycleEvaluationService.java`: update the stale immutable-snapshot class contract comment.
- Modify `src/test/java/com/toir/entity/sparepartlifecycle/SparePartLifecycleEntityContractTest.java`: assert applied-rule columns are intentionally updateable.
- Modify `docs/spare-part-service-life.md`: document active binding refresh and preserved baseline semantics.

### Task 1: Make Rule Bindings Updateable and Add Lookup Contracts

**Files:**
- Modify: `src/main/java/com/toir/entity/sparepartlifecycle/SparePartInstallation.java:95-103`
- Modify: `src/main/java/com/toir/repository/sparepartlifecycle/SparePartInstallationRepository.java`
- Modify: `src/main/java/com/toir/repository/MeterReadingRepository.java`
- Test: `src/test/java/com/toir/entity/sparepartlifecycle/SparePartLifecycleEntityContractTest.java`

**Interfaces:**
- Produces: `List<SparePartInstallation> findAllBySparePartIdAndStatusAndIsDeletedFalseOrderByInstalledAtAsc(UUID sparePartId, SparePartInstallationStatus status)`.
- Produces: `Optional<MeterReading> findTopByMeterIdAndReadAtLessThanEqualAndIsDeletedFalseOrderByReadAtDesc(UUID meterId, Instant readAt)`.
- Produces: updateable `appliedLifeRuleId`, `appliedRuleRevision`, and `appliedRuleSnapshot` JPA fields.

- [ ] **Step 1: Write the failing entity contract test**

Add a helper and assertions that require the three columns to use the JPA default `updatable = true`:

```java
assertUpdateableColumn(SparePartInstallation.class, "appliedLifeRuleId");
assertUpdateableColumn(SparePartInstallation.class, "appliedRuleRevision");
assertUpdateableColumn(SparePartInstallation.class, "appliedRuleSnapshot");

private static void assertUpdateableColumn(Class<?> type, String fieldName) throws Exception {
    Column column = type.getDeclaredField(fieldName).getAnnotation(Column.class);
    assertThat(column).isNotNull();
    assertThat(column.updatable()).isTrue();
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./mvnw -Dtest=SparePartLifecycleEntityContractTest test
```

Expected: FAIL because all three existing annotations declare `updatable = false`.

- [ ] **Step 3: Make only applied-rule fields updateable**

Replace the annotations with:

```java
@Column(name = "applied_life_rule_id")
private UUID appliedLifeRuleId;

@Column(name = "applied_rule_revision")
private Integer appliedRuleRevision;

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "applied_rule_snapshot", columnDefinition = "jsonb")
private String appliedRuleSnapshot;
```

Do not change serial, lot, position, replacement, or baseline immutability.

- [ ] **Step 4: Add the two repository method signatures**

Add to `SparePartInstallationRepository`:

```java
List<SparePartInstallation> findAllBySparePartIdAndStatusAndIsDeletedFalseOrderByInstalledAtAsc(
        UUID sparePartId,
        SparePartInstallationStatus status
);
```

Add to `MeterReadingRepository`:

```java
@Query("""
        select reading
        from MeterReading reading
        where reading.meterId = :meterId
          and reading.readAt <= :readAt
          and reading.isDeleted = false
        order by reading.readAt desc, reading.createdAt desc
        limit 1
        """)
Optional<MeterReading> findTopByMeterIdAndReadAtLessThanEqualAndIsDeletedFalseOrderByReadAtDesc(
        @Param("meterId") UUID meterId,
        @Param("readAt") Instant readAt
);
```

If Hibernate rejects JPQL `limit`, express this method as the equivalent native PostgreSQL query with `ORDER BY read_at DESC, created_at DESC LIMIT 1`; do not load all readings in memory.

- [ ] **Step 5: Run the entity and application-context compile checks**

Run:

```bash
./mvnw -Dtest=SparePartLifecycleEntityContractTest,SparePartLifeRuleResolverTest test
```

Expected: PASS.

- [ ] **Step 6: Commit the infrastructure contract**

```bash
git add src/main/java/com/toir/entity/sparepartlifecycle/SparePartInstallation.java src/main/java/com/toir/repository/sparepartlifecycle/SparePartInstallationRepository.java src/main/java/com/toir/repository/MeterReadingRepository.java src/test/java/com/toir/entity/sparepartlifecycle/SparePartLifecycleEntityContractTest.java
git commit -m "refactor: allow active spare-part rule rebinding"
```

### Task 2: Resolve Superseded Due-Event Cycles

**Files:**
- Modify: `src/main/java/com/toir/service/sparepartlifecycle/SparePartDueEventService.java`
- Test: `src/test/java/com/toir/service/sparepartlifecycle/SparePartDueEventServiceTest.java`

**Interfaces:**
- Produces: `void resolveOpenForRuleRebind(UUID installationId, Instant resolvedAt)`.
- Consumes: `SparePartDueEventRepository.findAllByInstallationIdAndStateInAndIsDeletedFalse(...)`.

- [ ] **Step 1: Write the failing old-cycle resolution test**

Create an open `OVERDUE` event and verify the new method resolves it without replacement metadata:

```java
@Test
void resolvesOpenCycleBeforeInstallationIsReboundToAnotherRule() {
    UUID installationId = UUID.randomUUID();
    Instant reboundAt = Instant.parse("2026-07-11T06:00:00Z");
    SparePartDueEvent oldEvent = new SparePartDueEvent();
    oldEvent.setInstallationId(installationId);
    oldEvent.setState(SparePartDueEventState.OVERDUE);
    when(repository.findAllByInstallationIdAndStateInAndIsDeletedFalse(
            eq(installationId), anySet())).thenReturn(List.of(oldEvent));
    when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

    service.resolveOpenForRuleRebind(installationId, reboundAt);

    assertThat(oldEvent.getState()).isEqualTo(SparePartDueEventState.RESOLVED);
    assertThat(oldEvent.getResolvedAt()).isEqualTo(reboundAt);
    assertThat(oldEvent.getLastEvaluatedAt()).isEqualTo(reboundAt);
    assertThat(oldEvent.getReplacementInstallationId()).isNull();
    verify(repository).save(oldEvent);
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
./mvnw -Dtest=SparePartDueEventServiceTest#resolvesOpenCycleBeforeInstallationIsReboundToAnotherRule test
```

Expected: compilation FAIL because `resolveOpenForRuleRebind` does not exist.

- [ ] **Step 3: Implement focused old-cycle resolution**

Add:

```java
@Transactional
public void resolveOpenForRuleRebind(UUID installationId, Instant resolvedAt) {
    repository.findAllByInstallationIdAndStateInAndIsDeletedFalse(
            installationId,
            Set.of(
                    SparePartDueEventState.UPCOMING,
                    SparePartDueEventState.WARNING,
                    SparePartDueEventState.DUE,
                    SparePartDueEventState.OVERDUE
            )
    ).forEach(event -> {
        event.setState(SparePartDueEventState.RESOLVED);
        event.setResolvedAt(resolvedAt);
        event.setLastEvaluatedAt(resolvedAt);
        repository.save(event);
    });
}
```

- [ ] **Step 4: Run all due-event tests**

Run:

```bash
./mvnw -Dtest=SparePartDueEventServiceTest test
```

Expected: PASS with no existing removal or monotonic-cycle regressions.

- [ ] **Step 5: Commit the due-event transition**

```bash
git add src/main/java/com/toir/service/sparepartlifecycle/SparePartDueEventService.java src/test/java/com/toir/service/sparepartlifecycle/SparePartDueEventServiceTest.java
git commit -m "fix: resolve superseded spare-part due cycles"
```

### Task 3: Rebind Active Installations and Preserve Baselines

**Files:**
- Create: `src/main/java/com/toir/service/sparepartlifecycle/SparePartRuleRebindingService.java`
- Create: `src/test/java/com/toir/service/sparepartlifecycle/SparePartRuleRebindingServiceTest.java`
- Modify: `src/main/java/com/toir/service/sparepartlifecycle/SparePartLifecycleEvaluationService.java`

**Interfaces:**
- Produces: `int rebindAffected(SparePartLifeRule changedRule, Instant effectiveAt)`.
- Consumes: rule resolver, ordered life-limit repository, canonical meter resolver, installation/baseline/meter-reading repositories, due-event service, lifecycle evaluation service, and Jackson `ObjectMapper`.
- Returns: number of active candidate installations whose binding was evaluated.

- [ ] **Step 1: Write a failing same-meter baseline preservation test**

Arrange an active installation with a catalog-rule snapshot, baseline `100`, current meter `2600`, and a new equipment rule with a `1000` limit. The resolver returns the equipment rule. Assert after `rebindAffected`:

```java
assertThat(installation.getAppliedLifeRuleId()).isEqualTo(equipmentRule.getId());
assertThat(installation.getAppliedRuleRevision()).isEqualTo(1);
assertThat(installation.getAppliedRuleSnapshot()).contains("\"limitValue\":1000");
verify(baselineRepository, never()).saveAll(any());
verify(dueEventService).resolveOpenForRuleRebind(installation.getId(), effectiveAt);
verify(evaluationService).reevaluate(installation.getId(), effectiveAt);
```

Also capture the baseline list and assert its original value remains `100`.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./mvnw -Dtest=SparePartRuleRebindingServiceTest#rebindsToEquipmentRuleWithoutResettingExistingMeterBaseline test
```

Expected: compilation FAIL because `SparePartRuleRebindingService` does not exist.

- [ ] **Step 3: Implement candidate selection and rule snapshot replacement**

Create a `@Service`, `@RequiredArgsConstructor` class with:

```java
@Transactional
public int rebindAffected(SparePartLifeRule changedRule, Instant effectiveAt) {
    Instant at = effectiveAt == null ? Instant.now() : effectiveAt;
    List<SparePartInstallation> candidates = installationRepository
            .findAllBySparePartIdAndStatusAndIsDeletedFalseOrderByInstalledAtAsc(
                    changedRule.getSparePartId(), SparePartInstallationStatus.ACTIVE)
            .stream()
            .filter(installation -> isWithinChangedScope(installation, changedRule))
            .toList();
    candidates.forEach(installation -> rebindOne(installation.getId(), at));
    return candidates.size();
}
```

Implement `isWithinChangedScope` with exact `Objects.equals` checks for equipment, node, and normalized slot according to `changedRule.getScopeType()`.

In `rebindOne`, lock with `findByIdAndIsDeletedFalseForUpdate`, resolve the effective rule using the installation's part/equipment/node/slot at `effectiveAt`, load ordered limits, resolve each meter limit through `CanonicalEquipmentMeterService`, and build `AppliedLifeLimitSnapshot` plus `AppliedLifeRuleSnapshot`. For no winning rule use:

```java
new AppliedLifeRuleSnapshot(
        null,
        0,
        SparePartLifeCombinationMode.MANUAL,
        SparePartDueAction.WARNING_ONLY,
        List.of()
)
```

Before replacing the three applied-rule fields call `dueEventService.resolveOpenForRuleRebind(installation.getId(), effectiveAt)`. Serialize with `objectMapper.writeValueAsString(snapshot)`, save the installation, then call `evaluationService.reevaluate(installation.getId(), effectiveAt)`.

- [ ] **Step 4: Implement baseline reuse by canonical meter ID**

Load existing baselines once and index them:

```java
Map<UUID, SparePartInstallationMeterBaseline> existingBaselines = baselineRepository
        .findAllByInstallationIdAndIsDeletedFalse(installation.getId())
        .stream()
        .collect(Collectors.toMap(
                SparePartInstallationMeterBaseline::getEquipmentMeterId,
                Function.identity(),
                (first, ignored) -> first,
                LinkedHashMap::new
        ));
```

For every meter limit, put the canonical meter ID into `AppliedLifeLimitSnapshot`. If that ID already exists in `existingBaselines`, do not change or re-save the baseline.

- [ ] **Step 5: Run the preservation test and verify GREEN**

Run:

```bash
./mvnw -Dtest=SparePartRuleRebindingServiceTest#rebindsToEquipmentRuleWithoutResettingExistingMeterBaseline test
```

Expected: PASS.

- [ ] **Step 6: Write failing scope and fallback tests**

Add separate tests proving:

```java
assertThat(service.rebindAffected(nodeSlotRule, effectiveAt)).isEqualTo(1);
verify(ruleResolver, times(1)).resolve(any(), any(), any(), any(), eq(effectiveAt));
```

for two same-part installations where only one matches the exact node/slot, and:

```java
when(ruleResolver.resolve(partId, equipmentId, nodeId, slot, effectiveAt))
        .thenReturn(Optional.of(catalogRule));
service.rebindAffected(deactivatedEquipmentRule, effectiveAt);
assertThat(installation.getAppliedLifeRuleId()).isEqualTo(catalogRule.getId());
```

for equipment-rule deactivation fallback.

- [ ] **Step 7: Run the new tests and verify RED, then implement exact scope filtering**

Run:

```bash
./mvnw -Dtest=SparePartRuleRebindingServiceTest test
```

Expected before completing `isWithinChangedScope`: FAIL because unrelated installations are rebound. After implementing all four scope branches: PASS.

- [ ] **Step 8: Update the evaluation service contract comment**

Replace the stale comment with:

```java
/** Re-evaluates active installations from their currently applied auditable rule snapshot and physical-installation baselines. */
```

- [ ] **Step 9: Commit core rebinding**

```bash
git add src/main/java/com/toir/service/sparepartlifecycle/SparePartRuleRebindingService.java src/main/java/com/toir/service/sparepartlifecycle/SparePartLifecycleEvaluationService.java src/test/java/com/toir/service/sparepartlifecycle/SparePartRuleRebindingServiceTest.java
git commit -m "feat: rebind active installations to effective life rules"
```

### Task 4: Reconstruct Missing Historical Baselines Without Fabrication

**Files:**
- Modify: `src/main/java/com/toir/service/sparepartlifecycle/SparePartRuleRebindingService.java`
- Modify: `src/test/java/com/toir/service/sparepartlifecycle/SparePartRuleRebindingServiceTest.java`

**Interfaces:**
- Consumes: `MeterReadingRepository.findTopByMeterIdAndReadAtLessThanEqualAndIsDeletedFalseOrderByReadAtDesc(meterId, installedAt)`.
- Produces: a new immutable `SparePartInstallationMeterBaseline` only when a historical reading exists.
- Produces: evaluator error `METER_BASELINE_REQUIRED` when it does not exist.

- [ ] **Step 1: Write the failing historical reconstruction test**

Arrange a new meter rule, no matching stored baseline, and a reading value `2500` at or before `installedAt`. Assert:

```java
ArgumentCaptor<List<SparePartInstallationMeterBaseline>> captor = ArgumentCaptor.forClass(List.class);
verify(baselineRepository).saveAll(captor.capture());
SparePartInstallationMeterBaseline created = captor.getValue().getFirst();
assertThat(created.getInstallationId()).isEqualTo(installation.getId());
assertThat(created.getEquipmentMeterId()).isEqualTo(meter.getId());
assertThat(created.getBaselineValue()).isEqualByComparingTo("2500");
assertThat(created.getBaselineReadingId()).isEqualTo(reading.getId());
assertThat(created.getBaselineRecordedAt()).isEqualTo(reading.getReadAt());
```

- [ ] **Step 2: Run it and verify RED**

Run:

```bash
./mvnw -Dtest=SparePartRuleRebindingServiceTest#reconstructsMissingBaselineFromLatestReadingAtInstallationTime test
```

Expected: FAIL because no baseline is created.

- [ ] **Step 3: Implement historical baseline reconstruction**

When no existing baseline matches a resolved meter, query the latest reading at or before `installedAt`. If present, create:

```java
SparePartInstallationMeterBaseline baseline = new SparePartInstallationMeterBaseline();
baseline.setInstallationId(installation.getId());
baseline.setEquipmentMeterId(meter.getId());
baseline.setMeterType(meter.getMeterType());
baseline.setBaselineValue(BigDecimal.valueOf(reading.getValue()));
baseline.setBaselineRecordedAt(reading.getReadAt());
baseline.setBaselineReadingId(reading.getId());
baseline.setRolloverContext(meter.getRolloverValue() == null
        ? null
        : "{\"rolloverValue\":" + meter.getRolloverValue() + "}");
```

Collect newly created rows and call `baselineRepository.saveAll(newBaselines)` once before reevaluation.

- [ ] **Step 4: Run the reconstruction test and verify GREEN**

Run the same focused command. Expected: PASS.

- [ ] **Step 5: Write the missing-history evaluation-error test**

Use the real `SparePartLifecycleEvaluationService` with mocked repositories, no existing baseline, and `Optional.empty()` historical lookup. Assert:

```java
assertThat(result.aggregateState()).isEqualTo(SparePartLifecycleEvaluationState.ERROR);
assertThat(result.errorCodes()).containsExactly("METER_BASELINE_REQUIRED");
verify(baselineRepository, never()).saveAll(any());
```

Also assert no baseline with zero or the current meter value is persisted.

- [ ] **Step 6: Run the full rebinding tests**

Run:

```bash
./mvnw -Dtest=SparePartRuleRebindingServiceTest,SparePartLifecycleEvaluationServiceTest test
```

Expected: PASS; missing history is visible as `ERROR` and successful reconstruction preserves installation-time consumption.

- [ ] **Step 7: Commit baseline reconstruction**

```bash
git add src/main/java/com/toir/service/sparepartlifecycle/SparePartRuleRebindingService.java src/test/java/com/toir/service/sparepartlifecycle/SparePartRuleRebindingServiceTest.java
git commit -m "fix: preserve historical meter baseline on rule changes"
```

### Task 5: Trigger Rebinding From Every Rule Mutation

**Files:**
- Modify: `src/main/java/com/toir/service/sparepartlifecycle/SparePartLifeRuleService.java`
- Modify: `src/test/java/com/toir/service/sparepartlifecycle/SparePartLifeRuleServiceTest.java`

**Interfaces:**
- Consumes: `SparePartRuleRebindingService.rebindAffected(SparePartLifeRule changedRule, Instant effectiveAt)`.
- Guarantees: limits are saved before rebinding; revised/deactivated rules are persisted before resolution.

- [ ] **Step 1: Add a mocked rebinding dependency and write mutation-hook tests**

Add `@Mock SparePartRuleRebindingService rebindingService;` and tests that capture the rule for create, revise, and deactivate:

```java
verify(rebindingService).rebindAffected(eq(savedRule), any(Instant.class));
```

For create, use Mockito `InOrder` to prove `limitRepository.saveAll(...)` occurs before `rebindAffected(...)`. For deactivate, assert the captured rule has `active == false`. For revise, assert only the newly created revision triggers rebinding.

- [ ] **Step 2: Run rule-service tests and verify RED**

Run:

```bash
./mvnw -Dtest=SparePartLifeRuleServiceTest test
```

Expected: FAIL because the service never invokes `rebindingService`.

- [ ] **Step 3: Inject the rebinding service and add mutation timestamps**

Add the final dependency and in `create` execute after limits are saved:

```java
Instant mutationAt = Instant.now();
rebindingService.rebindAffected(savedRule, mutationAt);
```

In `deactivate`, call it after `ruleRepository.save(rule)`. Keep `revise` delegating to `create(request)` so the new revision performs exactly one rebind after the old revision is closed and the new limits exist.

- [ ] **Step 4: Run rule and rebinding tests**

Run:

```bash
./mvnw -Dtest=SparePartLifeRuleServiceTest,SparePartRuleRebindingServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit mutation integration**

```bash
git add src/main/java/com/toir/service/sparepartlifecycle/SparePartLifeRuleService.java src/test/java/com/toir/service/sparepartlifecycle/SparePartLifeRuleServiceTest.java
git commit -m "fix: recalculate active installations after life-rule changes"
```

### Task 6: Document and Verify the Complete Regression

**Files:**
- Modify: `docs/spare-part-service-life.md`
- Verify: all files changed in Tasks 1-5.

**Interfaces:**
- Documents: applied rule snapshots are auditable current bindings for active installations, while installation baselines remain physical-history evidence.

- [ ] **Step 1: Update domain documentation**

Replace the statement that later rule changes cannot rewrite active installation snapshots with:

```markdown
Install captures the selected revision and ordered limits as an auditable JSON snapshot plus structured physical-installation meter baselines. Creating, revising, or deactivating a rule re-resolves affected active installations, replaces their applied snapshot, preserves or reconstructs installation-time baselines, and immediately recalculates due events. Removed and replaced installation history is never rebound.
```

Update the evaluation paragraph to state that evaluations read the currently applied snapshot and preserved installation baseline.

- [ ] **Step 2: Run all spare-part lifecycle tests**

Run:

```bash
./mvnw -Dtest='*SparePart*Life*,SparePartDueEventServiceTest,SparePartLifecycleEntityContractTest,MeterServiceLifecycleSafetyTest' test
```

Expected: all selected tests PASS with zero failures and zero errors.

- [ ] **Step 3: Run the broader backend suite**

Run:

```bash
./mvnw test
```

Expected: BUILD SUCCESS. If an environmental integration test is skipped or blocked, record its exact class and reason without claiming it passed.

- [ ] **Step 4: Inspect the final diff and whitespace**

Run:

```bash
git diff --check
git status --short
git diff --stat HEAD~5..HEAD
```

Expected: no whitespace errors; only planned backend lifecycle and documentation files differ from the pre-task baseline.

- [ ] **Step 5: Commit documentation**

```bash
git add docs/spare-part-service-life.md
git commit -m "docs: explain active spare-part rule rebinding"
```

## Plan Self-Review

- Spec coverage: mutation hooks, exact scope candidates, deterministic resolution, snapshot replacement, same-meter baseline reuse, historical reconstruction, missing-history error, old-cycle resolution, transaction scope, tests, and documentation are covered by Tasks 1-6.
- Scope: this remains one backend spare-part lifecycle subsystem and does not require a separate plan.
- Placeholder scan: the plan contains no deferred implementation placeholders; the JPQL/native alternative is explicitly bounded to the same indexed query behavior.
- Type consistency: `rebindAffected(SparePartLifeRule, Instant)`, `resolveOpenForRuleRebind(UUID, Instant)`, repository signatures, entity field names, and existing evaluator DTO accessors are consistent across tasks.
- TDD order: every behavior-changing task begins with a failing focused test and an explicit RED command before production edits.
