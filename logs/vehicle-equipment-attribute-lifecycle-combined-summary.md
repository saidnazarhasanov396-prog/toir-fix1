# Vehicle, Equipment, and Attribute Lifecycle Combined Summary

Audit date: 2026-05-26
Backend: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend`
Frontend: `/Users/tenzorsoft/Desktop/Work/toir/toir-front`
Scope: audit only; no production code, migrations, or dependencies changed.

## 1. Executive Summary

Overall status: RISKY

- The official Attribute Definition/Value architecture exists and is usable for equipment and vehicle dynamic data.
- The PM decision is not fully respected because both backend and frontend still expose manual arbitrary key/value attributes as a normal create/update/detail flow.
- Backend official attribute definition creation is aligned with the PM rule: it creates definitions, not concrete equipment values.
- Backend official value validation is reasonably strong, but scope/security and type-change cases are not complete.
- Vehicle is modeled compatibly as equipment plus `VehicleDetails`, but vehicle APIs and UI do not yet treat official attributes as a first-class detail/display contract.
- Frontend equipment create/update is close to the desired official dynamic-attribute flow, but still submits manual attributes.
- Frontend fleet/vehicle flows have enum, common-field, and official-attribute display mismatches.
- The next work should start with contract/data safety and security, not UI polish.

PM decision status: VIOLATED.

Main reason: `EquipmentManualAttribute` and `manualAttributes` remain active across backend migrations/entities/DTOs/controllers/services and frontend API/forms/components/tests. This is the rejected loose key/value path and competes with the official Attribute Definition/Value lifecycle.

## 2. Current Architecture Map

Backend:

```text
EquipmentType
  -> EquipmentAttributeDefinition(key, dataType, validation, options, required policy)
Equipment
  -> EquipmentAttributeValue(definitionId, typed value)
  -> EquipmentAttributeValueHistory
Vehicle
  -> Equipment(category = VEHICLE)
  -> VehicleDetails(common vehicle fields)
  -> EquipmentAttributeValue for dynamic fields
ManualAttribute
  -> EquipmentManualAttribute(key/value) [conflicting legacy/rejected path]
```

Frontend:

```text
Equipment type pages
  -> manage EquipmentAttributeDefinition
Equipment registry / dynamic passport
  -> render official definitions
  -> submit official EquipmentAttributeValuePayload[]
  -> also submit manualAttributes[] [conflict]
Fleet registry
  -> render metric filtered official definitions
  -> submit VehicleRequest.attributes[]
  -> also submit manualAttributes[] [conflict]
Equipment/vehicle detail pages
  -> manual attributes visible
  -> official attributes incomplete/inconsistent
