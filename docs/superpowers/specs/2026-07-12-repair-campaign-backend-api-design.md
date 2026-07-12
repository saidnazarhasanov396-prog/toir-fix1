# Repair Campaign Backend API Design

## Context and scope

The frontend contract is taken from `origin/fix/finance-module-style` in `toir-front` and the supplied `repair-campaign-backend-requirements (3).md`. This change implements the endpoints already called by the frontend and resolves the defect-list contract mismatch. Phase 5 close-readiness expansion remains out of scope because the requirements explicitly mark it as future work.

The backend work starts from local `Codex_org` commit `9509b337`, merged with `origin/main`. Existing uncommitted work in the primary checkout is preserved in place and is not copied into this isolated worktree.

## Considered approaches

1. **Thin integration with existing domain services (selected).** Add nested campaign endpoints, reuse the existing defect response assembler and attachment-group infrastructure, and introduce a focused risk aggregate. This matches the frontend exactly and minimizes duplicated behavior.
2. **Campaign-specific document and defect DTO APIs.** This would isolate the module but duplicate generic attachment and defect serialization logic, creating two contracts for the same data.
3. **Generic configurable register for risks and issues.** This could support future registers, but it adds schema and workflow abstraction not required by the current product.

## API and data design

### Campaign defects

`GET /api/v1/repair-campaigns/{campaignId}/defects` accepts `page`, `size`, optional `status`, and optional `severity`, and returns Spring's `Page<DefectResponse>`, the same shape returned by `GET /api/v1/defects`.

The actual schema stores the relation in the opposite direction from the prose requirement: `work_orders.defect_id -> defects.id`, while `work_orders.repair_campaign_id -> repair_campaigns.id`. The repository query therefore selects distinct defects joined through campaign work orders. The service first verifies the campaign exists and is in the caller's department scope, then reuses the existing bulk response enrichment to avoid N+1 lookups. The standard `DefectResponse` exposes `workOrderId` as the first linked work order so the existing frontend column is populated, while retaining `linkedWorkOrders` for full triad context.

### Campaign risks

`repair_campaign_risks` is a soft-deletable entity with campaign, title, description, likelihood, impact, status, optional owner, mitigation plan, due date, and audit timestamps. Database checks constrain enum values; foreign keys point to campaigns and users.

The nested CRUD API returns `RepairCampaignRiskResponse`. Create always sets `OPEN`. Update is partial: non-null content fields are applied and a supplied status is validated. Legal transitions are `OPEN -> MITIGATING`, `OPEN -> ACCEPTED`, and `MITIGATING -> CLOSED`; repeating the current status is an idempotent no-op. `ACCEPTED` and `CLOSED` are terminal. Delete sets `is_deleted=true` and returns 204.

The service validates campaign scope for every operation, validates an owner against active user records, resolves owner names in bulk for lists, and writes audit records under `REPAIR_CAMPAIGN`.

### Campaign attachments

`REPAIR_CAMPAIGN` is added to `AttachmentTargetType`. Attachment access resolves the campaign and checks department scope. Generic attachment read guards accept `REPAIR_CAMPAIGN_READ`; mutation guards accept `REPAIR_CAMPAIGN_UPDATE`. Files use the generic `DOCUMENT` category. No campaign-specific documents endpoint is added.

### Closing notes

`POST /api/v1/repair-campaigns/{id}/close` accepts an optional request body `{ "notes": string }`. Missing bodies and `{}` remain valid. A non-blank value is trimmed and stored in `repair_campaigns.closing_notes`; blank input becomes null. The value is returned as `closingNotes` in `RepairCampaignDto` and appears in the existing campaign audit snapshot.

### Defect-list consistency

`GET /api/v1/defect-lists` accepts optional `status` and already-supported `equipmentId`; both are applied in the repository query and scope-filtered fallback. Stats remain unchanged.

Campaign work-order creation no longer requires an approved defect list for `MEDIUM_REPAIR` and `CAPITAL_REPAIR`. A provided defect-list ID is still validated for existence, approval, and equipment consistency. Existing approval/start readiness checks remain unchanged, so a list can be attached after draft creation. This removes the frontend creation blocker without weakening the later execution gate.

## Security and errors

Read endpoints require `REPAIR_CAMPAIGN_READ`; risk writes require `REPAIR_CAMPAIGN_UPDATE`; existing close permission remains `REPAIR_CAMPAIGN_CLOSE`. Services enforce department scope in addition to controller authorities. Missing campaign/risk/owner resources return 404, invalid enum/state transitions and malformed inputs return 400, and cross-campaign risk IDs return 404 without exposing unrelated records.

## Testing strategy

All behavior is developed test-first. Controller contract tests cover request binding, response status, and authorities. Service tests cover campaign scope, defect filters, owner resolution, risk transitions, soft delete, and close-note persistence. Repository query contracts are covered by focused tests where practical, while migration SQL is checked by compilation and the project's migration test suite. Final verification runs targeted tests, full Maven tests, and package/compile under Java 21-compatible JDK.
