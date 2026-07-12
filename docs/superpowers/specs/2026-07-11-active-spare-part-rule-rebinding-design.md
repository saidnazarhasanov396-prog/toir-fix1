# Active Spare-Part Rule Rebinding Design

## Problem

An active `SparePartInstallation` stores the life-rule revision selected at installation time in `appliedLifeRuleId`, `appliedRuleRevision`, and `appliedRuleSnapshot`. Subsequent evaluations deliberately read that immutable snapshot. Creating, revising, or deactivating a more specific effective rule therefore does not change existing installations or their due events.

This produces stale remaining-life values. For example, an installation that captured a catalog rule with a 1,000,000 km limit continues to use that limit after an equipment-scoped 1,000 km rule becomes effective.

## Required Behavior

Every successful create, revise, or deactivate operation on a spare-part life rule must rebind all affected active installations to the rule that is effective after the mutation.

Rebinding must:

1. resolve the winning rule using the existing scope precedence and the mutation time;
2. replace the installation's applied rule identity and evaluation snapshot;
3. preserve meter baselines captured for the physical installation whenever the new rule uses the same meter;
4. evaluate the installation immediately and upsert or resolve its due event in the same transaction;
5. retain an auditable record of the applied rule revision through the installation and due-event fields.

The operation must not reset consumed life merely because an administrator changed a rule. If a part has consumed 2,500 km since installation, applying a 1,000 km rule makes it overdue by 1,500 km.

## Scope Selection

A rule mutation can affect active installations of the same spare part, but only installations for which the mutated scope could participate in resolution need to be considered:

- `CATALOG`: every active installation of the spare part;
- `EQUIPMENT`: active installations of the spare part on that equipment;
- `NODE`: active installations of the spare part on that equipment and node;
- `NODE_SLOT`: active installations of the spare part at that exact normalized position.

For correctness, each candidate is passed through `SparePartLifeRuleResolver` after the rule mutation. This also handles fallback when a rule is revised or deactivated.

## Rebinding Service

Introduce a focused transactional service responsible for rebinding active installations. `SparePartLifeRuleService` calls it after persisted rule and limit changes are complete.

For each candidate installation, the rebinding service:

1. locks and reloads the active installation;
2. resolves the effective rule at the supplied mutation timestamp;
3. loads its ordered limits;
4. builds a new applied-rule snapshot;
5. reconciles required meter baselines;
6. updates applied rule identity, revision, and snapshot;
7. invokes normal lifecycle reevaluation, which persists evaluation state and updates the due event.

The existing install and replace behavior remains unchanged.

## Baseline Reconciliation

Existing baselines belong to the physical installation, not to a rule revision. A baseline is reused when the new limit resolves to the same canonical equipment meter.

If the new rule introduces a meter for which the installation has no baseline, the service attempts to reconstruct the installation-time value from the latest valid meter reading at or before `installedAt`. The reconstructed baseline records that reading ID and timestamp.

If no trustworthy historical reading exists, rebinding must not silently use zero or the current reading. The installation is persisted with the new rule snapshot and an evaluation-error result explaining that the historical baseline is unavailable. The due/readiness projection must expose the error for operational correction.

Baselines no longer referenced by the winning rule remain as historical installation evidence and are ignored by the evaluator.

## Transaction and Failure Semantics

Rule mutation, candidate rebinding, evaluation, and due-event updates execute in one transaction. Domain errors in rule validation or resolution roll back the mutation.

Missing historical baseline data is an evaluation outcome rather than a transaction failure: the new rule remains effective, the affected installation becomes visibly blocked by `EVALUATION_ERROR`, and no fabricated remaining-life value is published.

Pessimistic installation locks serialize rebinding against install, replace, remove, meter-triggered reevaluation, and concurrent rule changes. Candidate lists are processed in stable installation order.

## Compatibility and Schema

The current entity marks `applied_rule_snapshot` as `updatable = false`. Rebinding requires making the three applied-rule fields updateable. No destructive migration is required because the database columns already support updates.

The public rule and installation APIs remain additive and retain their response shapes. Existing installations without an applicable rule continue to use the manual empty snapshot; deactivation can therefore fall back to a less-specific rule or to manual evaluation.

## Testing

Regression coverage must prove:

- creating a more-specific equipment rule rebinds an installation that captured a catalog rule;
- the original meter baseline is retained and consumed resource is not reset;
- revising a rule applies the new revision and limit to active installations;
- deactivating a rule falls back to the next effective scope;
- unrelated equipment, nodes, slots, and spare parts are not rebound;
- due events are updated or resolved immediately;
- a newly required meter uses a historical installation-time reading when available;
- an unavailable historical baseline yields `EVALUATION_ERROR` and never uses zero/current value;
- rule mutation rolls back on resolver ambiguity or unexpected rebinding failure;
- existing install, replace, scheduled evaluation, and meter-triggered evaluation tests continue to pass.

## Non-Goals

- Resetting part life when a rule changes.
- Reconstructing installations that do not exist in lifecycle history.
- Modifying equipment or vehicle design-lifetime fields.
- Rewriting completed or removed installation history.
- Adding asynchronous eventual-consistency infrastructure for the initial fix.
