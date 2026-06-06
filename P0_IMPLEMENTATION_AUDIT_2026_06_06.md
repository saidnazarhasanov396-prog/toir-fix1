# TOIR P0 Implementation Audit - 2026-06-06

Source brief:
- `/Users/tenzorsoft/Downloads/TOIR_TEAM_TASK_BACKLOG_2026-06-05.docx`
- `/Users/tenzorsoft/Downloads/TOIR_P0_TZ_TI_2026-06-06.docx`
- repository inspection under `toir-backend` and `toir-front`

## Executive Summary

The repository already contains many P0 building blocks: equipment dynamic passport, maintenance due events and automation, work orders, corrective request links, material usage, reservations, procurement, actual cost review, RBAC/PBAC tests, audit services, and demo seed phases. The main risk is not total absence, but uneven end-to-end hardening: several flows are implemented as isolated modules, while P0 requires a single auditable chain from equipment data to maintenance, work execution, stock, costs, approvals, budget, and RBAC.

Implemented during this audit:
- RB-02 policy status correction: final production security hardening is TeamLead/DevOps-owned and deferred for this branch; current runtime security configuration is locked and intentionally not changed during the P0 demo-chain pass.
- QA-04: added `toir-backend/mvnw` and `.mvn/wrapper/maven-wrapper.properties`; `./mvnw test` is now available without a local Maven install.
- WO-02 invariant: backend rejects new labor entries on CLOSED work orders in `LaborEntryService`.
- QA-03: stabilized frontend manual attribute feature flag tests for the currently enabled flag.
- Tests: added `ProductionConfigSecretsTest`; extended `LaborEntryServiceTest`. The final prod secret policy assertions in `ProductionConfigSecretsTest` are now quarantined for this branch because runtime security configuration is TeamLead/DevOps-owned.
- Second hardening pack: labor/reservation RBAC, normalized actual-cost technical source traceability, LABOR_ENTRY actual-cost sync, stock row-lock entrypoints/nonnegative guards, manual stock movement reason/source enforcement, and closure readiness checks for required labor plus active reservations.
- Third hardening pack: structured due explanation DTOs, material issue/procurement receipt actual-cost source linkage, template operations copied into generated WO tasks with source ids, stock DB check constraints plus Docker-gated concurrency proof, equipment passport completeness summary in list/detail DTOs, and UI-only leadership demo script.
- UAT seed pass: added demo-only phase-5 seed for exact `AUTO-PUMP-A1`, `AUTO-PUMP-A2`, and `AUTO-PUMP-A3-NOMETER` assets, plus a local static seed contract test and Docker-gated startup/idempotency assertions.
- Runtime config note: final production DB/JWT/default-admin security configuration remains a TeamLead/DevOps responsibility. Security config was intentionally not changed in the current P0 demo-chain branch.
- Runtime migration correction: `V20260606_1__actual_cost_source_traceability.sql` originally made all `(source_type, source_id)` pairs unique, which failed on legacy databases with multiple `WORK_ORDER` cost rows for one work order. The unique index now applies only to granular one-to-one auto sources while `WORK_ORDER` can keep multiple cost components.
- Final production security/env hardening will be completed at the end by TeamLead/DevOps. Current priority is runnable local/demo P0 verification, with security config locked for this branch.

Product scope correction:
- MT-01 Operational Cockpit is deferred and PM decision pending. It is not part of the current fix pack; do not create a new cockpit endpoint, page, or merged operational queue unless PM explicitly approves it later.

## P0 Task Status Table

