# Equipment Average Life and Dynamic Attributes Backend Audit

Date: 2026-05-25

## 1. Executive Summary

Audit only. No backend business code or migrations were changed.

- `averageOperatingLifeHours` does not currently exist as a core `Equipment` field.
- The closest hour-related fields are not equivalent:
  - `EquipmentKPI.operatingHours` maps to `equipment_kpis.operating_hours` for period KPI records, not equipment creation.
  - `VehicleDetails.currentEngineHours` maps to `vehicle_details.current_engine_hours` for vehicle runtime counters, not expected/average operating life.
- Backend already supports dynamic equipment type attributes and equipment attribute values.
- Equipment create/update already has partial dynamic value support through `EquipmentCreateRequest.attributes` and `EquipmentUpdateRequest.attributes`.
- Required dynamic attributes are only enforced when `attributes` is non-null. If equipment is created with `attributes == null`, `EquipmentAttributeService.upsertValues(...)` returns early and required attributes are not enforced.
- Backend does not appear to prevent dynamic attribute keys from colliding with core equipment fields such as `model`, `manufacturer`, `description`, or future `averageOperatingLifeHours`.
- Vehicle metrics are currently hardcoded in `vehicle_details`, `VehicleRequest`, `VehicleDetails`, and `VehicleService`, even though dynamic attributes could support many of those values.

## 2. Current Equipment Create/Update Contract

Controller: `src/main/java/com/toir/controller/equipment/EquipmentController.java`

- `GET /api/v1/equipment`
  - returns `Page<EquipmentDto>`
- `GET /api/v1/equipment/{id}`
  - returns `EquipmentDetailDto`
- `POST /api/v1/equipment`
  - request: `EquipmentCreateRequest`
  - response: `EquipmentDto`
- `PUT /api/v1/equipment/{id}`
  - request: `EquipmentUpdateRequest`
  - response: `EquipmentDto`
- `PATCH /api/v1/equipment/{id}/placement`
  - request: `EquipmentPlacementRequest`
  - response: `EquipmentDto`

Core entity: `src/main/java/com/toir/entity/equipment/Equipment.java`

Fields currently present:

- `code`
- `name`
- `inventoryNumber` -> DB column `inventory_number`
- `technicalNumber` -> DB column `technical_number`
- `serialNumber` -> DB column `serial_number`
- `model`
- `equipmentTypeId` -> DB column `equipment_type_id`
- `departmentId` -> DB column `department_id`
- `locationId` -> DB column `location_id`
- `parentId` -> DB column `parent_id`
- `criticalityClassId` -> DB column `criticality_class_id`
- `responsibleId` -> DB column `responsible_id`
- `manufacturer`
- `status`
- `category`
- `commissionedAt` -> DB column `commissioned_at`
- `warrantyUntil` -> DB column `warranty_until`
- `description`

Create DTO: `src/main/java/com/toir/dto/equipment/EquipmentCreateRequest.java`

- Required validation:
  - `@NotBlank name`
  - `@NotBlank inventoryNumber`
  - `@NotNull equipmentTypeId`
- Supports placement with `departmentId` or `warehouseId`.
- Supports dynamic values with `List<EquipmentAttributeValueRequest> attributes`.
- Does not include `averageOperatingLifeHours`.

Update DTO: `src/main/java/com/toir/dto/equipment/EquipmentUpdateRequest.java`

- No bean validation annotations on core fields.
- Supports dynamic values with `List<EquipmentAttributeValueRequest> attributes`.
- Does not include `averageOperatingLifeHours`.

Response DTO: `src/main/java/com/toir/dto/equipment/EquipmentDto.java`

- Returns core equipment fields and refs.
- Has legacy `PassportRef` with `passportNumber`, `powerKw`, `voltageV`, `pressureBar`.
- Does not include `averageOperatingLifeHours`.

Detail response: `src/main/java/com/toir/dto/equipment/EquipmentDetailDto.java`

- Wraps `EquipmentDto equipment`.
- Includes `List<EquipmentAttributeValueDto> attributes`.
- Detail endpoint returns dynamic attribute definitions plus values for the equipment.

Service: `src/main/java/com/toir/service/equipment/EquipmentService.java`

- `create(EquipmentCreateRequest request)`:
  - validates client-provided code, placement, department/warehouse, inventory uniqueness, parent.
  - calls `apply(entity, request)`.
  - saves equipment.
  - calls `equipmentAttributeService.upsertValues(saved, request.attributes())`.
