# Equipment Node Work Order Target Phase 3E Frontend Impact

## A. Backend Changes Summary
- Work order create now supports optional `equipmentNodeId`.
- Existing work-order create payloads without `equipmentNodeId` continue to work.
- Work order list/detail responses now include node reference fields:
  - `equipmentNodeId`
  - `equipmentNodeCode`
  - `equipmentNodeName`
  - `equipmentNodeType`
- If a work order is created from a defect that has `equipmentNodeId`, and the create request does not provide `equipmentNodeId`, the backend inherits the node from the defect.
- If the request provides `equipmentNodeId`, the request value is treated as the explicit target and must belong to the same equipment.
- Equipment node delete may now fail if active/non-deleted work orders reference the node.
- Expected delete conflict message:
  - `Equipment node is referenced by work orders`
- Current backend gap: there is no generic work-order update endpoint in this repository. Update payload adaptation should wait until a backend update route exists.

## B. What Frontend Must Change
- `src/types/api.ts`
  - Add optional `equipmentNodeId` to work-order create payloads.
  - Add `equipmentNodeId`, `equipmentNodeCode`, `equipmentNodeName`, and `equipmentNodeType` to work-order response types.
  - Do not assume work-order update is available yet; backend currently exposes lifecycle mutations, not generic edit/update.
- `src/lib/api.ts`
  - Ensure work-order create methods pass `equipmentNodeId` when selected.
  - Ensure work-order list/detail response typing accepts the node reference fields.
  - Add update payload support only after a backend update endpoint is added.
- Work-order create page/component
  - When `equipmentId` is selected, load equipment nodes.
  - Show optional "Equipment node / component" selector.
  - Use existing Phase 3B equipment node tree/flat helpers if available.
  - Only show nodes belonging to the selected equipment.
  - Clear selected node if equipment changes.
  - Send `equipmentNodeId` when selected.
  - If creating from a defect with node target, frontend may either omit `equipmentNodeId` and let backend inherit it, or send the same node explicitly.
- Work-order detail/list UI
  - Show node code/name/type when present.
  - Keep equipment-only work orders rendering normally when node fields are null.
- Equipment node tree UI
  - Preserve backend delete conflict messages.

## C. Payload Examples

Create work order with node:

```json
{
  "equipmentId": "equipment-uuid",
  "equipmentNodeId": "node-uuid",
  "title": "Replace bearing",
  "description": "Bearing overheating"
}
```

Actual backend create payload still requires the existing work-order fields such as `number`, `departmentId`, `type`, and `createdById`:

```json
{
  "number": "WO-2026-1001",
  "equipmentId": "equipment-uuid",
  "equipmentNodeId": "node-uuid",
  "departmentId": "department-uuid",
  "title": "Replace bearing",
  "type": "DEFECT",
  "workType": "REPAIR",
  "priority": "MEDIUM",
  "createdById": "user-uuid",
  "summary": "Bearing overheating"
}
```

Create work order equipment-only:

```json
{
  "number": "WO-2026-1002",
  "equipmentId": "equipment-uuid",
  "departmentId": "department-uuid",
  "title": "General repair",
  "type": "PLANNED",
  "workType": "REPAIR",
  "priority": "MEDIUM",
  "createdById": "user-uuid",
  "summary": "Equipment-level issue"
}
```

Update and clear node:

```json
{
  "equipmentId": "equipment-uuid",
  "equipmentNodeId": null,
  "title": "General repair"
}
```

Do not wire this update example until the backend adds a generic work-order update endpoint.

Example response fields:

```json
{
  "id": "work-order-uuid",
  "equipmentId": "equipment-uuid",
  "equipmentNodeId": "node-uuid",
  "equipmentNodeCode": "BRG-01",
  "equipmentNodeName": "Bearing",
  "equipmentNodeType": "COMPONENT",
  "number": "WO-2026-1001",
  "title": "Replace bearing"
}
```

## D. Error Cases Frontend Should Handle
- Node not found.
- Node belongs to another equipment.
- Node deleted/inactive.
- Decommissioned equipment rejects work-order creation.
- Delete node fails because work orders reference it.

## E. What Frontend Should NOT Do Yet
- Do not implement node repair-history aggregation UI unless backend adds it later.
- Do not implement node documents again.
- Do not implement spare-part/material node integration.
- Do not implement generic work-order update UI against this backend until a work-order update endpoint exists.
