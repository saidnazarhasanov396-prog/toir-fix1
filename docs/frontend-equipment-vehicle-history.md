# Equipment/Vehicle Transfer And Usage History Frontend Contract

Last updated: 2026-06-18

## What Changed

- Equipment and Vehicle location changes must go through Equipment placement flow.
- Vehicle is still an Equipment row, so vehicle department/location is now synchronized with:
  - `currentLocationType`
  - `currentWarehouseId`
  - `responsibleDepartmentId`
  - `equipment_location_history`
- A new generic Equipment usage-session API covers both Equipment and Vehicle.
- Old Vehicle driving-session APIs remain for backward compatibility, but they delegate to the generic usage-session logic.
- Work order replacement completion now moves replacement and old equipment via placement service, so history is written.

Frontend should use generic Equipment APIs for new screens. Keep vehicle-specific driving-session APIs only for old vehicle pages that already use them.

## Placement API

Existing endpoint:

```http
PATCH /api/v1/equipment/{equipmentId}/placement
```

Request shape:

```json
{
  "targetType": "DEPARTMENT",
  "warehouseId": null,
  "departmentId": "8c2ed54f-3401-4c20-bc1f-2d4b03dd1e8e",
  "warehouseStatus": null,
  "targetLocation": null,
  "note": "Moved to production department"
}
```

Supported `targetType` values:

- `DEPARTMENT`
- `WAREHOUSE`
- `OUTSIDE_FACILITY`

Warehouse move example:

```json
{
  "targetType": "WAREHOUSE",
  "warehouseId": "9f18c2f6-7784-4d23-bf9d-e3c42d940c82",
  "departmentId": null,
  "warehouseStatus": "AVAILABLE",
  "targetLocation": null,
  "note": "Returned to warehouse"
}
```

Important behavior:

- If equipment has an open usage session, transfer is rejected with conflict.
- Department-to-department, department-to-warehouse, warehouse-to-department, and outside moves write location history.
- Vehicle create/update also writes location history when department changes.

## Location History Timeline

New endpoint:

```http
GET /api/v1/equipment/{equipmentId}/location-history?page=0&size=20
```

Response is a Spring `Page<EquipmentLocationHistoryResponse>`:

```json
{
  "content": [
    {
      "id": "0e3d1ef1-3fd0-46ff-b6df-497278f56f03",
      "equipmentId": "04f398b1-60b7-4b23-9c1a-1e88a56b8854",
      "from": {
        "locationType": "DEPARTMENT",
        "departmentId": "0ac35790-9a2f-4f3d-9d91-385ef87115cb",
        "warehouseId": null,
        "outsideReason": null,
        "outsideTakenBy": null,
        "outsideRecipientUserId": null,
        "outsideStartedDate": null,
        "outsideExpectedReturnDate": null,
        "outsideDestination": null,
        "outsideReasonNote": null
      },
      "to": {
        "locationType": "WAREHOUSE",
        "departmentId": null,
        "warehouseId": "9f18c2f6-7784-4d23-bf9d-e3c42d940c82",
        "outsideReason": null,
        "outsideTakenBy": null,
        "outsideRecipientUserId": null,
        "outsideStartedDate": null,
        "outsideExpectedReturnDate": null,
        "outsideDestination": null,
        "outsideReasonNote": null
      },
      "responsibleDepartmentId": null,
      "changedBy": "18bcbe4f-1a9e-4b66-a553-64d10e278f6f",
      "changedAt": "2026-06-18T06:05:00Z",
      "activeUntil": "2026-06-18T11:10:00Z",
      "durationMinutes": 305,
      "note": "Returned to warehouse"
    }
  ],
  "pageable": {},
  "totalElements": 1,
  "totalPages": 1,
  "last": true,
  "first": true,
  "number": 0,
  "size": 20
}
```

Frontend timeline rules:

- Show `to.locationType` as the segment location.
- Use `to.departmentId` for department segment, `to.warehouseId` for warehouse segment.
- `changedAt` is segment start.
- `activeUntil` is next movement time; `null` means currently active segment.
- `durationMinutes` is backend-calculated. For active segment it is calculated up to current server time.
- `from` can be `null` for initial vehicle/equipment placement.

## Generic Usage Sessions

Use this for both Equipment and Vehicle.

Start:

```http
POST /api/v1/equipment/{equipmentId}/usage-sessions/start
```