- `update(UUID id, EquipmentUpdateRequest request)`:
  - validates no direct status change and department.
  - calls `applyForUpdate(...)`.
  - saves equipment.
  - calls `equipmentAttributeService.upsertValues(saved, request.attributes())` only when `request.attributes() != null`.

## 3. Whether Average Operating Life Already Exists

Search targets checked:

- `averageOperatingLifeHours`
- `averageOperatingLife`
- `operatingLifeHours`
- `expectedUsefulLifeHours`
- `serviceLifeHours`
- `lifetimeHours`
- `resourceHours`
- `engineHours`
- `odometer`
- `operatingHours`

Findings:

| Field | Location | DB column | Request/response exposure | Create required? | Update supported? | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| `averageOperatingLifeHours` | Not found | Not found | No | No | No | Required new core field is missing. |
| `operatingHours` | `src/main/java/com/toir/entity/equipment/EquipmentKPI.java` | `equipment_kpis.operating_hours` | `EquipmentKPIDto.operatingHours` | No | KPI record only | Period KPI input, not equipment expected life. |
| `currentEngineHours` | `src/main/java/com/toir/entity/equipment/VehicleDetails.java` | `vehicle_details.current_engine_hours` | `VehicleRequest`, `VehicleDetailDto` | Not required in DTO; defaults to `0` in service | Yes | Vehicle runtime counter, not average life. |
| `currentOdometerKm` | `src/main/java/com/toir/entity/equipment/VehicleDetails.java` | `vehicle_details.current_odometer_km` | `VehicleRequest`, `VehicleDetailDto` | Not required in DTO; defaults to `0` in service | Yes | Vehicle metric. |

Conclusion: `averageOperatingLifeHours` is absent. A new core `Equipment` field and DB migration are needed in the fix phase.

## 4. Recommended Backend Integration Approach

Recommended for next fix phase:

1. Add core field `averageOperatingLifeHours` to `Equipment`.
2. Persist it as `equipment.average_operating_life_hours`.
3. Add it to:
   - `EquipmentCreateRequest`
   - `EquipmentUpdateRequest`
   - `EquipmentDto`
   - `EquipmentService.apply(...)`
   - `EquipmentService.applyForUpdate(...)`
4. Make create require a positive hours value.
5. Allow update to change it, with positive validation when provided.
6. Do not model future cycles/revolutions yet.
7. Keep it out of dynamic attributes to avoid key collisions and PPR condition ambiguity.

Migration note: existing rows need a backfill/default decision. A safe migration likely needs either:

- nullable column first plus application validation for new creates, then operational backfill and later `NOT NULL`; or
- immediate `NOT NULL` with a documented default/backfill value.

## 5. Dynamic Attribute Definition Endpoints

Controller: `src/main/java/com/toir/controller/equipment/EquipmentAttributeController.java`

Definition endpoints:

- `GET /api/v1/equipment-types/{equipmentTypeId}/attributes`
  - response: `List<EquipmentAttributeDefinitionDto>`
- `POST /api/v1/equipment-types/{equipmentTypeId}/attributes`
  - request: `EquipmentAttributeDefinitionRequest`
  - response: `EquipmentAttributeDefinitionDto`
- `POST /api/v1/equipment-types/{equipmentTypeId}/attributes/batch`
  - request: `List<EquipmentAttributeDefinitionRequest>`
  - response: `List<EquipmentAttributeDefinitionDto>`
- `PUT /api/v1/equipment-types/{equipmentTypeId}/attributes/{attributeId}`
  - request: `EquipmentAttributeDefinitionRequest`
  - response: `EquipmentAttributeDefinitionDto`
- `DELETE /api/v1/equipment-types/{equipmentTypeId}/attributes/{attributeId}`

Option-source endpoints:

- `GET /api/v1/equipment-attribute-option-sources`
- `POST /api/v1/equipment-attribute-option-sources`
- `GET /api/v1/equipment-attribute-option-sources/{sourceId}/options`
- `PUT /api/v1/equipment-attribute-option-sources/{sourceId}/options`

Definition entity: `src/main/java/com/toir/entity/equipment/EquipmentAttributeDefinition.java`

