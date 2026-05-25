# Frontend note: backend main sync after Phase 3F

This main sync introduced frontend-visible API contract changes from `main`. These are not new Equipment Lifecycle backend features, but frontend consumers should account for them.

## Changed endpoint/DTO contracts

- `GET /api/v1/equipment-attribute-option-sources`
  - Response changed from a raw list to a paginated `Page<EquipmentAttributeOptionSourceDto>`.
  - Query params now include `search`, `page`, `size`, and `pageSize`.

- `EquipmentAttributeDefinitionDto.unit`
  - Response field changed from `String` to `UnitOfMeasurementDto`.
  - Existing dynamic passport fields, including `requiredForCriticalityClassIds`, remain present.

- PPR plan APIs
  - `PprPlanRequest` now uses `fromDate` and `toDate` instead of `year` and `month`.
  - `PprPlanDto` now returns `fromDate` and `toDate` instead of `year` and `month`.
  - PPR list/stats endpoints now accept optional `day`.
  - `PprTaskRequest.equipmentId` is now optional.

- `GET /api/v1/advisor/maintenance`
  - Added optional `equipmentId` filter.
  - Existing `urgency` filter remains.

- `GET /api/v1/advisor/maintenance/stats`
  - New stats endpoint with optional `equipmentId` and `urgency` filters.

- `GET /api/v1/oee`
  - No-filter request now returns all OEE records instead of an empty page.

## Equipment Lifecycle contracts

No new Equipment Lifecycle API changes were introduced by this sync beyond the already completed backend phase contracts. The following remain preserved:

- Equipment status lifecycle endpoints and status history.
- Dynamic passport required-by-criticality and value history endpoint.
- Equipment node hierarchy contracts.
- Defect, technical document, and work-order `equipmentNodeId` contracts.
- Equipment node lifecycle endpoint: `GET /api/v1/equipment-nodes/{nodeId}/lifecycle`.
