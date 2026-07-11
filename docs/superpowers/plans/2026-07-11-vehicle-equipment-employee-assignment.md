# Vehicle and Equipment Employee Assignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce Employee identity for Vehicle drivers, Equipment responsible people, and Equipment operators across writes, seeds, legacy migration, reads, and frontend clear behavior.

**Architecture:** Normal writes accept only `hr_employees.id`; User compatibility conversion exists only in seed/migration code. An additive migration classifies and converts exact-one legacy mappings before validating the Equipment Employee FK. Equipment update uses `clearResponsible` to distinguish omitted, assigned, and cleared states.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, PostgreSQL/Flyway, JUnit 5, Mockito, Testcontainers PostgreSQL 16, React, TypeScript, Vitest, Vite.

## Global Constraints

- Do not convert authentication, RBAC, audit, or action actors from User to Employee.
- Add `V20260711_3__equipment_responsible_employee_identity.sql`; versions `_1` and `_2` already exist, and applied migrations must not be edited.
- Never infer identity by name, phone, or email, and never silently null/delete unresolved rows.
- Normal APIs reject User-only UUIDs and have no User-to-Employee fallback.
- Preserve and finally commit/push every pre-existing working-tree change as explicitly authorized.
- Do not mutate remote/shared databases.

---

### Task 1: Read-only preflight

**Files:**
- No source changes.

**Interfaces:**
- Consumes: current update DTO, seed sources, and payload builder.
- Produces: recorded repository, migration, and baseline verification evidence.

- [ ] **Step 1: Capture baseline**

Run `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD`, and `git status --short` in both repositories; record the latest migration with `ls src/main/resources/db/migration | sort -V | tail -1`.

- [ ] **Step 2: Run baseline gates**

```bash
JAVA_HOME=/Users/tenzorsoft/Library/Java/JavaVirtualMachines/openjdk-24.0.1/Contents/Home ./mvnw -Dtest=VehicleServiceDriverAssignmentTest,EquipmentResponsibleRefTest,EquipmentUsageSessionServiceTest test
```

Run `yarn build` in the frontend. Expected: current focused tests/build pass or exact environmental blockers are recorded.

- [ ] **Step 3: Preserve the baseline evidence**

Write exact commands, commits, dirty paths, test counts, build result, and environmental blockers into the Task 1 report. Do not edit or commit repository files. RED tests are added and run by Tasks 2, 4, and 5 immediately before their corresponding implementations.

---

### Task 2: Correct Java and SQL seed identity

**Files:**
- Modify: `src/main/java/com/toir/repository/users/EmployeeRepository.java`
- Modify: `src/main/java/com/toir/config/SampleDataSeeder.java`
- Modify: `src/main/resources/db/navoiy-azot-seed/04_assets_equipment_vehicles.sql`
- Create: `src/test/java/com/toir/bootstrap/OperationalAssignmentSeedIdentityContractTest.java`
- Create or modify: `src/test/java/com/toir/config/SampleDataSeederTest.java`

**Interfaces:**
- Produces: `List<Employee> findAllByUserIdAndIsDeletedFalse(UUID)` and exact-one Employee seed mapping.

- [ ] **Step 1: Add exact-one repository query**

```java
@Query(value = "SELECT * FROM hr_employees WHERE user_id = cast(:userId as uuid) AND is_deleted = false ORDER BY id", nativeQuery = true)
List<Employee> findAllByUserIdAndIsDeletedFalse(@Param("userId") UUID userId);
```

Before implementation, add zero/one/multiple mapping tests and run them to capture the expected RED failure.

- [ ] **Step 2: Implement Java seed resolver**

```java
private UUID uniqueEmployeeIdForUser(UUID userId) {
    if (userId == null) return null;
    List<Employee> matches = employeeRepository.findAllByUserIdAndIsDeletedFalse(userId);
    if (matches.size() == 1) return matches.getFirst().getId();
    log.warn("Equipment seed responsible mapping skipped: userId={}, employeeMatches={}", userId, matches.size());
    return null;
}
```