```json
{
  "operatorEmployeeId": "fa0f0a6d-7b7e-4aa4-a5b0-f121157a6f5b",
  "startedAt": "2026-06-18T04:05:00Z",
  "meterId": null,
  "startMeterValue": null,
  "startOdometerKm": 1000.0,
  "startEngineHours": 200.0,
  "note": "Issued for shift"
}
```

Return:

```http
POST /api/v1/equipment/{equipmentId}/usage-sessions/{sessionId}/return
```

```json
{
  "returnedAt": "2026-06-18T11:08:00Z",
  "endMeterValue": null,
  "endOdometerKm": 1125.0,
  "endEngineHours": 225.0,
  "note": "Returned clean"
}
```

History:

```http
GET /api/v1/equipment/{equipmentId}/usage-sessions?page=0&size=20
```

Response item:

```json
{
  "id": "5efda5bd-cc5d-4119-a351-97bbcb3a58ef",
  "equipmentId": "04f398b1-60b7-4b23-9c1a-1e88a56b8854",
  "operatorEmployeeId": "fa0f0a6d-7b7e-4aa4-a5b0-f121157a6f5b",
  "operatorName": "Karim Ali",
  "departmentId": "8c2ed54f-3401-4c20-bc1f-2d4b03dd1e8e",
  "startedAt": "2026-06-18T04:05:00Z",
  "returnedAt": "2026-06-18T11:08:00Z",
  "durationMinutes": 423,
  "meterId": null,
  "startMeterValue": null,
  "endMeterValue": null,
  "meterDelta": null,
  "startOdometerKm": 1000.0,
  "endOdometerKm": 1125.0,
  "odometerDeltaKm": 125.0,
  "startEngineHours": 200.0,
  "endEngineHours": 225.0,
  "engineHoursDelta": 25.0,
  "status": "RETURNED",
  "issuedBy": "18bcbe4f-1a9e-4b66-a553-64d10e278f6f",
  "returnedBy": "18bcbe4f-1a9e-4b66-a553-64d10e278f6f",
  "note": "Returned clean"
}
```

Status values:

- `OPEN`
- `RETURNED`

Validation behavior:

- `operatorEmployeeId` is required.
- Operator must be active and in the same department as equipment.
- One equipment can have only one open usage session.
- One operator can have only one open usage session.
- Return meter/odometer/engine values cannot be lower than start values.

## Vehicle Backward-Compatible APIs

These still work, but frontend should prefer generic Equipment usage-session APIs for new work:

```http
POST /api/v1/vehicles/{equipmentId}/driving-sessions/start
POST /api/v1/vehicles/{equipmentId}/driving-sessions/{sessionId}/return
GET /api/v1/vehicles/{equipmentId}/driving-sessions?page=0&size=20
```

Vehicle start still enforces:

- Equipment category must be `VEHICLE`.
- Vehicle status must be `ACTIVE` or `STANDBY`.
- Vehicle must have assigned driver.
- Requested driver must be assigned driver.
- Driver must have `DRIVER` role and be in same department.

The returned response keeps old `VehicleDrivingSessionResponse` shape, but data is stored in generic usage sessions.

## Work Order Replacement Behavior

When replacement work order is completed:

- Replacement equipment is moved to work-order department via `PATCH /equipment/{id}/placement` logic.
- Old equipment is moved to selected return warehouse with `warehouseStatus=OUT_OF_SERVICE`.
- Both movements write `equipment_location_history`.

Frontend should refresh:

- Work order detail.
- Old equipment detail and location history.
- Replacement equipment detail and location history.
- Warehouse equipment list for the old return warehouse and replacement warehouse.

## Frontend Migration Checklist

- Equipment detail: add tabs or sections for `Location history` and `Usage sessions`.
- Vehicle detail: show same generic history/usage sections as Equipment.
- Transfer modals: call `PATCH /api/v1/equipment/{id}/placement`.
- Disable transfer action when there is an `OPEN` usage session, or handle 409 conflict.
- Usage start form:
  - Equipment: choose operator employee.
  - Vehicle: default operator to assigned driver if using vehicle-specific UI.
- Usage return form:
  - Ask meter value only when equipment has generic meter usage.
  - Ask odometer/engine hours for vehicle.
- Timeline duration: display backend `durationMinutes`; do not recalculate unless UX needs live active timer.
