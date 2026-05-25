# Work Order Conditional Required Fields Backend Result

Date: 2026-05-25

## Summary

Backend production validation was already present in `WorkOrderService.validateTypeRequiredRelations(...)`, so no backend production code was changed.

Added controller contract coverage for the existing conditional business validation:

- `type == EMERGENCY` requires `repairRequestId`
- `type == DEFECT` requires `defectId`

## Files Changed

- `src/test/java/com/toir/controller/WorkOrderControllerContractTest.java`
- `logs/work-order-conditional-required-fields-backend-result.md`

## Business Rule Implemented / Preserved

Existing backend service validation remains:

```java
request.type() == WorkOrderType.EMERGENCY && request.repairRequestId() == null
```

returns `400 Bad Request`.

```java
request.type() == WorkOrderType.DEFECT && request.defectId() == null
```

returns `400 Bad Request`.

No API field names were changed:

- request field remains `type`
- no `workOrderType` field was introduced
- `WorkOrderDto` response shape was not changed
- DB columns remain nullable because the rules are conditional

## Tests Added / Updated

Added controller contract tests:

- `createEmergencyWorkOrderWithoutRepairRequestReturns400`
- `createDefectWorkOrderWithoutDefectReturns400`

Both tests use the existing controller test style with mocked `WorkOrderService` throwing `RestException.badRequest(...)` and assert the project-standard error body:

- HTTP `400`
- `$.code == 400`
- `$.path == "/api/v1/work-orders"`
- `$.message` contains the expected validation message

## Commands Run

```sh
git diff --check
```

Result:

- Passed.

Requested targeted backend test command attempted:

```sh
mvn -Dtest=WorkOrderControllerContractTest,WorkOrderServiceTest test
```

or Maven wrapper equivalent.

Result:

- Not run.
- This checkout has no executable `./mvnw`.
- `mvn` is not installed in the environment.
- Command reported `MAVEN_UNAVAILABLE`.

## Tests Not Run

- `WorkOrderControllerContractTest`
- `WorkOrderServiceTest`
- Full backend test suite

Reason:

- Maven is unavailable locally.

## Remaining Risks

- Backend tests need to be run in an environment with Maven or a usable Maven wrapper.
- Controller tests mock the service, matching current project style, so service behavior remains covered by existing service tests rather than these controller tests.

## Push Status

No push was performed.
