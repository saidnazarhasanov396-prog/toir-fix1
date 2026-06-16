# Attachment Groups Backend Audit

Date: 2026-06-16

## Scope

This refactor is limited to document attachment management. Equipment pictures, vehicle pictures, repair request photos, and existing image/picture flows are intentionally out of scope and were not changed.

S3 remains the storage source of truth. Existing upload, download, object naming, bucket use, and presigned URL generation remain in the existing file module and S3 services.

## Current Implementations Audited

- File module: `UploadedFile`, `FileService`, `FileServiceImpl`, `FileValidator`, and `S3Service` already provide reusable S3-backed file metadata and storage operations.
- Equipment documents: `EquipmentDocument`, `EquipmentDocumentFile`, repositories, DTOs, controller endpoints, and `EquipmentService` document methods previously managed document grouping and file links locally.
- Vehicle documents: `VehicleDocument`, `vehicle_details.document_file_id`, repositories, DTOs, controller endpoints, and `VehicleService` document methods previously managed vehicle documents separately.
- Work order documents: `WorkOrderDocument`, repository, DTOs, controller endpoints, and `WorkOrderService` document methods previously managed work order documents separately.
- Stock movement files: `StockMovementFile`, repository, DTOs, controller endpoints, and `StockMovementService` file methods previously managed movement files separately.
- Repair requests: repair request photos use the legacy photo/file-asset path and were not modified. The unified attachment API now supports document attachments with `targetType=REPAIR_REQUEST`.
- Completion acts and approvals: the unified attachment API now supports `targetType=COMPLETION_ACT` and `targetType=APPROVAL`. Approval target authorization handles requester, approval participants, system admins, and supported underlying target scopes.
- Picture modules: `EquipmentPictureService`, `VehiclePictureService`, picture entities, picture repositories, picture DTOs, and picture migrations were audited as out of scope and left unchanged.

## Duplicated Logic Found

- Repeated file-upload loops in equipment, vehicle, work order, and stock movement services.
- Repeated file-count and empty-file validation.
- Repeated document metadata fields outside the file module.
- Repeated target ownership and department-scope checks near file operations.
- Repeated download and presigned-url membership checks.
- Repeated delete cleanup behavior around uploaded files.
- Separate per-module association tables that all linked business records to `uploaded_files`.

## Refactoring Plan and Status

1. Keep `uploaded_files` as the canonical file metadata table.
2. Add an attachment-group layer for logical business documents.
3. Route new document attachment APIs through `AttachmentGroupService`.
4. Route legacy document endpoints through `AttachmentGroupService` for backward compatibility.
5. Keep legacy document tables/classes for migration/backward compatibility, but do not add new attachment business logic to them.
6. Preserve S3 behavior by calling the existing `FileService` methods.
7. Preserve picture/photo modules unchanged.

Status: implemented.

## Data Model

New tables:

- `attachment_groups`
  - `id`
  - `title`
  - `description`
  - `target_type`
  - `target_id`
  - `document_type`
  - `document_number`
  - `created_by`
  - `created_at`
  - `deleted`
  - `deleted_at`

- `attachment_group_items`
  - `id`
  - `group_id`
  - `file_id`
  - `order_number`
  - `label`
  - `created_at`

The physical file metadata remains in `uploaded_files`.

## Migration

Added Flyway migration:

- `src/main/resources/db/migration/V20260616_3__attachment_groups.sql`

The migration creates group tables and backfills existing associations from:

- `equipment_documents`
- `equipment_document_files`
- `vehicle_documents`
- `work_order_documents`
- `stock_movement_files`

For equipment, vehicle, and work order documents, existing document IDs are reused as attachment group IDs to keep legacy document URLs resolvable after migration.

## API Contract

Base path:

```text
/api/v1/attachments/groups
```

Target types:

```text
EQUIPMENT
VEHICLE
WORK_ORDER
REPAIR_REQUEST
COMPLETION_ACT
APPROVAL
STOCK_MOVEMENT
```

Create group and upload files:

```text
POST /api/v1/attachments/groups
Content-Type: multipart/form-data

title: string
description: string, optional
targetType: enum
targetId: uuid
files: file[], required
labels: string[], optional, same count as files when supplied
```

