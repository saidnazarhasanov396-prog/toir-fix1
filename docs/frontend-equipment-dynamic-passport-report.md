# Frontend Integration Report: Equipment Dynamic Passport

## Summary

The backend now supports a hybrid equipment passport model.

Equipment still has fixed common fields such as `name`, `inventoryNumber`, `equipmentTypeId`, `departmentId`, `manufacturer`, `model`, and `serialNumber`.

Technical passport fields are now dynamic and depend on the selected equipment type.

Example:

```text
Equipment Type: Pump
Fields: flow_rate, head, motor_power, rpm, seal_type

Equipment Type: Vehicle
Fields: vin, plate_number, fuel_type, engine_power, mileage

Equipment Type: Computer
Fields: cpu, ram, storage, screen_size, refresh_rate
```

The frontend should render the technical passport from backend attribute definitions instead of hardcoding fields like `powerKw`, `pressureBar`, or `throughput`.

## New API Endpoints

### Equipment Type Passport Template

Use these endpoints to manage which fields belong to an equipment type.

```http
GET /api/v1/equipment-types/{equipmentTypeId}/attributes
POST /api/v1/equipment-types/{equipmentTypeId}/attributes
PUT /api/v1/equipment-types/{equipmentTypeId}/attributes/{attributeId}
DELETE /api/v1/equipment-types/{equipmentTypeId}/attributes/{attributeId}
```

### Equipment Passport Values

Use these endpoints to read and save actual values for a specific equipment item.

```http
GET /api/v1/equipment/{equipmentId}/attributes
PUT /api/v1/equipment/{equipmentId}/attributes
```

## Attribute Definition Response

Used by the frontend to render a dynamic form.

```json
{
  "id": "uuid",
  "equipmentTypeId": "uuid",
  "key": "motor_power",
  "label": "Motor Power",
  "labelRu": "Мощность двигателя",
  "labelUz": "Dvigatel quvvati",
  "dataType": "NUMBER",
  "unit": "kW",
  "required": true,
  "minValue": 0,
  "maxValue": 500,
  "options": [],
  "groupName": "Motor",
  "sortOrder": 10
}
```

Supported `dataType` values:

```text
TEXT
NUMBER
DATE
BOOLEAN
SELECT
MULTI_SELECT
FILE
REFERENCE
RANGE
JSON
```

## Attribute Value Request

Values can be sent by `key`:

```json
{
  "key": "motor_power",
  "valueNumber": 75
}
```

Or by `attributeDefinitionId`:

```json
{
  "attributeDefinitionId": "uuid",
  "valueNumber": 75
}
```

Available value fields:

```text
valueText
valueNumber
valueDate
valueBoolean
valueOption
valueJson
```

Frontend should send the matching value field based on `dataType`.

## Data Type Mapping

| dataType | Frontend control | Send field |
| --- | --- | --- |
| `TEXT` | Text input | `valueText` |
| `NUMBER` | Number input | `valueNumber` |
| `DATE` | Date picker | `valueDate` |
| `BOOLEAN` | Checkbox / toggle | `valueBoolean` |
| `SELECT` | Select dropdown | `valueOption` |
| `MULTI_SELECT` | Multi-select | `valueJson` |
| `FILE` | File/document picker | `valueText` |
| `REFERENCE` | Reference selector | `valueText` |
| `RANGE` | Number/range input | `valueNumber` |
| `JSON` | Structured editor / hidden serialized payload | `valueJson` |

For `SELECT`, use the `options` array from the definition.

## Create Equipment With Attributes

Endpoint:

```http
POST /api/v1/equipment
```

Example request:

```json
{
  "name": "Pump P-101",
  "inventoryNumber": "INV-000101",
  "equipmentTypeId": "uuid",
  "departmentId": "uuid",
  "manufacturer": "KSB",
  "model": "CPK 150-400",
  "serialNumber": "SN-001",
  "attributes": [
    {
      "key": "flow_rate",
      "valueNumber": 250
    },
    {
      "key": "head",
      "valueNumber": 80
    },
    {
      "key": "motor_power",
      "valueNumber": 75
    },
    {
      "key": "seal_type",
      "valueOption": "Mechanical seal"
    }
  ]
}
```

## Update Equipment With Attributes

Endpoint:

```http
PUT /api/v1/equipment/{id}
```

Example request:

```json
{
  "name": "Pump P-101 Updated",
  "attributes": [
    {
      "key": "motor_power",
      "valueNumber": 90
    }
  ]
}
```