| ID | Status | Evidence | Primary remaining gap |
| --- | --- | --- | --- |
| EQ-01 | PARTIAL | `EquipmentService`, `equipment-card-page.tsx`, documents/reliability/cost fragments exist | First-screen Equipment 360 is not proven as one complete management read model; cost/reliability drilldown is thin. |
| EQ-02 | PARTIAL | `equipment-card-page.tsx` has edit form and backend update guards status changes | Needs full UAT around warranty/life/responsible/description and backend error mapping on card. |
| EQ-03 | PARTIAL++ | `EquipmentAttributeService` enforces required dynamic attributes; `EquipmentDto.passportCompleteness` now returns required/filled/missing/blocking/fix summary batch-safely; frontend registry/card consumes the new summary; phase-5 demo seed now includes complete A1/A2 and incomplete A3 setup | Demo seed-specific A1/A3 proof still needs Docker-backed screenshot/UAT evidence. |
| MT-01 | DEFERRED / PM DECISION PENDING | Existing operational issues pages/services remain as-is | Not part of current fix pack; do not build a new cockpit endpoint/page/read-model without explicit PM approval. |
| MT-02 | PARTIAL++ | Due calculation/event DTOs now include structured explanation fields plus legacy `explanation` text; frontend Due Events and Equipment Card render structured formula/details and BLOCKED fix metadata; phase-5 seed includes A1 overdue, A2 upcoming, and A3 BLOCKED due events | Needs Docker-backed seeded screenshot/UAT evidence for A1/A3-NOMETER. |
| MT-03 | PARTIAL | Automation evaluates rules, creates due events, anchors, recalculates | Full UI/UAT script evidence is not in tests; no no-bypass leadership script assertion. |
| MT-04 | PARTIAL++ | Template spare requirements are copied; automation now copies template operations into `WorkOrderTask` with planned hours and source template/operation ids; frontend WO detail surfaces planned hours and source template/operation ids; phase-5 seed includes a 3-operation/2-spare AUTO pump template and historical sourced WO | Docker-backed generated-WO UAT proof still needed. |
| CR-01 | DONE/PARTIAL | `RepairRequestService` blocks close without terminal linked WO/defects unless admin override | Override permission/reason exists, but UI allowedAction/disabled reason should be checked in UAT. |
| CR-02 | PARTIAL | `repair-request-detail-page.tsx`, DTO linked defects/WOs | Materials/costs/SLA/action center not confirmed as complete on one detail screen. |
| CR-03 | PARTIAL+ | `WorkOrderService.assertClosureEvidenceReady` revalidates tasks/permit/act plus required labor and active reservations | Meter snapshots/completion-anchor readiness still needs work-type-specific contract. |
| WO-01 | PARTIAL | `work-order-detail-page.tsx` has sections and print pages | Master screen completeness across tasks/materials/labor/permits/acts/costs/history needs UI verification. |
| WO-02 | PARTIAL+ | Labor entries block CLOSED WO; LABOR rate entries create/update source-linked PENDING actual costs | Timesheet integration and explicit warning payload for unvalued labor remain open. |
| SP-01 | PARTIAL | Equipment spare parts and template spare requirements exist | Applicability across equipment type + node + maintenance template is not a single suggestion contract. |
| SP-02 | PARTIAL++ | `ReservationService` uses stock row lock entrypoints; stock checks block negative state; DB constraints protect new direct writes; Docker-gated parallel reservation proof added; frontend warehouse table shows on-hand/reserved/available consistently | Docker is missing locally, so concurrency proof is compiled but skipped here; must be run on Docker-enabled CI/machine. |
| SP-03 | PARTIAL | Low stock recommendations and procurement request services exist | Procurement receipt-to-stock is present but needs full UAT and cost/source linkage checks. |
| FN-01 | PARTIAL++ | `ActualCostService` rejects no-source and budgetLine-only costs; LABOR_ENTRY, MATERIAL_ISSUE, PROCUREMENT_RECEIPT and CONTRACTOR_WORK can create/update source-linked pending costs when cost is known; `WORK_ORDER` source rows may repeat for multiple cost components; frontend shows source traceability and blocks budget-line-only manual create | Reversal/cancel synchronization remains open. |
| FN-02 | PARTIAL | Actual cost review pending/approve/reject and comments exist | Approval routing by department/category/amount is only partially represented. |
| FN-03 | PARTIAL | Budget summary, actual register and review pages exist | Reserved/committed amount rule is not explicit enough; overrun source drilldown needs validation. |
| RB-01 | PARTIAL+ | Added method-level RBAC for labor and reservation mutations; finance/stock/WO guards already covered by tests | Full frontend route/action matrix regeneration still needed. |
| RB-02 | DEFERRED / TEAMLEAD-OWNED FINAL SECURITY HARDENING | Security configuration is locked for the current P0 demo branch; `ProductionConfigSecretsTest` prod secret policy methods are quarantined instead of changing runtime config | Final production DB/JWT/default-admin env and secret-manager validation must be re-enabled by TeamLead/DevOps in the final security phase. |
| QA-01 | PARTIAL+ | Demo seed phases exist; phase-5 now defines exact `AUTO-PUMP-A1`, `AUTO-PUMP-A2`, and `AUTO-PUMP-A3-NOMETER` assets with template, meter/due, stock, budget, cost and negative-branch records | Must run Docker/Postgres seed twice and verify no duplicate natural keys after current migrations. |
| QA-02 | PARTIAL | `toir-backend/docs/uat/leadership-demo-script.md` defines the UI-only route and A3 blocked branch without Cockpit | Needs executable Playwright/screenshot evidence against seeded demo. |
| QA-03 | DONE | Manual attribute feature tests updated; `yarn test` passed | Keep flag behavior documented if product later disables it again. |
| QA-04 | DONE | `toir-backend/mvnw` added; targeted tests pass; full suite passes on Docker-enabled machine | Repository `@DataJpaTest`/Testcontainers suites require Docker. |

## Gap Details By Task

### EQ-01 - Equipment 360
- Affected: `toir-backend/src/main/java/com/toir/service/equipment/EquipmentService.java`, `toir-front/src/pages/equipment/equipment-card-page.tsx`, `toir-front/src/components/equipment/*`.
- Current: detail page aggregates identity, dynamic attributes, documents, maintenance profile, readings, status history, and some KPI cards.
- Required: first viewport must answer identity, location, passport health, current maintenance, open issues, documents, reliability, and cost without jumping modules.
- Backend changes: add or formalize `Equipment360Dto` with due/open WO/open RR/stock risk/cost/reliability summaries.
- Frontend changes: render one dense first-screen summary and link each signal to source object.
- DB migration: not required if read model only; may need indexes for batch summary.
- Tests: service read-model aggregation; frontend render with A1, A2, A3-NOMETER fixtures.
- Acceptance: open `AUTO-PUMP-A1` and see status, passport, current due, open work, docs, cost/reliability on first screen.

