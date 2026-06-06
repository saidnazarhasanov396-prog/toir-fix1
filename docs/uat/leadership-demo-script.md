# Leadership Demo Script - P0 Maintenance Chain

Scope: UI-only demo path for the current P0 fix pack. MT-01 Operational Cockpit is deferred and must not be used as the demo entry point until PM explicitly approves it.

Do not use SQL, Postman, manual DB edits, or direct API calls during the demo.

## Demo Roles

- Engineer / PPR engineer: opens equipment, explains passport and due event, creates or requests WO.
- Approver / maintenance manager: approves due event if approval is required.
- Foreman: executes WO tasks, labor, materials, readiness, completion and close.
- Storekeeper: reserves/issues materials from warehouse screens or WO material actions.
- Economist: reviews finance/budget rows created from technical sources.

## Primary Route

1. Sign in as engineer.
   Expected: user lands in the application with equipment and maintenance menu access.

2. Open Equipment Registry and search for `AUTO-PUMP-A1`.
   Expected: one equipment row is visible. Registry shows a passport completeness summary such as required/filled counts and critical missing count.

3. Open the Equipment Card for `AUTO-PUMP-A1`.
   Expected: first screen shows identity, location/placement, passport state, warranty/lifetime signals and links to maintenance/due history.

4. Open the passport completeness block.
   Expected: required fields are either complete or missing fields are listed with labels, blocking reason when applicable, and a fill action that returns to the equipment passport editor.

5. Open Due Events from the Equipment Card or the Due Events page filtered to `AUTO-PUMP-A1`.
   Expected: due event explanation shows structured formula details: base source/date, last completion anchor, meter/current/interval/remaining values or calendar interval/tolerance, trigger policy, and legacy explanation text as supporting context.

6. Approve or create the work order from the due event.
   Expected: BLOCKED events cannot create a WO. DUE/OVERDUE events create exactly one WO for the cycle, or move to an approval step when configured.

7. Open the generated WO Detail.
   Expected: WO contains copied template operations/checklist tasks with planned hours and source template/operation traceability. The WO is not an empty execution document.

8. Add or review labor.
   Expected: labor cannot be added after WO is CLOSED. Labor with a known rate creates or updates a source-linked pending actual cost; unvalued labor does not fake a cost.

9. Add or issue materials.
   Expected: material issue updates stock safely and creates or updates a source-linked pending actual cost when unit cost is known. Unknown unit cost produces a visible warning/status instead of a fake amount.

10. Check closure readiness.
    Expected: close/complete action shows backend reasons for missing evidence such as incomplete tasks, required labor, active reservations, permit/act gaps or other configured readiness blockers.

11. Complete and close the WO after evidence is ready.
    Expected: WO transitions through completion/close with audit trail; linked maintenance due event is resolved and completion anchor is recorded.

12. Return to the Equipment Card.
    Expected: due history shows the completed cycle and the next cycle calculation is visible. The card remains the source of truth for history, not Cockpit.

13. Open Finance / Budget source rows as economist.
    Expected: actual cost rows have technical source type and source id. Budget line alone is not accepted as technical evidence. Approved costs update budget actual once; rejected costs do not.

## Negative Branch: A3 No Meter

1. Sign in as engineer and open Equipment Registry.
   Expected: `AUTO-PUMP-A3-NOMETER` is visible.

2. Open the Equipment Card for `AUTO-PUMP-A3-NOMETER`.
   Expected: passport completeness shows missing critical fields or required setup gaps with a fix action.

3. Open Due Events for `AUTO-PUMP-A3-NOMETER`.
   Expected: event is BLOCKED when the configured meter/passport prerequisite is missing.

4. Inspect the structured explanation.
   Expected: blocking code and field are concrete, for example missing active meter type; fix link points back to the equipment meter/passport setup screen.

5. Attempt Approval/Create WO.
   Expected: UI prevents the action or backend returns the same blocked reason. No WO is created from a blocked event.

## Demo Evidence Checklist

- Screenshot: Equipment Registry with `AUTO-PUMP-A1` passport completeness badge.
- Screenshot: Equipment Card passport completeness block.
- Screenshot: Due Event structured explanation.
- Screenshot: generated WO tasks copied from template.
- Screenshot: closure readiness before and after evidence is complete.
- Screenshot: Equipment Card history/next cycle after close.
- Screenshot: Finance/Budget actual cost row with technical source.
- Screenshot: `AUTO-PUMP-A3-NOMETER` blocked explanation and fix action.
