# Fleet Maintenance Extension Design

Date: 2026-04-27

## Source

This design is based on `/Users/tenzorsoft/Downloads/TZ_TOIR_Fleet_Maintenance_EN.docx`.

## Decision Summary

Fleet Maintenance will be implemented as an extension of the existing TOIR MRO/CMMS model, not as a standalone fleet system.

The core domain rule is:

```text
Vehicle = Equipment where category = VEHICLE
```

Fleet-specific fields belong in detail or context tables. Shared MRO processes continue to use the existing entities:

- `Equipment`
- `RepairRequest`
- `Defect`
- `WorkOrder`
- `PprPlan` / `PprTask`
- `MaintenanceRegulation`
- `EquipmentMeter` / `MeterReading`
- `SparePart`, `Warehouse`, `StockMovement`, `ProcurementRequest`
- `Contractor`
- `ActualCost`
- `Notification`
- reports and analytics

The API will follow the current repository style:

```text
/api/v1/vehicles
```

It will not use `/api/v1/toir/vehicles`.

## Non-Goals

The implementation must not introduce duplicate process entities such as:

- `VehicleRepairRequest`
- `VehicleWorkOrder`
- `VehicleActualCost`
- `VehicleProcurement`
- `VehiclePprTask`

Vehicle-specific data may be attached through context entities, but the operational process remains the common TOIR process.

Fuel and tire management are included in the overall target architecture, but they should be delivered after the vehicle registry, vehicle card, meters, preventive maintenance, and repair/order context flows are stable.

## Current Codebase Fit

The backend already has most core MRO primitives.

- `Equipment` exists but does not yet have a first-class equipment category field.
- `EquipmentMeter` and `MeterReading` exist and should become the universal asset meter mechanism required by the fleet spec.
- `MaintenanceRegulation` already has partial meter trigger fields and should be extended rather than replaced.
- `TechnicalDocument` exists but lacks expiry/status support required for insurance and technical inspection control.
- `RepairRequest` and `WorkOrder` exist and should receive optional fleet context records.
- The frontend already has pages for equipment, meters, maintenance regulations, repair requests, work orders, documents, costs, and dashboards.

Before implementing fleet screens, the existing frontend/backend list response mismatch should be fixed. The frontend commonly expects `{ items, meta }`, while the backend currently returns a mix of raw lists, Spring `Page`, and custom page wrappers.

## Shared API Contract

All paginated list endpoints used by the frontend should return:

```ts
type PageResponse<T> = {
  items: T[];
  meta: {
    page: number;
    pageSize: number;
    total: number;
  };
};
```

Public API requests should use one-based `page` values. Backend services may convert to zero-based indexes internally.

This contract should be applied before or during the first fleet implementation phase to avoid repeating adapter code in new fleet pages.

## Backend Domain Model

### Enums

Add or extend these enums:

```text
EquipmentCategory:
  PRODUCTION_EQUIPMENT
  VEHICLE
  ENERGY_EQUIPMENT
  INSTRUMENTATION
  BUILDING_INFRASTRUCTURE
  OTHER

VehicleType:
  PASSENGER_CAR
  TRUCK
  BUS
  SPECIAL_EQUIPMENT
  FORKLIFT
  TRAILER
  OTHER

VehicleBreakdownType:
  ENGINE
  TRANSMISSION
  BRAKES
  ELECTRICAL
  TIRES
  BODY
  SUSPENSION
  COOLING
  FUEL_SYSTEM
  ACCIDENT
  OTHER

MaintenanceTriggerType:
  CALENDAR
  METER
  CALENDAR_OR_METER
  CONDITION
  MANUAL

VehicleInspectionType:
  PRE_TRIP
  POST_TRIP
  MECHANIC
  MONTHLY
  SEASONAL

VehicleInspectionResult:
  PASSED
  FAILED
  NEEDS_REPAIR

VehicleDocumentStatus:
  ACTIVE
  EXPIRING_SOON
  EXPIRED
  ARCHIVED

TireStatus:
  IN_WAREHOUSE
  INSTALLED
  REMOVED
  WRITTEN_OFF
  LOST
```

`EquipmentStatus` should support fleet outage states. The preferred extension is:

```text
ACTIVE
STANDBY
IN_REPAIR
OUT_OF_SERVICE
CONSERVATION
DECOMMISSIONED
```