```

## 3. Attribute Lifecycle Audit

| Flow | Current behavior | Expected behavior | Status | Evidence | Recommended fix |
|---|---|---|---|---|---|
| Definition create/list/update/delete | Backend and frontend support official definitions by equipment type. | Type-owned definitions with stable keys. | OK/Risk | Backend `EquipmentAttributeService`; frontend `equipment-type-attributes-page.tsx` | Keep; restrict incompatible updates when values exist. |
| Value create/update/read | Backend validates official typed values; frontend can submit typed payloads. | Values only through official typed contract. | OK/Risk | Backend `EquipmentAttributeService.upsertValues`; frontend `equipment-attributes.ts` | Add PBAC and missing frontend display fixes. |
| Manual key/value | Backend and frontend expose arbitrary manual attrs. | Rejected by PM. | Bug | Backend `EquipmentManualAttributeService`; frontend `ManualAttributesEditor` | Deprecate/remove write paths; migrate or read-only preserve existing data. |
| Required validation | Backend enforces official required attrs; frontend equipment forms validate required attrs. | Required constraints enforced consistently. | OK/Risk | Backend `validateRequired`; frontend `findMissingRequiredRegistryAttributes` | Cover vehicle criticality and type-change cases. |
| Deleted/incompatible definitions | Soft delete preserves values; updates can mutate incompatible fields. | Existing data must remain coherent. | Risk | Backend `updateDefinition`, `deleteDefinition` | Add compatibility policy and tests. |

## 4. Equipment Lifecycle With Attributes Audit

| Flow | Current behavior | Expected behavior | Status | Evidence | Recommended fix |
|---|---|---|---|---|---|
| Create | Official attrs validated on create. | Correct official flow. | OK | Backend `EquipmentService.create`; frontend `equipment-registry-page.tsx` | Keep. |
| Update | Official attrs supported, but type-change without attrs bypasses required validation. | Type changes must validate new type attributes. | Bug | Backend `EquipmentService.update` | Require attrs or reject type changes until handled. |
| Detail/passport | Backend returns official attrs; frontend card mishandles root-level detail attrs and shows manual attrs. | Official attrs display reliably. | Bug | Backend `EquipmentDetailDto`; frontend `equipment-card-page.tsx` | Fix consumer after backend contract cleanup. |
| Manual attrs | Active in create/update/detail. | Not a main flow. | Bug | Backend DTOs/controllers; frontend manual components | Remove/deprecate. |
| Lifecycle/status | Equipment status lifecycle is controlled. | Preserve attributes across lifecycle. | OK/Risk | Backend `EquipmentStatusLifecycleService` | Add preservation tests. |

## 5. Vehicle Lifecycle With Attributes Audit

| Flow | Current behavior | Expected behavior | Status | Evidence | Recommended fix |
|---|---|---|---|---|---|
| Model | Vehicle is equipment category plus vehicle details. | Compatible single identity model. | OK | Backend `VehicleService`, `VehicleDetails` | Keep. |
| Create/update attrs | Official attrs can be submitted, but manual attrs also submitted. | Official only for dynamic fields. | Bug | Backend `VehicleRequest`; frontend `fleet-registry-page.tsx` | Remove manual path. |
| Detail attrs | Official attrs are not returned/rendered as first-class vehicle detail fields. | Vehicle detail displays official attrs. | Bug | Backend `VehicleDetailDto`; frontend `vehicle-card-page.tsx` | Add official attrs to contract and UI. |
| Status lifecycle | Vehicle update sets equipment status directly. | Use equipment lifecycle/status history. | Bug | Backend `VehicleService.applyEquipment` | Align with equipment lifecycle service. |
| Common fields | Frontend can omit common vehicle metrics on create in dynamic metric mode. | Common fields stay on vehicle details. | Bug | Frontend `fleet-registry-page.tsx::vehiclePayload` | Always submit common fields separately. |
| Security | Vehicle controller lacks explicit RBAC. | Vehicle CRUD protected like equipment. | Bug | Backend `VehicleController` | Add `@PreAuthorize` and PBAC. |

## 6. API Contract Matrix

| Area | Backend endpoint | Backend DTO | Frontend API method | Frontend consumer | Status | Problem | Recommended action |
|---|---|---|---|---|---|---|---|
| Equipment create/update | `/api/v1/equipment` | `EquipmentCreateRequest`, `EquipmentUpdateRequest` | `createEquipment`, `updateEquipment` | Equipment registry | Bug | Manual attrs plus official attrs; type-change gap. | Keep official attrs; deprecate manual; validate type changes. |
| Equipment detail | `GET /api/v1/equipment/{id}` | `EquipmentDetailDto` | `getEquipmentById` | Equipment card/edit | Bug | Frontend card reads official attrs incorrectly. | Normalize detail consumer. |
| Attribute definitions | `/api/v1/equipment-types/{id}/attributes` | Definition DTO/request | `get/create/update/deleteEquipmentTypeAttribute` | Type pages/forms | OK/Risk | Incompatible updates not guarded. | Add backend policy and UX validation. |
| Attribute values | `/api/v1/equipment/{id}/attributes` | Value DTO/request | `get/update/createEquipmentAttributes` | Passport/fleet edit | Bug/Risk | Backend lacks equipment PBAC. | Add scope checks and frontend 403 handling. |
| Manual attrs | `/manual-attributes` endpoints | Manual attr DTOs | Manual attr API methods | Equipment/fleet/card | Bug | Rejected loose key/value. | Remove/deprecate write flow. |
| Vehicle create/update/detail | `/api/v1/vehicles` | `VehicleRequest`, `VehicleDetailDto` | Vehicle API methods | Fleet/card | Bug | No RBAC, missing official detail attrs, manual attrs, enum mismatch. | Align backend contract and frontend type/UI. |
| Option sources | `/api/v1/equipment-attribute-option-sources` | Option source DTOs | Option source API methods | Attribute pages | Risk | Frontend exposes update/delete source methods not present in backend. | Add/remove to match contract. |

## 7. Business Logic Issues

P0:

- None confirmed.

P1:

- PM rule violation from manual key/value lifecycle in both repos.
- Backend vehicle CRUD lacks explicit RBAC and complete scope checks.
- Backend official attribute value endpoints lack equipment department PBAC.
- Vehicle status update bypasses equipment lifecycle/history.
- Vehicle detail contract omits official attributes while frontend can submit them.
- Equipment/vehicle type changes can bypass required dynamic attributes.
- Frontend vehicle enum values do not match backend.
- Frontend fleet create can omit common vehicle fields in dynamic metric mode.

P2:

- Backend definition updates can invalidate existing values.
- Backend maintenance/PPR condition values are not fully type-validated.
- Frontend equipment card does not reliably display official attributes.
- Frontend option-source select handling is inconsistent.
- Frontend query invalidation and permission gates need tightening.

P3:

- Attribute cache keys should be standardized.
- Manual-attribute tests should be converted to deprecation/read-only compatibility coverage.

## 8. Architecture Issues

- Duplicate dynamic-data models: official attributes and manual key/value attributes.
- Wrong ownership: manual fields are owned by instances, while business rules require type-owned definitions.
- Weak vehicle boundary: vehicle service manages equipment state directly and does not consistently reuse equipment lifecycle/security rules.
- DTO mismatch: frontend/backend create/update supports official vehicle attrs, but detail response/display does not.
- N+1 risk is mostly avoided now by keeping list APIs light; future list-with-attrs must bulk load.
- Missing constraints/policies around definition mutation and type changes.
- Query-cache fragmentation makes frontend dynamic forms harder to reason about.

## 9. Test Gap Matrix

| Missing test | Layer | File suggested | Scenario | Expected assertion | Priority |
|---|---|---|---|---|---|
| Manual attrs rejected/deprecated | Backend + frontend contract | Backend controller tests; frontend form tests | Equipment/vehicle submit manual attrs | Manual write path is blocked or absent. | P1 |
| Attribute values require scope | Backend security | `EquipmentAttributeControllerSecurityTest` | Cross-department attr read/write | 403. | P1 |
| Vehicle CRUD permissions | Backend security | `VehicleControllerSecurityTest` | Unauthorized vehicle CRUD | 403. | P1 |
| Vehicle detail official attrs | Backend/frontend contract | `VehicleServiceTest`; vehicle card test | Vehicle has official attr value | Detail/API/UI contains official attr. | P1 |
| Type change required attrs | Backend service | `EquipmentServiceTest`, `VehicleServiceTest` | Change type without required attrs | Bad request. | P1 |
| Vehicle enum alignment | Frontend unit | Fleet registry test | Render type options | Values match backend enum. | P1 |
| Common vehicle fields preserved | Frontend form | Fleet registry test | Dynamic metrics enabled on create | Common fields remain in request. | P1 |
| Definition incompatible update | Backend service | `EquipmentAttributeServiceTest` | Change dataType with values | Bad request. | P2 |
| Option source select rendering | Frontend component | Equipment attribute field test | SELECT with optionSourceId | Options load/render. | P2 |
| Lifecycle preserves attributes | Backend service/integration | Equipment/Vehicle service tests | Status/placement change | Official values unchanged/history correct. | P2 |

## 10. Fix Plan

Phase 1: Contract/data safety fixes

- Freeze/deprecate manual attribute write paths and document existing data handling.
- Add official `attributes` to vehicle detail contract.
- Add RBAC/PBAC to vehicle CRUD and official attribute value endpoints.
- Align vehicle enum values.

Phase 2: Backend validation/service fixes

- Enforce required attributes on equipment/vehicle type changes.
- Align vehicle status updates with equipment lifecycle service.
- Restrict incompatible definition updates.
- Type-check maintenance/PPR condition values.

Phase 3: Frontend dynamic form/API fixes

- Remove manual attribute submission from equipment/fleet forms.
- Render official attributes on equipment and vehicle detail pages.
- Normalize attribute query keys and invalidations.
- Fix option-source loading and boolean empty display.

Phase 4: Vehicle-specific lifecycle alignment

- Keep common vehicle fields in `VehicleDetails`.
- Use official equipment attribute definitions/values for type-specific dynamic vehicle fields.
- Add vehicle permission gates once backend authorities are enforced.

Phase 5: Tests and regression coverage

- Add backend security/contract/service tests and frontend unit/component tests listed above.
- Include regression coverage that arbitrary `extraData`, loose `key/value`, or `manualAttributes` do not become the main business flow.
- Run full backend and frontend verification after fixes.

## 11. Verification Commands

Backend:

```bash
./mvnw -DskipTests compile
./mvnw test
./mvnw -Dtest=EquipmentAttributeServiceTest,EquipmentServiceTest,VehicleServiceTest test
./mvnw -Dtest=EquipmentAttributeControllerContractTest,VehicleControllerContractTest test
./mvnw -Dtest=EquipmentPbacScopeTest test
git diff --check
rg -n "<<<<<<<|=======|>>>>>>>" src
```

Frontend:

```bash
npm run build
npm test
npm run lint
git diff --check
rg -n "<<<<<<<|=======|>>>>>>>" src
```

Audit-only checks used:

```bash
git status --short
rg --files src
rg -n "EquipmentAttribute|ManualAttribute|manualAttributes|Vehicle|VehicleType|PreAuthorize|ScopeAccess|invalidateQueries"
```
