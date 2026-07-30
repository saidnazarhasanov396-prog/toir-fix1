# Multiple bank details for counteragents

Date: 2026-07-30

## Goal

Allow a counteragent to store up to ten ordered bank-detail records instead of
one `bankName`/`bankAccount`/`mfo` triplet. Preserve all existing data, keep
unknown API consumers working during a one-release transition, and use the
primary bank detail in the counteragent acceptance act.

This design spans:

- `toir-backend`, which owns persistence, validation, migration, and the API
  contract;
- `toir-frontend`, which owns editing, read-only display, printing, and
  localization.

## Decisions

- A counteragent may have zero through ten bank details.
- At most one detail may have `isPrimary = true`; zero explicit primary details
  is valid.
- Array order is authoritative. The backend persists it as `displayOrder`.
- When no detail is explicitly primary, consumers use the first detail by
  `displayOrder`, then by `id`, as the effective primary.
- The acceptance act automatically displays the effective primary. Selecting a
  bank account while generating an act is outside this task.
- Unknown external API consumers require a one-release dual-contract
  transition. The new frontend uses only `bankDetails`.
- Fully empty draft rows are ignored. Partially completed rows must be
  completed or removed.

## Persistence model

Create `counteragent_bank_details` with:

| Column | Contract |
| --- | --- |
| `id` | UUID primary key |
| `counteragent_id` | Required FK to `counteragents(id)` with `ON DELETE CASCADE` |
| `bank_name` | Required string |
| `bank_account` | Required string |
| `mfo` | Required string |
| `is_primary` | Required boolean, default `false` |
| `display_order` | Required non-negative integer |

Add:

- an index supporting ordered lookup by
  `(counteragent_id, display_order, id)`;
- a PostgreSQL partial unique index on `counteragent_id` where
  `is_primary = true`, protecting the invariant from races and writes that
  bypass the service;
- service validation that returns a comprehensible client error before the
  database constraint is reached.

`Counteragent` owns the collection using the repository's established
`@OneToMany(cascade = ALL, orphanRemoval = true)` pattern. The service
reconciles request IDs against children owned by that counteragent, updates
known children, creates ID-less children, and removes omitted children.
Unknown IDs and IDs belonging to another counteragent are rejected.

The application currently soft-deletes counteragents. A normal service delete
therefore makes the parent and its details unreachable through active
counteragent queries but does not physically remove either record. The FK
cascade applies when a counteragent is physically purged. Update-time child
removal is physical through orphan removal. Tests must distinguish these
behaviors instead of claiming that a soft delete fires an SQL cascade.

## Migration and historical partial data

The Flyway migration creates one child for every counteragent where at least
one legacy bank field is non-blank:

- `is_primary = true`;
- `display_order = 0`;
- each available legacy value is trimmed and preserved;
- a missing component is stored as an empty string so the required database
  columns do not cause historical data loss.

Counteragents with three blank or null legacy fields receive no child.

The migration is idempotent at the data-selection level: it does not create a
second migrated child for a counteragent that already has bank-detail rows.
A migration contract test verifies the DDL and backfill predicates. A
PostgreSQL migration test or dry-run verifies complete, partial, and empty
legacy examples, including preservation and primary/order values.

Legacy columns remain for the compatibility period and are synchronized by the
counteragent service. They are removed only in a separate, explicitly approved
cleanup release.

## API contracts

Use separate request and response shapes:

```text
CounteragentBankDetailRequest
  id?: UUID
  bankName: string
  bankAccount: string
  mfo: string
  isPrimary: boolean

CounteragentBankDetailDto
  id: UUID
  bankName: string
  bankAccount: string
  mfo: string
  isPrimary: boolean
  displayOrder: int
```

`CounteragentRequest` gains optional `bankDetails` and temporarily retains
deprecated `bankName`, `bankAccount`, and `mfo`. Optionality is significant:
an omitted array invokes legacy compatibility; a present empty array means
"delete all bank details."

`CounteragentDto` always returns ordered `bankDetails` and temporarily retains
deprecated legacy fields as an effective-primary mirror.

### Normalization and precedence

For create and update:

1. Trim every component. Empty legacy strings normalize to null.
2. Reject more than ten new details.
3. Reject blank components in new-array records.
4. Reject more than one explicit primary.
5. Treat request-array position as authoritative and assign
   `displayOrder = index`; clients do not set the stored order directly.
6. If `bankDetails` is absent, convert a non-empty legacy triplet into one
   primary detail. During the compatibility window, a partial legacy triplet
   is preserved using empty strings and logged for remediation rather than
   discarded.
7. If `bankDetails` and any legacy field are both present, select the array's
   comparison detail as follows:
   - the single explicit primary, if present;
   - otherwise the first array element;
   - no comparison detail for an empty array.
8. Compare the normalized legacy triplet with that comparison detail. Reject
   any mismatch, including a non-empty legacy triplet paired with an empty
   array, as an ambiguous request.
9. If both representations agree, the array remains the source of truth.

