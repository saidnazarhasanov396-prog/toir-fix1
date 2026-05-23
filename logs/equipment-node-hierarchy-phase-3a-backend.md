# Equipment Node Hierarchy Phase 3A Backend Report

## Branch
- `codex/equipment-lifecycle-main-sync-backend`

## Files changed
- `src/main/java/com/toir/dto/equipmentnode/EquipmentNodeDto.java`
- `src/main/java/com/toir/repository/equipment/EquipmentNodeRepository.java`
- `src/main/java/com/toir/service/equipment/EquipmentNodeService.java`
- `src/test/java/com/toir/controller/equipment/EquipmentNodeControllerContractTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentNodeServiceTest.java`

## DTO parent field decision
- Canonical response field remains `parentId`.
- Request payloads now accept both `parentId` and the frontend compatibility alias `parentNodeId`.
- Internally, both inputs normalize to `EquipmentNodeDto.parentId`.

## Alias compatibility decision
- `parentNodeId` was added as a Jackson alias only.
- No public response rename was introduced, so existing backend clients using `parentId` remain compatible.

## Hierarchy validation rules
- Parent node must exist.
- Parent node must belong to the same equipment as the child node.
- A node cannot be assigned as its own parent.
- Updates prevent circular parent chains by walking parent ancestry before saving.
- List endpoint continues to return flat nodes with enough fields for frontend tree rendering: `id`, `equipmentId`, `parentId`, `code`, `name`, `nodeType`, `serialNumber`, and `description`.

## Serial number decision
- `serialNumber` was already present on the entity and DTO.
- Service-level uniqueness validation was added for non-blank serial numbers within the same equipment and active nodes.
- No database unique index was added in this phase because existing data was not audited for duplicates and no current Flyway migration for `equipment_nodes` was present in the inspected migration folder.

## Delete restriction behavior
- Existing child-node restriction is preserved.
- Delete validation is now centralized in `assertNodeCanBeDeleted`.
- The method includes the extension point for future checks against defects, documents, repair history, and work orders, but those integrations were not implemented in this phase.

## Migration
- No migration was added.
- Schema changes were not required for the DTO alias, parent validation, cycle validation, or service-level serial uniqueness.

## Tests added/updated
- Added `EquipmentNodeServiceTest`
  - create with parent builds hierarchy
  - parent from different equipment is rejected
  - self-parent update is rejected
  - circular parent update is rejected
  - delete with children is rejected
  - duplicate code within equipment is rejected
  - same code in different equipment is allowed
  - duplicate serial within equipment is rejected
- Added `EquipmentNodeControllerContractTest`
  - list response returns `parentId`
  - create accepts `parentId`
  - create accepts `parentNodeId` alias
  - update returns controlled error for circular hierarchy

## Commands run
- `git status --short --branch`
- `/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn -Dtest=EquipmentNodeServiceTest,EquipmentNodeControllerContractTest test`
- `rg --files src/test/java | rg 'Equipment(Node|.*Equipment).*Test'`
- `nc -z localhost 5433; printf 'postgres_5433_exit=%s\n' "$?"`
- `git diff --check`
- `git diff --name-status`
- `git status --short --branch`

## Targeted test result
- Command: `/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn -Dtest=EquipmentNodeServiceTest,EquipmentNodeControllerContractTest test`
- Result: `BUILD SUCCESS`
- Tests: 12 run, 0 failures, 0 errors, 0 skipped

## Diff check result
- `git diff --check`: passed with no whitespace errors.

## Full Maven test status
- Full `mvn test` was not run.
- PostgreSQL on `localhost:5433` was checked and was not reachable: `postgres_5433_exit=1`.
- Previous backend phases also documented the full test dependency on the local PostgreSQL test database.

## Remaining risks
- Serial-number uniqueness is enforced in service code, not by a database unique constraint.
- Delete restriction still only blocks child nodes today. Defect, document, repair history, and work-order usage checks remain future integration work.
- Equipment nodes are still not integrated as defect targets.
- Node documents and node repair history are not implemented in this phase by design.
- No max-depth rule was added because no existing business rule was found.

## Frontend Phase 3B readiness
- Frontend Phase 3B can start.
- The backend now accepts the current frontend `parentNodeId` request alias while preserving canonical `parentId` responses for tree construction.
