# Vehicle Document Upload Integration

Date: 2026-05-25

This report summarizes the backend Vehicle module changes related to secure document upload and access.

## Summary

Vehicle documents are now stored through the secure backend file system instead of raw/local/manual file references.

The Vehicle module now supports one optional document per vehicle. The document is stored as an `UploadedFile` record and referenced from `vehicle_details.document_file_id`.

Existing vehicle JSON create/update APIs are still supported and do not require multipart upload.

## New Vehicle Document Endpoints

Base path:

```text
/api/v1/vehicles
```

### Attach Or Replace Vehicle Document

```http
POST /api/v1/vehicles/{equipmentId}/document
Content-Type: multipart/form-data
```

Multipart field:

```text
document
```

Example:

```js
const formData = new FormData();
formData.append("document", file);

await fetch(`/api/v1/vehicles/${equipmentId}/document`, {
  method: "POST",
  headers: {
    Authorization: `Bearer ${token}`,
  },
  body: formData,
});
```

Response:

```json
{
  "equipment": {
    "...": "existing equipment fields"
  },
  "vehicleDetails": {
    "...": "existing vehicle details fields",
    "document": {
      "id": "4d6b37fa-2e70-4b6c-b8bb-984fdc248c5f",
      "originalName": "vehicle-passport.pdf",
      "contentType": "application/pdf",
      "size": 123456,
      "downloadUrl": "/api/files/4d6b37fa-2e70-4b6c-b8bb-984fdc248c5f/download",
      "presignedUrlEndpoint": "/api/files/4d6b37fa-2e70-4b6c-b8bb-984fdc248c5f/presigned-url"
    }
  }
}
```

Notes:

- If a document already exists, this endpoint replaces it.
- The old document is soft-deleted only after the new document is successfully attached.
- The frontend should send the file using field name `document`.

### Get Vehicle Document Metadata

```http
GET /api/v1/vehicles/{equipmentId}/document
```

Response:

```json
{
  "id": "4d6b37fa-2e70-4b6c-b8bb-984fdc248c5f",
  "originalName": "vehicle-passport.pdf",
  "contentType": "application/pdf",
  "size": 123456,
  "downloadUrl": "/api/files/4d6b37fa-2e70-4b6c-b8bb-984fdc248c5f/download",
  "presignedUrlEndpoint": "/api/files/4d6b37fa-2e70-4b6c-b8bb-984fdc248c5f/presigned-url"
}
```

If no document is attached, the backend returns `404`.

### Get Vehicle Document Presigned URL

```http
GET /api/v1/vehicles/{equipmentId}/document/presigned-url
```

Response:

```json
{
  "fileId": "4d6b37fa-2e70-4b6c-b8bb-984fdc248c5f",
  "url": "https://...",
  "expiresAt": "2026-05-25T12:30:00"
}
```

Use this when the frontend needs temporary direct access to the stored file.

### Delete Vehicle Document

```http
DELETE /api/v1/vehicles/{equipmentId}/document
```

Response:

```http
204 No Content
```

Behavior:

- Detaches the document from the vehicle.
- Soft-deletes the uploaded file metadata.
- Deletes the object from MinIO/S3 through the secure file service.

If no document is attached, the backend returns `404`.

## Vehicle Detail Response Change

Existing vehicle detail responses now include an optional `document` object inside `vehicleDetails`.

Example:

```json
{
  "equipment": {
    "...": "existing equipment fields"
  },
  "vehicleDetails": {
    "id": "9f5456c2-c3e0-4a8e-85bb-2e7022f67eee",
    "plateNumber": "01A123AA",
    "vin": "VIN123456789",
    "...": "existing vehicle fields",
    "document": {
      "id": "4d6b37fa-2e70-4b6c-b8bb-984fdc248c5f",
      "originalName": "vehicle-passport.pdf",
      "contentType": "application/pdf",
      "size": 123456,
      "downloadUrl": "/api/files/4d6b37fa-2e70-4b6c-b8bb-984fdc248c5f/download",
      "presignedUrlEndpoint": "/api/files/4d6b37fa-2e70-4b6c-b8bb-984fdc248c5f/presigned-url"
    }
  }
}
```

If the vehicle has no document:

```json
{
  "vehicleDetails": {
    "...": "existing vehicle fields",
    "document": null
  }
}
```

## Existing Vehicle APIs

These remain JSON-based and backward compatible:

```http
POST /api/v1/vehicles
PUT  /api/v1/vehicles/{equipmentId}
GET  /api/v1/vehicles/{equipmentId}
GET  /api/v1/vehicles
GET  /api/v1/vehicles/stats
DELETE /api/v1/vehicles/{equipmentId}
```

No multipart document is required in existing create/update requests.

## File Validation

Vehicle documents use the shared secure `FileService` validation.

Allowed file types include:

- PNG
- JPEG
- WEBP
- PDF
- TXT
- DOC
- DOCX
- XLS
- XLSX

Maximum size:

```text
150MB
```

Blocked dangerous extensions include:

- `exe`
- `sh`
- `bat`
- `cmd`
- `js`
- `jar`
- `war`
- `php`
- `py`
- `dll`

The backend uses Apache Tika to detect the actual MIME type. Frontend `file.type` is not trusted as the source of truth.

## Error Handling

Errors use the existing backend error format:

```json
{
  "message": "File type is not allowed",
  "path": "/api/v1/vehicles/{equipmentId}/document",
  "timestamp": "2026-05-25T12:00:00",
  "code": 415
}
```

Common statuses:

- `400` invalid request or invalid file
- `401` missing/invalid authentication
- `403` user cannot access the vehicle or file
- `404` vehicle/document not found
- `413` or `400` file too large depending on where rejection occurs
- `415` file type not allowed
- `500` storage or unexpected backend failure

## Authorization

Document endpoints require authentication.

Backend checks:

- user must be authenticated
- user must be allowed to access the vehicle/equipment
- user must own the uploaded file according to `FileService` ownership checks

The frontend should treat `403` as a permission failure and avoid retry loops.

## Frontend Recommendations

Use the `document` object from vehicle detail responses to render document state:

- If `document === null`, show upload action.
- If `document` exists, show filename, size, and actions:
  - download
  - preview/open using presigned URL if needed
  - replace
  - delete

For direct download through the backend:

```text
document.downloadUrl
```

For temporary direct object access:

```text
document.presignedUrlEndpoint
```

Do not store or depend on MinIO object names, raw S3 URLs, or internal storage paths. They are intentionally not exposed.

## Backend Storage Notes

Vehicle documents are stored under file category:

```text
VEHICLE_DOCUMENT
```

Folder-like object path:

```text
vehicle-documents/yyyy/MM/uuid.extension
```

This path is internal and should not be used by the frontend.

## Migration Note

The backend migration adds:

```text
vehicle_details.document_file_id
```

This references:

```text
uploaded_files.id
```

The field is nullable, so existing vehicles without documents continue to work.
