# Equipment Lifecycle / Digital Equipment Passport Gap Audit - Backend

Branch: `codex/equipment-lifecycle-main-sync-backend`

Scope: audit only. No source code, migrations, API names, or behavior were changed.

Baseline: main sync completed; external Maven verification reported `Tests run: 1533, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.

Verification performed for this audit:
- Read-only inspection with `rg`, `nl`, and `sed`.
- Final source-change verification with `git diff --name-status`.
- No backend test suite was run because this audit created only this Markdown report.

## 1. Reference Data / Directories

### A. Current status
PARTIAL

### B. Backend findings
- Existing files/classes: `entity/Department.java`, `Location.java`, `EquipmentType.java`, `CriticalityClass.java`, `ServiceClass.java`, `UnitOfMeasurement.java`, `Material.java`, `SparePart.java`, `Manufacturer.java`.
- Existing endpoints/services/repositories include `EquipmentTypeController`, `EquipmentTypeService`, `EquipmentTypeRepository`, `SparePartController`, `SparePartService`, `SparePartRepository`, and directory-style services for units/departments/locations.
- Equipment type CRUD exists at `/api/v1/equipment-types` with stats and RBAC annotations.
- Spare part CRUD exists at `/api/v1/spare-parts`; duplicate code validation exists in `SparePartService`.
- Existing migrations include equipment types, unit/material/spare-part related tables, and unique active indexes for equipment code/inventory number in `V20260427_1__fleet_vehicle_foundation.sql`.
- Missing backend parts: consistent directory import/export is not evident for all required dictionaries; delete restriction when used is inconsistent across directories; active/inactive and unique-code behavior is not uniformly proven for all directories.
- Risky inconsistencies: some directories are domain-rich, but not all required reference concepts appear to have the same lifecycle, search, active/inactive, and usage-protection contract.

### C. Frontend findings
- Frontend has pages for equipment types, spare parts, warehouse/reference areas, and dynamic attribute option sources, but directory coverage is uneven.
- Some reference data appears embedded in module pages rather than a consistent directory UX.

### D. Contract findings
- Equipment type and spare-part contracts are visible and generally aligned.
- Directory contracts are not uniformly visible for active/inactive, deletion conflicts, or import.

### E. Tests
- Existing tests found: `EquipmentTypeControllerContractTest`, `EquipmentTypeRepositoryStatsTest`, RBAC/PBAC tests, spare-part and warehouse tests.
- Missing backend tests: directory delete restriction when used, active/inactive filtering, duplicate code for each dictionary.
- Suggested backend tests: `ReferenceDirectoryLifecycleServiceTest`, `EquipmentTypeDeleteRestrictionTest`.
- Suggested methods: `deleteType_whenUsed_returnsConflict`, `searchDirectories_filtersInactive`, `createDirectory_duplicateCode_returnsConflict`.

### F. Recommendation
P1: important for correct lifecycle behavior.

### G. Suggested implementation phase
Phase 1: Foundation / contract hardening

## 2. Equipment Creation

### A. Current status
PARTIAL

### B. Backend findings
- Existing files/classes: `Equipment.java`, `EquipmentCreateRequest`, `EquipmentUpdateRequest`, `EquipmentDto`, `EquipmentDetailDto`, `EquipmentRepository`, `EquipmentService`, `EquipmentController`.
- Endpoints: `/api/v1/equipment` supports list, detail, stats, create, update, placement update, delete, and children.
- Service methods: `EquipmentService.create`, `update`, `search`, `findDetailById`, `updatePlacement`, `delete`.
- Validations found: generated-only equipment code (`validateClientProvidedCode`), inventory-number duplicate check, required department-or-warehouse placement, department/warehouse existence, parent self/cycle checks.
- Repository filtering supports department, type, status, category, search, replacement availability.
- Missing backend parts: explicit draft vs usable-equipment lifecycle is not evident; mass import is not evident; duplicate checks are narrower than full code/name/inventory/model expectations; delete restriction checks only child equipment in the inspected service path.
- Risky inconsistencies: manual status can be supplied at create/update without a dedicated status-change reason/history workflow.

### C. Frontend findings
- Equipment registry supports list filters, type/department/warehouse selection, create/update/delete, placement transfer, dynamic attributes, and equipment detail navigation.
- Search by code/name/inventory appears supported through the generic equipment search parameter.
- Mass import and draft/usable workflow were not found.

### D. Contract findings
- Backend generates code and rejects client-provided code; frontend create payload does not include code, which aligns.
- Frontend uses optional `inventoryNumber`, while backend checks duplicates when provided. Required-field semantics should be contract-tested.
- Frontend can send `status` during create/update; backend accepts it without status-transition semantics.

### E. Tests
- Existing tests found: `EquipmentServiceTest`, `EquipmentControllerContractTest`, `EquipmentRepositoryTest`, `EquipmentRepositoryQueryContractTest`, `EquipmentPbacScopeTest`, `RbacEquipmentSecurityTest`.
- Missing backend tests: draft vs usable rules, status reason/history, import duplicate handling, delete restriction when used by work orders/defects/downtime.
- Suggested backend tests: `EquipmentCreationLifecycleServiceTest`, `EquipmentDeletionRestrictionServiceTest`.
- Suggested methods: `create_generatesUniqueCode`, `create_duplicateInventory_returnsConflict`, `delete_whenReferencedByWorkOrder_returnsConflict`.

### F. Recommendation
P0: must fix before demo/production for deletion/reference restrictions and lifecycle status integrity; P1 for import/draft completeness.

### G. Suggested implementation phase
Phase 1: Foundation / contract hardening

## 3. Classification

### A. Current status
PARTIAL

### B. Backend findings
- Existing concepts: `CriticalityClass`, `ServiceClass`, `responsibleId` on equipment, department and type filtering in `EquipmentService.search`.
- Existing DTO fields include `criticalityClassId`, `responsibleId`, `departmentId`, `equipmentTypeId`, `status`, and category.
- PPR generation filters equipment by type and department and excludes `DECOMMISSIONED`.
- Missing backend parts: criticality matrix, bulk classification update, service-class impact on PPR/regulations, classification notification impact, and explicit repair-priority impact are not evident.
- Risky inconsistencies: criticality appears stored as reference data, but no central classification policy service was found.

### C. Frontend findings
- Equipment registry exposes status/category/department/type filtering and can set criticality/responsible fields in payload paths.
- No obvious criticality matrix or bulk classification UI was found.

### D. Contract findings
- `criticalityClassId` exists in backend/frontend payloads, but service-class contract is not clearly connected to equipment create/update.
- Classification fields do not appear to drive downstream priority or notifications by contract.

### E. Tests
- Existing tests found: equipment service/controller/repository tests and PPR lifecycle tests.
- Missing backend tests: criticality-driven repair priority, service-class-driven PPR/regulation behavior, bulk classification authorization.
- Suggested backend tests: `EquipmentClassificationPolicyServiceTest`, `EquipmentBulkClassificationControllerContractTest`.
- Suggested methods: `criticality_changesRepairPriority`, `serviceClass_filtersRegulations`, `bulkUpdate_appliesScopeRules`.

### F. Recommendation
P1: important for correct lifecycle behavior.

### G. Suggested implementation phase
Phase 1: Foundation / contract hardening

## 4. Equipment Passport / Dynamic Technical Characteristics

### A. Current status
PARTIAL

### B. Backend findings
- Existing files/classes: `EquipmentPassport`, `EquipmentAttributeDefinition`, `EquipmentAttributeValue`, `EquipmentAttributeOptionSource`, `EquipmentAttributeOptionItem`.
- Controllers/endpoints: `/api/v1/equipment/{equipmentId}/passport`, `/equipment-types/{equipmentTypeId}/attributes`, `/equipment/{equipmentId}/attributes`, option-source endpoints.
- Service methods: `EquipmentPassportService.findByEquipment`, `upsert`; `EquipmentAttributeService.findDefinitions`, `createDefinition`, `updateDefinition`, `deleteDefinition`, `findValues`, `upsertValues`.
- Validations found: duplicate attribute key per type, min/max consistency, required attributes, one value field per attribute, data-type-specific value field, numeric range, option validation against inline/options-source lists.
- Migrations: `V20260521_1__equipment_dynamic_passport_attributes.sql`, `V20260521_3__equipment_attribute_option_sources.sql`, and `V20260521_2__maintenance_regulation_attribute_conditions.sql`.
- Tests found: `EquipmentAttributeServiceTest`, `EquipmentAttributeControllerContractTest`, PPR dynamic-condition tests.
- Missing backend parts: required fields by criticality, passport attribute change history, document attachment as first-class passport artifact, versioned passport snapshots, and type-specific template governance beyond definitions.
- Risky inconsistencies: deleting an attribute definition soft-deletes the definition but inspected service did not prove restriction when values/regulations already use it.

### C. Frontend findings
- Existing dynamic passport components use `AttributeDefinition` and `AttributeValue` from `src/types/api.ts`.
- Pages/components: `equipment-type-attributes-page.tsx`, `dynamic-attributes-section.tsx`, `dynamic-passport-form.tsx`, `equipment-card-page.tsx`, `maintenance-regulations-page.tsx`.
- UI supports type-based definitions, values on create/update/detail, option sources, and maintenance applicability conditions.
- Missing frontend parts: criticality-based required fields, visible history of attribute changes, passport document lifecycle and versioning as part of dynamic passport.

### D. Contract findings
- Important mismatch risk: frontend has both `EquipmentAttributeDefinition` and older `AttributeDefinition`; active API methods use `AttributeDefinition`, which includes fields such as `name`, `choices`, `optionSourceId`, and `options`, while backend DTOs are centered on `key`, `label`, `dataType`, `unit`, `required`, `minValue`, `maxValue`, `options`, `groupName`, `sortOrder`, and `optionSourceId`.
- Frontend `updateEquipmentAttributeValues` uses `{ values: [{ attributeId, value }] }`, while backend controller/service expects `List<EquipmentAttributeValueRequest>` under the equipment attributes payload shape used elsewhere. This is a contract mismatch candidate even if unused.
- Nullable/required semantics for `label`, `required`, `options`, and `valueJson` need contract tests.

### E. Tests
- Existing tests found: backend attribute service/controller tests; frontend `equipment-attributes.test.ts`.
- Missing backend tests: criticality-required attributes, definition delete while values exist, option-source update with historical values, change history.
- Suggested backend tests: `EquipmentAttributeLifecycleServiceTest`, `EquipmentPassportHistoryServiceTest`.
- Suggested methods: `upsert_missingCriticalityRequiredAttribute_returnsBadRequest`, `deleteDefinition_whenValuesExist_returnsConflict`, `updateAttribute_recordsHistory`.

### F. Recommendation
P0: must fix before demo/production for frontend/backend contract hardening; P1 for history and criticality rules.

### G. Suggested implementation phase
Phase 2: Dynamic passport completeness

## 5. Equipment Node Hierarchy

### A. Current status
PARTIAL

### B. Backend findings
- Existing files/classes: `EquipmentNode`, `EquipmentNodeType`, `EquipmentNodeDto`, `EquipmentNodeController`, `EquipmentNodeService`, `EquipmentNodeRepository`.
- Endpoints: `GET/POST /api/v1/equipment/{equipmentId}/nodes`, `PUT/DELETE /api/v1/equipment-nodes/{id}`.
- Validations found: duplicate node code within equipment; delete restriction when node has children.
- Audit logging exists for create/update/delete.
- Missing backend parts: defect target by node, node documents, node repair history, serial-number uniqueness/validation, parent-cycle validation for nodes, delete restriction when a node is used by defects/work orders/documents.
- Risky inconsistencies: frontend payload uses `parentNodeId`, while DTO/service use `parentId`, so parent/child tree creation may not agree.

### C. Frontend findings
- `EquipmentNodesSection` provides node list/create/update/delete UI on equipment card.
- Missing frontend parts: tree visualization, node document UI, node defect/repair-history context, node-level quick actions.

### D. Contract findings
- Naming mismatch: frontend API payload uses `parentNodeId`; backend DTO/service uses `parentId`.
- Node type appears typed as enum backend-side, but frontend form uses a free text input/string.

### E. Tests
- Existing tests found: equipment controller/service tests; no targeted node tests were confirmed in the inspected list.
- Missing backend tests: parent-cycle prevention, parent field mapping, node used-by restrictions.
- Suggested backend tests: `EquipmentNodeServiceTest`, `EquipmentNodeControllerContractTest`.
- Suggested methods: `createNode_withParentId_buildsTree`, `deleteNode_withDefectTarget_returnsConflict`, `updateNode_circularParent_returnsBadRequest`.

### F. Recommendation
P1: important for correct lifecycle behavior.

### G. Suggested implementation phase
Phase 3: Node/document lifecycle

## 6. Equipment Documents

### A. Current status
PARTIAL

### B. Backend findings
- Existing files/classes: `TechnicalDocument`, `TechnicalDocumentDto`, `DocumentType`, `TechnicalDocumentController`, `TechnicalDocumentService`, `TechnicalDocumentRepository`, `FileAsset`.
- Endpoints: `GET/POST /api/v1/equipment/{equipmentId}/documents`, `DELETE /api/v1/technical-documents/{id}`.
- Service resolves file asset metadata and download URL via `FileAssetRepository`.
- Missing backend parts: node-level documents, document versioning beyond a `revision` field, preview endpoint contract, per-document access rights, document availability from work order/repair context, and category-specific lifecycle policies.
- Risky inconsistencies: service creates records for equipment ID without inspecting equipment existence in the shown method; upload is separate and needs transactional consistency checks.

### C. Frontend findings
- Equipment card uploads file assets and creates technical documents; it lists equipment-level documents.
- Missing frontend parts: node-level document attachment, version history, preview flow, work-order/repair-context document access.

### D. Contract findings
- Frontend expects paginated technical documents, while backend controller must be checked for pagination support; service method shown returns a list.
- Document type enum needs contract verification against frontend string union.

### E. Tests
- Existing tests found: `TechnicalDocumentServiceTest`.
- Missing backend tests: upload/document transaction, node document support, versioning, access restrictions.
- Suggested backend tests: `TechnicalDocumentControllerContractTest`, `EquipmentDocumentAccessServiceTest`.
- Suggested methods: `createDocument_missingFile_returnsBadRequest`, `listDocuments_includesDownloadUrl`, `createNodeDocument_whenNodeMissing_returnsNotFound`.

### F. Recommendation
P1: important for correct lifecycle behavior.

### G. Suggested implementation phase
Phase 3: Node/document lifecycle

## 7. QR / Marking / Scan Flow

### A. Current status
PARTIAL

### B. Backend findings
- Existing controllers/tests: `EquipmentLabelController`, `EquipmentScanCompatibilityController`, `EquipmentLabelControllerContractTest`, `EquipmentScanControllerContractTest`.
- Endpoints include `/api/v1/equipment/{id}/label`, `/api/v1/equipment/{id}/label.svg`, `/api/v1/equipment/by-code/{code}`, `/api/v1/equipment/resolve-scan`, and compatibility `/api/v1/scan/equipment/{id}`.
- Missing backend parts: durable scan URL history policy after decommission, explicit printable label lifecycle/versioning, unauthenticated vs authenticated scan contract, and quick-action policy enforcement from scan context.

### C. Frontend findings
- Pages: `equipment-label-page.tsx` and `equipment-scan-landing-page.tsx`.
- Scan landing links to master console, repair request creation, defect creation, passport, RCA/downtime, and label.
- Missing frontend parts: enter meter reading quick action from scan, explicit decommission-history notice, offline/mobile-specific flow.

### D. Contract findings
- Frontend uses route `/scan/equipment/:equipmentId` and backend has scan compatibility endpoint, but long-term stable URL semantics require confirmation.
- Quick actions depend on downstream pages accepting `equipmentId` and `mode=create` query parameters.

### E. Tests
- Existing tests found: label and scan controller contract tests.
- Missing backend tests: scan after decommission, stable URL redirects, permission boundaries for scan quick actions.
- Suggested backend tests: `EquipmentScanLifecycleContractTest`.
- Suggested methods: `scan_decommissionedEquipment_stillResolves`, `resolveScan_byCode_returnsStableLandingPayload`.

### F. Recommendation
P1: important for correct lifecycle behavior.

### G. Suggested implementation phase
Phase 4: QR/meter/condition monitoring

## 8. Meters / Usage-Based Maintenance

### A. Current status
PARTIAL

### B. Backend findings
- Existing files/classes: `EquipmentMeter`, `MeterReading`, `MeterType`, `MeterSource`, `MeterService`, `MeterController`, meter repositories.
- Endpoints: `/api/v1/meters` CRUD/stats/triggers, `/api/v1/meters/readings`, `/api/v1/meters/{id}/readings`, delete reading.
- Validations found: inactive meter conflict, monotonic reading validation with rollover support, reading source/device/note capture, reading history with safe limit.
- Tests found: `MeterControllerContractTest`, `MeterServiceStatsTest`.
- Missing backend parts: correction/adjustment flow separate from delete, SCADA/IoT/import ingestion pipeline, usage-based PPR trigger completeness beyond exposed trigger endpoint, full audit/history semantics for corrections.
- Risky inconsistencies: deleting a reading does not visibly recalculate current value in the inspected service snippet.

### C. Frontend findings
- `meters-page.tsx` supports meter list, filters by equipment/meter type, stats, create/edit, reading history, and manual reading add.
- Missing frontend parts: correction workflow, SCADA/IoT/import source management, usage-based PPR trigger configuration visibility, scan quick action to enter meter reading.

### D. Contract findings
- Meter enums align at a basic level (`MeterType`, `MeterSource`).
- Frontend sends `source` as string in add-reading payload; type should be narrowed to backend enum in the API client.

### E. Tests
- Existing tests found: meter controller/service tests.
- Missing backend tests: current value recalculation after delete/correction, usage-trigger generation, source validation.
- Suggested backend tests: `MeterReadingCorrectionServiceTest`, `UsageBasedPprTriggerServiceTest`.
- Suggested methods: `addReading_lowerValueWithoutRollover_returnsConflict`, `deleteLatestReading_recalculatesCurrentValue`, `usageTrigger_createsPprCandidate`.

### F. Recommendation
P1: important for correct lifecycle behavior.

### G. Suggested implementation phase
Phase 4: QR/meter/condition monitoring

## 9. Condition Monitoring

### A. Current status
PARTIAL

### B. Backend findings
- Existing files/classes: `ConditionReading`, `ConditionParameter`, `ConditionReadingService`, `ConditionReadingController`, `ConditionReadingRepository`.
- Endpoints: `GET/POST /api/v1/equipment/{equipmentId}/condition-readings`, `GET /api/v1/condition-readings/alarms`, `DELETE /api/v1/condition-readings/{id}`.
- Validations/logic: unit normalization, warning/alarm severity computation, webhook publishing, auto-create defect on ALARM.
- Tests found: `ConditionReadingControllerContractTest`, `ConditionReadingServiceTest`.
- Missing backend parts: persisted threshold definitions separate from each reading, alarm acknowledgment/assignment workflow, inspection/repair-request creation from alarm, SCADA/IoT import pipeline, trend aggregation endpoints.
- Risky inconsistencies: auto-created defect has generated ad hoc code and category/severity strings, which may bypass dictionary/validation policies.

### C. Frontend findings
- Equipment card reads condition readings; `ConditionMonitoringCard` provides trend UI; `alarm-center-page.tsx` lists alarm center records.
- Missing frontend parts: alarm workflow actions, threshold management UI, SCADA/IoT import status, inspection/repair-request from alarm.

### D. Contract findings
- Condition severity is string-based (`OK/WARN/ALARM`) and should be locked by DTO/enum contract.
- Alarm-center pagination contract needs verification because frontend expects paginated responses.

### E. Tests
- Existing tests found: condition controller/service tests.
- Missing backend tests: alarm-to-defect dictionary compliance, alert deduplication, alarm workflow actions.
- Suggested backend tests: `ConditionAlarmWorkflowServiceTest`.
- Suggested methods: `recordAlarm_autoCreatesDefectOnce`, `recordWarn_publishesWebhookNoDefect`, `alarmCenter_filtersBySeverity`.

### F. Recommendation
P1: important for correct lifecycle behavior.

### G. Suggested implementation phase
Phase 4: QR/meter/condition monitoring

## 10. Equipment Spare Parts Binding

### A. Current status
PARTIAL

### B. Backend findings
- Existing files/classes: `EquipmentSparePart`, `EquipmentSparePartDto`, `EquipmentSparePartRequest`, `EquipmentSparePartController`, `EquipmentSparePartService`, `EquipmentSparePartRepository`, `SparePart`, `Material`, warehouse/material-usage entities.
- Endpoints: list/add/update/remove equipment spare-part links; reverse lookup by spare part.
- Service fields include `position`, `quantityPerUnit`, `consumptionRatePerYear`, `criticality`, `notes`.
- Existing integrations: work-order material usage, warehouse stock/reorder services, spare-part services.
- Missing backend parts: alternatives/analogs, critical spare flag as explicit boolean, minimum-stock inheritance into equipment binding, work-order material suggestions from applicable spare parts, reorder suggestions integrated by equipment applicability, strict traceability from equipment binding to material usage.
- Risky inconsistencies: duplicate equipment-spare-part link prevention was not found in inspected service.

### C. Frontend findings
- Equipment card reads equipment spare parts; `spare-parts-page.tsx`, warehouse pages, and work-order material lifecycle pages exist.
- Missing frontend parts: equipment-level binding management on card, alternatives/analogs, suggestions in work-order material flow from equipment applicability.

### D. Contract findings
- Backend request has `quantityPerUnit` and `criticality`; frontend update payload mirrors these but needs create/list contract verification.
- Binding does not appear tied to material usage traceability fields.

### E. Tests
- Existing tests found: warehouse/material lifecycle tests and warehouse equipment tests.
- Missing backend tests: duplicate binding, suggestion generation, traceability from usage back to equipment spare part.
- Suggested backend tests: `EquipmentSparePartBindingServiceTest`, `WorkOrderMaterialSuggestionServiceTest`.
- Suggested methods: `addDuplicateBinding_returnsConflict`, `suggestMaterials_usesEquipmentApplicableSpareParts`, `materialUsage_keepsEquipmentSparePartTrace`.

### F. Recommendation
P1 for suggestions/traceability; P2 for alternatives/analogs.

### G. Suggested implementation phase
Phase 5: spare parts/material integration

## 11. Equipment History / Lifecycle Timeline

### A. Current status
PARTIAL

### B. Backend findings
- Existing detail aggregation in `EquipmentService.findDetailById` loads repair requests, defects, work orders, downtime events, children, and attributes.
- Existing audit infrastructure: `AuditLog`, `AuditBuilderService`, audit logs in equipment/node/meter/condition/document/spare-part services.
- Existing analytics/RCA classes and repositories for downtime, costs, defects, work orders, material usage.
- Missing backend parts: single lifecycle timeline API with typed event categories, separation of technical/financial/audit history in one contract, timeline filters/export, repeated defects visibility as a first-class history feature.
- Risky inconsistencies: history is assembled from multiple module APIs, so sorting, filtering, pagination, and event causality may differ by frontend page.

### C. Frontend findings
- Equipment card shows repair request, defect, work order, and downtime history cards.
- RCA drilldown shows defects, downtime, failure causes, trends, and risk signals including repeated defects.
- Missing frontend parts: unified timeline with filters/export and explicit technical/financial/audit separation.

### D. Contract findings
- Equipment detail response and analytics/RCA response overlap but are not a unified timeline contract.
- Pagination/filtering is inconsistent: detail history arrays are embedded, while drilldown/list pages paginate separately.

### E. Tests
- Existing tests found: equipment detail/controller tests, RCA/analytics controller tests, work-order/defect/material lifecycle tests.
- Missing backend tests: unified event ordering, repeated defect calculation, export filters.
- Suggested backend tests: `EquipmentLifecycleTimelineServiceTest`.
- Suggested methods: `timeline_ordersMixedEventsByOccurredAt`, `timeline_filtersTechnicalFinancialAudit`, `timeline_marksRepeatedDefects`.

### F. Recommendation
P1: important for correct lifecycle behavior.

### G. Suggested implementation phase
Phase 6: history/status lifecycle

## 12. Equipment Status Lifecycle

### A. Current status
PARTIAL

### B. Backend findings
- Existing enum: `EquipmentStatus` includes `ACTIVE`, `STANDBY`, `IN_REPAIR`, `CONSERVATION`, `OUT_OF_SERVICE`, `DECOMMISSIONED`.
- Equipment create/update accepts status; list filters by status; PPR generator excludes `DECOMMISSIONED`.
- Placement rules restrict installing warehouse equipment with warehouse status `OUT_OF_SERVICE`.
- Missing backend parts: dedicated manual status-change endpoint, reason for status change, status history table/API, decommission restrictions across create work order/defect/PPR/meter/document actions, automatic status changes from work orders, manual-vs-automatic precedence protection.
- Risky inconsistencies: status can be overwritten through generic equipment update without reason or source; no inspected code proves automatic work-order status updates equipment status safely.

### C. Frontend findings
- Equipment registry/card display and edit status; filters by status exist.
- Missing frontend parts: status-change dialog with reason/source, status history UI, decommission restriction messaging, manual-vs-auto conflict handling.

### D. Contract findings
- Enum values match target list.
- Contract lacks a status-transition object with `fromStatus`, `toStatus`, `reason`, `source`, `changedBy`, and `changedAt`.

### E. Tests
- Existing tests found: equipment service/controller tests and work-order lifecycle tests.
- Missing backend tests: status transition history, decommission restrictions, manual status not overwritten by automatic changes.
- Suggested backend tests: `EquipmentStatusLifecycleServiceTest`, `EquipmentStatusWorkOrderIntegrationTest`.
- Suggested methods: `manualStatusChange_requiresReasonAndWritesHistory`, `completeWorkOrder_doesNotOverwriteManualOutOfService`, `decommissionedEquipment_rejectsNewWorkOrder`.

### F. Recommendation
P0: must fix before demo/production.

### G. Suggested implementation phase
Phase 6: history/status lifecycle

## Backend Summary

Overall backend status counts across the 12 microprocesses:
- DONE: 0
- PARTIAL: 12
- MISSING: 0
- UNKNOWN / NEEDS MANUAL CONFIRMATION: 0

Backend is beyond simple CRUD because it has dynamic attributes, meters, condition readings, QR/label, node records, spare-part binding, warehouse/PPR/work-order/defect integrations, and audit logging. It is not yet a full Digital Equipment Passport because core lifecycle guarantees are incomplete: contract hardening, status history, decommission restrictions, node/document lifecycle, passport history, and traceable spare-part/material integration.