Use only the returned Employee ID in `linkEquipmentCriticalityAndResponsible`.

- [ ] **Step 3: Replace Navoiy User projection**

```sql
LEFT JOIN users u ON u.username = v.responsible_username AND u.is_deleted = false
LEFT JOIN LATERAL (
    SELECT min(e.id) AS id FROM hr_employees e
    WHERE e.user_id = u.id AND e.is_deleted = false
    HAVING count(*) = 1
) responsible_employee ON true
```

Project `responsible_employee.id`, never `u.id`. Preserve the Vehicle `driver.id` mapping.

- [ ] **Step 4: Add zero/one/multiple mapping tests and static source contracts**

Assert one match stores Employee ID, zero/multiple store null with no User fallback, SQL contains `responsible_employee.id`, and Java no longer contains `eq.setResponsibleId(admin)`.

- [ ] **Step 5: Run and commit**

Run `./mvnw -Dtest=OperationalAssignmentSeedIdentityContractTest,SampleDataSeederTest test`; then commit only Task 2 files with `fix: seed equipment responsibility with employee ids`.

---

### Task 3: Add safe Equipment identity migration

**Files:**
- Create: `src/main/resources/db/migration/V20260711_3__equipment_responsible_employee_identity.sql`
- Create: `src/test/java/com/toir/migration/EquipmentResponsibleEmployeeIdentityMigrationContractTest.java`
- Create: `src/test/java/com/toir/migration/EquipmentResponsibleEmployeeIdentityMigrationPostgresTest.java`

**Interfaces:**
- Produces: converted exact-one mappings, `idx_equipment_responsible_id`, and validated `fk_equipment_responsible_employee`.

- [ ] **Step 1: Write failing SQL contract**

```java
assertThat(sql).contains("REFERENCES hr_employees(id) NOT VALID")
    .contains("VALIDATE CONSTRAINT fk_equipment_responsible_employee")
    .contains("CREATE INDEX IF NOT EXISTS idx_equipment_responsible_id")
    .doesNotContain("SET responsible_id = NULL")
    .doesNotContain("DELETE FROM equipment");
```

- [ ] **Step 2: Implement classification/backfill**

Create a temporary classification table containing `VALID_EMPLOYEE`, `LEGACY_USER_WITH_UNIQUE_EMPLOYEE`, `AMBIGUOUS`, and `UNRESOLVED`. Convert only:

```sql
UPDATE equipment e
SET responsible_id = c.mapped_employee_id, updated_at = now()
FROM equipment_responsible_identity_classification c
WHERE e.id = c.equipment_id
  AND c.classification = 'LEGACY_USER_WITH_UNIQUE_EMPLOYEE';
```

Raise a bounded descriptive exception before update/constraint work if ambiguous or unresolved rows exist.

- [ ] **Step 3: Add FK and comment**

```sql
CREATE INDEX IF NOT EXISTS idx_equipment_responsible_id ON equipment(responsible_id);
ALTER TABLE equipment ADD CONSTRAINT fk_equipment_responsible_employee
  FOREIGN KEY (responsible_id) REFERENCES hr_employees(id) NOT VALID;
ALTER TABLE equipment VALIDATE CONSTRAINT fk_equipment_responsible_employee;
COMMENT ON COLUMN equipment.responsible_id IS
  'Operational responsible Employee identity; references hr_employees.id';
```

- [ ] **Step 4: Add PostgreSQL scenarios**

Cover valid preservation, unique conversion, null, User-only failure, multiple-Employee failure, orphan failure, post-FK User rejection, clean-schema migration, and unchanged actor columns.

- [ ] **Step 5: Run and commit**

Run migration contract/Postgres tests and `FlywayEmptyDbSmokeTest`; record Docker skips exactly. Commit Task 3 files with `feat: enforce equipment responsible employee identity`.

---

### Task 4: Backend tri-state Equipment update

