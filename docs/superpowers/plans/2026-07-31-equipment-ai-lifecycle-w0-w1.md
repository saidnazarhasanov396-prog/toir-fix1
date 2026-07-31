# Equipment AI Lifecycle W0/W1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the approved W0 canonical semantics and a bounded, immutable, read-only `EquipmentLifecycleContext` v1 assembler on the exact audited backend baseline.

**Architecture:** Java 21 records define the external contract. A mandatory caller-supplied policy controls windows, included sections, measurement granularity, and per-section limits. One read-only Spring service executes bounded `limit + 1` repository reads, maps only allowlisted fields, applies canonical precedence, emits data-quality/watermark metadata, and computes an opaque deterministic SHA-256 fingerprint from canonical JSON that excludes `generatedAt`.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring Data JPA, Jackson, JUnit 5, AssertJ, Mockito.

## Global Constraints

- Start at `813872f6d95f101c6583f51add5206c3bdc00de6` on `codex/equipment-ai-lifecycle-w0-w1`.
- Backend W0/W1 only; no frontend, migration, REST controller, export, AI call, result persistence, Kafka, or outbox.
- `schemaVersion` is exactly `"1.0"`.
- All high-cardinality included sections require explicit positive caller limits.
- Tests are written before corresponding production behavior, but the user forbids Maven/JUnit/build execution.
- Verification must say exactly: `NOT RUN — skipped by user instruction.`
- Preserve the pre-existing untracked PPR design document without editing or committing it.

---

### Task 1: Canonical contract vocabulary and mandatory policy

**Files:**
- Create: `docs/ai/equipment-lifecycle/EQUIPMENT_AI_LIFECYCLE_W0_CANONICAL_SPEC.md`
- Create: `src/main/java/com/toir/dto/equipmentlifecycle/EquipmentLifecycleSection.java`
- Create: `src/main/java/com/toir/dto/equipmentlifecycle/EquipmentLifecycleContextPolicy.java`
- Create: `src/main/java/com/toir/dto/equipmentlifecycle/EquipmentLifecycleDataQuality.java`
- Test: `src/test/java/com/toir/dto/equipmentlifecycle/EquipmentLifecycleContextPolicyTest.java`

**Interfaces:**
- Produces: `EquipmentLifecycleContextPolicy(Instant historyStart, Duration futurePlanningHorizon, Set<EquipmentLifecycleSection> includedSections, Map<EquipmentLifecycleSection,Integer> maxRowsBySection, MeasurementGranularity measurementGranularity)`
- Produces: `int requiredLimit(EquipmentLifecycleSection section)`
- Produces: exact availability states from the approved prompt and safe issue severity/code records.

- [ ] **Step 1: Write policy behavior tests**

Create tests that hand-construct policies and assert:

```java
assertThatThrownBy(() -> policyWithoutLimit.requiredLimit(METER_HISTORY))
        .isInstanceOf(IllegalArgumentException.class);
assertThat(validPolicy.requiredLimit(METER_HISTORY)).isEqualTo(25);
```

Cover null history start, negative planning horizon, missing included-section
limit, non-positive limit, immutable defensive copies, and explicitly excluded
optional sections.

- [ ] **Step 2: Implement immutable policy and vocabulary**

Use compact constructors for validation and `Map.copyOf`/`Set.copyOf`.
`MeasurementGranularity` contains `RAW` for W1; it is explicit rather than an
unbounded hidden default.

- [ ] **Step 3: Cross-check W0 specification**

Confirm approved rule, code-proven source, mapping, and unresolved gap are
separate for maintenance, PPR, failures, recurrence, components, statuses,
health score, RUL, time, unit, currency, privacy, availability, ordering,
legacy, and fingerprint semantics.

### Task 2: Immutable v1 DTO and deterministic fingerprint

**Files:**
- Create: `src/main/java/com/toir/dto/equipmentlifecycle/EquipmentLifecycleContextV1.java`
- Create: `src/main/java/com/toir/service/equipmentlifecycle/EquipmentLifecycleFingerprintService.java`
- Test: `src/test/java/com/toir/service/equipmentlifecycle/EquipmentLifecycleFingerprintServiceTest.java`

**Interfaces:**
- Produces: `EquipmentLifecycleContextV1` with all required logical sections.
- Produces: `String fingerprint(EquipmentLifecycleContextV1 context, EquipmentLifecycleContextPolicy policy)`.
- Fingerprint material includes schema, `asOf`, policy, consistency, sections,
  quality, and watermarks; it excludes `generatedAt` and `contextFingerprint`.

- [ ] **Step 1: Write fingerprint and immutability tests**

Use fixed UUIDs and instants. Assert:

```java
assertThat(fingerprintService.fingerprint(first, policy))
        .isEqualTo(fingerprintService.fingerprint(sameContentDifferentGeneratedAt, policy));
assertThat(fingerprintService.fingerprint(first, policy))
        .isNotEqualTo(fingerprintService.fingerprint(changedMeterValue, policy));
```

