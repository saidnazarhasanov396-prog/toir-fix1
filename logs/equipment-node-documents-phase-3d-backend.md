# Equipment Node Documents Phase 3D Backend

## Summary
- Technical documents now support an optional `equipmentNodeId` target.
- A document can remain equipment-level or target a node under the same equipment.
- Existing equipment document APIs remain compatible with payloads that do not include `equipmentNodeId`.
- `GET /api/v1/equipment/{equipmentId}/documents` returns all active documents for the equipment, including node-level documents.
- Added `GET /api/v1/equipment-nodes/{nodeId}/documents` for node-specific document lists.

## Files Changed
- `src/main/java/com/toir/entity/TechnicalDocument.java`
- `src/main/java/com/toir/dto/technicaldocument/TechnicalDocumentDto.java`
- `src/main/java/com/toir/repository/TechnicalDocumentRepository.java`
- `src/main/java/com/toir/service/TechnicalDocumentService.java`
- `src/main/java/com/toir/controller/TechnicalDocumentController.java`
- `src/main/java/com/toir/service/equipment/EquipmentNodeService.java`
- `src/test/java/com/toir/service/TechnicalDocumentServiceTest.java`
- `src/test/java/com/toir/controller/TechnicalDocumentControllerContractTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentNodeServiceTest.java`
- `logs/equipment-node-documents-phase-3d-frontend-impact.md`

## Migration Added
- `src/main/resources/db/migration/V20260523_5__technical_documents_equipment_node_target.sql`
  - Adds nullable `technical_documents.equipment_node_id`.
  - Adds an index for `equipment_node_id`.
  - Adds FK `technical_documents.equipment_node_id -> equipment_nodes.id`.

## API Contract Changes
- `POST /api/v1/equipment/{equipmentId}/documents`
  - Accepts optional `equipmentNodeId`.
  - Accepts `documentType` as an alias for existing `type`.
  - Accepts `fileAssetId` as an alias for existing `fileId`.
- `GET /api/v1/equipment/{equipmentId}/documents`
  - Continues to return equipment documents.
  - Includes node-level documents for the same equipment.
- `GET /api/v1/equipment-nodes/{nodeId}/documents`
  - Returns active documents linked to that node.
- Document responses are extended with:
  - `equipmentNodeId`
  - `equipmentNodeCode`
  - `equipmentNodeName`
  - `equipmentNodeType`

## Validation Rules
- `equipmentNodeId` is optional.
- If `equipmentNodeId` is provided:
  - the node must exist and be non-deleted,
  - the node must belong to the same equipment as the document route,
  - otherwise the service returns a controlled not-found or bad-request error.
- Existing equipment decommission restrictions for attaching technical documents are preserved for node-level documents.

## Delete Restriction
- Equipment node delete now checks active/non-deleted technical documents.
- If any active technical document references the node, delete fails with `409 Conflict`.
- Conflict message: `Equipment node is referenced by technical documents`.
- Existing delete protections for child nodes and defect references remain in place.

## Tests Added Or Updated
- `TechnicalDocumentServiceTest`
  - `createDocument_withEquipmentNode_setsNodeTarget`
  - `createDocument_withNodeFromDifferentEquipment_returnsBadRequest`
  - `createDocument_withoutNode_stillWorks`
  - `listEquipmentDocuments_includesNodeReference`
  - `listNodeDocuments_returnsOnlyNodeDocuments`
- `TechnicalDocumentControllerContractTest`
  - `createDocument_acceptsEquipmentNodeId`
  - `getDocuments_returnsEquipmentNodeReference`
  - `getNodeDocuments_returnsPageOrList`
- `EquipmentNodeServiceTest`
  - `deleteNode_withReferencedTechnicalDocument_returnsConflict`

## Commands Run
- `/tmp/apache-maven-3.9.11/bin/mvn -Dtest=TechnicalDocumentServiceTest,TechnicalDocumentControllerContractTest,EquipmentNodeServiceTest test`
- `/tmp/apache-maven-3.9.11/bin/mvn clean -Dtest=TechnicalDocumentServiceTest,TechnicalDocumentControllerContractTest,EquipmentNodeServiceTest test`
- `/tmp/apache-maven-3.9.11/bin/mvn test`
- `git diff --check`

## Targeted Test Result
- Passed.
- Clean targeted run result:
  - Tests run: 24
  - Failures: 0
  - Errors: 0
  - Skipped: 0

## Full Maven Test Status
- Failed because PostgreSQL test database was unavailable at `localhost:5433`.
- Maven summary:
  - Tests run: 1600
  - Failures: 0
  - Errors: 47
  - Skipped: 0
- Root failure observed in DB-backed tests:
  - `Connection to localhost:5433 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.`

## Remaining Backend Risks
- Full repository verification still needs to be rerun with the configured PostgreSQL test database available.
- No node-specific create endpoint was added; creation is intentionally handled through the existing equipment document endpoint with optional `equipmentNodeId`.