**Files:**
- Modify: `src/main/java/com/toir/dto/equipment/EquipmentUpdateRequest.java`
- Modify: `src/main/java/com/toir/service/equipment/EquipmentService.java`
- Modify: `src/test/java/com/toir/service/equipment/EquipmentServiceTest.java`
- Modify: affected DTO/controller constructor tests.

**Interfaces:**
- Consumes: `UUID responsibleId` and nullable `Boolean clearResponsible`.
- Produces: preserve/assign/clear semantics.

- [ ] **Step 1: Add `clearResponsible` to the update record and compatibility constructors**

Place it after `responsibleId`; legacy constructors pass `null`.

Before implementation, add the clear/preserve/contradictory tests from the approved design and run them to capture the expected RED failure.

- [ ] **Step 2: Implement the single update helper**

```java
private void applyResponsibleUpdate(Equipment entity, EquipmentUpdateRequest request) {
    if (Boolean.TRUE.equals(request.clearResponsible())) {
        if (request.responsibleId() != null) {
            throw RestException.badRequest("responsibleId must be omitted when clearResponsible is true");
        }
        entity.setResponsibleId(null);
    } else if (request.responsibleId() != null) {
        validateResponsibleEmployee(request.responsibleId());
        entity.setResponsibleId(request.responsibleId());
    }
}
```

Remove the old null-coalescing setter and duplicate update validation.

- [ ] **Step 3: Cover all identity boundaries**

Test preserve, clear, assign, contradictory payload, Employee without User, and User-only UUID rejected via empty `EmployeeRepository`; verify no User fallback.

- [ ] **Step 4: Run and commit**

Run `EquipmentServiceTest,EquipmentResponsibleRefTest,EquipmentControllerContractTest`; commit Task 4 files with `fix: support explicit equipment responsible clearing`.

---

### Task 5: Frontend explicit clear and selector contracts

