# HR Employee Passport And Pictures Frontend Handoff

Date: 2026-06-25  
Backend branch: `Codex_org`

## Backend Summary

The backend now supports two HR employee file workflows:

1. Employee passport documents use the existing attachment group API with `targetType=HR_EMPLOYEE`.
2. Employee photos use new HR picture-handle endpoints, allowing multiple photos per employee with stable picture ids and download URLs.

## Employee Passport Documents

Passports should be implemented with the generic attachment group module.

Create a passport attachment group:

```http
POST /api/v1/attachments/groups
Content-Type: multipart/form-data
```

Required form fields:

- `title`: user-facing document title, for example `Passport`
- `targetType`: `HR_EMPLOYEE`
- `targetId`: employee id
- `files`: one or more passport files

Recommended optional fields:

- `description`: for example `Employee passport`
- `documentType`: `PASSPORT`
- `documentNumber`: passport number
- `labels`: one label per file, for example `front`, `back`, `registration`

Important validation:

- If `labels` is provided, its count must match `files`.
- The backend stores HR employee attachment files in the `PASSPORT` file category.
- Access follows employee data scope: scope admin, employee department, own employee record, or assigned user record.

List employee passport/document groups:

```http
GET /api/v1/attachments/groups?targetType=HR_EMPLOYEE&targetId={employeeId}
```

Download a passport file:

```http
GET /api/v1/attachments/groups/{groupId}/files/{fileId}/download
```

Get a presigned URL:

```http
GET /api/v1/attachments/groups/{groupId}/files/{fileId}/presigned-url
```

Add more files to an existing passport group:

```http
POST /api/v1/attachments/groups/{groupId}/files
Content-Type: multipart/form-data
```

Delete a file or group:

```http
DELETE /api/v1/attachments/groups/{groupId}/files/{fileId}
DELETE /api/v1/attachments/groups/{groupId}
```

Frontend permission expectations:

- Read passport groups/files with `EMPLOYEE_READ`, `SYSTEM_ADMIN`, or `*`.
- Create/update/delete passport groups/files with `EMPLOYEE_UPDATE`, `SYSTEM_ADMIN`, or `*`.

## Employee Photos

Employee photos have dedicated picture-handle endpoints. Use these for actual employee portraits or gallery photos, not passport scans.

Upload multiple photos:

```http
POST /api/v1/hr/employees/{employeeId}/pictures
Content-Type: multipart/form-data
```

Form fields:

- `files`: one or more image files
- `pictureNames`: optional; one display name per file
- `pictureType`: optional; suggested values can be frontend-owned, for example `PROFILE`, `BADGE`, `WORKSITE`, `DOCUMENTARY`

Image validation:

- Allowed content types: `image/jpeg`, `image/jpg`, `image/png`, `image/webp`, `image/gif`.
- If `pictureNames` is provided, its count must match `files`.
- If `pictureNames` is omitted, backend uses each original filename as the picture name.

Upload response shape:

```json
[
  {
    "id": "picture-id",
    "employeeId": "employee-id",
    "pictureName": "Portrait",
    "pictureType": "PROFILE",
    "originalName": "portrait.png",
    "contentType": "image/png",
    "size": 12345,
    "uploadedAt": "2026-06-25T06:00:00",
    "uploadedBy": "user-id",
    "downloadUrl": "/api/v1/hr/employee-pictures/{pictureId}/download"
  }
]
```

List employee photos:

```http
GET /api/v1/hr/employees/{employeeId}/pictures?page=0&size=20
```

Response is a Spring `Page<EmployeePictureDto>` with `content`, `totalElements`, `number`, `size`, etc. Page index is zero-based for this endpoint.

Download/render a photo:

```http
GET /api/v1/hr/employee-pictures/{pictureId}/download
```

The backend returns the image with inline `Content-Disposition`, so the frontend can use the `downloadUrl` directly as an image source when authenticated requests are supported.

Delete a photo:

```http
DELETE /api/v1/hr/employee-pictures/{pictureId}
```

Frontend permission expectations:

- List/download photos with `EMPLOYEE_READ`, `SYSTEM_ADMIN`, or `*`.
- Upload/delete photos with `EMPLOYEE_UPDATE`, `SYSTEM_ADMIN`, or `*`.

## Suggested Frontend UX

Employee detail page:

- Add a `Passport documents` section backed by attachment groups filtered with `targetType=HR_EMPLOYEE`.
- Add an `Employee photos` section backed by the new HR picture endpoints.
- Keep passport documents and employee photos visually separate because they use different backend contracts.

Passport section:

- Support multi-file upload into one group.
- Capture `documentNumber` for the passport number.
- Use per-file labels for front/back/registration pages if the UI exposes labels.
- Show each file with original name, label, size, and download action.

Photos section:

- Support multi-image upload.
- Let the user set optional display names and an optional type/category.
- Render thumbnails from `downloadUrl`.
- Show newest photos first, matching backend order.
- Delete by `pictureId`, not by uploaded file id.

## Backend Files Added Or Changed

- `AttachmentTargetType.HR_EMPLOYEE`
- `FileCategory.EMPLOYEE_PICTURE`
- `FileCategory.PASSPORT` routing for HR employee attachment groups
- `EmployeePicture`, `EmployeePictureRepository`, `EmployeePictureDto`, `EmployeePictureService`
- Migration: `V20260625_1__hr_employee_pictures.sql`

## Verification

Backend focused verification command:

```bash
./mvnw -Dtest=AttachmentTargetAccessServiceTest,AttachmentGroupControllerContractTest,EmployeePictureServiceTest,HrControllerContractTest,RbacHrSecurityTest,HrEmployeePicturesMigrationContractTest,FlywayMigrationVersionContractTest test
```

Latest result before handoff: 63 tests, 0 failures.