### Equipment

Add `category` to `Equipment`.

Rules:

- Default category for existing rows should be `PRODUCTION_EQUIPMENT` or `OTHER`, based on the safest migration choice.
- Vehicles are normal equipment rows with `category = VEHICLE`.
- `/vehicles` is a specialized fleet view over equipment where `category = VEHICLE`.

### VehicleDetails

Create `VehicleDetails` for vehicle-only fields.

Fields:

```text
id
equipmentId
plateNumber
vin
brand
model
manufactureYear
vehicleType
bodyNumber
chassisNumber
engineNumber
fuelType
fuelTankCapacity
carryingCapacity
seatCount
assignedDriverId
currentOdometerKm
currentEngineHours
registrationCertificateNumber
insurancePolicyNumber
insuranceExpiryDate
technicalInspectionExpiryDate
gpsDeviceId
createdAt
updatedAt
```

Rules:

- `VehicleDetails` may exist only for `Equipment.category = VEHICLE`.
- `plateNumber` is required and unique.
- `vin` is unique when present.
- `currentOdometerKm` and `currentEngineHours` must be non-negative.
- `assignedDriverId` must reference an existing user or employee when present.

### Universal Meters

Use the existing `EquipmentMeter` and `MeterReading` model as the universal meter-reading mechanism.

The meter type vocabulary should cover:

```text
ODOMETER
ENGINE_HOURS
OPERATING_HOURS
CYCLES
TEMPERATURE
PRESSURE
VIBRATION
OTHER
```

The source vocabulary should cover:

```text
MANUAL
TRIP_SHEET
GPS
WORK_ORDER
INSPECTION
ADJUSTMENT
IOT
SCADA
IMPORT
```

Rules:

- A normal reading cannot be lower than the previous reading.
- A lower reading is a correction and requires a mandatory comment.
- All meter changes are audit-logged.
- After each new vehicle odometer or engine-hours reading, the maintenance due checker runs for that vehicle.

### MaintenanceRegulation

Extend `MaintenanceRegulation` for calendar, meter, and whichever-comes-first logic.

Fields:

```text
triggerType
intervalDays
meterType
meterIntervalValue
advanceDays
advanceMeterValue
whicheverComesFirst
applicableEquipmentCategory
applicableVehicleType
```

Rules:

- Existing calendar behavior must continue to work.
- Counter-based rules must apply beyond vehicles where useful.
- Vehicle-specific rules filter by `applicableEquipmentCategory = VEHICLE` and optionally `applicableVehicleType`.

### VehicleMaintenanceDueService

Create `VehicleMaintenanceDueService`.

Responsibilities:

- Load active `Equipment` where `category = VEHICLE`.
- Load `VehicleDetails`.
- Load latest odometer and engine-hour readings.
- Load active `MaintenanceRegulation` records applicable to vehicles.
- Find the last completed maintenance for each vehicle/regulation pair.
- Calculate next due date and next due meter values.
- Create a normal `PprTask` when due.
- Create notifications for due and upcoming maintenance.

The service must not create a vehicle-specific PPR task entity.

### RepairRequestVehicleContext

Create optional vehicle context for repair requests.

Fields:

```text
id
repairRequestId
equipmentId
driverId
odometerKm
engineHours
breakdownType
isVehicleOperable
accidentRelated
locationDescription
createdAt
updatedAt
```

Rules:

- If `isVehicleOperable = false`, vehicle equipment status becomes `OUT_OF_SERVICE` or `IN_REPAIR`.
- If `accidentRelated = true`, photo/report/comment evidence is required before normal processing.
- A request cannot be closed without result or rejection reason.

### WorkOrderVehicleContext

Create optional vehicle context for work orders.

Fields:

```text
id
workOrderId
equipmentId
odometerAtStart
odometerAtEnd
engineHoursAtStart
engineHoursAtEnd
downtimeStart
downtimeEnd
roadTestRequired
roadTestResult
createdAt
updatedAt
```

Rules:

- When a vehicle work order starts, equipment status becomes `IN_REPAIR`.
- When it closes successfully, equipment status becomes `ACTIVE`.
- If repair is incomplete or the vehicle is not roadworthy, equipment status becomes `OUT_OF_SERVICE`.

### VehicleInspection

Create vehicle inspection support with default checklist behavior.

Default checklist items:

