# Frontend Attachment Groups

Date: 2026-06-16

## What Changed

Document attachments are now managed as attachment groups.

An attachment group is the business document. Files are members of the group.

Example:

```text
Attachment Group: Passport
- front.pdf
- back.pdf
```

Do not create two separate documents for `front.pdf` and `back.pdf`. Create one group named `Passport` and upload both files into that group.

## What Did Not Change

- Equipment pictures stay on the existing picture endpoints.
- Vehicle pictures stay on the existing picture endpoints.
- Repair request photos stay on the existing photo endpoints.
- Image/picture upload UI should not be migrated to attachment groups.

## Target Types

Use these `targetType` values:

```text
EQUIPMENT
VEHICLE
WORK_ORDER
REPAIR_REQUEST
COMPLETION_ACT
APPROVAL
STOCK_MOVEMENT
```

Target ID mapping:

- Equipment documents: `targetType=EQUIPMENT`, `targetId={equipmentId}`
- Vehicle documents: `targetType=VEHICLE`, `targetId={equipmentId}`
- Work order documents: `targetType=WORK_ORDER`, `targetId={workOrderId}`
- Repair request documents: `targetType=REPAIR_REQUEST`, `targetId={requestId}`
- Completion act documents: `targetType=COMPLETION_ACT`, `targetId={completionActId}`
- Approval documents: `targetType=APPROVAL`, `targetId={approvalRequestId}`
- Stock movement documents: `targetType=STOCK_MOVEMENT`, `targetId={movementId}`

## Create a Document Group

Endpoint:

```text
POST /api/v1/attachments/groups
Content-Type: multipart/form-data
```

Form fields:

```text
title: required document title
description: optional
targetType: required enum
targetId: required uuid
files: required, repeat once per file
labels: optional, repeat once per file when supplied
```

TypeScript example:

```ts
const form = new FormData();
form.append("title", "Passport");
form.append("description", "Driver passport");
form.append("targetType", "EQUIPMENT");
form.append("targetId", equipmentId);

form.append("files", frontFile);
form.append("labels", "front");
form.append("files", backFile);
form.append("labels", "back");

const response = await fetch("/api/v1/attachments/groups", {
  method: "POST",
  body: form,
});
```

When using labels, the number of `labels` values must match the number of `files` values.

## Add Files to an Existing Group

Endpoint:

```text
POST /api/v1/attachments/groups/{groupId}/files
Content-Type: multipart/form-data
```

Use this when the user adds another page, side, scan, or supporting file to an existing business document.

```ts
const form = new FormData();
form.append("files", page3File);
form.append("labels", "page 3");

await fetch(`/api/v1/attachments/groups/${groupId}/files`, {
  method: "POST",
  body: form,
});
```

## List Groups for a Screen

Endpoint:

```text
GET /api/v1/attachments/groups?targetType={targetType}&targetId={targetId}
```

Use this for document tabs/sections. Render one row/card per attachment group, not one row per file.

Recommended UI model:

```ts
type AttachmentGroup = {
  id: string;
  title: string;
  description?: string | null;
  targetType: string;
  targetId: string;
  documentType?: string | null;
  documentNumber?: string | null;
  createdBy: string;
  createdAt: string;
  files: AttachmentFile[];
};

type AttachmentFile = {
  id: string;
  fileId: string;
  originalName: string;
  contentType: string;
  size: number;
  orderNumber: number;
  label?: string | null;
  downloadUrl: string;
  presignedUrlEndpoint: string;
};
```

## Read One Group

Endpoint:

```text
GET /api/v1/attachments/groups/{groupId}
```

Use this for a document detail drawer/page where the user sees all files inside a logical document.

## Download and Preview

Each file item includes:

```text
downloadUrl
presignedUrlEndpoint
```

Use `downloadUrl` for direct backend download.

Use `presignedUrlEndpoint` when the UI needs a temporary S3 URL:

```text
GET /api/v1/attachments/groups/{groupId}/files/{fileId}/presigned-url
```

## Delete

Remove one file from a group:

```text
DELETE /api/v1/attachments/groups/{groupId}/files/{fileId}
```

Delete the whole business document group:

```text
DELETE /api/v1/attachments/groups/{groupId}
```

Backend follows existing project rules for physical file deletion.

## Legacy Endpoints

Existing equipment, vehicle, work order, and stock movement document endpoints still work temporarily and delegate to the unified backend service.

New frontend work should use `/api/v1/attachments/groups`.

Do not build new document UI against these legacy module-specific endpoints:

```text
/api/v1/equipment/{id}/documents
/api/v1/vehicles/{id}/documents
/api/v1/work-orders/{id}/documents
/api/v1/stock-movements/{id}/files
```

Use them only while maintaining old screens during migration.

## Validation UX

Show user-facing errors for:

- no files selected
- unsupported file type
- file too large
- more than 25 files in one group
- missing title
- invalid or missing target
- labels count not matching files count
- forbidden target due to department, ownership, or approval scope

File size/type limits come from the existing backend file module. Keep frontend limits aligned with backend responses instead of hardcoding a conflicting rule.

## Suggested Screen Behavior

- Document list: show group title, description, file count, created date, and first few file names.
- Document detail: show all files sorted by `orderNumber`.
- Upload: ask for document title once, then allow multiple files.
- Add file: append files to an existing group rather than creating another group with the same title.
- Picture/photo tabs: keep using current picture/photo APIs.