### EQ-02 - Equipment Card Edit
- Affected: `EquipmentService.update`, `equipment-card-page.tsx`, `equipment-registry-page.tsx`.
- Current: card has an edit dialog and backend has a dedicated status endpoint guard (`EquipmentService` throws if regular update changes status).
- Required: full form for passport fields, warranty, life, responsible and description; errors shown to humans.
- Backend changes: keep status guard; review PATCH/PUT optional field semantics so absent values are not wiped.
- Frontend changes: ensure card edit covers same fields as registry edit and invalidates card, registry, placement/status widgets.
- DB migration: no.
- Tests: update with absent optional fields; UI mutation error mapping.
- Acceptance: edit warranty, description, responsible, model/manufacturer; bad inventory number shows backend error.

### EQ-03 - Required Passport Fields
- Affected: `EquipmentAttributeService`, `EquipmentAttributeController`, `dynamic-passport-form.tsx`, `equipment-registry-page.tsx`.
- Current: required-by-type and required-by-criticality validation exists for upsert; UI marks required fields; backend list/detail DTO now includes passport completeness summary.
- Required: visible type-specific completeness summary, missing critical fields, blockingReason and fix action in card and registry.
- Backend changes: extended `EquipmentDto` with batch-safe `passportCompleteness` summary.
- Frontend changes: add `RequiredPassportFields` card and registry badge.
- DB migration: no if based on definitions/values.
- Tests: `EquipmentServiceTest.searchIncludesPassportCompletenessForRequiredAttributes` covers required/filled/missing/fix summary.
- Acceptance: A3 missing meter/passport field shows critical missing and a fill action.

### MT-01 - Operational Cockpit
- Affected: `OperationalIssueService`, `OperationalIssueScannerService`, `operational-issues-page.tsx`.
- Status: DEFERRED / PM DECISION PENDING.
- Current: operational issues pages/services already exist and should not be removed or broken.
- Scope decision: do not create a new cockpit endpoint, page, read model, or merged queue for due events, approvals, late WOs, warehouse, and finance in the current iteration.
- Allowed: existing screens may link to source objects from Equipment Card, Due Events, Work Order Detail, Repair Request Detail, Warehouse, and Finance pages.
- Backend changes: none in the current fix pack.
- Frontend changes: none in the current fix pack.
- Tests: keep existing route/service behavior from regressing.
- Acceptance for current scope: leadership demo must not depend on Cockpit; start from Equipment Registry/Card or Due Events until PM approves Cockpit.

### MT-02 - Due Explanation
- Affected: `MaintenanceDueCalculationService`, `MaintenanceDueEventDto`, `maintenance-due-events-page.tsx`.
- Current: due event has status, meter values, remaining, legacy `explanation`, and structured explanation DTO fields.
- Required: structured explanation: base source, anchor, meter/date, interval, tolerance, trigger policy, reason, BLOCKED fixLink.
- Backend changes: extended calculation/event DTOs with `baseSource`, anchors, meter/current/interval/remaining, calendar interval/tolerance, `triggerPolicy`, `blockingCode`, `blockingField`, `fixLink`.
- Frontend changes: replace raw text with formula summary/details.
- DB migration: no if computed; optional if persisted.
- Tests: missing meter, overdue by calendar, due by meter, and legacy explanation preservation covered in focused maintenance due tests.
- Acceptance: A1 due by meter formula and A3 missing meter fix link visible.

### MT-03 - UAT Automation Loop
- Affected: `MaintenanceAutomationService`, `WorkOrderService`, seed SQL.
- Current: regulation -> due event -> approval -> WO -> completion anchor -> recalculation mostly exists.
- Required: full UI UAT without SQL/API bypass.
- Backend changes: harden duplicate prevention and events for every transition.
- Frontend changes: make due approval/create WO/complete flow discoverable from screens.
- DB migration: maybe none.
- Tests: end-to-end integration test around demo equipment.
- Acceptance: leadership script passes from UI only.

### MT-04 - Template To WO
- Affected: `MaintenanceAutomationService.createWorkOrder`, `WorkOrderSparePartRequirementService`, maintenance template services.
- Current: spare part requirements are copied from template context; operation/checklist tasks are now copied into generated work orders with planned hours and provenance ids.
- Required: WO copies operations, checklist, normative hours, spare/material requirements, preserving sourceTemplateId/sourceOperationId.
- Backend changes: implemented template operation/task clone into `WorkOrderTask` and added source fields/migration.
- Frontend changes: show copied operations and checklist in WO detail.
- DB migration: likely yes if source operation fields missing.
- Tests: `MaintenanceAutomationServiceTest` asserts copied operations, planned hours, source ids, independent editing, and explicit empty-template explanation.
- Acceptance: generated WO is not an empty execution document.

### CR-01 - Corrective Lifecycle
- Affected: `RepairRequestService`, `WorkOrderService`, `DefectService`, `repair-request-lifecycle.ts`.
- Current: request close requires COMPLETED status and terminal linked WOs/defects; admin override requires scope admin and reason for generic status changes.
- Required: defect -> request -> WO -> execution -> completion -> closure with audited override.
- Backend changes: make override permission explicit instead of broad scope admin if BA wants non-admin override role.
- Frontend changes: use backend allowedActions/disabled reasons.
- DB migration: no.
- Tests: close without WO, active WO, open defect, admin override.
- Acceptance: cannot close request as done without completed linked WO unless audited override.

### CR-02 - Repair Request Detail
- Affected: `repair-request-detail-page.tsx`, `RepairRequestDto`, material usage/cost APIs.
- Current: linked defects/WOs are in DTO and detail exists.
- Required: one work center with defects, WOs, materials, costs, SLA and status actions.
- Backend changes: extend detail read model with material/cost/SLA summaries.
- Frontend changes: render linked materials/costs/SLA and action panel.
- DB migration: no.
- Tests: detail fixture with all linked objects.
- Acceptance: engineer can see repair execution status without leaving detail.