On every successful write, legacy columns mirror the effective primary so old
readers observe current data. The response follows the same selection rule:
explicit primary, otherwise first by `displayOrder` then `id`, otherwise null
legacy fields.

Legacy request use and partial legacy triplets are logged with endpoint and
consumer-identifying request metadata where available. Logs cannot prove
whether a client reads a particular response property, so removal also
requires an owner/consumer inventory and endpoint traffic review.

## Backend service boundaries

Keep responsibilities explicit:

- `CounteragentService` validates counteragent-level rules and delegates child
  reconciliation.
- A focused bank-detail reconciliation method or collaborator validates IDs,
  normalizes values, assigns order, and mutates the owned collection.
- DTO mapping returns an immutable ordered list and computes legacy mirrors
  from the same effective-primary helper.
- The database partial index is the final invariant guard.

Error responses must distinguish:

- more than ten details;
- incomplete new-array detail;
- multiple primary details;
- unknown or foreign child ID;
- conflicting array and legacy representations.

## Frontend form model

Add a frontend `CounteragentBankDetail` type containing optional `id`, the
three editable strings, `isPrimary`, and response `displayOrder` where present.
`CounteragentForm` contains `bankDetails` and no scalar bank fields.

The form validation result is addressable:

```ts
type CounteragentFormValidationError =
  | { code: "name" }
  | { code: "inn" }
  | { code: "bankDetailIncomplete"; index: number };
```

Payload normalization:

- removes rows where all three trimmed values are empty;
- trims every retained value;
- preserves IDs and array order;
- if filtering removed the primary empty row, marks the first retained row
  primary;
- sends only `bankDetails`, never deprecated fields.

Hydration maps the ordered response list into editable rows without losing IDs
or primary state.

## Bank-details editor

Extract a focused `CounteragentBankDetailsEditor` from the counteragent dialog.
It renders the project's established bordered repeatable-card pattern:

- empty-state guidance plus an add button;
- three inputs per row;
- a radio-style "Primary" control;
- a delete control;
- a row-local validation message.

Behavior:

- the first added row is primary;
- selecting a primary clears the flag on all other rows atomically;
- deleting the primary assigns primary to the first remaining started or
  completed row;
- zero rows is valid;
- adding is disabled at ten rows and a localized limit explanation is shown;
- drag-and-drop reordering is not part of this task.

Historical incomplete rows block saving until completed or deleted. Their
row-local message explicitly says: "Complete all bank-detail fields or delete
this row to save." This is intentional remediation behavior, not a generic
form failure.

## Read-only and print views

The counteragent details dialog:

- shows one "Bank details — not specified" row for an empty list;
- otherwise shows every ordered bank detail as bank/account/MFO;
- labels an explicit primary.

The counteragent acceptance act:

- selects the explicit primary, otherwise the first ordered detail;
- renders bank/account/MFO as one localized field;
- renders `—` when there are no details.

A repository search found no other frontend print-page references to the
legacy scalar fields. The implementation repeats this search before completion
and applies the same effective-primary rule to any new occurrences.

## Localization

Every new user-facing string must be added with matching keys to:

- `src/i18n/locales/uz.json`;
- `src/i18n/locales/ru.json`;
- `src/i18n/locales/en.json`.

This includes at least:

- section title and empty-state guidance;
- "Add bank details";
- "Primary";
- delete accessibility text where a new key is needed;
- the ten-record limit;
- the row-local incomplete/removal instruction;
- read-only empty and primary labels;
- acceptance-act bank-details label.

`yarn i18n:check` is a required merge gate.

## Testing

Backend unit tests cover:

- create/update with zero, one, and multiple details;
- deterministic order assignment;
- ID-based create/update/delete reconciliation;
- rejection of foreign/unknown IDs;
- rejection of incomplete array records;
- rejection above ten records;
- rejection of multiple primaries;
- matching and conflicting dual representations;
- legacy-only complete and partial requests;
- legacy response mirror selection;
- soft-delete behavior and physical FK cascade as distinct cases.

Migration tests cover:

- table, FK cascade, indexes, and constraints;
- complete legacy triplet backfill;
- partial legacy triplet preservation;
- empty legacy rows not being created;
- no duplicate backfill.

Frontend model tests cover hydration, trimming, empty-row filtering, primary
repair after filtering, and indexed incomplete-row validation.

Component tests cover add, delete, primary switching, automatic primary
replacement, row-local remediation text, empty state, and the ten-row limit.
A print-selection helper test covers explicit primary, ordered fallback, and
empty data.

Required verification:

```text
Backend: targeted CounteragentService and migration tests, then the relevant
project test suite.

Frontend: targeted Vitest files, yarn lint, yarn test, yarn i18n:check, and
yarn build.
```

## Rollout and cleanup

1. Deploy the migration and dual-contract backend.
2. Deploy the array-only frontend.
3. Observe legacy request warnings and inventory endpoint consumers for at
   least one release cycle.
4. Obtain explicit API-owner approval for removal.
5. In a separate change, remove deprecated request/response fields, service
   mirroring, legacy columns, and compatibility logging.

The cleanup is not part of this implementation.
