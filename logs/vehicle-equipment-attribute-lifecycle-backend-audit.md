# Vehicle, Equipment, and Attribute Lifecycle Backend Audit

Audit date: 2026-05-26
Repository: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend`
Scope: audit only; no production code or Flyway migrations changed.

## 1. Executive Summary

Overall status: RISKY

- The official equipment attribute model is present and mostly normalized: `EquipmentAttributeDefinition` is tied to `EquipmentType`, and `EquipmentAttributeValue` is tied to concrete `Equipment`.
- `POST /api/v1/equipment-types/{equipmentTypeId}/attributes` creates attribute definitions, not concrete equipment values. This matches the PM decision.
- Definition uniqueness is backed by active `equipment_type_id + attribute_key` logic in service and Flyway schema.
- The PM rule is currently violated by a separate loose manual key/value path: `equipment_manual_attributes`, manual attribute DTOs, manual attribute services/controllers, and vehicle/equipment request payloads.
- Equipment create validates official attributes through `EquipmentAttributeService.upsertValues`, but equipment update can change `equipmentTypeId` without revalidating required attributes when `attributes` is omitted.
- Vehicle is implemented as `Equipment.category = VEHICLE` plus `VehicleDetails`, which is a compatible architecture, but the vehicle API omits official attributes from detail responses and still exposes manual attributes.
- Security is inconsistent: equipment CRUD uses RBAC/PBAC, but vehicle CRUD lacks controller-level `@PreAuthorize`, and official attribute value endpoints do not enforce equipment department scope.
- Backend tests cover many official attribute behaviors, but current tests also preserve the rejected manual key/value path and miss vehicle/security/type-change regressions.

PM decision status: VIOLATED. The official Attribute Definition/Value lifecycle exists, but the manual arbitrary key/value lifecycle is still a first-class backend API and DTO contract.

## 2. Current Architecture Map

Backend domain model map:

- `Equipment` (`src/main/java/com/toir/entity/equipment/Equipment.java`): common equipment fields, `equipmentTypeId`, department/location/warehouse placement, lifecycle status, category, criticality, parent relation.
- `EquipmentType` (`src/main/java/com/toir/entity/equipment/EquipmentType.java`): type catalogue with code, localized names, and category.
- `EquipmentAttributeDefinition` (`src/main/java/com/toir/entity/equipment/EquipmentAttributeDefinition.java`): dynamic field definition tied to `equipmentTypeId`; stable technical key is stored as `attribute_key`.
- `EquipmentAttributeValue` (`src/main/java/com/toir/entity/equipment/EquipmentAttributeValue.java`): concrete typed value tied to one equipment and one definition.
- `EquipmentAttributeValueHistory` (`src/main/java/com/toir/entity/equipment/EquipmentAttributeValueHistory.java`): change history for official values.
- `EquipmentAttributeRequiredCriticality` (`src/main/java/com/toir/entity/equipment/EquipmentAttributeRequiredCriticality.java`): extra required policy by criticality class.
- `VehicleDetails` (`src/main/java/com/toir/entity/equipment/VehicleDetails.java`): vehicle-specific common fields tied one-to-one to an equipment row.
- `EquipmentManualAttribute` (`src/main/java/com/toir/entity/equipment/EquipmentManualAttribute.java`): arbitrary key/value storage tied to equipment. This conflicts with the PM decision.

Lifecycle text diagram:

```text
EquipmentType
  -> EquipmentAttributeDefinition(key, type, constraints)
       -> EquipmentAttributeRequiredCriticality
Equipment
  -> EquipmentAttributeValue(definitionId, typed value)
       -> EquipmentAttributeValueHistory
Vehicle
  -> Equipment(category = VEHICLE)
  -> VehicleDetails
  -> EquipmentAttributeValue for dynamic vehicle attributes, when VehicleRequest.attributes is used
  -> EquipmentManualAttribute currently also accepted, but should be deprecated/replaced