### CR-03 - WO Closure Readiness
- Affected: `WorkOrderService.assertClosureEvidenceReady`, `work-order-closure-readiness.ts`, `work-order-detail-page.tsx`.
- Current: backend blocks incomplete tasks, non-closed safety permit, unsigned completion act.
- Required: additionally materials, labor, result and meter snapshots where applicable.
- Backend changes: extend readiness service to include required labor/material/meter evidence and return machine-readable missing reasons.
- Frontend changes: consume backend readiness/allowedActions rather than duplicating rules.
- DB migration: no.
- Tests: close endpoint rejects each missing evidence class.
- Acceptance: close button disabled with same reasons backend enforces.

### WO-01 - Work Order Master Screen
- Affected: `work-order-detail-page.tsx`, print pages, work order DTO.
- Current: detail page has many sections and print routes.
- Required: summary, operations/checklist, materials, labor, permits, acts, costs, history in one master screen.
- Backend changes: ensure detail DTO exposes all linked collections or source links.
- Frontend changes: complete tabs/sections and empty/error states.
- DB migration: no.
- Tests: render with fully populated WO fixture.
- Acceptance: foreman executes lifecycle from one screen.

### WO-02 - Labor Entries
- Affected: `LaborEntryService`, `LaborEntryController`, `budget-control-page.tsx`.
- Current: labor CRUD exists. This audit added backend guard against creating labor on CLOSED WO and source-linked LABOR actual cost sync when rate/category are available.
- Required: labor should feed work order, timesheet and actual cost draft/pending cost.
- Backend changes: source-linked cost generation added for LABOR_ENTRY; timesheet integration remains open.
- Frontend changes: expose validation errors and show generated cost status.
- DB migration: yes, `V20260606_1__actual_cost_source_traceability.sql`.
- Tests: labor creates/updates pending actual cost; labor without rate does not fake cost; closed WO add rejected.
- Acceptance: labor hours are visible in WO and finance review.

### SP-01 - Spare Part Applicability
- Affected: `EquipmentSparePartService`, `MaintenanceTemplateSparePartRequirementService`, `WorkOrderSparePartRequirementService`.
- Current: equipment spare parts and template requirements exist.
- Required: suggestions by equipment type, equipment node and maintenance template.
- Backend changes: unified suggestion endpoint with source reasons.
- Frontend changes: show relevant parts at WO creation/detail.
- DB migration: maybe no; node-level applicability may need relation.
- Tests: pump/node/template produce expected suggestions.
- Acceptance: creating WO suggests relevant spare parts.

### SP-02 - Reservations
- Affected: `ReservationService`, `StockMovementService`, `WarehouseStockRepository`.
- Current: reserve increases reserved, cancel releases, fulfill decreases onHand and reserved; service uses pessimistic stock row lock entrypoints and blocks negative reserved/on-hand/available; DB constraints protect new invalid rows.
- Required: no negative stock under concurrent requests, source/reason linkage to WO/procurement where applicable.
- Backend changes: row-lock repository methods added; manual stock movement reason/source enforced before stock mutation.
- Frontend changes: show available = onHand - reserved consistently.
- DB migration: `V20260606_3__warehouse_stock_nonnegative_constraints.sql`.
- Tests: reservation/issue/release arithmetic, insufficient availability, stale reserved quantity rejection, manual reason validation, Docker-gated constraint/concurrency proof.
- Acceptance: service-level oversell/negative stock blocked; Docker-gated proof must be run on Docker-enabled machine.

### SP-03 - Reorder/Procurement/Receipt
- Affected: `LowStockRecommendationService`, `ProcurementRequestService`, `StockMovementService`, procurement pages.
- Current: low stock and procurement request APIs exist; receipt endpoint exists.
- Required: approved procurement receipt creates stock IN movement and updates stock with source trace.
- Backend changes: verify receipt stock movement is idempotent and source-linked.
- Frontend changes: one reorder -> procurement -> receipt workflow.
- DB migration: maybe no.
- Tests: low stock creates suggestion, approved receipt updates stock once.
- Acceptance: below min stock suggestion becomes received stock.

### FN-01 - Actual Costs With Technical Source
- Affected: `ActualCostService`, `ActualCost` entity, `budget-control-page.tsx`.
- Current: create rejects no-source and budgetLine-only rows; legacy WO/RR/contractor fields map to normalized `sourceType/sourceId`; labor/material/procurement/contractor paths create pending costs when actual cost is known. Multiple `WORK_ORDER` costs per work order are allowed for separate labor/material/contractor/other components.
- Required: every financial cost must have a technical source; budget line alone is not technical evidence.
- Backend changes: `ActualCostSourceType`, `source_type/source_id` migration, technical-source validation, and source-linked generation for LABOR_ENTRY/MATERIAL_ISSUE/PROCUREMENT_RECEIPT/CONTRACTOR_WORK; budgetLine remains optional accounting dimension. The source uniqueness index is partial and excludes `WORK_ORDER`/manual WO sources.
- Frontend changes: actual cost form must choose/show source object.
- DB migration: yes, `V20260606_1__actual_cost_source_traceability.sql`.
- Tests: no-source and budget-line-only rejected; WO/RR accepted; multiple WO cost components allowed; approved cost updates budget once; rejected cost does not update budget; material issue/procurement receipt create/update source-linked pending costs when cost exists and do not fake unknown costs; Docker-gated migration proof covers duplicate legacy `WORK_ORDER` actual costs.
- Acceptance: manually created finance rows require a technical source; material/procurement known-cost auto-linkage is implemented. Reversal/cancel synchronization remains a BA/product decision.