Also construct lists/maps in different insertion orders and assert equal
fingerprints after canonical ordering. Assert missing numeric values serialize
as null, not zero.

- [ ] **Step 2: Implement the contract records**

Define typed records for core Equipment, hierarchy, technical attributes,
lifecycle events, meter readings, maintenance summary/events, planned
maintenance, Work Orders, Defects, Repair Requests, inspections, condition
measurements, installed/replaced components, warranty, costs, environment,
document metadata, section metadata, issues, and watermarks.

Every measurement carries `unit`; every monetary amount carries `currency`.
Collections defensively normalize null to immutable empty lists.

- [ ] **Step 3: Implement canonical SHA-256**

Use an injected/copy-configured Jackson mapper with alphabetic property and map
ordering. Serialize a dedicated fingerprint-material record, hash UTF-8 bytes
with `MessageDigest.getInstance("SHA-256")`, and return lowercase hex.

### Task 3: Bounded repository access

**Files:**
- Modify: `src/main/java/com/toir/repository/equipment/EquipmentNodeRepository.java`
- Modify: `src/main/java/com/toir/repository/equipment/EquipmentAttributeValueRepository.java`
- Modify: `src/main/java/com/toir/repository/equipment/EquipmentStatusHistoryRepository.java`
- Modify: `src/main/java/com/toir/repository/equipment/EquipmentLocationHistoryRepository.java`
- Modify: `src/main/java/com/toir/repository/MeterReadingRepository.java`
- Modify: `src/main/java/com/toir/repository/ConditionReadingRepository.java`
- Modify: `src/main/java/com/toir/repository/maintenance/MaintenanceCompletionAnchorRepository.java`
- Modify: `src/main/java/com/toir/repository/WorkOrderRepository.java`
- Modify: `src/main/java/com/toir/repository/PprTaskRepository.java`
- Modify: `src/main/java/com/toir/repository/maintenance/MaintenanceDueEventRepository.java`
- Modify: `src/main/java/com/toir/repository/defects/DefectRepository.java`
- Modify: `src/main/java/com/toir/repository/repair/RepairRequestRepository.java`
- Modify: `src/main/java/com/toir/repository/sparepartlifecycle/SparePartInstallationRepository.java`
- Modify: `src/main/java/com/toir/repository/TechnicalDocumentRepository.java`
- Modify: `src/main/java/com/toir/repository/actualCost/ActualCostRepository.java`
- Create: `src/main/java/com/toir/repository/inspection/EquipmentInspectionLifecycleProjection.java`
- Create: `src/main/java/com/toir/repository/inspection/EquipmentInspectionLifecycleRepository.java`

**Interfaces:**
- Each query accepts Equipment ID, window endpoints where relevant, and
  `limitPlusOne`.
- Each query excludes soft-deleted rows and orders by business time then ID.
- Inspection query joins result, checkpoint, and round and returns only direct
  Equipment checkpoint linkage.

- [ ] **Step 1: Write repository contract tests**

Use reflection/query-annotation contract tests in the existing repository-test
style to assert the bounded methods expose Equipment ID, window, and limit and
the query text contains soft-delete and deterministic order predicates.

- [ ] **Step 2: Add bounded native/JPQL queries**

Use `LIMIT :limitPlusOne` for native queries. Do not add total-count queries.
For current collections, order by semantic key/updated time and ID. For
histories, use the canonical business timestamp and ID.

- [ ] **Step 3: Add batched reference lookups only where already proven**

Reuse `findAllByIdInAndIsDeletedFalse` for meter, definition, Work Order, spare
part, and other safe reference enrichment. Never fetch User/Employee or file
content entities for the context.

### Task 4: Read-only assembler core, bounds, and coverage

**Files:**
- Create: `src/main/java/com/toir/service/equipmentlifecycle/EquipmentLifecycleContextAssembler.java`
- Test: `src/test/java/com/toir/service/equipmentlifecycle/EquipmentLifecycleContextAssemblerTest.java`

**Interfaces:**
- Produces: `EquipmentLifecycleContextV1 assemble(UUID equipmentId, Instant asOf, EquipmentLifecycleContextPolicy policy)`.
- Annotated `@Transactional(readOnly = true)`.
- Uses injected `Clock` and follows `RestException.notFound`.

- [ ] **Step 1: Write tests for core semantics and bounds**

Assert one captured `asOf`, generated time from a fixed Clock, required limits,
limit+1 truncation, deterministic history ordering, soft-deleted Equipment
not-found behavior, excluded section metadata, and empty-versus-unavailable
distinction.

- [ ] **Step 2: Map current-state sections**

Map Equipment/passport, hierarchy, attributes, warranty, environment, and safe
document metadata. Exclude contacts, actor IDs, file IDs/URLs/body, notes,
descriptions, and raw JSON text.

