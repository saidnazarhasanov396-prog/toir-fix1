# Equipment Node Lifecycle Phase 3F Frontend Impact

Date: 2026-05-23

## A. Backend changes summary

- Added read endpoint: `GET /api/v1/equipment-nodes/{nodeId}/lifecycle`
- Endpoint decision: `/lifecycle` was chosen instead of `/history` because the response includes current node summary, counts, related records, and optional timeline data, not only historical events.
- Query params:
  - `includeTimeline`: optional boolean, default `true`
  - `limit`: optional integer, default `50`, maximum `200`; applies to each related section and the timeline
- Response includes:
  - `node`: node summary
  - `counts`: defect, work order, and document counts for the node
  - `defects`: node-targeted defects
  - `workOrders`: node-targeted work orders
  - `documents`: node-targeted technical documents
  - `timeline`: optional mixed list of defect, work order, and document items
- Timeline behavior:
  - Sorted descending by best available date.
  - Uses `updatedAt` when present, otherwise `createdAt`.
  - Timeline item `type` values are `DEFECT`, `WORK_ORDER`, and `DOCUMENT`.
- Missing or deleted nodes return controlled `404 Not Found`.

## Response shape

```json
{
  "node": {
    "id": "node-uuid",
    "equipmentId": "equipment-uuid",
    "parentId": "parent-node-uuid",
    "code": "BRG-01",
    "name": "Bearing",
    "nodeType": "COMPONENT",
    "serialNumber": "SN-1"
  },
  "counts": {
    "defects": 1,
    "workOrders": 1,
    "documents": 1
  },
  "defects": [
    {
      "id": "defect-uuid",
      "code": "DEF-1",
      "title": "Bearing overheating",
      "status": "OPEN",
      "severity": "HIGH",
      "createdAt": "2026-05-23T10:00:00Z",
      "updatedAt": "2026-05-23T11:00:00Z"
    }
  ],
  "workOrders": [
    {
      "id": "work-order-uuid",
      "number": "WO-1",
      "title": "Replace bearing",
      "status": "IN_PROGRESS",
      "type": "DEFECT",
      "workType": "REPAIR",
      "priority": "HIGH",
      "createdAt": "2026-05-23T12:00:00Z",
      "updatedAt": "2026-05-23T13:00:00Z"
    }
  ],
  "documents": [
    {
      "id": "document-uuid",
      "title": "Bearing drawing",
      "type": "DRAWING",
      "revision": "R1",
      "documentDate": "2026-05-23",
      "file": null,
      "createdAt": "2026-05-23T14:00:00Z",
      "updatedAt": "2026-05-23T15:00:00Z"
    }
  ],
  "timeline": [
    {
      "type": "WORK_ORDER",
      "id": "work-order-uuid",
      "title": "Replace bearing",
      "status": "IN_PROGRESS",
      "metadata": "DEFECT",
      "occurredAt": "2026-05-23T13:00:00Z",
      "sourceCreatedAt": "2026-05-23T12:00:00Z"
    }
  ]
}
```

## B. What frontend should change

- `src/types/api.ts`
  - Add node lifecycle response types for `node`, `counts`, `defects`, `workOrders`, `documents`, and `timeline`.
  - Reuse existing enum/string unions where practical, but keep fields nullable where backend can return `null`.
- `src/lib/api.ts`
  - Add `getEquipmentNodeLifecycle(nodeId, params?)`.
  - Params should support `includeTimeline?: boolean` and `limit?: number`.
- `src/components/equipment/equipment-nodes-section.tsx`
  - Add a "View lifecycle/history" action for a node.
  - Use the selected node id to call the new lifecycle endpoint.
- Equipment card or new node detail panel
  - Show node summary.
  - Show related defects.
  - Show related work orders.
  - Show related documents.
  - Show timeline if returned.

## C. UX guidance

- Do not duplicate the existing node documents endpoint in the UI if the lifecycle view already includes enough document data for the current screen.
- Show empty states for nodes with no defects, work orders, documents, or timeline items.
- Keep the existing node tree UI intact.
- Use existing links to defect, work-order, and document detail pages if routes exist.
- Keep backend error messages visible, especially the `404` case if a node was deleted while the user had it open.

## D. What frontend should NOT do yet

- Do not implement node material/spare-part integration.
- Do not implement work-order node update UI.
- Do not implement node repair analytics beyond the returned lifecycle data.
