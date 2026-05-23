# Equipment Node Defect Target Phase 3C Frontend Impact

## A. Backend Changes Summary
- Defect create/update now supports a new optional field: `equipmentNodeId`.
- Defect responses now include node reference fields when a defect targets a node:
  - `equipmentNodeId`
  - `equipmentNodeCode`
  - `equipmentNodeName`
  - `equipmentNodeType`
- Defects without `equipmentNodeId` are still valid and continue to target equipment only.
- Equipment node delete can now fail with a backend conflict if active/non-deleted defects reference that node.
- Expected delete conflict message:
  - `Equipment node is referenced by active defects`

## B. What Frontend Must Change
- `src/types/api.ts`
  - Add optional `equipmentNodeId` to defect create/update payload types.
  - Add `equipmentNodeId`, `equipmentNodeCode`, `equipmentNodeName`, and `equipmentNodeType` to defect response type.
- `src/lib/api.ts`
  - Ensure defect create/update methods can pass `equipmentNodeId` through unchanged.
- Defect create/edit page/component
  - When `equipmentId` is selected, load equipment nodes for that equipment.
  - Show optional selector labelled like "Equipment node / component".
  - Use existing node tree/flat helpers from Phase 3B if available.
  - Only show nodes belonging to the selected equipment.
  - Clear the selected node if `equipmentId` changes.
  - Send no `equipmentNodeId` or send `equipmentNodeId: null` when the user leaves/clears the selector.
- Defect detail/list UI
  - Show node code/name/type if present.
  - Keep existing equipment display unchanged for equipment-only defects.
- Equipment node tree UI
  - Existing delete error display should preserve backend conflict messages.
  - If a node has referenced defects, show the backend message instead of a generic delete failure.

## C. Frontend Payload Examples

Create defect with equipment node:

```json
{
  "equipmentId": "equipment-uuid",
  "equipmentNodeId": "node-uuid",
  "title": "Bearing overheating",
  "description": "Temperature is above normal"
}
```

Create defect targeting equipment only:

```json
{
  "equipmentId": "equipment-uuid",
  "title": "Bearing overheating",
  "description": "Temperature is above normal"
}
```

Update defect and clear node target:

```json
{
  "equipmentId": "equipment-uuid",
  "equipmentNodeId": null,
  "title": "Bearing overheating",
  "description": "Temperature is back under review"
}
```

Example response with node target:

```json
{
  "id": "defect-uuid",
  "code": "DEF-2026-0001",
  "title": "Bearing overheating",
  "description": "Temperature is above normal",
  "equipmentId": "equipment-uuid",
  "equipmentName": "Pump A",
  "equipmentNodeId": "node-uuid",
  "equipmentNodeCode": "BRG-01",
  "equipmentNodeName": "Bearing",
  "equipmentNodeType": "COMPONENT",
  "status": "OPEN"
}
```
