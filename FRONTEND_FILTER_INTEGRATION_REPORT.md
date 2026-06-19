# Frontend Filter Integration Report

Date: 2026-06-19

Backend branch: `Codex_org`

## Scope

Backend filtering was added for:

- `GET /api/v1/hr/employees`
- `GET /api/v1/hr/employees/stats`
- `GET /api/v1/repair-requests`
- `GET /api/v1/repair-requests/stats`
- `GET /api/v1/users`

No database migration is required. Existing query params remain supported. Frontend can add filters incrementally by appending query params.

## Shared Rules

- Text filters are case-insensitive `contains`.
- Blank string values are ignored by backend filter logic.
- UUID, enum, and boolean filters are exact match.
- Date/time ranges are inclusive.
- Stats endpoints accept the same filters as their list endpoint, except `page` and `size`.
- Technical/internal fields such as `passwordHash`, `isDeleted`, and hidden persistence fields are not exposed as filter params.

## HR Employees

Endpoint: `GET /api/v1/hr/employees`

Stats: `GET /api/v1/hr/employees/stats`

Pagination: existing behavior is unchanged. Keep current frontend usage.

| Param | Type | Behavior |
| --- | --- | --- |
| `search` | string | Global search across employee fields plus department, brigade, specialisation, and work role display names |
| `activeOnly` | boolean | Exact active flag |
| `departmentId` | UUID | Exact department, still PBAC-scoped by backend |
| `brigadeId` | UUID | Exact brigade |
| `workRoleCode` | string | Exact work role code, case-insensitive |
| `personnelNumber` | string | Contains |
| `firstName` | string | Contains |
| `lastName` | string | Contains |
| `middleName` | string | Contains |
| `position` | string | Contains |
| `userId` | UUID | Exact linked user |
| `specialisationId` | UUID | Exact specialisation |
| `hireDateFrom` | `YYYY-MM-DD` | `hireDate >= value` |
| `hireDateTo` | `YYYY-MM-DD` | `hireDate <= value` |
| `terminatedDateFrom` | `YYYY-MM-DD` | `terminatedDate >= value` |
| `terminatedDateTo` | `YYYY-MM-DD` | `terminatedDate <= value` |
| `grade` | string | Contains |
| `phone` | string | Contains |
| `email` | string | Contains |

Example:

```http
GET /api/v1/hr/employees?search=mechanic&departmentId=...&specialisationId=...&hireDateFrom=2026-06-01&hireDateTo=2026-06-19&activeOnly=true
```

## Repair Requests

Endpoint: `GET /api/v1/repair-requests`

Stats: `GET /api/v1/repair-requests/stats`

Pagination: `page` is 0-based; `size` is unchanged.

| Param | Type | Behavior |
| --- | --- | --- |
| `status` | enum | Exact |
| `departmentId` | UUID | Exact, still PBAC-scoped by backend |
| `equipmentId` | UUID | Exact |
| `priority` | enum | Exact |
| `search` | string | Global search across request fields plus equipment, department, location, reporter, and assignee display fields |
| `number` | string | Contains |
| `title` | string | Contains |
| `description` | string | Contains |
| `templateId` | UUID | Exact |
| `locationId` | UUID | Exact |
| `reporterId` | UUID | Exact |
| `assignedToId` | UUID | Exact |
| `criticality` | enum | Exact |
| `source` | enum | Exact |
| `detectedAtFrom` | ISO instant | `detectedAt >= value` |
| `detectedAtTo` | ISO instant | `detectedAt <= value` |
| `targetCompletionAtFrom` | ISO instant | `targetCompletionAt >= value` |
| `targetCompletionAtTo` | ISO instant | `targetCompletionAt <= value` |
| `actualCompletionAtFrom` | ISO instant | `actualCompletionAt >= value` |
| `actualCompletionAtTo` | ISO instant | `actualCompletionAt <= value` |
| `reactedAtFrom` | ISO instant | `reactedAt >= value` |
| `reactedAtTo` | ISO instant | `reactedAt <= value` |
| `rejectionReason` | string | Contains |
| `clarificationReason` | string | Contains |
| `closeResult` | string | Contains |
| `hasLinkedDefects` | boolean | `true` means at least one linked defect, `false` means none |
| `hasLinkedWorkOrders` | boolean | `true` means at least one linked work order, `false` means none |

Enums:

- `status`: `DRAFT`, `OPEN`, `REGISTERED`, `IN_REVIEW`, `NEEDS_CLARIFICATION`, `REJECTED`, `APPROVED`, `ASSIGNED`, `IN_PROGRESS`, `COMPLETED`, `CLOSED`, `CANCELLED`
- `priority`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`, `EMERGENCY`
- `criticality`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- `source`: `MANUAL`, `OPERATOR`, `SCADA`, `INSPECTION`, `MOBILE`

Example:

```http
GET /api/v1/repair-requests?status=OPEN&priority=HIGH&criticality=CRITICAL&hasLinkedWorkOrders=false&detectedAtFrom=2026-06-01T00:00:00Z
```

## Users

Endpoint: `GET /api/v1/users`

Pagination: `page` is 0-based; `size` is unchanged.

| Param | Type | Behavior |
| --- | --- | --- |
| `search` | string | Global search across user, department, primary role, and assigned role display fields |
| `username` | string | Contains |
| `email` | string | Contains |
| `fullName` | string | Contains |
| `position` | string | Contains |
| `phone` | string | Contains |
| `status` | enum | Exact |
| `departmentId` | UUID | Exact |
| `primaryRoleId` | UUID | Exact |
| `primaryRoleCode` | string | Exact, case-insensitive |
| `roleId` | UUID | Exact assigned role |
| `roleCode` | string | Exact assigned role code, case-insensitive |
| `lastLoginFrom` | ISO instant | `lastLoginAt >= value` |
| `lastLoginTo` | ISO instant | `lastLoginAt <= value` |

Enums:

- `status`: `ACTIVE`, `INACTIVE`, `SUSPENDED`

Example:

```http
GET /api/v1/users?search=operator&status=ACTIVE&departmentId=...&roleCode=MECHANIC&lastLoginFrom=2026-06-01T00:00:00Z
```

## Frontend Wiring Notes

- Keep current list calls unchanged until UI controls are added.
- Use the same filter object to request list and stats where stats exist.
- Do not send empty strings for inactive filters if easy; backend ignores them either way.
- Use ISO instants for date-time filters, for example `2026-06-19T10:00:00Z`.
- Display enum choices exactly as backend enum values or map labels to those values.
- For users, do not send `BLOCKED`; backend statuses are `ACTIVE`, `INACTIVE`, and `SUSPENDED`.
