# PBAC Phase 4B - Backend Scope Foundation

Date/time: 2026-05-19 16:21 +05

## What Changed

- Added central `ScopeAccessService` for future data-scope checks.
- Updated `SecurityScope.isAdmin()` so PBAC scope admin includes:
  - `SYSTEM_ADMIN` primary role
  - `SYSTEM_ADMIN` authority
  - `*` authority
  - `*` permission on `AuthenticatedUser`
- Added employee identity resolution through `Employee.userId`:
  - `EmployeeRepository.findByUserIdAndIsDeletedFalse(UUID userId)`
  - `ScopeAccessService.currentEmployeeId()`
- Added conservative warehouse scope foundation:
  - `SYSTEM_ADMIN` and `*` can access warehouses.
  - non-admin users are denied by `canAccessWarehouse` until a warehouse assignment model exists.
- Added lightweight `ScopeObjectResolver` interface for future module-specific object scope resolvers.

## Available Scopes

- Current user id from `AuthenticatedUser.id`.
- Current department id from `AuthenticatedUser.departmentId`.
- Derived employee id by looking up `hr_employees.user_id`.

## Missing Scopes

- `warehouseIds`
- `brigadeId`
- `contractorId`
- direct `employeeId` in JWT/profile

## Compatibility Notes

- Existing `SYSTEM_ADMIN` behavior is preserved.
- `*` wildcard now works as a full PBAC scope bypass.
- Existing list filter behavior in `SecurityScope.enforceDepartmentScope` is preserved.
- No module endpoints were changed in this phase.
- No frontend code was changed.
- No DB migrations or DTO changes were added.

## Future Usage

Future controllers/services should use `ScopeAccessService` after RBAC permission checks:

- `assertCanAccessDepartment(departmentId)` for direct department-scoped objects.
- `assertCanAccessEmployee(employeeId)` for employee-owned objects.
- `assertCanAccessAssignedUser(userId)` for assigned-user workflows.
- `assertCanAccessWarehouse(warehouseId)` only after the warehouse assignment model is confirmed or implemented.

Next recommended phase: PBAC Phase 4C, department-scoped list/detail/mutation enforcement for Equipment, PPR, Repair Request, Work Order, Defect/Defect List, Inspection, and Employee.