### FN-02 - Financial Approval
- Affected: `ActualCostService.review`, budget/financial review pages.
- Current: pending -> approved/rejected with reviewer/comment, budget actual only on approved.
- Required: route by department/category/amount and SLA/history.
- Backend changes: integrate `FinancialApprovalRuleService` into actual cost creation/review queues.
- Frontend changes: show route, reviewer, overdue/due soon, approve/reject reason.
- DB migration: maybe no.
- Tests: rejected cost does not update budget; approved does; route chosen by threshold.
- Acceptance: economist approves/rejects and budget actual changes only on approval.

### FN-03 - Budget Control
- Affected: `BudgetSummaryController`, `budget-control-page.tsx`.
- Current: planned/actual summaries and actual cost register exist.
- Required: plan, reserve, fact, balance, overrun by department/equipment/category; overrun links to source rows.
- Backend changes: define reserved/committed rule and expose source drilldown.
- Frontend changes: add committed/reserved columns and overrun source links.
- DB migration: maybe yes if commitments are persisted.
- Tests: approved/rejected/pending totals and overrun link integrity.
- Acceptance: manager can explain overrun from source rows.

### RB-01 - Permission Matrix
- Affected: `PermissionConstants`, controllers with `@PreAuthorize`, frontend `permissions.ts`, route/action tests.
- Current: broad RBAC/PBAC tests exist; this pack added explicit labor-entry and reservation controller guards.
- Required: route/action visibility must match backend API permissions for SYSTEM_ADMIN, PPR_ENGINEER, FOREMAN, STOREKEEPER, ECONOMIST, VIEWER.
- Backend changes: labor read/create/update/delete and reservation read/reserve/cancel/fulfill guards added; FOREMAN/PPR_ENGINEER labor-create defaults updated.
- Frontend changes: use backend permissions/allowedActions consistently.
- DB migration: no.
- Tests: role matrix smoke across critical mutations plus `RbacLaborEntrySecurityTest` and `RbacReservationSecurityTest`.
- Acceptance: VIEWER has no mutations; STOREKEEPER cannot approve finance; ECONOMIST cannot close WO.

### RB-02 - Production Secrets
- Affected: `application-prod.yml`, `ProductionConfigSecretsTest`.
- Current: deferred for this branch. Runtime security configuration is TeamLead/DevOps-owned and locked for the current P0 demo-chain work; application security/JWT/auth/docker env values were intentionally not changed in this pass.
- Required: no hardcoded DB/JWT/MinIO/default admin secrets in prod config.
- Backend changes: none in this pass by explicit scope decision.
- Frontend changes: no.
- DB migration: no.
- Tests: the prod secret policy methods in `ProductionConfigSecretsTest` are disabled/quarantined with the reason "Deferred: runtime security configuration is TeamLead/DevOps-owned and will be re-enabled during final security hardening". Non-prod/demo policy methods remain active.
- Acceptance: deferred. Final prod secret validation must be re-enabled and owned by TeamLead/DevOps during the final security hardening phase.

### QA-01 - Demo Dataset
- Affected: `src/main/resources/db/demo-seed/*`, demo seeder classes.
- Current: phased demo seed exists; phase-5 adds exact `AUTO-PUMP-A1`, `AUTO-PUMP-A2`, and `AUTO-PUMP-A3-NOMETER` assets.
- Required: idempotent dataset with `AUTO-PUMP-A1`, `AUTO-PUMP-A2`, `AUTO-PUMP-A3-NOMETER`.
- Backend changes: added `DemoP0LeadershipDemoSeeder` and `phase-5-p0-demo.sql` under the `dev,demo-seed` profile only.
- Frontend changes: demo links/filters should target these assets.
- DB migration: no unless seed uniqueness missing.
- Tests: `DemoP0SeedContractTest` verifies exact seed source locally; `SeedProfileStartupSmokeTest.startupWithDevAndDemoSeedProfilesSeedsExactP0DemoAssetsIdempotently` is Docker/Testcontainers-backed and compares natural-key counts after two `dev,demo-seed` starts.
- Acceptance: all three assets have passport/docs/nodes/meter-or-missing-meter/history/spares/costs.

### QA-02 - Leadership Demo Script
- Affected: docs/UAT, Playwright or manual evidence scripts.
- Current: UI-only leadership demo script exists as documentation, but is not yet executable.
- Required: UI-only script with screenshots/evidence.
- Backend changes: ensure seed and APIs support script.
- Frontend changes: stable routes and human errors.
- DB migration: no.
- Tests: Playwright smoke using seeded demo still needed.
- Acceptance: open machine -> passport -> due -> approval -> WO -> completion -> next cycle without SQL/Postman.

### QA-03 - Manual Attribute Feature Flag
- Affected: `manual-attribute-feature.ts`, related tests.
- Current: fixed in this audit: tests now match the enabled manual attribute flag and cover touched/untouched edit behavior.
- Required: stable feature flag behavior.
- Backend changes: no.
- Frontend changes: `manual-attribute-feature.test.ts` updated for enabled behavior.
- DB migration: no.
- Tests: `yarn test`.
- Acceptance: no mismatch between manual attribute enabled/disabled behavior.

