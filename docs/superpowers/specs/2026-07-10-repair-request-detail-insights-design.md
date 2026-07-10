# Repair Request Detail Insights API Design

## Goal

Implement the backend contracts already consumed by the repair-request detail page:

- `GET /api/v1/repair-requests/{id}/costs-summary`
- `GET /api/v1/repair-requests/{id}/close-readiness`
- `GET /api/v1/repair-requests/{id}/timeline`

The responses must match the TypeScript contracts in the `toir-front/Codex_org` branch. The implementation must avoid client-side N+1 requests, preserve existing repair-request scope checks, and require no frontend changes.

## Architecture

Add a focused `RepairRequestInsightsService` for the three read-only projections. Keep repair-request mutations and the existing detail projection in `RepairRequestService`.

`RepairRequestController` will expose the three routes. Each route will:

1. require `REPAIR_REQUEST_READ` (or the existing administrator/wildcard authorities),
2. load the repair request through the existing non-deleted lookup,
3. run the existing request-level scope assertion, and
4. delegate to `RepairRequestInsightsService`.

Response records and the timeline event enum will live under `com.toir.dto.repairrequest`. Existing `CloseReadinessSeverity` and `CloseReadinessGroupStatus` enums will be reused.

No database migration is required.

## Costs Summary

The service will load linked work orders once, then load actual costs for both scopes:

- costs whose `work_order_id` belongs to a linked work order,
- costs whose `repair_request_id` points directly to the request, and
- costs whose `source_type`/`source_id` directly identify the repair request.

The repository query will return each cost once even if more than one relationship matches. Deleted costs and costs linked through deleted work orders are excluded.

Kinds are derived primarily from `ActualCostSourceType`:

- `LABOR_ENTRY` -> `LABOR`
- `MATERIAL_ISSUE` -> `MATERIAL`
- `CONTRACTOR_WORK` or `COUNTERAGENT_WORK` -> `CONTRACTOR`

For manually entered work-order or repair-request costs, the cost-category code supplies the fallback classification (`LABOR`, `MATERIALS`, and contractor codes such as `CONTRACTOR` or `CTR`). Costs that cannot be mapped to the three-contract enum are excluded rather than mislabeled.

Source labels will be enriched in batches from labor entries/users, material usages/spare parts, contractor work, work orders, and cost categories. Notes or the category/work-order label provide a stable fallback. This prevents per-row lookups.

Rows include `PENDING`, `APPROVED`, and `REJECTED` items so the UI can show review state. Totals include approved and pending amounts and exclude rejected amounts. Rows are sorted newest first by `costDate`, then by ID for deterministic output. Currency is `UZS`.

## Close Readiness

The response contains five groups initialized to `READY`: `workOrders`, `defects`, `warranty`, `meterReadings`, and `sla`.

Blocking checks are:

- request status must be `COMPLETED`,
- at least one linked work order must exist,
- every linked work order must be terminal (`COMPLETED`, `CLOSED`, or `CANCELLED`), and
- every linked defect must be terminal (`RESOLVED`, `CLOSED`, or `CANCELLED`).

The request-status blocker is associated with `workOrders` and targets the overview tab because the frontend contract has no separate status group.

Warnings are:

- warranty was active at creation but no warranty handling decision is recorded,
- target completion time has passed without actual completion,
- an active equipment meter has no reading recorded against this repair request.

Warnings never make `ready` false. A blocking item marks its group `BLOCKED`; a warning marks a still-ready group `WARNING`.

`isOverdue` compares `targetCompletionAt` with the current instant only when `actualCompletionAt` is absent. `reactionOverdue` is true only when an active repair-request SLA rule can validly represent initial reaction time and the recorded reaction missed that threshold. The current SLA model has overdue/emergency triggers but no initial-reaction trigger, so the implementation will not fabricate a threshold and will return false until such a rule exists.

## Timeline

The timeline is a curated merge of:

- audit rows for `entity_type = repair_request` and the requested ID,
- the repair request's creation timestamp,
- linked defect creation timestamps,
- linked work-order creation timestamps, and
- meter readings recorded against the request.

Audit rows will be processed oldest first. Event types are inferred from the audit action, message, and current snapshot. Status changes use the chronological previous known status plus the status found in each current snapshot. Specialized events (`ASSIGNED`, `CLARIFICATION_REQUESTED`, `WARRANTY_DECISION`, `REJECTED`, and `CLOSED`) take precedence over generic `STATUS_CHANGE` events.

Linked events include `targetType` and `targetId`. Meter-reading messages include the meter label/value when available. Actor IDs come from audit rows or source records, and actor names are resolved with one batched user query.

Synthetic IDs are deterministic when no persisted audit event ID exists. Semantically duplicate creation or status events are removed. Final output is sorted oldest first by `occurredAt` and ID.

## Error Handling and Security

- Unknown or deleted repair requests return the existing 404 response.
- Request scope violations return the existing access-denied response.
- The endpoints are read-only and do not modify audit or domain state.
- Empty related data returns valid zero/empty responses, not 404.
- Null optional fields remain null and collections are never null.

## Testing

Service tests will cover:

- batch cost aggregation, direct and linked scopes, de-duplication, classification, rejected-total exclusion, labels, and deterministic ordering,
- every readiness blocker and warning, group precedence, overdue calculation, and ready-state calculation,
- audit-to-event classification, status transition derivation, actor-name enrichment, synthetic linked/meter events, de-duplication, and ordering.

Controller contract/security tests will cover:

- exact JSON field names and enum serialization for all three routes,
- unauthenticated and unrelated-authority rejection,
- successful access with `REPAIR_REQUEST_READ`, and
- delegation only after the existing request scope assertion succeeds.

The targeted service and controller tests will run first, followed by the relevant Maven test suite and a clean repository status check before pushing `Codex_org`.