If `attributes` is omitted, existing dynamic passport values are not changed.

## Equipment Detail Response

Endpoint:

```http
GET /api/v1/equipment/{id}
```

Response now includes `attributes`:

```json
{
  "equipment": {},
  "repairRequests": [],
  "defects": [],
  "workOrders": [],
  "downtimeEvents": [],
  "attributes": [
    {
      "id": "uuid",
      "equipmentId": "uuid",
      "attributeDefinitionId": "uuid",
      "key": "motor_power",
      "label": "Motor Power",
      "labelRu": "Мощность двигателя",
      "labelUz": "Dvigatel quvvati",
      "dataType": "NUMBER",
      "unit": "kW",
      "required": true,
      "options": [],
      "groupName": "Motor",
      "sortOrder": 10,
      "valueNumber": 75
    }
  ]
}
```

## Recommended Frontend Flow

1. User opens Create Equipment.
2. User fills common fields.
3. User selects `equipmentTypeId`.
4. Frontend calls:

```http
GET /api/v1/equipment-types/{equipmentTypeId}/attributes
```

5. Frontend renders dynamic fields sorted by `sortOrder`.
6. Frontend groups fields by `groupName` when present.
7. User fills technical passport values.
8. Frontend sends common equipment fields plus `attributes`.

## Recommended UI Structure

Equipment form should have two main sections:

```text
General Information
Technical Passport
```

`General Information` should use existing fixed fields.

`Technical Passport` should be generated dynamically from equipment type attribute definitions.

## Backend Validation

Backend validates:

```text
unknown keys are rejected
duplicate attribute values are rejected
required attributes must be filled
NUMBER and RANGE check minValue and maxValue
SELECT checks allowed options
attribute must belong to the selected equipment type
```

Frontend should display backend validation messages near the relevant dynamic field when possible.

## Notes

The existing fixed `equipment_passports` model still exists. Treat it as legacy/basic passport header data for now.

The new dynamic attributes are the preferred place for type-specific technical characteristics.

## PPR / Maintenance Regulation Integration

Maintenance regulations can now use dynamic passport attributes as applicability conditions.

This means PPR generation can distinguish equipment of the same type by passport values.

Example:

```text
Regulation: High-power pump monthly service
Equipment type: Pump
Condition: motor_power > 50
```

During PPR generation, the backend will create tasks only for matching equipment.

Regulations without conditions keep the previous behavior and apply to all active equipment of the selected `equipmentTypeId`.

### Maintenance Regulation Condition Shape

Maintenance regulation request/response now supports:

```json
{
  "attributeConditions": [
    {
      "attributeKey": "motor_power",
      "operator": "GREATER_THAN",
      "valueNumber": 50
    }
  ]
}
```

Supported operators:

```text
EQUALS
NOT_EQUALS
GREATER_THAN
GREATER_THAN_OR_EQUALS
LESS_THAN
LESS_THAN_OR_EQUALS
EXISTS
NOT_EXISTS
```

Available condition value fields:

```text
valueText
valueNumber
valueDate
valueBoolean
valueOption
```

### Maintenance Regulation Example

Endpoint:

```http
POST /api/v1/maintenance-regulations
```

Example request:

```json
{
  "name": "High-power pump monthly service",
  "description": "Monthly service for pumps above 50 kW",
  "equipmentTypeId": "uuid",
  "maintenanceKind": "PREVENTIVE",
  "normativeLaborHours": 4,
  "active": true,
  "periodicityUnit": "MONTH",
  "periodicityValue": 1,
  "toleranceDays": 3,
  "requiresShutdown": false,
  "attributeConditions": [
    {
      "attributeKey": "motor_power",
      "operator": "GREATER_THAN",
      "valueNumber": 50
    }
  ]
}
```

### Recommended Frontend Flow For Regulations

1. User selects `equipmentTypeId` in maintenance regulation form.
2. Frontend calls:

```http
GET /api/v1/equipment-types/{equipmentTypeId}/attributes
```

3. Frontend shows optional condition builder.
4. User selects an attribute, operator, and value.
5. Frontend sends conditions in `attributeConditions`.

For `NUMBER` attributes, show numeric comparison operators.

For `TEXT`, `SELECT`, and `BOOLEAN`, prefer `EQUALS`, `NOT_EQUALS`, `EXISTS`, and `NOT_EXISTS`.

For `DATE`, date comparisons can use equality and greater/less operators.