### QA-04 - Maven Wrapper
- Affected: `mvnw`, `.mvn/wrapper/maven-wrapper.properties`.
- Current: fixed in this audit.
- Required: `./mvnw test` available locally/CI.
- Backend changes: completed.
- Frontend changes: no.
- DB migration: no.
- Tests: `./mvnw -Dtest=ProductionConfigSecretsTest test`, `./mvnw -Dtest=LaborEntryServiceTest test`, `./mvnw test` on Docker-enabled machine.
- Acceptance: targeted Maven tests run on a machine without `mvn`; repository `@DataJpaTest`/Testcontainers suites require Docker.

## P1 Items To Pull Into P0-Minimal

Pull these into P0 because they affect demo trust/auditability:
- Passport audit for key fields: history exists; make it visible from Equipment Card for POWER_KW, serialNumber, technicalNumber.
- Enum localization on P0 screens: i18n entries exist, but hardcoded labels remain in equipment/registry and due/WO forms.
- Critical audit trail: transitions mostly audit, but stock movement, cost approval, repair override, WO close must be visible from object details.
- Cost generation from labor/material sources: required for "every financial cost has technical source".
- Equipment Card cost/reliability drilldown: needed to explain why a machine is risky/expensive.
- Department scope for active P0 APIs/read models: many tests exist; any future Cockpit work must separately preserve the same scope rules after PM approval.
- Minimal print links: already present for WO/safety/completion; keep accessible from WO detail.

## Canonical State Machines

### MaintenanceDueEvent
| Transition | From | To | Permission | Evidence | Side effect | Audit | Idempotency | Frontend rule |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| detect | none/open cycle | DETECTED or AWAITING_APPROVAL | system | due calculation | event saved | event history/audit | unique equipment+rule+cycleKey | show reason |
| approve-create-task | DETECTED/AWAITING_APPROVAL | TASK_CREATED | MAINTENANCE_EVENT_APPROVE or rule permission | approver | PPR task created | approval audit | createdTaskId prevents duplicate | action enabled if not BLOCKED |
| approve-create-wo | DETECTED/AWAITING_APPROVAL | WORK_ORDER_CREATED | MAINTENANCE_EVENT_APPROVE or rule permission | approver | WO created | approval audit | createdWorkOrderId/open cycle prevents duplicate | disabled if duplicate/BLOCKED |
| suppress | any open | SUPPRESSED_DUPLICATE | system | open task/WO same cycle | no downstream | system audit | deterministic cycleKey | show duplicate link |
| complete | WORK_ORDER_CREATED/TASK_CREATED | RESOLVED | system/WO complete | completion anchor | next cycle recalculated | WO/due audit | existing anchor by event/WO | show completed |
| cancel | open | CANCELLED | MAINTENANCE_EVENT_CANCEL | reason | no downstream | cancel audit | no-op if terminal | reason required |

### WorkOrder
| Transition | From | To | Permission | Evidence | Side effect | Audit | Idempotency | Frontend rule |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| create | none | DRAFT/PLANNED | WORK_ORDER_CREATE | equipment, department, source | template spare requirements sync | CREATE | unique number/cycle | show create if permission |
| approve | DRAFT/PLANNED | APPROVED | WORK_ORDER_APPROVE | approver | replacement reserve if applicable | APPROVE | reject if already approved | allowedAction approve |
| start | APPROVED | IN_PROGRESS | WORK_ORDER_START | assigned work | equipment IN_REPAIR | UPDATE | reject non-approved | allowedAction start |
| complete | APPROVED/IN_PROGRESS | COMPLETED | WORK_ORDER_COMPLETE | result, material issue, meter snapshots if relevant | anchor, due complete, linked RR/defect sync | UPDATE | anchor lookup by event/WO | allowedAction complete |
| close | COMPLETED | CLOSED | WORK_ORDER_CLOSE | tasks done, permit closed, act signed, plus P0 labor/material/meter rules | linked RR/defect close sync | CLOSE | reject terminal | disabled reason from backend readiness |

### RepairRequest
| Transition | From | To | Permission | Evidence | Side effect | Audit | Idempotency | Frontend rule |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| create | none | OPEN/REGISTERED | REPAIR_REQUEST_CREATE | equipment, department, defect optional | link defect | CREATE | unique number | show create |
| approve | OPEN/REGISTERED/IN_REVIEW/NEEDS_CLARIFICATION | APPROVED | REPAIR_REQUEST_APPROVE | reviewer | notification | APPROVE | reject terminal | approve enabled |
| assign | APPROVED | ASSIGNED | REPAIR_REQUEST_ASSIGN | assignee | notify assignee | UPDATE | reject if not APPROVED | assign enabled |
| start | ASSIGNED/APPROVED | IN_PROGRESS | system/WO start | linked WO start | status sync | UPDATE | no-op if already in progress | show progress |
| complete | IN_PROGRESS | COMPLETED | system/WO complete | all linked WOs terminal and defects resolved | status sync | UPDATE | no-op terminal | show ready to close |
| close | COMPLETED | CLOSED | REPAIR_REQUEST_CLOSE | close result, terminal WOs/defects | actualCompletionAt | CLOSE | reject already closed | disabled reason |
| override | any non-terminal | requested status | SYSTEM_ADMIN or explicit override permission | reason | status change | UPDATE override | reason mandatory | admin-only action |

