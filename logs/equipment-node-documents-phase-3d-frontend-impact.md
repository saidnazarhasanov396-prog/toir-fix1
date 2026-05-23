# Equipment Node Documents Phase 3D Frontend Impact

## A. Backend Changes Summary
- Technical documents now support optional `equipmentNodeId`.
- Documents can target equipment only, or equipment plus one equipment node/component.
- `POST /api/v1/equipment/{equipmentId}/documents` accepts optional `equipmentNodeId`.
- Request body accepts `documentType` as an alias for the existing backend `type` field.
- Request body accepts `fileAssetId` as an alias for the existing backend `fileId` field.
- `GET /api/v1/equipment/{equipmentId}/documents` returns all active documents for the equipment, including node-level documents.
- New endpoint added: `GET /api/v1/equipment-nodes/{nodeId}/documents`.
- Document responses now include node reference fields:
  - `equipmentNodeId`
  - `equipmentNodeCode`
  - `equipmentNodeName`
  - `equipmentNodeType`
- Equipment node delete may now fail if active/non-deleted technical documents reference the node.
- Expected delete conflict message:
  - `Equipment node is referenced by technical documents`

## B. What Frontend Must Change
- `src/types/api.ts`
  - Add optional `equipmentNodeId` to technical document create payload.
  - Add `equipmentNodeId`, `equipmentNodeCode`, `equipmentNodeName`, and `equipmentNodeType` to technical document response type.
  - Keep existing document `type` response handling; create payload may use existing `type` or `documentType`.
  - Create payload may use existing `fileId` or `fileAssetId`.
- `src/lib/api.ts`
  - Ensure equipment document create/list methods support the new node fields.
  - Add node document list API for `GET /api/v1/equipment-nodes/{nodeId}/documents`.
- Equipment card documents section
  - Allow selecting an optional node/component when uploading or creating a document.
  - Load nodes for the selected equipment and only show nodes belonging to that equipment.
  - Clear selected node if the selected equipment changes.
  - Show node code/name/type in the document list when present.
  - Treat equipment document list as a complete equipment document context, including node-level documents.
- Equipment node tree/section
  - Optionally show documents count/link later using `GET /api/v1/equipment-nodes/{nodeId}/documents`.
  - Preserve backend delete conflict messages.

## C. Payload Examples

Create equipment-level document:

```json
{
  "fileAssetId": "file-uuid",
  "documentType": "DRAWING",
  "title": "Pump drawing",
  "revision": "R1",
  "documentDate": "2026-05-23"
}
```

The backend still requires `title` for technical documents. The backend accepts either `fileAssetId` or `fileId` in create requests.

```json
{
  "fileId": "file-uuid",
  "documentType": "DRAWING",
  "title": "Pump drawing",
  "revision": "R1",
  "documentDate": "2026-05-23"
}
```

Create node-level document:

```json
{
  "fileId": "file-uuid",
  "equipmentNodeId": "node-uuid",
  "documentType": "CERTIFICATE",
  "title": "Bearing certificate",
  "revision": "R1",
  "documentDate": "2026-05-23"
}
```

Example response with node reference:

```json
{
  "id": "document-uuid",
  "equipmentId": "equipment-uuid",
  "equipmentNodeId": "node-uuid",
  "fileId": "file-uuid",
  "file": {
    "id": "file-uuid",
    "fileName": "stored-drawing.pdf",
    "originalName": "Drawing.pdf",
    "mimeType": "application/pdf",
    "sizeBytes": 12345,
    "downloadUrl": "/api/v1/files/assets/file-uuid/download"
  },
  "title": "Bearing drawing",
  "revision": "R1",
  "type": "DRAWING",
  "documentDate": "2026-05-23",
  "equipmentNodeCode": "BRG-01",
  "equipmentNodeName": "Bearing",
  "equipmentNodeType": "COMPONENT"
}
```