Add files to existing group:

```text
POST /api/v1/attachments/groups/{groupId}/files
Content-Type: multipart/form-data

files: file[], required
labels: string[], optional, same count as files when supplied
```

Read group:

```text
GET /api/v1/attachments/groups/{groupId}
```

List groups by target:

```text
GET /api/v1/attachments/groups?targetType={targetType}&targetId={targetId}
```

Download a file in a group:

```text
GET /api/v1/attachments/groups/{groupId}/files/{fileId}/download
```

Get a presigned URL for a file in a group:

```text
GET /api/v1/attachments/groups/{groupId}/files/{fileId}/presigned-url
```

Delete a group:

```text
DELETE /api/v1/attachments/groups/{groupId}
```

Remove one file from a group:

```text
DELETE /api/v1/attachments/groups/{groupId}/files/{fileId}
```

Response shape:

```json
{
  "id": "group-uuid",
  "title": "Technical Passport",
  "description": "Optional description",
  "targetType": "EQUIPMENT",
  "targetId": "target-uuid",
  "documentType": "PASSPORT",
  "documentNumber": "TP-123",
  "createdBy": "user-uuid",
  "createdAt": "2026-06-16T11:58:36",
  "files": [
    {
      "id": "item-uuid",
      "fileId": "uploaded-file-uuid",
      "originalName": "front.pdf",
      "storedName": "stored-name.pdf",
      "contentType": "application/pdf",
      "size": 12345,
      "orderNumber": 0,
      "label": "front",
      "uploadedBy": "user-uuid",
      "uploadedAt": "2026-06-16T11:58:36",
      "downloadUrl": "/api/v1/attachments/groups/group-uuid/files/uploaded-file-uuid/download",
      "presignedUrlEndpoint": "/api/v1/attachments/groups/group-uuid/files/uploaded-file-uuid/presigned-url"
    }
  ]
}
```

## Security and Validation

- Target existence and scope are checked before upload, list, download, presigned URL generation, delete, and file removal.
- Department, ownership, requester, approval participant, and system-admin access rules reuse existing services and repositories.
- Existing RBAC/PBAC remains enforced through controller annotations and target-scope checks.
- Validation covers empty file lists, invalid target type, invalid target ID, label count mismatch, title length, metadata length, and maximum 25 files per group.
- File type and file size validation remain in the existing file module.

## Classes Changed

New:

- `AttachmentGroupController`
- `AttachmentGroupDto`
- `AttachmentGroup`
- `AttachmentGroupItem`
- `AttachmentTargetType`
- `AttachmentGroupRepository`
- `AttachmentGroupItemRepository`
- `AttachmentGroupService`
- `AttachmentTargetAccessService`

Changed:

- `EquipmentService`
- `VehicleService`
- `WorkOrderService`
- `StockMovementService`
- `EquipmentDocumentDto`
- `VehicleDocumentDto`
- `WorkOrderDocumentDto`
- `StockMovementFileDto`
- `FileService`
- `FileServiceImpl`
- `MaterialStockPbacScopeTest`

Added tests:

- `AttachmentGroupServiceTest`
- `AttachmentTargetAccessServiceTest`
- `AttachmentGroupControllerContractTest`
- `AttachmentGroupsMigrationContractTest`

## Test Results

Passed:

```text
./mvnw -Dtest=AttachmentGroupServiceTest,AttachmentTargetAccessServiceTest,AttachmentGroupControllerContractTest,AttachmentGroupsMigrationContractTest test
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
```

Passed:

```text
./mvnw -Dtest=EquipmentControllerContractTest,VehicleControllerContractTest,WorkOrderControllerContractTest,StockMovementControllerContractTest test
Tests run: 109, Failures: 0, Errors: 0, Skipped: 0
```

## Confirmations

- Picture modules were not modified.
- S3 upload/download/presigned URL implementation was not modified.
- Existing file metadata remains in `uploaded_files`.
- Document attachment APIs now use the unified attachment group service.
- Legacy equipment, vehicle, work order, and stock movement document/file endpoints delegate to the unified service.
