# PPR Phase 4A Backend Work Order Generation Result

## 1. Files changed

- `src/main/java/com/toir/controller/PprPlanController.java`
- `src/main/java/com/toir/repository/WorkOrderRepository.java`
- `src/main/java/com/toir/service/PprGeneratorService.java`
- `src/test/java/com/toir/controller/PprPlanControllerContractTest.java`
- `src/test/java/com/toir/security/PprPbacScopeTest.java`
- `src/test/java/com/toir/security/RbacPprSecurityTest.java`
- `src/test/java/com/toir/service/PprGeneratorServiceLifecycleTest.java`

No migration was added. Idempotency is enforced in service logic.

## 2. Endpoint added

Added:

```http
POST /api/v1/ppr-plans/{planId}/work-orders/generate?createdById={userId}
```

The endpoint uses the existing PPR plan generate authorization and existing PBAC plan access checks before delegating to the generator service.

Response shape:

```json
{
  "planId": "uuid",
  "createdCount": 1,
  "skippedCount": 1,
  "createdWorkOrderIds": ["uuid"],
  "skippedItems": [
    {
      "pprTaskId": "uuid",
      "reason": "WORK_ORDER_ALREADY_EXISTS"
    }
  ]
}
```

## 3. Service behavior

Implemented `PprGeneratorService.generateWorkOrdersForPlan(UUID planId, UUID createdById)`.

Behavior:

- Loads the PPR plan by id.
- Requires `createdById`.
- Requires plan status `APPROVED` or `IN_PROGRESS`.
- Loads tasks for the plan.
- Creates one WorkOrder per eligible PPR task.
- Uses `WorkOrderService.create(...)` so existing WorkOrder validation remains active.
- Does not start, complete, or otherwise mutate PPR tasks.
- Does not create PlannedShutdown records.
- Does not change equipment status.
- Does not implement operating-hours due calculation.
- Does not use `averageOperatingLifeHours` as runtime.

## 4. Eligibility rules

Eligible task:

- `PprTask.status == APPROVED`
- `PprTask.equipmentId` is present
- no active/non-deleted WorkOrder already exists for the same `pprTaskId`
- target equipment can be loaded
- department can be resolved from equipment department, falling back to plan department

Skip reasons:

- `TASK_STATUS_NOT_APPROVED`
- `TASK_EQUIPMENT_MISSING`
- `WORK_ORDER_ALREADY_EXISTS`
- `EQUIPMENT_NOT_FOUND`
- `DEPARTMENT_MISSING`

Invalid plan status is rejected with a business 400 instead of returning skips.

## 5. WorkOrder field mapping

Generated WorkOrder mapping:

- `number`: generated server-side as `WO-PPR-{year}-{sequence}`
- `title`: `PprTask.title`
- `equipmentId`: `PprTask.equipmentId`
- `departmentId`: equipment department, fallback to plan department
- `pprTaskId`: `PprTask.id`
- `createdById`: request query parameter
- `startPlannedAt`: `PprTask.scheduledStart`
- `endPlannedAt`: `PprTask.scheduledEnd`
- `priority`: `PprTask.priority`
- `summary`: includes PPR plan code/name and task code

The implementation intentionally goes through `WorkOrderService.create(...)` to preserve manual PPR task to WorkOrder validation, including the equipment match validation added from main.

## 6. Type mapping

WorkOrder type mapping:

- `PREVENTIVE_MAINTENANCE`:
  - regulation kind `INSPECTION` or `DIAGNOSTIC` -> `WorkOrderType.INSPECTION`
  - otherwise -> `WorkOrderType.PLANNED`
- `PLANNED_REPAIR` -> `WorkOrderType.PLANNED`
- `CAPITAL_REPAIR` -> `WorkOrderType.OVERHAUL`
- legacy/null PPR type -> `WorkOrderType.PLANNED`

Work type mapping:

- regulation kind `INSPECTION` or `DIAGNOSTIC` -> `WorkType.DIAGNOSTICS`
- otherwise -> `WorkType.REPAIR`

## 7. Idempotency behavior

Before creating a WorkOrder, the service checks for an active/non-deleted WorkOrder with the same `pprTaskId`.

Added repository method:

```java
existsByPprTaskIdAndIsDeletedFalse(UUID pprTaskId)
```

No DB-level unique index was added in this phase. Remaining concurrency risk is documented below.

## 8. Tests added/updated

Controller/security:

- `PprPlanControllerContractTest`
  - verifies the new generate WorkOrders endpoint response contract
- `RbacPprSecurityTest`
  - verifies PPR generate permission can call WorkOrder generation
  - verifies read-only PPR permission cannot call WorkOrder generation
- `PprPbacScopeTest`
  - verifies PBAC plan scope is checked before WorkOrder generation delegation

Service:

- `PprGeneratorServiceLifecycleTest`
  - approved plan + approved task creates linked WorkOrder
  - generated WorkOrder includes `pprTaskId`
  - preventive inspection maps to inspection/diagnostics
  - planned repair maps to planned WorkOrder
  - capital repair maps to overhaul WorkOrder
  - task without equipment is skipped
  - existing active WorkOrder is skipped
  - non-approved task is skipped
  - draft plan rejects WorkOrder generation
  - generated WorkOrder uses task scheduled dates

Existing PPR generator and WorkOrder service regression tests were included in targeted verification.

## 9. Verification results

Passed:

```bash
/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn -q -DskipTests compile
```

Passed:

```bash
/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn test -Dtest=PprPlanControllerContractTest,PprPlanServiceLifecycleTest,PprGeneratorServiceLifecycleTest,PprGeneratorDynamicConditionTest,WorkOrderServiceTest
```

Result: 148 tests run, 0 failures, 0 errors.

Passed:

```bash
/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn test -Dtest=RbacPprSecurityTest,PprPlanEndpointSecurityTest,PprPbacScopeTest
```

Result: 47 tests run, 0 failures, 0 errors.

Full suite command:

```bash
/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn test
```

Failed due local PostgreSQL being unavailable at `localhost:5433`.

Exact root cause:

```text
Connection to localhost:5433 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.
```

Full-suite summary after the environment failure:

```text
Tests run: 1784, Failures: 0, Errors: 48, Skipped: 5
```

Diff whitespace check:

```bash
git diff --check
```

Passed.

## 10. Remaining risks

- WorkOrder idempotency is service-level only. Concurrent generation requests could race before either transaction commits. A later phase should consider a partial unique index on active `work_orders(ppr_task_id)` after checking existing data.
- Generated WorkOrder number allocation uses the existing repository existence check and in-process reservation for the current batch. This is safe for a single generation call but not fully concurrency-proof.
- Only `APPROVED` PPR tasks are eligible. If product later wants direct generation from `PLANNED` tasks, the eligibility policy must be changed deliberately.
- Plans must be `APPROVED` or `IN_PROGRESS`. If current operations expect generation from `GENERATED`, that status should be reviewed before frontend integration.
- Capital repair generation creates WorkOrders only; planned shutdowns and equipment out-of-service status changes remain intentionally out of scope.

## 11. Next phase recommendation

Recommended next phase:

1. Add frontend action for `POST /ppr-plans/{planId}/work-orders/generate`.
2. Display created/skipped counts and skip reasons.
3. Add optional DB-level idempotency hardening for `pprTaskId`.
4. Later, implement operating-hours due calculation from a real current counter source, not `averageOperatingLifeHours`.