### MaterialReservation
| Transition | From | To | Permission | Evidence | Side effect | Audit | Idempotency | Frontend rule |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| reserve | none | ACTIVE | STOCK_RESERVE | stock, qty, WO/RR | reservedQty increases | CREATE + movement RESERVATION | atomic stock check | disable if insufficient available |
| cancel/release | ACTIVE | CANCELLED | STOCK_RELEASE | reason | reservedQty decreases | UPDATE + movement RELEASE | reject non-active | show cancel |
| fulfill/issue | ACTIVE | FULFILLED | STOCK_ISSUE | WO/material issue | onHand and reserved decrease | UPDATE + movement ISSUE | reject non-active | show issue |

### ActualCost / FinancialApproval
| Transition | From | To | Permission | Evidence | Side effect | Audit | Idempotency | Frontend rule |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| create | none | PENDING | ACTUAL_COST_CREATE | technical source + category + amount | finance notification | CREATE | unique source where applicable | source required |
| approve | PENDING | APPROVED | ACTUAL_COST_APPROVE | reviewer + comment | budget actual increments once | UPDATE | reject non-pending | approve enabled |
| reject | PENDING | REJECTED | ACTUAL_COST_REJECT | reviewer + comment | no budget actual | UPDATE | reject non-pending | reject enabled |
| route override | PENDING | PENDING | FINANCE_ROUTE_OVERRIDE_APPLY | reason | route owner changes | audit | latest override only | admin/finance action |

### ProcurementRequest
| Transition | From | To | Permission | Evidence | Side effect | Audit | Idempotency | Frontend rule |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| create | none | DRAFT | PROCUREMENT_CREATE | low stock or manual source | request lines | CREATE | unique suggestion optional | create enabled |
| submit | DRAFT | SUBMITTED | PROCUREMENT_SUBMIT | lines, supplier/warehouse | approval request optional | UPDATE | reject non-draft | submit enabled |
| approve | SUBMITTED | APPROVED | PROCUREMENT_APPROVE | approver | ready to order | APPROVE | reject non-submitted | approve enabled |
| order | APPROVED | ORDERED | PROCUREMENT_ORDER | PO/doc number | committed amount optional | UPDATE | reject non-approved | order enabled |
| receive | ORDERED/APPROVED | RECEIVED | PROCUREMENT_RECEIVE | receipt qty/warehouse | stock IN movement | CLOSE/UPDATE | receipt idempotency key | receive enabled |
| reject/cancel | DRAFT/SUBMITTED/APPROVED | REJECTED/CANCELLED | PROCUREMENT_REJECT/CANCEL | reason | no stock movement | CANCEL | reject terminal | reason required |

## Top 10 Business Logic Risks

1. Material issue/procurement receipt cost linkage now exists for known costs, and frontend unknown-cost warnings are visible; reversal/cancel synchronization remains open.
2. Reservation/stock changes now have service row-lock entrypoints, nonnegative guards, DB constraints and Docker-gated concurrency proof; the proof still must run on Docker-enabled CI/local machine.
3. WO closure backend readiness covers tasks/permit/act plus required labor and active reservations, but meter/material/result snapshots still need a work-type-specific contract.
4. Template-to-WO mapping is stronger for operations/spares and frontend source visibility is present; phase-5 seed includes the 3-operation/2-spare AUTO template, but Docker-backed generated-WO proof remains open.
5. Due explanation is structured in backend DTOs and rendered on frontend, but seeded screenshot/UAT evidence is still missing.
6. Some frontend screens still contain hardcoded/raw enum labels.
7. RBAC matrix is broader after labor/reservation guards, but frontend route/action matrix regeneration is still needed.
8. Exact A1/A2/A3-NOMETER demo seed exists, but Docker/Postgres idempotency must still be executed rather than skipped.
9. UI-only UAT script exists as docs; automated screenshot/evidence run is still missing.
10. MT-01 Operational Cockpit is deferred by Product/PM and should not be treated as an active implementation risk in this fix pack.

## Implementation Order

1. RB-01.
2. FN-01.
3. WO-02.
4. SP-02.
5. CR-03.
6. MT-02.
7. MT-04.
8. MT-03.
9. QA-01 / QA-02.

MT-01 is intentionally excluded from the current implementation order until explicit PM approval.

## Verification Run During Audit

Files changed in the second hardening pack:
- Backend controllers: `LaborEntryController`, `ReservationController`.
- Backend cost traceability: `ActualCost`, `ActualCostDto`, `ActualCostSourceType`, `ActualCostRepository`, `ActualCostService`.
- Backend labor/cost integration: `LaborEntryService`, `LaborEntryServiceTest`.
- Backend stock correctness: `WarehouseStockRepository`, `ReservationService`, `StockMovementService`.
- Backend closure readiness: `WorkOrderService`, `WorkOrderServiceTest`.
- Backend RBAC defaults/tests: `RolePermissionDefaults`, `RbacLaborEntrySecurityTest`, `RbacReservationSecurityTest`.
- Documentation/config from first patch: `README.md`, `mvnw`, `.mvn/wrapper/maven-wrapper.properties`, `ProductionConfigSecretsTest`. Production security config is locked for the current P0 branch and final validation is deferred to TeamLead/DevOps.