```

Key migrations:

- `src/main/resources/db/migration/V20260521_1__equipment_dynamic_passport_attributes.sql`: official definition/value tables, FK constraints, active unique indexes.
- `src/main/resources/db/migration/V20260521_2__maintenance_regulation_attribute_conditions.sql`: maintenance conditions by `attribute_key`.
- `src/main/resources/db/migration/V20260521_3__equipment_attribute_option_sources.sql`: option source support.
- `src/main/resources/db/migration/V20260523_2__equipment_attribute_required_criticality.sql`: required-by-criticality mapping.
- `src/main/resources/db/migration/V20260523_3__equipment_attribute_value_history.sql`: official value history.
- `src/main/resources/db/migration/V20260427_1__fleet_vehicle_foundation.sql`: vehicle foundation using equipment plus vehicle details.
- `src/main/resources/db/migration/V20260526_3__equipment_manual_attributes.sql`: loose key/value manual attributes. This contradicts the PM decision.

## 3. Attribute Lifecycle Audit

| Flow | Current behavior | Expected behavior | Status | Evidence | Recommended fix |
|---|---|---|---|---|---|
| Create definition | Validates type, key, label, min/max, reserved keys, and active uniqueness; creates definition only. | Create a definition for an equipment type, not concrete equipment values. | OK | `EquipmentAttributeService.createDefinition`, `EquipmentAttributeController.createDefinition` | Keep this contract. Add regression test that definition creation never creates `EquipmentAttributeValue`. |
| Batch create definitions | Validates duplicate keys in request and active DB conflicts. | Preserve stable unique keys per type. | OK | `EquipmentAttributeService.createDefinitionsBatch` | Keep; add contract test for duplicate key casing/normalization if not already covered. |
| List definitions | Returns active definitions by equipment type, enriched with option values and required criticality. | Frontend can render dynamic forms by type. | OK | `EquipmentAttributeService.findDefinitions` | Keep; verify ordering and option-source fallback in frontend. |
| Update definition | Allows key/type/constraints/options to change after values may exist. | Existing values must remain valid or update must be restricted/migrated intentionally. | Risk | `EquipmentAttributeService.updateDefinition` | Block or explicitly migrate incompatible `dataType`, key, and constraint changes when values exist. |
| Delete definition | Soft-deletes definition and required-criticality rows; values remain but are hidden from active reads. | Existing historical values must not be destroyed; active input must reject deleted definitions. | OK/Risk | `EquipmentAttributeService.deleteDefinition`, `findValues` | Keep data preservation, but define read policy for archived/deleted attributes in passports/history. |
| Create/update official values | Resolves by `attributeDefinitionId` or stable `key`; rejects unknown keys and wrong type definitions; validates one typed field, min/max, required and options. | Values must go through official typed definition validation. | OK | `EquipmentAttributeService.upsertValues`, `replaceValues`, `validateValue`, `validateRequired` | Add tests for disabled/deleted definitions, type changes, and option-source values. |
| Empty value handling | Required definitions reject empty values; optional empty payloads are accepted and saved with no typed value. | Optional blank values should either clear explicitly or be rejected according to product semantics. | Risk | `EquipmentAttributeService.validateValue`, `serializeValue` | Decide clear-vs-empty semantics; add tests for clearing values. |
| `MULTI_SELECT` and `JSON` validation | `MULTI_SELECT`/`JSON` use `valueJson`; JSON is accepted as an official typed attribute value. | JSON should only be allowed as a typed definition, not as loose arbitrary business flow. | Risk | `EquipmentAttributeService.validateValue` | Keep only if governed by definitions; validate JSON shape or constrain usage where needed. |
| Required by criticality | Required definitions can be global or criticality-specific. | Required attributes are enforced on create/update values. | OK/Risk | `EquipmentAttributeRequiredCriticality`, `EquipmentAttributeService.validateRequired` | Vehicle frontend lacks criticality context; add vehicle-specific tests and UI handling. |
| Attribute conditions for maintenance/PPR | Conditions reference stable `attribute_key`; service validates key exists for equipment type; PPR loads definitions/values in bulk. | Conditions should use stable key and type-aware values. | OK/Risk | `MaintenanceRegulationService.replaceConditions`, `PprGeneratorService.loadAttributeIndex` | Add type compatibility validation for condition values. |
| Manual arbitrary attributes | Full CRUD and request DTO support for `key/value` manual attributes. | Rejected by PM; dynamic data must use Attribute Definition/Value lifecycle. | Bug | `EquipmentManualAttributeService`, `EquipmentManualAttributeController`, `V20260526_3__equipment_manual_attributes.sql` | Deprecate from write paths and migrate UX/API toward official definitions/values. Do not edit old migration. |

## 4. Equipment Lifecycle With Attributes Audit

| Flow | Current behavior | Expected behavior | Status | Evidence | Recommended fix |
|---|---|---|---|---|---|
| Equipment create | Saves equipment, then calls `equipmentAttributeService.upsertValues(saved, request.attributes() or empty list)`, enforcing required official attributes. | Official attribute values accepted only through validated contract. | OK | `EquipmentService.create` | Keep. Add controller contract tests for required missing attributes. |
| Equipment create manual attributes | Also accepts `request.manualAttributes()` and writes loose key/value rows. | Do not accept arbitrary key/value as business flow. | Bug | `EquipmentCreateRequest.manualAttributes`, `EquipmentService.create`, `EquipmentManualAttributeService.replaceAll` | Remove/deprecate write support in a compatibility phase; preserve existing rows read-only if needed. |
| Equipment update with attributes | Calls `upsertValues` only when `request.attributes() != null`. | Updating attributes should use official contract and validate required values. | OK/Risk | `EquipmentService.update` | Keep partial update support, but handle type changes separately. |
| Equipment type change | `equipmentTypeId` can change while `attributes` is omitted; old values remain and new required values are not validated. | Type change must validate required attributes for new type and define old-value handling. | Bug | `EquipmentService.applyUpdate`, `EquipmentService.update` | Require `attributes` on type change or reject type change until migration policy is implemented. |
| Equipment detail | Returns common equipment DTO, official `attributes`, and manual attributes. | Detail/passport should return official attributes in renderable format. | OK/Bug | `EquipmentService.findDetailById`, `EquipmentDetailDto` | Keep official attributes; remove/deprecate manual attributes from write-facing contracts. |
| Equipment list/search | Returns `EquipmentDto` without heavy attributes; type/department/location references are batched. | List should stay light unless attribute filters are explicitly supported. | OK | `EquipmentService.search` | Keep; document that attribute filtering is unsupported or add explicit endpoint later. |
| Status/lifecycle | Direct status mutation is blocked in normal update; status endpoint uses lifecycle service/history. | Lifecycle transitions should be controlled and auditable. | OK | `EquipmentService.validateNoDirectStatusChange`, `EquipmentStatusLifecycleService` | Keep; align vehicle update behavior. |
| Warehouse/replacement interactions | Audit did not find direct attribute mutation in warehouse transfer paths. | Attribute values should persist across lifecycle/placement changes. | OK/Unknown | `EquipmentController.updatePlacement`, `EquipmentService.updatePlacement` | Add regression test that placement/status changes preserve attributes. |
| Passport APIs | Official attributes are returned in equipment detail; history endpoint exists for official values. | Passport/detail UI should render official definitions/values consistently. | OK/Risk | `EquipmentAttributeController.findValues`, `findHistory`, `EquipmentService.findDetailById` | Ensure frontend consumes root-level detail attributes correctly. |

## 5. Vehicle Lifecycle With Attributes Audit

| Flow | Current behavior | Expected behavior | Status | Evidence | Recommended fix |
|---|---|---|---|---|---|
| Vehicle model | Vehicle is an `Equipment` row with `category = VEHICLE` plus `VehicleDetails`. | Compatible architecture if common fields remain on equipment/vehicle details and dynamic fields use official attributes. | OK | `VehicleService.create`, `VehicleDetails` | Keep as the primary identity model. |
| Vehicle create official attributes | Calls `equipmentAttributeService.upsertValues(savedEquipment, request.attributes() or empty list)`. | Vehicle dynamic attributes should use official Attribute Definition/Value lifecycle. | OK | `VehicleService.create`, `VehicleRequest.attributes` | Keep; add contract tests. |
| Vehicle create manual attributes | Accepts `VehicleRequest.manualAttributes` and writes loose key/value rows. | Rejected by PM. | Bug | `VehicleService.create`, `VehicleRequest.manualAttributes` | Deprecate manual write path; map future dynamic data to official definitions/values. |
| Vehicle update official attributes | Calls `upsertValues` only when `request.attributes() != null`. | Attribute updates should be validated; type change must validate new required values. | Risk | `VehicleService.update` | Apply same type-change policy as equipment. |
| Vehicle detail official attributes | Detail response includes manual attributes but not official `EquipmentAttributeValueDto` values. | Vehicle detail should expose official attributes in renderable form. | Bug | `VehicleService.findByEquipmentId`, `VehicleDetailDto.from` | Add official `attributes` to `VehicleDetailDto`; keep shape aligned with `EquipmentDetailDto`. |
| Vehicle lifecycle/status | `VehicleService.applyEquipment` sets `equipment.status` directly from request, bypassing `EquipmentStatusLifecycleService`. | Status changes should use the same lifecycle/history rules as equipment. | Bug | `VehicleService.applyEquipment`, `VehicleService.update` | Reject direct status in vehicle update or delegate to lifecycle service. |
| Vehicle identity in repair/defect/work order flows | Vehicle uses equipment ID as identity; frontend repair APIs appear to pass `equipmentId`. | Work orders/defects should reference the canonical equipment ID. | OK/Unknown | `VehicleController`, frontend `vehicle-card-page.tsx` repair actions | Add integration tests across vehicle repair/defect flows. |
| Vehicle scope/ownership | Service checks scope for document methods; normal get/create/update/delete rely on incomplete controller/service enforcement. | Vehicle CRUD must enforce RBAC and department scope like equipment. | Bug | `VehicleController`, `VehicleService.enforceVehicleAccess` | Add `@PreAuthorize` and PBAC checks for all CRUD/detail paths. |

## 6. API Contract Matrix

| Area | Backend endpoint | Backend DTO | Frontend API method | Frontend consumer | Status | Problem | Recommended action |
|---|---|---|---|---|---|---|---|
| Equipment list | `GET /api/v1/equipment` | `Page<EquipmentDto>` | `api.getEquipment` | Equipment registry/list pages | OK | No dynamic attrs in list by design. | Keep list light; document no attribute filters. |
| Equipment detail | `GET /api/v1/equipment/{id}` | `EquipmentDetailDto` | `api.getEquipmentById` | Equipment card, edit form | Risk | Includes official attrs and manual attrs; frontend card mishandles official attrs. | Preserve official attrs, phase out manual attrs. |
| Equipment create | `POST /api/v1/equipment` | `EquipmentCreateRequest` | `api.createEquipment` | `equipment-registry-page.tsx` | Bug | Supports both official `attributes` and rejected `manualAttributes`. | Keep official; deprecate manual. |
| Equipment update | `PUT /api/v1/equipment/{id}` | `EquipmentUpdateRequest` | `api.updateEquipment` | Registry/card pages | Bug | Type change can bypass required attributes; manual attrs still accepted. | Require attr validation on type change; deprecate manual. |
| Equipment status | `PATCH /api/v1/equipment/{id}/status` | `EquipmentStatusChangeRequest` | `api.updateEquipmentStatus` | Equipment card/status UI | OK | Lifecycle service used. | Keep. |
| Equipment type list/detail | `GET /api/v1/equipment-types`, `GET /api/v1/equipment-types/{id}` | `EquipmentTypeDto` | `api.getEquipmentTypes`, `api.getEquipmentTypeById` | Equipment type pages, forms | OK/Risk | `GET /stats` lacks `@PreAuthorize`. | Add RBAC to stats endpoint. |
| Attribute definitions list | `GET /api/v1/equipment-types/{id}/attributes` | `List<EquipmentAttributeDefinitionDto>` | `api.getEquipmentTypeAttributes` | Dynamic forms, type attr page | OK | Query key naming inconsistent on frontend. | Keep backend; normalize frontend cache keys. |
| Attribute definition create | `POST /api/v1/equipment-types/{id}/attributes` | `EquipmentAttributeDefinitionRequest` | `api.createEquipmentTypeAttribute` | Equipment type attr pages | OK | Creates definitions only. | Add explicit contract test for PM rule. |
| Attribute definition update | `PUT /api/v1/equipment-types/{id}/attributes/{attributeId}` | `EquipmentAttributeDefinitionRequest` | `api.updateEquipmentTypeAttribute` | Equipment type attr pages | Risk | Can mutate key/type while values exist. | Restrict incompatible updates. |
| Attribute definition delete | `DELETE /api/v1/equipment-types/{id}/attributes/{attributeId}` | none | `api.deleteEquipmentTypeAttribute` | Equipment type attr pages | OK/Risk | Soft-deletes definition; archived values hidden. | Define archived display policy. |
| Attribute values read | `GET /api/v1/equipment/{equipmentId}/attributes` | `List<EquipmentAttributeValueDto>` | `api.getEquipmentAttributes` | Dynamic passport, fleet edit | Bug | RBAC only; no equipment department PBAC in controller. | Add `ScopeAccessService.assertCanAccessEquipment`. |
| Attribute values write | `PUT/POST /api/v1/equipment/{equipmentId}/attributes` | `List<EquipmentAttributeValueRequest>` | `api.updateEquipmentAttributes`, `api.createEquipmentAttributes` | Dynamic passport, forms | Bug | No PBAC on equipment ID in controller. | Add scope checks and tests. |
| Attribute history | `GET /api/v1/equipment/{equipmentId}/attributes/history` | `List<EquipmentAttributeValueHistoryDto>` | `api.getEquipmentAttributeHistory` | Equipment card/history | Bug/Risk | No PBAC; frontend normalization bug may hide filters. | Add scope checks; fix consumer separately. |
| Manual attributes | `/api/v1/equipment/{id}/manual-attributes`, `/api/v1/vehicles/{id}/manual-attributes` | Manual attr DTOs | Manual attr API methods | Equipment/fleet/card pages | Bug | Rejected arbitrary key/value lifecycle. | Deprecate write APIs; preserve/migrate data intentionally. |
| Vehicle list/stats | `GET /api/v1/vehicles`, `GET /api/v1/vehicles/stats` | `VehicleSummaryDto`, `VehicleStatsResponse` | `api.getVehicles`, `api.getVehicleStats` | Fleet registry | Bug | No `@PreAuthorize`; stats/list only department scope via `SecurityScope`. | Add RBAC and tests. |
| Vehicle detail | `GET /api/v1/vehicles/{equipmentId}` | `VehicleDetailDto` | `api.getVehicleById` | Vehicle card/edit | Bug | Omits official attributes; includes manual attrs. | Add official attrs; phase out manual. |
| Vehicle create | `POST /api/v1/vehicles` | `VehicleRequest` | `api.createVehicle` | Fleet registry | Bug | No controller RBAC; manual attrs accepted. | Add RBAC/PBAC; keep official attrs only. |
| Vehicle update | `PUT /api/v1/vehicles/{equipmentId}` | `VehicleRequest` | `api.updateVehicle` | Fleet registry | Bug | No controller RBAC; direct status mutation; type-change attr gap. | Align with equipment lifecycle. |
| Vehicle delete | `DELETE /api/v1/vehicles/{equipmentId}` | none | `api.deleteVehicle` | Fleet registry | Bug | No controller RBAC/PBAC; audit action uses CREATE in service delete. | Add checks; correct audit action in future fix. |
| Option sources | `GET/POST /api/v1/equipment-attribute-option-sources`, `GET/PUT /{sourceId}/options` | Option source DTOs | `api.getOptionSources*`, `api.createOptionSource`, `api.updateOptionSource`, `api.deleteOptionSource` | Equipment type attr UI | Risk | Backend lacks update/delete source endpoints that frontend API exposes. | Either add endpoints or remove unused client methods. |

## 7. Business Logic Issues

P0:

- None confirmed in this audit.

P1:

- PM rule violation: `EquipmentManualAttribute` is a loose arbitrary key/value lifecycle exposed through equipment and vehicle APIs.
- Official attribute value read/write/history endpoints do not enforce equipment department PBAC.
- Vehicle CRUD/detail/delete endpoints lack explicit RBAC and are not aligned with equipment scope checks.
- Vehicle update bypasses equipment status lifecycle/history by setting status directly.
- Vehicle detail does not return official attribute values, so official vehicle attributes are not a complete API contract.
- Equipment/vehicle type changes can bypass required attributes when `attributes` is omitted.

P2:

- Definition update can mutate key/type/constraints with existing values and no compatibility check.
- Maintenance regulation attribute condition values are not type-validated against their definitions.
- Optional empty attribute values can be saved with no typed value; clear semantics are unclear.
- Vehicle delete audit uses `AuditAction.CREATE` in `VehicleService.delete`.
- `EquipmentTypeController.stats` lacks `@PreAuthorize` while adjacent endpoints have it.
- Manual attribute tests reinforce a rejected product behavior.

P3:

- Archived/deleted attribute display policy is not explicit.
- Attribute list/detail ordering and option-source behavior need frontend/backend contract documentation.

## 8. Architecture Issues

- Duplication: official attributes and manual attributes both represent dynamic data, creating two competing ownership models.
- Wrong ownership: manual attributes let equipment/vehicle instances define ad hoc fields instead of using type-owned definitions.
- Weak boundaries: vehicle service creates/updates equipment rows directly and does not reuse equipment lifecycle controls consistently.
- Loose key-value bypass: `EquipmentManualAttributeService` and `EquipmentManualAttributeController` bypass type, required, options, min/max, and lifecycle validation.
- DTO mismatch: `VehicleRequest` accepts official `attributes`, but `VehicleDetailDto` does not return them.
- N+1 risk: official equipment list avoids attributes, which is good; any future list-with-attributes endpoint should bulk load definitions/values like `PprGeneratorService`.
- Missing constraints/policies: official schema has good FK/unique constraints, but definition mutation with existing values is not governed.
- Transaction risk: create/update methods are transactional, but type-change semantic integrity is not guaranteed.
- Testability issue: manual attribute tests currently make the rejected flow look intentional.

## 9. Test Gap Matrix

| Missing test | Layer | File suggested | Scenario | Expected assertion | Priority |
|---|---|---|---|---|---|
| `createDefinitionDoesNotCreateEquipmentValues` | Controller/service | `EquipmentAttributeControllerContractTest` or `EquipmentAttributeServiceTest` | POST type attribute definition | Only definition row is created; no value rows exist. | P1 |
| `attributeValuesRequireEquipmentScope` | Security/controller | `EquipmentAttributeControllerSecurityTest` | User with `EQUIPMENT_UPDATE` writes another department equipment attributes | 403. | P1 |
| `vehicleCrudRequiresVehiclePermissions` | Security/controller | `VehicleControllerSecurityTest` | Missing permission calls create/update/delete/detail | 403. | P1 |
| `vehicleDetailReturnsOfficialAttributes` | Service/controller | `VehicleServiceTest`, `VehicleControllerContractTest` | Vehicle has official attr value | Detail response contains `attributes` with key/value. | P1 |
| `vehicleUpdateDoesNotBypassStatusLifecycle` | Service | `VehicleServiceTest` | Update request changes status | Direct status update rejected or lifecycle history created. | P1 |
| `equipmentTypeChangeRequiresNewRequiredAttributes` | Service | `EquipmentServiceTest` | Change equipment type without required attrs | Bad request. | P1 |
| `vehicleTypeChangeRequiresNewRequiredAttributes` | Service | `VehicleServiceTest` | Change vehicle equipment type without required attrs | Bad request. | P1 |
| `manualAttributesNotAcceptedOnCreate` | Contract | `EquipmentControllerContractTest`, `VehicleControllerContractTest` | Request includes `manualAttributes` after deprecation | Request rejected or field ignored with documented compatibility behavior. | P1 |
| `definitionTypeCannotChangeWithExistingValues` | Service | `EquipmentAttributeServiceTest` | Change NUMBER definition to DATE after value exists | Bad request. | P2 |
| `deletedDefinitionRejectsNewValuesButKeepsHistory` | Service | `EquipmentAttributeServiceTest` | Delete definition then submit value by old key | Bad request; history/read policy verified. | P2 |
| `maintenanceAttributeConditionValidatesType` | Service | `MaintenanceRegulationServiceTest` | NUMBER attr condition receives text | Bad request. | P2 |
| `placementAndStatusPreserveAttributeValues` | Service/integration | `EquipmentServiceTest` | Move equipment/status transition | Existing official values unchanged. | P2 |

## 10. Fix Plan

Phase 1: Contract/data safety fixes

- Mark manual attribute write APIs and DTO fields as deprecated in contract notes.
- Decide compatibility behavior for existing manual rows: read-only display, migration into official definitions, or controlled deletion in a future migration.
- Add official `attributes` to `VehicleDetailDto` and ensure backend vehicle detail matches equipment detail semantics.
- Add RBAC/PBAC to vehicle CRUD and official attribute value endpoints.

Phase 2: Backend validation/service fixes

- Enforce required official attributes whenever `equipmentTypeId` changes on equipment or vehicle.
- Reject direct vehicle status mutation or route it through `EquipmentStatusLifecycleService`.
- Restrict incompatible definition updates when values exist.
- Add type-aware validation for maintenance/PPR attribute conditions.

Phase 3: Frontend dynamic form/API fixes

- Remove or disable manual attribute writes from equipment and vehicle forms.
- Ensure frontend uses official `attributes` for vehicle detail and edit hydration.
- Normalize equipment attribute cache keys and option-source behavior.

Phase 4: Vehicle-specific lifecycle alignment

- Treat vehicles consistently as equipment category `VEHICLE` plus `VehicleDetails`.
- Keep common vehicle fields on `VehicleDetails`; use official attributes only for type-specific dynamic fields.
- Add vehicle lifecycle/status history consistency with equipment.

Phase 5: Tests and regression coverage

- Add backend security, contract, service, and migration-mapping tests from the matrix.
- Keep old Flyway migrations unchanged; use new migrations only if data migration is required.
- Add regression tests that rejected loose key/value payloads do not become the main flow.

## 11. Verification Commands For Future Fixes

Backend:

```bash
./mvnw -DskipTests compile
./mvnw test
./mvnw -Dtest=EquipmentAttributeServiceTest test
./mvnw -Dtest=EquipmentServiceTest test
./mvnw -Dtest=VehicleServiceTest test
./mvnw -Dtest=EquipmentAttributeControllerContractTest,VehicleControllerContractTest test
./mvnw -Dtest=EquipmentPbacScopeTest test
git diff --check
rg -n "<<<<<<<|=======|>>>>>>>" src
```

Audit-only verification used for this report:

```bash
git status --short
rg --files src/main/java src/main/resources/db/migration src/test
rg -n "EquipmentAttribute|ManualAttribute|Vehicle|equipmentTypeId|attributes|PreAuthorize|ScopeAccess"
```