**Files:**
- Modify: `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/lib/equipment-registry-form.ts`
- Modify: `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/lib/equipment-registry-form.test.ts`
- Modify: `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/lib/api.ts`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/modules/equipment/libs/tests/operational-assignment-selectors.test.ts`

**Interfaces:**
- Produces: explicit null/clear for empty edit and Employee-ID selector guarantees.

- [ ] **Step 1: Build explicit update payload**

First add the explicit-clear payload test and run it to capture the expected RED failure.

```typescript
const responsibleId = form.responsibleId?.trim() || null;
return {
  name: form.name.trim(),
  attributes,
  responsibleId,
  clearResponsible: responsibleId === null,
};
```

Update API type to `responsibleId?: string | null; clearResponsible?: boolean`.

- [ ] **Step 2: Add selector source contracts**

Assert Vehicle, Equipment, and operator selectors call `getEmployees`, use Employee `id`, never option-map `employee.userId`, and do not use `getUsers` as fallback.

- [ ] **Step 3: Run and commit**

Run focused Vitest files and `yarn build`; commit Task 5 files with `fix: send explicit equipment responsible clear`.

---

### Task 6: Canonical reads and Employee export

**Files:**
- Modify if required: `src/main/java/com/toir/service/VehicleService.java`
- Modify: `src/test/java/com/toir/service/VehicleServiceDriverAssignmentTest.java`
- Modify: `src/main/java/com/toir/service/ReportsService.java`
- Modify: `src/test/java/com/toir/service/ReportsServiceTest.java`
- Modify: `src/test/java/com/toir/service/equipment/EquipmentResponsibleRefTest.java`

**Interfaces:**
- Produces: canonical `assigned_driver_id` proof, unresolved-ID integrity error, and batch Employee export fields.

- [ ] **Step 1: Add canonical Vehicle read test**

Set `equipment.responsibleId` to a different Employee ID and assert detail returns `details.assignedDriverId`. Modify production only if the test exposes a mismatch.

- [ ] **Step 2: Reject unresolved Equipment response identity**

For non-null responsible UUID absent from the batch Employee map, throw a data-integrity exception containing Equipment ID and responsible UUID; never query User.

- [ ] **Step 3: Batch-enrich CSV**

Keep raw `responsibleId`, add `responsibleEmployeeCode` and `responsibleEmployeeName`, and load all Employees in one repository call. Test one call for multiple rows.

- [ ] **Step 4: Run and commit**

Run `VehicleServiceDriverAssignmentTest,EquipmentResponsibleRefTest,ReportsServiceTest`; commit with `fix: enforce employee identity in assignment reads`.

---

### Task 7: Vehicle preflight and rollout checklist

**Files:**
- Create: `scripts/db/preflight_vehicle_driver_employee_identity.sql`
- Create: `docs/rollout/vehicle-equipment-employee-assignment-rollout.md`
- Create: `src/test/java/com/toir/migration/VehicleDriverEmployeeIdentityPreflightContractTest.java`

**Interfaces:**
- Produces: read-only classification and deploy evidence checklist.

- [ ] **Step 1: Write read-only classification query**

Return all four classifications with Vehicle/equipment IDs, responsible UUID, User match, and Employee-link count. Include no DML.

- [ ] **Step 2: Guard rollout risk**

```java
assertThat(preflightSql).contains("VALID_EMPLOYEE", "LEGACY_USER_WITH_UNIQUE_EMPLOYEE", "AMBIGUOUS", "UNRESOLVED");
assertThat(preflightSql.toUpperCase()).doesNotContain("UPDATE ").doesNotContain("DELETE ");
assertThat(vehicleMigrationSql).contains("SET assigned_driver_id = NULL");
```

- [ ] **Step 3: Write rollout checklist**

Include remote commits, Flyway state, pending migrations, counts, validated FK definitions, User-only proofs, actor-field proof, GET API checks, UI checks, and `BLOCKED` status without authenticated evidence.

- [ ] **Step 4: Run and commit**

Run `VehicleDriverEmployeeIdentityPreflightContractTest`; commit with `docs: add employee assignment rollout safeguards`.

---

### Task 8: Verification, authorized all-change commits, and push

**Files:**
- Verify and commit every changed/untracked file in both repositories.

**Interfaces:**
- Produces: verified commits pushed to both `Codex_org` branches.

- [ ] **Step 1: Run complete focused backend gate**

```bash
JAVA_HOME=/Users/tenzorsoft/Library/Java/JavaVirtualMachines/openjdk-24.0.1/Contents/Home ./mvnw -Dtest=VehicleServiceDriverAssignmentTest,EquipmentServiceTest,EquipmentResponsibleRefTest,EquipmentUsageSessionServiceTest,OperationalAssignmentSeedIdentityContractTest,SampleDataSeederTest,EquipmentResponsibleEmployeeIdentityMigrationContractTest,EquipmentResponsibleEmployeeIdentityMigrationPostgresTest,VehicleDriverEmployeeIdentityPreflightContractTest,ReportsServiceTest,FlywayEmptyDbSmokeTest test
```

Expected: zero failures/errors; Docker-dependent skips are reported and block a production-complete claim.

- [ ] **Step 2: Run frontend gate**

Run focused Vitest files, then `yarn build`. Expected: zero test failures and build exit 0.

- [ ] **Step 3: Review final scope**

Run `git diff --check`, `git diff --stat`, `git status --short`, and focused identity searches. Confirm action User fields remain unchanged.

- [ ] **Step 4: Commit all remaining authorized files**

Run `git add -A` and commit separately in backend and frontend with repository-appropriate finalization messages. This intentionally includes every pre-existing change authorized by the user.

- [ ] **Step 5: Verify committed state**

Run `git status --short`, `git log -5 --oneline`, and `git show --stat --oneline HEAD` in both repositories. Expected: clean trees and intended commits.

- [ ] **Step 6: Push without force**

Run `git push origin Codex_org` separately in backend and frontend. Expected: both pushes succeed.

- [ ] **Step 7: Report honestly**

Print starting/final commits, migration/FK results, conversion classifications, exact tests/skips, protected-runtime blocker, file groups, final statuses, pushed commits, and `PARTIAL` unless authenticated remote database/runtime proof is available.