```text
brakes
lights
turnSignals
tires
oilLevel
coolantLevel
steering
mirrors
documents
firstAidKit
fireExtinguisher
```

Rules:

- `FAILED` or `NEEDS_REPAIR` result offers to create a normal `RepairRequest`.
- Critical item failure for brakes or steering changes vehicle status to `OUT_OF_SERVICE`.

### Vehicle Documents

Extend `TechnicalDocument` or add a document detail table so vehicle documents can have:

```text
expiryDate
status
issuer
documentNumber
```

Document types should support:

```text
TECH_PASSPORT
INSURANCE
TECHNICAL_INSPECTION
PERMIT
LEASE_CONTRACT
GPS_CONTRACT
OTHER
```

Rules:

- Notify 30, 14, and 7 days before expiry.
- Notify on expiry date.
- After expiry, status becomes `EXPIRED`.
- Expired or soon-expiring documents appear in fleet dashboard metrics and filters.

### Tire and Fuel Records

Tire management target model:

```text
VehicleTire:
  id
  serialNumber
  brand
  model
  size
  season
  purchaseDate
  purchasePrice
  status
  warehouseId

VehicleTireInstallation:
  id
  equipmentId
  tireId
  position
  installedAt
  installedOdometer
  removedAt
  removedOdometer
  removalReason
```

Fuel record target model:

```text
VehicleFuelRecord:
  id
  equipmentId
  date
  fuelType
  liters
  amount
  odometerKm
  driverId
  fuelCardNumber
  station
  source
  comment
  createdAt
```

Fuel tracking should be implemented after registry, meters, maintenance, repair/order context, inspections, and documents.

## Backend API Design

Use repo-style routes:

```text
GET    /api/v1/vehicles
GET    /api/v1/vehicles/{equipmentId}
POST   /api/v1/vehicles
PUT    /api/v1/vehicles/{equipmentId}
DELETE /api/v1/vehicles/{equipmentId}

GET    /api/v1/equipment/{equipmentId}/meter-readings
POST   /api/v1/equipment/{equipmentId}/meter-readings

POST   /api/v1/vehicles/maintenance/check-due
POST   /api/v1/vehicles/{equipmentId}/maintenance/generate
GET    /api/v1/vehicles/{equipmentId}/maintenance/upcoming

GET    /api/v1/repair-requests/{id}/vehicle-context
POST   /api/v1/repair-requests/{id}/vehicle-context
PUT    /api/v1/repair-requests/{id}/vehicle-context

GET    /api/v1/work-orders/{id}/vehicle-context
POST   /api/v1/work-orders/{id}/vehicle-context
PUT    /api/v1/work-orders/{id}/vehicle-context

GET    /api/v1/vehicles/{equipmentId}/inspections
POST   /api/v1/vehicles/{equipmentId}/inspections

GET    /api/v1/fleet/dashboard
```

Vehicle creation is transactional:

1. Create `Equipment` with `category = VEHICLE`.
2. Create `VehicleDetails`.
3. Create odometer and engine-hours meters if initial values are supplied.
4. Create initial meter readings for those initial values.
5. Optionally create vehicle documents.
6. Return a joined `VehicleDetailDto`.

## Frontend Design

Create a fleet feature area in the frontend:

```text
src/features/fleet/
  api.ts
  types.ts
  query-keys.ts
  components/
    vehicle-registry-table.tsx
    vehicle-form-dialog.tsx
    vehicle-status-badge.tsx
    vehicle-meter-panel.tsx
    vehicle-maintenance-panel.tsx
    vehicle-documents-panel.tsx
    vehicle-inspection-panel.tsx
    vehicle-costs-panel.tsx
  pages/
    fleet-registry-page.tsx
    vehicle-card-page.tsx
    fleet-dashboard-page.tsx
```

Routes:

```text
/vehicles
/vehicles/:equipmentId
/vehicles/:equipmentId/maintenance
/vehicles/:equipmentId/inspections
/fleet-dashboard
```

Navigation:

- Add Fleet under the existing Equipment/Assets group.
- Do not create a separate product shell for fleet.

### Fleet Registry

Columns:

- plate number
- brand/model
- vehicle type
- department
- assigned driver
- odometer
- engine hours
- status
- next maintenance
- insurance expiry
- technical inspection expiry

Filters:

