# PPR Schedule Window and Test Gate Design

**Date:** 2026-07-28
**Status:** Approved
**Scope:** F-01 and F-11 from the TOIR business-logic audit
**Repository:** `toir-backend`

## 1. Purpose

This change makes generated PPR execution windows reflect working shifts and
prevents a task from becoming overdue before its scheduled work has ended. It
also makes backend tests a mandatory GitLab merge gate and removes the known
test-contract defects that currently prevent the gate from being trusted.

The implementation must preserve the current uncommitted Annual Maintenance
Schedule diagnostics and weekday-shifting work.

## 2. Schedule Semantics

Generated work is scheduled in exact working hours:

- the default shift starts at `09:00`;
- the default shift duration is `8` hours;
- the default non-working weekdays are `SATURDAY` and `SUNDAY`;
- all three defaults are external configuration;
- plan-level excluded weekdays are additional non-working weekdays;
- a future production-calendar provider can add dated holidays or replace the
  default calendar without changing PPR generation code.

The calculator moves a start on a non-working day to the next working shift.
It consumes labor hours inside working shifts and resumes at the next working
shift when labor remains.

With the default configuration:

| Labor | Start | End |
| ---: | --- | --- |
| 1 hour | Monday 09:00 | Monday 10:00 |
| 8 hours | Monday 09:00 | Monday 17:00 |
| 9 hours | Monday 09:00 | Tuesday 10:00 |
| 16 hours | Monday 09:00 | Tuesday 17:00 |
| 24 hours | Monday 09:00 | Wednesday 17:00 |

A task occurrence that starts within the plan period may finish after the plan
period. Generation must not silently truncate labor at `plan.endDate`.

For generated tasks, `dueDate` equals `scheduledEnd`. Manual task commands keep
their explicit dates but must satisfy the same invariant.

## 3. Components

### `ScheduleWindowCalculator`

A focused scheduling service accepts:

- requested start date;
- positive normative labor hours;
- calendar context;
- additional excluded weekdays.

It returns an immutable `ScheduleWindow` containing `scheduledStart` and
`scheduledEnd`.

The calendar context is obtained through a `WorkingCalendarProvider` boundary.
The first implementation is configuration-backed. Its interface is ready to
accept department-, equipment-, and date-specific production calendars later.

### `PprTaskScheduleInvariant`

One domain policy validates:

- `scheduledStart` is not after `scheduledEnd`;
- `dueDate` is present;
- `dueDate` is not before `scheduledEnd`.

Both generated-task paths and all manual add/update paths call this policy
before persistence. No controller or alternate service endpoint may bypass it.

### PPR generation integration

Both the Maintenance Schedule builder path and the fixed PPR generation path
use `ScheduleWindowCalculator`. Existing hard-coded `plusDays(ceil(hours / 8))`
logic and the `plan.endDate` truncation are removed.

Generated values are assigned as:

```text
scheduledStart = calculatedWindow.start
scheduledEnd   = calculatedWindow.end
dueDate        = calculatedWindow.end
```

## 4. Overdue Semantics

The permanent business rule is:

```text
task is overdue only when now is after dueDate
and now is after scheduledEnd
```

For all newly created valid tasks, `dueDate >= scheduledEnd`, so this is
equivalent to comparing `now` with `dueDate`.

The explicit `scheduledEnd` guard remains permanently at the detector boundary.
It is useful for imported, historical, or externally corrupted rows whose
dates predate the new invariant. It is not a temporary migration flag and does
not change valid-task semantics.

Null legacy dates are handled defensively: a task is not automatically
transitioned to `OVERDUE` unless the available deadline fields prove that both
the due threshold and execution end have passed.

## 5. Configuration

Configuration is namespaced under `toir.ppr.scheduling`:

```yaml
toir:
  ppr:
    scheduling:
      shift-start: "09:00"
      shift-hours: 8
      weekend-days:
        - SATURDAY
        - SUNDAY
```

Startup validation rejects:

- non-positive shift duration;
- a shift duration greater than 24 hours;
- an empty or invalid shift start;
- all seven weekdays configured as non-working.

## 6. F-11 Test Gate

### Test source fixes

- Keep the real `PprPlanDto` fixture in
  `MaintenanceScheduleCalculationServiceTest`; do not enable inline Mockito or
  change the project-wide mock maker.
- Correct list JSONPath expressions such as `"1id"` and `"1planId"` to
  `$[0].id` and `$[0].planId`.
- Correct the same malformed list JSONPath pattern in notification contract
  tests so the full test gate is not blocked by an equivalent defect.

### GitLab pipeline

The pipeline runs for:

- merge request pipelines;
- the default branch.

Stages run in this order:

1. `test`: `./mvnw test --no-transfer-progress`;
2. `compile`: package the application only after tests pass;
3. existing image build;
4. existing deploy.

A test error or failure blocks packaging and all downstream stages. The test
job uses the existing Java 21 Maven image and publishes Surefire reports as
JUnit artifacts.

Deployment behavior and WMS/Finance navigation are outside this change.

## 7. Tests

### Unit tests

`ScheduleWindowCalculator` covers:

- 1, 8, 9, 16, and 24 labor hours;
- a Friday-to-Monday rollover;
- a start on a configured weekend;
- an additional plan-level excluded weekday;
- invalid labor and invalid calendar configuration.

`PprTaskScheduleInvariant` covers equal due/end, later due, earlier due, and an
invalid start/end range.

### Service tests

- Both generator paths persist the calculated window and equal due date.
- Generation near the plan boundary does not truncate normative labor.
- Manual add and update reject `dueDate < scheduledEnd`.
- `OverdueDetectorService` does not transition a legacy-invalid task before
  `scheduledEnd`.
- The detector transitions a valid task only after its due date.

### Release verification

- Run the focused F-01/F-11 tests first.
- Run the complete backend unit test suite.
- Confirm the GitLab configuration creates a merge-request test job whose
  failure blocks later stages.

The current local desktop runtime contains a JRE but no `javac`; local Maven
verification therefore requires a Java 21 JDK runtime or the GitLab test job.

## 8. Compatibility and Non-Goals

- No production data rewrite is required.
- Historical invalid rows remain readable and are protected by the detector
  boundary.
- No full holiday database or shift-roster UI is introduced.
- F-03 through F-09 are not implemented in this delivery.
- F-10 remains explicitly excluded.
- Existing Annual Maintenance Schedule weekday-shifting and diagnostic changes
  are preserved.