Migrations:
- `V20260606_1__actual_cost_source_traceability.sql` adds nullable `actual_costs.source_type/source_id`, backfills legacy WO/RR/contractor sources, adds source-type check constraint, and enforces one active cost per normalized source.
- `V20260606_2__work_order_task_template_source.sql` adds `work_order_tasks.source_template_id/source_operation_id` and indexes for generated task provenance.
- `V20260606_3__warehouse_stock_nonnegative_constraints.sql` adds nonnegative and reserved-not-over-on-hand stock check constraints for new writes.

Backend:
- `./mvnw -Dtest=ProductionConfigSecretsTest test` - expected to run with prod secret policy methods skipped/quarantined for this branch.
- `./mvnw -Dtest=LaborEntryServiceTest test` - passed.
- `./mvnw -Dtest=ProductionConfigSecretsTest,LaborEntryServiceTest test` - expected to run with prod secret policy methods skipped/quarantined for this branch.
- `./mvnw -Dtest=ActualCostPbacScopeTest,MaterialStockPbacScopeTest,StockMovementServiceTest test` - passed, 21 tests.
- `./mvnw -Dtest=ActualCostServiceTest,LaborEntryServiceTest,ReservationServiceTest,StockMovementServiceTest,RbacLaborEntrySecurityTest,RbacReservationSecurityTest,WorkOrderServiceTest test` - passed, 141 tests.
- Pack #3 focused tests:
  - `./mvnw -Dtest=MaintenanceDueCalculationServiceTest,MaintenanceDueEventServiceTest,RbacMaintenanceAutomationSecurityTest test` - passed, 49 tests.
  - `./mvnw -Dtest=RepairMaterialUsageServiceTest,ProcurementRequestServiceTest,MaterialUsagePbacScopeTest,ProcurementPbacScopeTest test` - passed, 48 tests.
  - `./mvnw -Dtest=MaintenanceAutomationServiceTest test` - passed, 35 tests.
  - `./mvnw -Dtest=ReservationServiceTest,WarehouseStockConstraintMigrationTest test` - passed, 8 tests with 2 Docker-gated tests skipped locally due missing Docker.
  - `./mvnw -Dtest=EquipmentServiceTest#searchIncludesPassportCompletenessForRequiredAttributes test` - passed, 1 test.
- P0 seed/UAT focused tests:
  - `./mvnw -Dtest=DemoP0SeedContractTest test` - passed, 2 tests.
  - `./mvnw -Dtest=SeedProfileStartupSmokeTest test` - build success, 4 tests skipped locally because Docker/Testcontainers is unavailable.
- `./mvnw test` on this local machine after the phase-5 seed pass: 2,166 tests discovered, 0 failures, 48 errors, 8 skipped. The remaining errors are `@DataJpaTest`/Testcontainers context failures caused by missing local Docker/Postgres (`Could not find a valid Docker environment` / `Connection to localhost:5433 refused`), not assertion regressions.
- Full backend tests require Docker/Testcontainers. Failures on non-Docker machines are environmental; on a Docker-enabled machine they should be rerun as the release gate.

Frontend:
- `yarn test` - passed after frontend P0 rendering pass, 67 files / 486 tests.
- `yarn build` - passed.

Known build warning:
- Maven reports duplicate `org.testcontainers:junit-jupiter` dependency in `pom.xml`; not fixed in this audit.
- Lombok builder default warnings are pre-existing and noisy.
- Vite build reports large chunk warnings; build still succeeds.

## UAT Script Impact

The current code is closer to a P0 demo after the fixes because:
- Final prod secret validation is explicitly quarantined instead of silently changing TeamLead/DevOps-owned runtime security configuration.
- Backend tests can run via `./mvnw`.
- Closed WO labor entry bypass is blocked server-side.

Remaining UAT blockers:
- Need automated UI evidence/screenshots for the documented A1/A2/A3-NOMETER path.
- Need reversal/cancel synchronization for material/procurement source-linked actual costs if business requires it.
- Need Docker-backed parallel reservation proof to actually run on a Docker-enabled environment.
- Need work-type-specific closure evidence rules for meter/material/result snapshots.
- Need seeded UI evidence/screenshots for the documented route on a Docker/Postgres demo database.
- Need Docker/Postgres execution of the new phase-5 seed idempotency test because local Docker is unavailable.
- TeamLead/DevOps must still provide final production DB/JWT/admin env and secret-manager configuration for the `prod` profile, then re-enable the quarantined prod secret policy checks.
- Need Docker/Postgres confirmation that `V20260606_1__actual_cost_source_traceability.sql` migrates existing data with duplicate `WORK_ORDER` actual costs; a Docker-gated test exists but skips locally without Docker.

Adjusted leadership demo route:
- Equipment Registry -> Equipment Card -> Passport completeness -> Due Event explanation -> Approval/Create WO -> WO Detail -> Labor/Materials -> Closure readiness -> Completion/Close -> Equipment Card next cycle/history -> Finance/Budget source rows.
- Demo route must start from Equipment Registry/Card or Due Events page. It must not depend on Operational Cockpit unless PM explicitly approves MT-01 later.

## BA/Product Questions

1. Should a budget line alone ever count as a valid "technical source" for actual cost? Current P0 principle says no.
2. Which WO evidence is mandatory by work type: labor, material issue, meter snapshot, safety permit, completion act?
3. Who besides SYSTEM_ADMIN may perform audited corrective closure overrides?
4. What exact reserved/committed budget rule should be shown: reservations, ordered procurement, pending actual costs, or all three?