- vehicle type
- status
- department
- driver
- overdue maintenance
- expiring insurance
- expiring technical inspection

### Vehicle Card

Tabs:

- Passport
- Meters
- Preventive Maintenance
- Repair Requests
- Work Orders
- Documents
- Costs
- History

Later tabs:

- Inspections
- Tires
- Fuel

## Permissions

Server-side authorization is mandatory.

Initial role mapping:

- `SYSTEM_ADMIN`: references, roles, access.
- `GARAGE_MANAGER` / `FLEET_MANAGER`: fleet registry, rules, PM control.
- `MECHANIC` / `FOREMAN`: inspections, diagnosis, repairs, work orders.
- `DRIVER`: repair requests, pre-trip inspections, odometer readings.
- `STOREKEEPER`: parts, oils, tires.
- `ECONOMIST`: cost control and approval.
- `VIEWER`: read-only.

If new roles are not added immediately:

- `GARAGE_MANAGER` maps to existing `WORKSHOP_HEAD`.
- `MECHANIC` maps to existing `FOREMAN`.

## Delivery Phases

### Phase 0: Shared Contract Cleanup

- Standardize paginated list response shape.
- Normalize public page indexing to one-based pages.
- Fix existing frontend API type drift before adding fleet pages.

### Phase 1: Vehicle Foundation

- Add `EquipmentCategory`.
- Add `Equipment.category`.
- Add `VehicleDetails`.
- Add vehicle CRUD service/controller.
- Add frontend fleet registry.
- Add vehicle card shell.

### Phase 2: Universal Meters

- Align meter types and sources with the fleet spec.
- Add correction workflow and audit logging.
- Add vehicle odometer and engine-hour panels.
- Trigger due maintenance checks after new readings.

### Phase 3: Counter-Based Preventive Maintenance

- Extend `MaintenanceRegulation`.
- Implement `VehicleMaintenanceDueService`.
- Generate normal `PprTask` records.
- Create notifications for due and upcoming PM.
- Add frontend PM tab and maintenance actions.

### Phase 4: Repair and Work Order Context

- Add `RepairRequestVehicleContext`.
- Add `WorkOrderVehicleContext`.
- Add vehicle status transitions.
- Show vehicle requests and work orders in the vehicle card.

### Phase 5: Inspections and Documents

- Add vehicle inspection model and default checklist.
- Add failed-inspection to repair-request flow.
- Extend vehicle documents with expiry/status.
- Add document expiry notification job.

### Phase 6: Tires, Fuel, Dashboard

- Add tire inventory and installation history.
- Add fuel records.
- Add fleet dashboard metrics:
  - total vehicles
  - active vehicles
  - vehicles under repair
  - overdue maintenance
  - maintenance due in 7 days
  - overdue documents
  - maintenance costs
  - cost per kilometer
  - downtime
  - total vehicle ownership cost

## Testing and Verification

Backend tests:

- Vehicle creation creates `Equipment`, `VehicleDetails`, and initial meters transactionally.
- `VehicleDetails` cannot exist for non-vehicle equipment.
- Plate and VIN uniqueness are enforced.
- Meter corrections require comments.
- Meter readings trigger maintenance due checks.
- Due PM creates normal `PprTask`.
- Vehicle repair request and work order contexts change equipment status correctly.
- Failed critical inspection changes equipment status to `OUT_OF_SERVICE`.
- Document expiry creates notifications and dashboard signals.

Frontend tests:

- Fleet API adapters consume the shared `{ items, meta }` response shape.
- Fleet registry renders empty, loading, error, and populated states.
- Vehicle form validates required vehicle fields.
- Vehicle card tabs render from route and query data.
- Meter reading mutation invalidates vehicle detail, meter history, and maintenance queries.
- Repair/order status changes invalidate vehicle detail and history.

End-to-end smoke flow:

1. Create vehicle.
2. Add odometer reading.
3. Run maintenance due check.
4. Generate maintenance task.
5. Create repair request with vehicle context.
6. Start and close work order.
7. Verify vehicle status, meter history, costs, and history panels.

## Open Implementation Notes

The existing frontend and backend are separate git repositories. This design document is stored in the backend repository because backend owns the core domain model. Frontend implementation details are still included here and should be mirrored in frontend task planning.

The existing local frontend build environment may require dependency installation before verification. That is separate from the fleet design and should be addressed during implementation planning.
