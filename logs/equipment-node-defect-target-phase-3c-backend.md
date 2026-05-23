# Equipment Node Defect Target Phase 3C Backend Report

## Summary
- Added optional defect target field `equipmentNodeId`.
- Defects can still target equipment only.
- Defects can now target equipment plus one active/non-deleted equipment node.
- Defect list/detail responses now include equipment node reference fields when present.
- Equipment node delete now fails with a controlled conflict when active/non-deleted defects reference the node.

## Files Changed
- `src/main/java/com/toir/entity/defects/Defect.java`
- `src/main/java/com/toir/dto/defect/DefectRequest.java`
- `src/main/java/com/toir/dto/defect/DefectDto.java`
- `src/main/java/com/toir/dto/defect/DefectResponse.java`
- `src/main/java/com/toir/repository/defects/DefectRepository.java`
- `src/main/java/com/toir/service/defects/DefectService.java`
- `src/main/java/com/toir/service/equipment/EquipmentNodeService.java`
- `src/test/java/com/toir/service/defects/DefectServiceTest.java`
- `src/test/java/com/toir/controller/DefectControllerContractTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentNodeServiceTest.java`
- `src/test/java/com/toir/model/TriadSchemaModelContractTest.java`
- `src/main/resources/db/migration/V20260523_4__defects_equipment_node_target.sql`
- `logs/equipment-node-defect-target-phase-3c-backend.md`
- `logs/equipment-node-defect-target-phase-3c-frontend-impact.md`

## Migration Added
- `V20260523_4__defects_equipment_node_target.sql`
- Adds nullable `defects.equipment_node_id`.
- Adds partial index `idx_defects_equipment_node_id`.
- Adds FK `fk_defects_equipment_node` from `defects.equipment_node_id` to `equipment_nodes.id`.

## DTO / API Contract Changes
- `DefectRequest` now accepts optional `equipmentNodeId`.
- Existing request payloads without `equipmentNodeId` still work.
- Existing `DefectRequest` positional constructor compatibility was preserved for backend tests/code.
- `DefectResponse` now includes:
  - `equipmentNodeId`
  - `equipmentNodeCode`
  - `equipmentNodeName`
  - `equipmentNodeType`
- Existing response fields were not renamed.
- Existing `DefectResponse` positional constructor compatibility was preserved for backend tests/code.

## Validation Rules
- Create defect:
  - Existing equipment validation/status guard remains in place through `EquipmentStatusLifecycleService`.
  - If `equipmentNodeId` is present, the service loads it with `findByIdAndIsDeletedFalse`.
  - Missing/deleted node returns `404 Equipment node not found: {id}`.
  - Node from a different equipment returns `400 Equipment node belongs to a different equipment`.
- Update defect:
  - If `equipmentNodeId` is present, the same node existence and same-equipment validation runs.
  - Sending `equipmentNodeId: null` clears the existing node target.
  - Existing repair-request consistency validation remains unchanged.

## Delete Restriction Behavior
- `EquipmentNodeService.delete` now checks `DefectRepository.existsByEquipmentNodeIdAndIsDeletedFalse`.
- If any active/non-deleted defect references the node, delete fails with:
  - HTTP status: `409 Conflict`
  - Message: `Equipment node is referenced by active defects`
- Existing child-node delete restriction remains first and unchanged.

## Tests Added / Updated
- `DefectServiceTest`
  - `createDefect_withEquipmentNode_setsNodeTarget`
  - `createDefect_withNodeFromDifferentEquipment_returnsBadRequest`
  - `createDefect_withoutNode_stillWorks`
  - `updateDefect_setsNodeTarget`
  - `updateDefect_clearsNodeTarget_ifSupported`
- `DefectControllerContractTest`
  - `createDefect_acceptsEquipmentNodeId`
  - `getDefect_returnsEquipmentNodeReference`
- `EquipmentNodeServiceTest`
  - `deleteNode_withReferencedDefect_returnsConflict`
- `TriadSchemaModelContractTest`
  - `defectMustExposeEquipmentNodeTarget`

## Commands Run
- `./mvnw -Dtest=DefectServiceTest,DefectControllerContractTest,EquipmentNodeServiceTest test`
  - Failed before tests: no Maven wrapper exists in this repo.
- `mvn -Dtest=DefectServiceTest,DefectControllerContractTest,EquipmentNodeServiceTest test`
  - Failed before tests: `mvn` was not installed in the shell path.
- Downloaded temporary Maven 3.9.11 to `/tmp/apache-maven-3.9.11`.
- `/tmp/apache-maven-3.9.11/bin/mvn -Dtest=DefectServiceTest,DefectControllerContractTest,EquipmentNodeServiceTest test`
  - Passed, but Maven initially reused compiled classes.
- `/tmp/apache-maven-3.9.11/bin/mvn clean -Dtest=DefectServiceTest,DefectControllerContractTest,EquipmentNodeServiceTest test`
  - Passed after clean compile.
- `/tmp/apache-maven-3.9.11/bin/mvn test`
  - Failed due PostgreSQL test DB unavailable at `localhost:5433`.
- `git diff --check`
  - Passed with no whitespace errors.
- `/tmp/apache-maven-3.9.11/bin/mvn -Dtest=DefectServiceTest,DefectControllerContractTest,EquipmentNodeServiceTest test`
  - Final targeted verification passed.

## Targeted Test Result
- Command: `/tmp/apache-maven-3.9.11/bin/mvn clean -Dtest=DefectServiceTest,DefectControllerContractTest,EquipmentNodeServiceTest test`
- Result: passed.
- Summary: `Tests run: 58, Failures: 0, Errors: 0, Skipped: 0`.
- Fresh compile performed:
  - `Compiling 700 source files`
  - `Compiling 191 test source files`
- Final targeted verification after reports:
  - Command: `/tmp/apache-maven-3.9.11/bin/mvn -Dtest=DefectServiceTest,DefectControllerContractTest,EquipmentNodeServiceTest test`
  - Result: passed.
  - Summary: `Tests run: 58, Failures: 0, Errors: 0, Skipped: 0`.

## Full Maven Test Status
- Command: `/tmp/apache-maven-3.9.11/bin/mvn test`
- Result: failed because PostgreSQL test DB was unavailable.
- Summary: `Tests run: 1591, Failures: 0, Errors: 47, Skipped: 0`.
- Root environment error observed:
  - `Connection to localhost:5433 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.`
- The failing errors were repository/DataJpa context failures after the first database connection refusal.

## Remaining Backend Risks
- Full repository/integration verification still needs a running PostgreSQL test DB on `localhost:5433` with the expected credentials from `application-test.yml`.
- The new Flyway migration was not applied against a live PostgreSQL database in this session because the local DB was unavailable.
- The migration assumes the deployed database already has `defects` and `equipment_nodes`, matching the current migration style in this backend.