- [ ] **Step 3: Map bounded histories**

Map status/location, meter, condition, direct Equipment inspections, Work
Orders, PPR/due events, Defects, Repair Requests, components, and direct costs.
Fetch `limit + 1`, trim to the policy limit, and emit section metadata and
watermarks from returned rows only.

- [ ] **Step 4: Finalize data quality and fingerprint**

Collect safe issue codes, build a fingerprint-free context, calculate the
fingerprint, and return an identical copy with `contextFingerprint` populated.

### Task 5: Canonical maintenance, failure, and component behavior

**Files:**
- Modify: `src/main/java/com/toir/service/equipmentlifecycle/EquipmentLifecycleContextAssembler.java`
- Test: `src/test/java/com/toir/service/equipmentlifecycle/EquipmentLifecycleCanonicalMappingTest.java`

**Interfaces:**
- Anchor-first maintenance mapping.
- Work Order fallback only for `COMPLETED`/`CLOSED`.
- PPR completion never creates actual-maintenance history.
- Defect, Repair Request, and condition sections remain separate.
- Installation rows are the sole source for installed/replaced components.

- [ ] **Step 1: Write canonical mapping tests**

Cover all requested behaviors:

1. anchor precedence;
2. completed/closed Work Order fallback;
3. closed with missing actual timestamp returns null plus warning;
4. cancelled/non-completed work excluded from successful maintenance;
5. linked PPR/Work Order not double-counted;
6. Defect/request/condition separation;
7. stored recurrence ignored and unknown warning;
8. issued material absent from installed components;
9. installation/removal mapping;
10. unit/currency preservation;
11. deterministic deduplication;
12. legacy warning behavior;
13. privacy-bearing fields absent.

- [ ] **Step 2: Implement anchor-first deduplication**

Index anchors by Work Order ID, emit anchor events first, then terminal Work
Orders without an anchor. Preserve source IDs and null completion time.

- [ ] **Step 3: Implement failure and recurrence policy**

Map Defect facts without descriptions. Keep Repair Requests and condition
signals separate. Set derived recurrence null and add
`UNRELIABLE_RECURRENCE` while normalization remains unproven.

- [ ] **Step 4: Implement component and cost policy**

Split active versus removed/replaced installation records. Do not read material
issue/reservation as installation. Aggregate only directly Equipment-linked
Actual Costs by status under explicit `UZS`; mark truncation/indirect-cost
exclusion.

### Task 6: JSON Schema, synthetic example, and compatibility checks

**Files:**
- Create: `docs/ai/equipment-lifecycle/equipment-lifecycle-context-v1.schema.json`
- Create: `docs/ai/equipment-lifecycle/equipment-lifecycle-context-v1.example.json`
- Test: `src/test/java/com/toir/service/equipmentlifecycle/EquipmentLifecycleContractShapeTest.java`

**Interfaces:**
- Schema mirrors serialized DTO property names and required/null behavior.
- Example contains synthetic IDs and no real Equipment/person/financial data.

- [ ] **Step 1: Write contract/privacy tests**

Parse the example with the repository-configured Jackson mapper and assert schema version,
fingerprint shape, null numeric fields, provenance, truncation, and absence of
contact/token/binary/signed URL property names.

- [ ] **Step 2: Write JSON Schema**

Use draft 2020-12, `$defs` for reusable metadata/issue/watermark structures,
explicit required arrays, and nullable unions matching Java records.

- [ ] **Step 3: Write the synthetic example**

Use UUID values under the `00000000-0000-0000-0000-*` namespace, synthetic
Equipment codes, UTC instants, nulls, one truncation warning, and one
parser-required document-metadata issue.

### Task 7: Implementation report and allowed static verification

**Files:**
- Create: `EQUIPMENT_AI_LIFECYCLE_W0_W1_IMPLEMENTATION_2026-07-31.md`
- Modify: task files only when static review finds defects.

**Interfaces:**
- Report contains all 19 required sections and the exact verification text.

- [ ] **Step 1: Write implementation report**

Record exact baseline/branch/final HEAD, every section state/source, precedence,
fingerprint, bounds, privacy, repository queries, tests, limitations, deferred
W2 work, and files changed.

- [ ] **Step 2: Perform allowed static checks**

Run only:

```bash
rg
git diff --check
git status
git diff
git rev-parse
```

Do not run Maven, Gradle, JUnit, Testcontainers, npm, Vitest, compilation, or
application startup. Report `NOT RUN — skipped by user instruction.`

- [ ] **Step 3: Review scope**

Confirm frontend is unchanged, no migration/controller/export/model/Kafka code
exists, and the pre-existing untracked PPR design document is neither staged
nor modified.

- [ ] **Step 4: Commit and push**

Stage only W0/W1 task files, commit with:

```text
Add equipment lifecycle AI context foundation
```

Push only `codex/equipment-ai-lifecycle-w0-w1` without force.