- table: `equipment_attribute_definitions`
- key column: `attribute_key`
- data type column: `data_type`
- required column: `is_required`
- min/max: `min_value`, `max_value`
- inline options: `options_json`
- option source: `option_source_id`
- grouping: `group_name`
- ordering: `sort_order`
- localized labels: `label`, `label_ru`, `label_uz`

DTOs:

- `EquipmentAttributeDefinitionRequest`
- `EquipmentAttributeDefinitionDto`
- `EquipmentAttributeOptionDto`
- `EquipmentAttributeOptionSourceRequest`
- `EquipmentAttributeOptionSourceDto`

Data types: `src/main/java/com/toir/enums/EquipmentAttributeDataType.java`

- `TEXT`
- `NUMBER`
- `DATE`
- `BOOLEAN`
- `SELECT`
- `MULTI_SELECT`
- `FILE`
- `REFERENCE`
- `RANGE`
- `JSON`

## 6. Dynamic Attribute Value Save/Read Support Status

Value endpoints:

- `GET /api/v1/equipment/{equipmentId}/attributes`
  - response: `List<EquipmentAttributeValueDto>`
- `GET /api/v1/equipment/{equipmentId}/attributes/history`
  - response: `Page<EquipmentAttributeValueHistoryDto>`
- `PUT /api/v1/equipment/{equipmentId}/attributes`
  - request: `List<EquipmentAttributeValueRequest>`
  - response: `List<EquipmentAttributeValueDto>`
- `POST /api/v1/equipment/{equipmentId}/attributes`
  - request: `List<EquipmentAttributeValueRequest>`
  - response: `List<EquipmentAttributeValueDto>`

Storage:

- `src/main/java/com/toir/entity/equipment/EquipmentAttributeValue.java`
- table: `equipment_attribute_values`
- columns:
  - `equipment_id`
  - `attribute_definition_id`
  - `value_text`
  - `value_number`
  - `value_date`
  - `value_boolean`
  - `value_option`
  - `value_json`

Create/update payload support:

- `EquipmentCreateRequest.attributes`
- `EquipmentUpdateRequest.attributes`
- field type: `List<EquipmentAttributeValueRequest>`

Read support:

- `EquipmentDetailDto.attributes` returns dynamic values on `GET /api/v1/equipment/{id}`.
- `GET /api/v1/equipment/{id}/attributes` returns values directly.
- `GET /api/v1/equipment` list returns `EquipmentDto` and does not include dynamic `attributes`.

Validation implemented in `EquipmentAttributeService`:

- unknown `attributeDefinitionId` is rejected if it does not belong to the equipment type.
- unknown `key` is rejected.
- duplicate values in the same request are rejected.
- required attributes are enforced when `upsertValues(...)` runs.
- criticality-specific required attributes are supported by `equipment_attribute_required_criticality`.
- expected value field is enforced by `dataType`.
- only one value field may be populated.
- number/range `minValue` and `maxValue` are enforced.
- `SELECT` options are enforced against either option source or inline options.

Validation gap:

- `upsertValues(equipment, requests)` returns immediately when `requests == null`.
- `EquipmentService.create(...)` passes `request.attributes()` directly, so required dynamic attributes are not enforced when create omits `attributes`.
- `EquipmentService.update(...)` only calls `upsertValues(...)` when `request.attributes() != null`, so omitted dynamic attributes do not trigger required validation.

## 7. Validation Gaps

- No core validation for `averageOperatingLifeHours` because the field does not exist.
- No backend reserved-key validation for equipment type attribute definitions.
- Dynamic attributes can collide with core fields such as:
  - `id`
  - `code`
  - `name`
  - `manufacturer`
  - `model`
  - `description`
  - `equipmentTypeId`
  - `departmentId`
  - `warehouseId`
  - `averageOperatingLifeHours`
- Risk: a dynamic attribute with key `model` can coexist with core `Equipment.model`, producing duplicate UI fields and ambiguous payloads.
- Required dynamic attributes are only enforced when dynamic values are submitted.
- Definition validation normalizes keys and checks duplicate keys per equipment type, but does not compare against reserved core field names.

## 8. Migration Needs

Relevant migrations:

- `src/main/resources/db/migration/V20260521_1__equipment_dynamic_passport_attributes.sql`
  - creates `equipment_attribute_definitions`
  - creates `equipment_attribute_values`
- `src/main/resources/db/migration/V20260521_3__equipment_attribute_option_sources.sql`
  - adds `option_source_id`
  - creates option source/item tables
- `src/main/resources/db/migration/V20260523_2__equipment_attribute_required_criticality.sql`
  - creates criticality-specific required policy table
- `src/main/resources/db/migration/V20260523_3__equipment_attribute_value_history.sql`
  - creates value history table
- `src/main/resources/db/migration/V20260427_1__fleet_vehicle_foundation.sql`
  - creates `vehicle_details` with `fuel_tank_capacity`, `carrying_capacity`, `seat_count`, `current_odometer_km`, `current_engine_hours`
- `src/main/resources/db/migration/B20260523_7__schema_baseline.sql`
  - baseline includes `equipment`, dynamic attribute tables, `equipment_kpis.operating_hours`, and `vehicle_details` metric columns.

Equipment table currently has no average operating life column. Adding `averageOperatingLifeHours` requires a new Flyway migration in the next phase.

No migration should rename existing applied migrations. Existing vehicle metric columns are hardcoded schema and should be left in place unless a separate data migration/deprecation plan is approved.

## 9. PPR / Maintenance Condition Filtering Relation

Maintenance regulation conditions:

- Entity: `src/main/java/com/toir/entity/maintenance/MaintenanceRegulationAttributeCondition.java`
- Key field: `attributeKey`
- Table: `maintenance_regulation_attribute_conditions`
- Value columns: `value_text`, `value_number`, `value_date`, `value_boolean`, `value_option`

Maintenance regulation service:

- `src/main/java/com/toir/service/maintanance/MaintenanceRegulationService.java`
- `toCondition(...)` validates `attributeKey` exists as an active `EquipmentAttributeDefinition` for the regulation equipment type.

PPR generator:

- `src/main/java/com/toir/service/PprGeneratorService.java`
- `loadAttributeIndex(...)` loads definitions by equipment type and values by equipment id.
- `matchesConditions(...)` looks up `MaintenanceRegulationAttributeCondition.attributeKey` in definitions and compares against `EquipmentAttributeValue`.

Impact of core `averageOperatingLifeHours`:

- Adding it as a core `Equipment` field will not automatically make it usable in maintenance regulation attribute conditions or PPR filtering.
- That is acceptable if average operating life is only a core equipment property.
- If future PPR filtering must use average life, the condition engine would need explicit support for core fields or a documented mirror strategy. Mirroring to dynamic attributes is not recommended for the initial requirement because it creates duplication.

## 10. Test Gaps

Backend tests needed in next fix phase:

- `EquipmentCreateRequest`/controller contract rejects missing `averageOperatingLifeHours`.
- `EquipmentCreateRequest`/controller contract rejects zero/negative values.
- `EquipmentService.create(...)` persists average life.
- `EquipmentService.update(...)` updates average life.
- `EquipmentDto.from(...)` exposes average life.
- Flyway migration contract confirms `equipment.average_operating_life_hours`.
- Dynamic attribute definition service rejects reserved keys.
- Equipment create enforces required dynamic attributes even when `attributes` is omitted or empty, if that behavior is desired.
- Vehicle metrics migration tests only if moving metrics to dynamic values is included in a later phase.

## 11. Exact Implementation Checklist For Next Fix Phase

Backend fix checklist:

1. Add `averageOperatingLifeHours` to `Equipment` with `@Column(name = "average_operating_life_hours", nullable = false)` or use a nullable transition migration.
2. Add a Flyway migration for `equipment.average_operating_life_hours`.
3. Decide and document existing-row backfill/default behavior.
4. Add field to `EquipmentCreateRequest` with required positive validation.
5. Add field to `EquipmentUpdateRequest` with positive validation.
6. Add field to `EquipmentDto`.
7. Map field in `EquipmentDto.from(...)`.
8. Map field in `EquipmentService.apply(...)`.
9. Map field in `EquipmentService.applyForUpdate(...)`.
10. Add reserved dynamic attribute key validation in `EquipmentAttributeService.createDefinition(...)`, `createDefinitionsBatch(...)`, and `updateDefinition(...)`.
11. Include `averageoperatinglifehours`, `average_operating_life_hours`, and existing core fields in the reserved-key set.
12. Decide whether `EquipmentService.create(...)` should call dynamic required validation even when `attributes == null`.
13. Add controller/service/migration tests.
14. Do not implement cycles/revolutions until that requirement is explicit.
