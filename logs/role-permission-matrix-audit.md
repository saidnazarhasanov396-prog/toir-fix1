# TOIR Role Permission Matrix Audit

Audit date: 2026-05-19

Scope: audit only. No Java source, frontend source, migrations, role seeds, endpoint contracts, DTOs, or behavior were changed.

## 1. Executive Summary

Current status:

- RBAC permissions are string authorities. The backend source of truth is `PermissionConstants`; the frontend mirrors the same permission names in `src/lib/permissions.ts`.
- There is no separate `Permission` entity and no normalized role-permission join table. `Role.permissions` is a JSONB `List<String>` stored directly on the `roles` table.
- `AuthService.login` collects permissions from the user's `primaryRole` plus all entries in `user.roles`, writes them into the JWT `permissions` claim, and `JwtAuthenticationFilter` converts both role codes and permission strings into Spring authorities.
- `SYSTEM_ADMIN` currently receives `["*"]` from `DataBootstrap`, and PBAC helpers treat both `SYSTEM_ADMIN` and `"*"` as full scope bypass.
- Current role bootstrapping only creates granular access for `SYSTEM_ADMIN`. All other built-in roles receive legacy `["read"]`, so the current seed state is not aligned with the new RBAC guards.

Biggest seed gaps:

- Most backend/frontend permission constants are not assigned to non-admin roles by source-controlled bootstrap or Flyway migration.
- Required business roles such as `ADMIN`, `MAINTENANCE_ENGINEER`, `MAINTENANCE_MANAGER`, `FINANCE_MANAGER`, `HR_MANAGER`, and `INSPECTOR` are not currently seeded.
- Existing built-in roles like `PPR_ENGINEER`, `STOREKEEPER`, `SUPPLY_SPECIALIST`, `ECONOMIST`, `FOREMAN`, and `VIEWER` are present but only get legacy `read` unless production data was manually edited.

Main production risks:

- Non-admin seeded users will lose most guarded backend endpoints and most guarded frontend routes after RBAC is enforced, because `read` does not satisfy guards such as `PPR_PLAN_READ`, `ANALYTICS_READ`, or `WAREHOUSE_READ`.
- UI action buttons and sidebars depend on granular frontend permissions, so missing seeds will make modules disappear even when PBAC would otherwise allow scoped access.
- A few admin-only annotations use `SYSTEM_ADMIN` role checks directly; wildcard is broadly supported elsewhere, but `@RequiresAdmin` should be reviewed in a later phase for `"*"` compatibility.

## 2. Current Permission Model

Backend constants:

- `src/main/java/com/toir/security/PermissionConstants.java` defines `WILDCARD`, `READ_LEGACY`, and all granular module permissions.
- Controllers mainly use `@PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('<PERMISSION>')")`.
- `SecurityAccessService.hasPermission` also treats `SYSTEM_ADMIN` and `"*"` as bypass authorities.
- PBAC helpers `SecurityScope` and `ScopeAccessService` treat `SYSTEM_ADMIN` primary role, `SYSTEM_ADMIN` authority, wildcard authority, and wildcard principal permission as scope-admin access.

Frontend constants:

- `toir-front/src/lib/permissions.ts` mirrors every backend permission constant.
- Frontend-only extras are `SYSTEM_ADMIN`, `LEGACY_BUDGETS_APPROVE = "budgets:approve"`, and `LEGACY_NOTIFICATIONS_UPDATE = "notifications:update"`.
- `access-control.ts`, `route-access.ts`, `PermissionGate`, and sidebar nav helpers use permissions from the authenticated profile. `SYSTEM_ADMIN` primary role and `"*"` permission bypass frontend permission checks.

JWT flow:

- `User.primaryRole` is a `ManyToOne` to `Role`.
- `User.roles` is a `ManyToMany` through `user_roles`.
- `AuthService.login` initializes both role relationships, unions permissions from all roles plus the primary role, and passes role codes as JWT `authorities`.
- JWT extra claims include `email`, `fullName`, `departmentId`, `primaryRoleCode`, and `permissions`.
- `JwtAuthenticationFilter` reads JWT `authorities`, adds `primaryRoleCode`, adds all `permissions`, and builds `SimpleGrantedAuthority` entries.

SYSTEM_ADMIN / `"*"` behavior:

- `DataBootstrap` creates `SYSTEM_ADMIN` with `permissions = ["*"]`.
- Backend controller guards generally permit either `SYSTEM_ADMIN` or `"*"`.
- PBAC scope bypass is preserved for both `SYSTEM_ADMIN` and `"*"`.
- Frontend `can()` returns true for `primaryRoleCode === SYSTEM_ADMIN`, wildcard permission, or the requested permission.

## 3. Permission Inventory

| Module | Permissions | Backend used | Frontend used | Seeded? | Notes |
|---|---|---:|---:|---|---|
| System/Admin | `USER_READ`, `USER_CREATE`, `USER_UPDATE`, `USER_DELETE`, `ROLE_READ`, `ROLE_CREATE`, `ROLE_UPDATE`, `ROLE_DELETE`, `AUDIT_LOG_READ` | Yes | Yes | No non-admin seed | Needed for users/roles/audit routes and APIs. |
| PPR | `PPR_PLAN_READ`, `PPR_PLAN_CREATE`, `PPR_PLAN_UPDATE`, `PPR_PLAN_DELETE`, `PPR_PLAN_APPROVE`, `PPR_PLAN_GENERATE`, `PPR_TASK_READ`, `PPR_TASK_CREATE`, `PPR_TASK_UPDATE`, `PPR_TASK_APPROVE`, `PPR_TASK_START`, `PPR_TASK_COMPLETE`, `PPR_TASK_CANCEL`, `PPR_TASK_POSTPONE` | Yes, except `PPR_TASK_UPDATE` not found in backend guards | Yes | No non-admin seed | `PPR_TASK_UPDATE` is frontend/constant-ready but currently not seen in backend guard usage. |
| Repair | `REPAIR_REQUEST_READ`, `REPAIR_REQUEST_CREATE`, `REPAIR_REQUEST_UPDATE`, `REPAIR_REQUEST_APPROVE`, `REPAIR_REQUEST_ASSIGN`, `REPAIR_REQUEST_REJECT`, `REPAIR_REQUEST_CLOSE` | Yes | Yes | No non-admin seed | PBAC still controls scoped data access. |
| Work Order | `WORK_ORDER_READ`, `WORK_ORDER_CREATE`, `WORK_ORDER_APPROVE`, `WORK_ORDER_START`, `WORK_ORDER_COMPLETE`, `WORK_ORDER_CLOSE` | Yes | Yes | No non-admin seed | Current sensitive access annotation also blocks `VIEWER`/`CONTRACTOR`. |
| Defects | `DEFECT_READ`, `DEFECT_CREATE`, `DEFECT_UPDATE`, `DEFECT_RESOLVE`, `DEFECT_DELETE`, `DEFECT_LIST_READ`, `DEFECT_LIST_CREATE`, `DEFECT_LIST_UPDATE`, `DEFECT_LIST_APPROVE`, `DEFECT_LIST_CLOSE`, `DEFECT_LIST_DELETE` | Yes | Yes | No non-admin seed | Category-style dictionaries use `CATEGORY_*`. |
| Warehouse/Stock | `WAREHOUSE_READ`, `WAREHOUSE_CREATE`, `WAREHOUSE_UPDATE`, `WAREHOUSE_DELETE`, `WAREHOUSE_EQUIPMENT_READ`, `WAREHOUSE_EQUIPMENT_ASSIGN`, `WAREHOUSE_EQUIPMENT_STATUS_UPDATE`, `STOCK_READ`, `STOCK_RECEIVE`, `STOCK_ISSUE`, `STOCK_MOVE`, `STOCK_ADJUST`, `MATERIAL_USAGE_READ`, `MATERIAL_USAGE_ISSUE`, `SPARE_PART_READ`, `SPARE_PART_CREATE`, `SPARE_PART_UPDATE`, `SPARE_PART_DELETE` | Yes, except `WAREHOUSE_EQUIPMENT_ASSIGN` not found in backend guards | Yes | No non-admin seed | Assignment permission is frontend/constant-ready but not currently enforced by backend guards found in audit. |
| Procurement | `PROCUREMENT_READ`, `PROCUREMENT_CREATE`, `PROCUREMENT_SUBMIT`, `PROCUREMENT_APPROVE`, `PROCUREMENT_REJECT`, `PROCUREMENT_ORDER`, `PROCUREMENT_RECEIVE`, `PROCUREMENT_CANCEL` | Yes | Yes | No non-admin seed | Supply role should receive only confirmed workflow permissions. |
| Finance | `ACTUAL_COST_READ`, `ACTUAL_COST_CREATE`, `ACTUAL_COST_APPROVE`, `ACTUAL_COST_REJECT`, `BUDGET_READ`, `BUDGET_CREATE`, `BUDGET_UPDATE`, `BUDGET_APPROVE`, `FINANCE_ROUTE_OVERRIDE_READ`, `FINANCE_ROUTE_OVERRIDE_APPLY`, `FINANCE_ROUTE_OVERRIDE_CLEAR` | Yes | Yes | No non-admin seed | Route override permissions are high-risk and should be limited. |
| Approvals | `APPROVAL_READ`, `APPROVAL_CREATE`, `APPROVAL_APPROVE`, `APPROVAL_REJECT`, `APPROVAL_CANCEL`, `APPROVAL_UPDATE` | Yes, except `APPROVAL_UPDATE` not found in backend guards | Yes, except `APPROVAL_UPDATE` not found in frontend usage | No non-admin seed | `APPROVAL_UPDATE` exists but appears unused. |
| Inspection | `INSPECTION_READ`, `INSPECTION_CREATE`, `INSPECTION_UPDATE`, `INSPECTION_DELETE`, `INSPECTION_START`, `INSPECTION_COMPLETE` | Yes | Yes | No non-admin seed | Current controller uses `INSPECTION_UPDATE` for some non-terminal actions too. |
| Knowledge | `KNOWLEDGE_READ`, `KNOWLEDGE_CREATE`, `KNOWLEDGE_UPDATE`, `KNOWLEDGE_DELETE` | Yes | Yes | No non-admin seed | Phase 4J kept global read unless linked object scope applies. |
| Analytics/Reports | `ANALYTICS_READ`, `ANALYTICS_EXPORT` | Yes | Yes | No non-admin seed | Dashboard/report/export routes depend on these permissions plus PBAC scoping. |
| Equipment | `EQUIPMENT_READ`, `EQUIPMENT_CREATE`, `EQUIPMENT_UPDATE`, `EQUIPMENT_DELETE`, `EQUIPMENT_TRANSFER` | Yes | Yes | No non-admin seed | PBAC controls department/equipment visibility. |
| Reference Data | `DEPARTMENT_READ`, `DEPARTMENT_CREATE`, `DEPARTMENT_UPDATE`, `DEPARTMENT_DELETE`, `BRIGADE_READ`, `BRIGADE_CREATE`, `BRIGADE_UPDATE`, `BRIGADE_DELETE`, `LOCATION_READ`, `LOCATION_CREATE`, `LOCATION_UPDATE`, `LOCATION_DELETE`, `EQUIPMENT_TYPE_READ`, `EQUIPMENT_TYPE_CREATE`, `EQUIPMENT_TYPE_UPDATE`, `EQUIPMENT_TYPE_DELETE`, `CATEGORY_READ`, `CATEGORY_CREATE`, `CATEGORY_UPDATE`, `CATEGORY_DELETE` | Yes | Yes | No non-admin seed | Some reference controllers also have sensitive access guard. |
| HR | `EMPLOYEE_READ`, `EMPLOYEE_CREATE`, `EMPLOYEE_UPDATE`, `EMPLOYEE_DELETE`, `TIMESHEET_READ`, `TIMESHEET_CREATE`, `TIMESHEET_UPDATE`, `TIMESHEET_APPROVE`, `TIMESHEET_DELETE` | Yes | Yes | No non-admin seed | Department PBAC still applies. |
| Notifications | `NOTIFICATION_READ`, `NOTIFICATION_MARK_READ`, `NOTIFICATION_ADMIN`, `NOTIFICATION_UPDATE` | Not found in backend guards during audit | Not found in frontend usage except legacy raw strings | No non-admin seed | Frontend notifications page still checks legacy `notifications:update` and `budgets:approve`. |
| Integrations | `INTEGRATION_READ`, `INTEGRATION_CREATE`, `INTEGRATION_UPDATE`, `INTEGRATION_DELETE`, `INTEGRATION_TEST`, `INTEGRATION_SYNC` | Not found in backend guards during audit | Constants only | No non-admin seed | Integration endpoints currently appear admin-annotation based, not granular-permission based. |

Backend/frontend mismatches:

- Backend constants all exist in the frontend permission catalog.
- Frontend adds `SYSTEM_ADMIN` as a role constant, not a backend permission constant.
- Frontend adds legacy raw permission constants `budgets:approve` and `notifications:update`; these do not exist in backend `PermissionConstants`.

Permissions used in backend guards but not in constants:

- `SYSTEM_ADMIN`, `VIEWER`, and `CONTRACTOR` are role codes used in guard expressions/annotations, not permission constants.

Constants not found in backend guard usage during audit:

- `APPROVAL_UPDATE`
- `INTEGRATION_CREATE`
- `INTEGRATION_DELETE`
- `INTEGRATION_READ`
- `INTEGRATION_TEST`
- `INTEGRATION_UPDATE`
- `NOTIFICATION_ADMIN`
- `NOTIFICATION_MARK_READ`
- `NOTIFICATION_READ`
- `NOTIFICATION_UPDATE`
- `PPR_TASK_UPDATE`
- `WAREHOUSE_EQUIPMENT_ASSIGN`

Constants not found in frontend usage outside `permissions.ts` during audit:

- `APPROVAL_UPDATE`
- `INTEGRATION_CREATE`
- `INTEGRATION_DELETE`
- `INTEGRATION_READ`
- `INTEGRATION_SYNC`
- `INTEGRATION_TEST`
- `INTEGRATION_UPDATE`
- `NOTIFICATION_ADMIN`
- `NOTIFICATION_MARK_READ`
- `NOTIFICATION_READ`
- `NOTIFICATION_UPDATE`

## 4. Current Roles Found

| Role | Current permissions | Problems |
|---|---|---|
| `SYSTEM_ADMIN` | `["*"]` | Correct for full access. Keep as wildcard. |
| `TECHNICAL_DIRECTOR` | `["read"]` | Legacy only; cannot satisfy granular guards. |
| `CHIEF_MECHANIC` | `["read"]` | Legacy only; cannot satisfy granular guards. |
| `CHIEF_POWER_ENGINEER` | `["read"]` | Legacy only; cannot satisfy granular guards. |
| `CHIEF_INSTRUMENT_ENGINEER` | `["read"]` | Legacy only; cannot satisfy granular guards. |
| `WORKSHOP_HEAD` | `["read"]` | Legacy only; cannot satisfy granular guards. |
| `SECTION_HEAD` | `["read"]` | Legacy only; cannot satisfy granular guards. |
| `FOREMAN` | `["read"]` | Legacy only; should map to maintenance approval/execution permissions if business confirms. |
| `RELIABILITY_ENGINEER` | `["read"]` | Legacy only; likely needs equipment, analytics, defects, and knowledge read/create permissions. |
| `PPR_ENGINEER` | `["read"]` | Legacy only; should map to PPR plan/task workflow permissions. |
| `STOREKEEPER` | `["read"]` | Legacy only; should map to warehouse/stock/spare-part/material usage permissions. |
| `SUPPLY_SPECIALIST` | `["read"]` | Legacy only; should map to procurement workflow permissions. |
| `ECONOMIST` | `["read"]` | Legacy only; should map to actual cost, budget, analytics, and possibly route override permissions. |
| `CONTRACTOR` | `["read"]` | Legacy only; risky to grant broadly without contractor-user PBAC mapping. |
| `VIEWER` | `["read"]` | Legacy only; should become explicit read-only matrix if product wants viewer access. |

Roles requested for future matrix but not currently seeded:

- `ADMIN`
- `MAINTENANCE_ENGINEER`
- `MAINTENANCE_MANAGER`
- `FINANCE_MANAGER`
- `HR_MANAGER`
- `INSPECTOR`

## 5. Proposed Role Matrix

| Role | Proposed permissions | PBAC scope | Business questions |
|---|---|---|---|
| `SYSTEM_ADMIN` | `*` only | Full bypass. | None; do not replace wildcard with a long list unless the platform design changes. |
| `ADMIN` | `USER_*`, `ROLE_*`, `AUDIT_LOG_READ`, broad operational read, reference data manage as confirmed | Usually global administrative access, but not necessarily PBAC bypass unless explicitly granted `*` or `SYSTEM_ADMIN`. | Is `ADMIN` a non-system tenant/admin role or equivalent to system admin without seed/bootstrap control? |
| `PPR_ENGINEER` | `PPR_PLAN_READ`, `PPR_PLAN_CREATE`, `PPR_PLAN_UPDATE`, `PPR_PLAN_GENERATE`, `PPR_TASK_READ`, `PPR_TASK_CREATE`, `PPR_TASK_UPDATE`, `PPR_TASK_START`, `PPR_TASK_COMPLETE`, `PPR_TASK_POSTPONE`, `EQUIPMENT_READ`, `WORK_ORDER_READ`, `REPAIR_REQUEST_READ`, `KNOWLEDGE_READ` | Department-scoped through PBAC. | Can PPR engineers approve plans/tasks or only prepare and execute? |
| `MAINTENANCE_ENGINEER` | `EQUIPMENT_READ`, `REPAIR_REQUEST_READ`, `REPAIR_REQUEST_UPDATE`, `WORK_ORDER_READ`, `WORK_ORDER_START`, `WORK_ORDER_COMPLETE`, `DEFECT_READ`, `DEFECT_CREATE`, `DEFECT_UPDATE`, `PPR_TASK_READ`, `PPR_TASK_START`, `PPR_TASK_COMPLETE`, `KNOWLEDGE_READ`, `KNOWLEDGE_CREATE` | Department/equipment/work-order scoped. | Should maintenance engineers create work orders or only execute assigned ones? |
| `FOREMAN` | `REPAIR_REQUEST_READ`, `REPAIR_REQUEST_APPROVE`, `REPAIR_REQUEST_ASSIGN`, `REPAIR_REQUEST_REJECT`, `REPAIR_REQUEST_CLOSE`, `WORK_ORDER_READ`, `WORK_ORDER_APPROVE`, `WORK_ORDER_START`, `WORK_ORDER_COMPLETE`, `WORK_ORDER_CLOSE`, `DEFECT_READ`, `DEFECT_LIST_READ`, `DEFECT_LIST_APPROVE`, `DEFECT_LIST_CLOSE`, `EMPLOYEE_READ`, `TIMESHEET_READ`, `TIMESHEET_APPROVE` | Department/brigade scoped where PBAC supports it. | Should scope be whole department or assigned brigade only? |
| `MAINTENANCE_MANAGER` | Foreman set plus `WORK_ORDER_CREATE`, `DEFECT_LIST_CREATE`, `DEFECT_LIST_UPDATE`, `PPR_PLAN_READ`, `PPR_TASK_READ`, `ANALYTICS_READ`, `KNOWLEDGE_READ`, `KNOWLEDGE_CREATE`, `KNOWLEDGE_UPDATE` | Department-scoped; analytics scoped to department. | Should managers delete defects/defect lists, or is close/resolve sufficient? |
| `STOREKEEPER` | `WAREHOUSE_READ`, `WAREHOUSE_EQUIPMENT_READ`, `WAREHOUSE_EQUIPMENT_STATUS_UPDATE`, `STOCK_READ`, `STOCK_RECEIVE`, `STOCK_ISSUE`, `STOCK_MOVE`, `STOCK_ADJUST`, `MATERIAL_USAGE_READ`, `MATERIAL_USAGE_ISSUE`, `SPARE_PART_READ`, `SPARE_PART_CREATE`, `SPARE_PART_UPDATE` | Warehouse/department scoped through existing PBAC; no invented warehouse assignment rules. | Should stock adjust be limited to senior storekeeper/admin? Should storekeeper delete spare parts? |
| `SUPPLY_SPECIALIST` | `PROCUREMENT_READ`, `PROCUREMENT_CREATE`, `PROCUREMENT_SUBMIT`, `PROCUREMENT_ORDER`, `PROCUREMENT_RECEIVE`, `PROCUREMENT_CANCEL`, `STOCK_READ`, `WAREHOUSE_READ`, `SPARE_PART_READ` | Procurement scoped through warehouse/department PBAC. | Can supply approve/reject, or should approval stay with managers/finance? |
| `ECONOMIST` | `ACTUAL_COST_READ`, `ACTUAL_COST_CREATE`, `BUDGET_READ`, `BUDGET_CREATE`, `BUDGET_UPDATE`, `ANALYTICS_READ`, `ANALYTICS_EXPORT` | Department/finance scoped unless senior finance is global by policy. | Can economist approve/reject actual costs or budgets? |
| `FINANCE_MANAGER` | Economist set plus `ACTUAL_COST_APPROVE`, `ACTUAL_COST_REJECT`, `BUDGET_APPROVE`, optionally `FINANCE_ROUTE_OVERRIDE_READ`, `FINANCE_ROUTE_OVERRIDE_APPLY`, `FINANCE_ROUTE_OVERRIDE_CLEAR` | Finance/department scoped; route override should be senior-only. | Should route override be finance manager only, admin only, or split apply/clear? |
| `INSPECTOR` | `INSPECTION_READ`, `INSPECTION_CREATE`, `INSPECTION_UPDATE`, `INSPECTION_START`, `INSPECTION_COMPLETE`, `EQUIPMENT_READ`, `DEFECT_CREATE`, `DEFECT_READ`, `KNOWLEDGE_READ` | Inspection route/department/performer scoped through PBAC. | Can inspectors delete inspections or only complete/update them? |
| `HR_MANAGER` | `EMPLOYEE_READ`, `EMPLOYEE_CREATE`, `EMPLOYEE_UPDATE`, `EMPLOYEE_DELETE`, `TIMESHEET_READ`, `TIMESHEET_UPDATE`, `TIMESHEET_APPROVE`, `TIMESHEET_DELETE`, `BRIGADE_READ`, `BRIGADE_CREATE`, `BRIGADE_UPDATE` | Department-scoped through PBAC. | Should HR manager see all departments or only own department? |
| `VIEWER` | Read-only set: `EQUIPMENT_READ`, `REPAIR_REQUEST_READ`, `WORK_ORDER_READ`, `DEFECT_READ`, `DEFECT_LIST_READ`, `PPR_PLAN_READ`, `PPR_TASK_READ`, `WAREHOUSE_READ`, `STOCK_READ`, `SPARE_PART_READ`, `PROCUREMENT_READ`, `BUDGET_READ`, `ACTUAL_COST_READ`, `APPROVAL_READ`, `INSPECTION_READ`, `KNOWLEDGE_READ`, `ANALYTICS_READ`, reference `*_READ` as confirmed | PBAC-scoped read-only unless business explicitly wants global read. | Should viewer be global read-only or PBAC-scoped read-only? |
| `CONTRACTOR` | Conservative option: no broad grants until contractor-user mapping exists. If enabled: `WORK_ORDER_READ`, `WORK_ORDER_START`, `WORK_ORDER_COMPLETE`, maybe `MATERIAL_USAGE_READ` for assigned work only. | Needs contractor-work mapping before production access. | Should contractor access be enabled before contractor-user PBAC mapping exists? |

## 6. Seed/Migration Plan

Existing seed source:

- Roles are seeded by `DataBootstrap`, not by a Flyway role-permission migration.
- `SYSTEM_ADMIN` is seeded with `["*"]`.
- Other built-in system roles are seeded with `["read"]`.
- The default admin user is created by `DataBootstrap` when `app.bootstrap.create-default-admin=true`.
- Demo data seeders are dev-profile only and do not define the permission matrix.

Flyway/manual SQL state:

- Flyway is enabled with `locations: classpath:db/migration`.
- `spring.sql.init.mode` is `never`, so manual SQL files are not auto-executed by Spring SQL init.
- `src/main/resources/db/manual/2026-04-23_integration_endpoints_sync_flags.sql` is outside Flyway locations and should remain manual unless deliberately migrated.
- Current Flyway migrations do not seed granular permissions or role-permission links.
- No evidence of a normalized `permissions` table or `role_permissions` table was found in the audited source.

Missing permissions:

- All granular permissions are missing from source-controlled non-admin role seeds.
- Since there is no permission table, "missing permission rows" means missing strings in each role's JSONB `permissions` list.

Missing role-permission links:

- All role-to-granular-permission assignments for built-in non-admin roles are missing from source-controlled seed/bootstrap state.
- Required-but-not-seeded roles need either new role rows or a product decision not to introduce them yet.

Recommended future migration:

- Add a new versioned Flyway migration only. Do not edit applied migrations.
- Use idempotent `INSERT ... ON CONFLICT DO NOTHING` for any new role codes.
- Use idempotent JSONB merge/update logic to append missing permission strings without deleting existing permissions.
- Ensure `SYSTEM_ADMIN` keeps `"*"`.
- Do not remove `read`; leave legacy strings in place unless a later migration explicitly deprecates them.
- Do not delete existing production role permissions. Only add missing permissions needed by the approved matrix.
- Include rollback guidance as "manual removal of appended permissions only after business approval", because removing permissions can break users.

Migration checksum risk:

- Editing any existing file under `src/main/resources/db/migration` risks Flyway checksum mismatches in environments where it has already run.
- Phase 5B should create a new migration with a later version number.

## 7. Frontend Impact

Frontend route/sidebar behavior:

- Protected routes and sidebar items use granular permissions. With current non-admin `["read"]`, most business routes are hidden and direct navigation is denied by the frontend route wrapper.
- Action buttons are controlled by module access helper files such as `ppr-calendar-access.ts`, `warehouse-access.ts`, `finance-access.ts`, `knowledge-access.ts`, and similar helpers.
- `SYSTEM_ADMIN` and wildcard permission are handled consistently by frontend permission helpers.
- Some routes/nav items remain unguarded at the frontend route/sidebar layer, including notifications, master console, vehicles, maintenance regulations/templates, repair campaigns, planned shutdowns, contractors, alarm center, advisor, ops metrics, certifications, calibrations, webhooks, integration settings, meters, and settings. Backend guards still matter; frontend route openness should be reviewed separately from role seed normalization.
- Frontend notifications page directly checks legacy raw strings `notifications:update` and `budgets:approve`; this is not aligned with backend `PermissionConstants`.

| Role | Visible routes/actions after proposed seed | Hidden routes/actions | Risk |
|---|---|---|---|
| `SYSTEM_ADMIN` | Everything, through `SYSTEM_ADMIN`/`*` bypass | None expected | Keep wildcard and primary role behavior unchanged. |
| `ADMIN` | Users/roles/audit, broad operational routes, reference data per final matrix | Any module not included in final admin matrix | Needs business distinction from `SYSTEM_ADMIN`. |
| `PPR_ENGINEER` | PPR calendar, equipment read, relevant work-order/repair read actions, own scoped execution actions | Admin, finance approval, stock management, HR management | Missing `PPR_TASK_READ` or `PPR_PLAN_READ` hides PPR route entirely. |
| `MAINTENANCE_ENGINEER` | Equipment, repair requests, work orders, defects, PPR tasks, knowledge | Admin, finance, HR, procurement, warehouse mutation | Needs exact create/approve boundary. |
| `FOREMAN` | Maintenance approval/execution, defect list review, employee/timesheet read/approve if granted | System admin, finance route override, stock adjust unless granted | Brigade-vs-department scope is a business decision. |
| `MAINTENANCE_MANAGER` | Broad maintenance dashboard/actions, analytics read if granted | System configuration, route override unless granted | Risk of over-granting delete permissions. |
| `STOREKEEPER` | Warehouse/reorder, spare parts, stock operations, material usage | PPR/finance/HR/admin unless granted | `STOCK_ADJUST` is sensitive and should be confirmed. |
| `SUPPLY_SPECIALIST` | Procurement, stock/warehouse/spare-part read | Procurement approve/reject unless granted; admin/finance | Approval boundaries need business decision. |
| `ECONOMIST` | Budgets, financial review, analytics/export if granted | Maintenance mutation, HR/admin, finance route override unless granted | Export can expose scoped finance data; PBAC remains required. |
| `FINANCE_MANAGER` | Finance approval, budgets, actual costs, route override if approved | Maintenance/warehouse/admin unless granted | Route override is high-risk. |
| `INSPECTOR` | Inspection/mobile inspection, equipment read, defect create/read | Admin, finance, warehouse, most maintenance approval | Delete inspection should be withheld unless confirmed. |
| `HR_MANAGER` | Employees, timesheet, brigades if granted | Finance, stock, maintenance approval unless granted | Department scope policy needs confirmation. |
| `VIEWER` | Read-only routes included in final read matrix | All mutation actions | Decide global-vs-PBAC-scoped viewer. |
| `CONTRACTOR` | None by default, or assigned work-order route only after mapping exists | Broad department modules | Do not grant broad read before contractor PBAC model exists. |

## 8. Questions for PM/Business Owner

- Should `STOREKEEPER` adjust stock, or only receive/issue/move?
- Should `ECONOMIST` see all departments or only own department?
- Should `FOREMAN` manage all work orders in the department or only assigned brigade work?
- Should `CONTRACTOR` access be enabled before contractor-user mapping exists?
- Should `VIEWER` be global read-only or PBAC-scoped read-only?
- Should `ADMIN` exist as a non-wildcard role distinct from `SYSTEM_ADMIN`?
- Should `FINANCE_ROUTE_OVERRIDE_APPLY` and `FINANCE_ROUTE_OVERRIDE_CLEAR` be restricted to `FINANCE_MANAGER` and `SYSTEM_ADMIN` only?
- Should `PPR_ENGINEER` approve PPR plans/tasks, or should approval belong to a manager role?
- Should `WAREHOUSE_EQUIPMENT_ASSIGN`, `PPR_TASK_UPDATE`, and `APPROVAL_UPDATE` be wired to backend guards or removed from the future matrix after product confirmation?
- Should legacy frontend permissions `budgets:approve` and `notifications:update` be migrated to `BUDGET_APPROVE` and `NOTIFICATION_UPDATE`?

## 9. Recommended Next Phase

- Phase 5B: Role permission seed migration.
- Phase 5C: Role matrix tests for login JWT permissions, route visibility, and backend RBAC access per role.
- Phase 5D: Production smoke test checklist covering admin login, non-admin seeded users, scoped data access, exports, and no regression to `SYSTEM_ADMIN`/`"*"` bypass.

Phase 5B implementation constraints:

- Add only a new migration.
- Keep `SYSTEM_ADMIN` with `"*"`.
- Add missing permission strings idempotently.
- Do not remove existing permission strings.
- Do not edit existing migrations or source bootstrap behavior until migration behavior is agreed.

## 10. Commands Run

- `git status --short` in `toir-backend`
- `rg --files src/main/java src/test/java src/main/resources | rg 'Role|Permission|Auth|Jwt|SecurityScope|ScopeAccess|User|migration|sql$'`
- `rg --files . | rg 'permissions|permission|routes|sidebar|guard|auth|menu|navigation|rbac|access'` in `toir-front`
- `sed -n '1,220p' src/main/java/com/toir/entity/users/Role.java`
- `sed -n '1,260p' src/main/java/com/toir/entity/users/User.java`
- `sed -n '1,260p' src/main/java/com/toir/security/PermissionConstants.java`
- `sed -n '1,260p' src/main/java/com/toir/service/AuthService.java`
- `sed -n '1,260p' src/main/java/com/toir/security/JwtService.java`
- `sed -n '1,260p' src/main/java/com/toir/security/JwtAuthenticationFilter.java`
- `sed -n '1,260p' src/main/java/com/toir/security/SecurityScope.java`
- `sed -n '1,260p' src/main/java/com/toir/security/ScopeAccessService.java`
- `sed -n '1,260p' src/main/java/com/toir/service/users/RoleService.java`
- `sed -n '1,220p' src/main/java/com/toir/config/DataBootstrap.java`
- `find . -path './target' -prune -o -name '*.sql' -print`
- `rg -n 'ddl-auto|flyway|bootstrap|seed-demo-data|create-default-admin|spring\\.sql|data\\.sql|schema\\.sql' src/main/resources -S`
- `sed -n '1,160p' src/test/java/com/toir/service/AuthServiceTest.java`
- `sed -n '1,150p' src/test/java/com/toir/security/JwtAuthenticationFilterAuthorityMappingTest.java`
- `sed -n '1,140p' src/test/java/com/toir/security/SecurityScopeAdminBypassTest.java`
- `sed -n '1,260p' src/lib/permissions.ts` in `toir-front`
- `sed -n '1,260p' src/lib/access-control.ts && sed -n '1,240p' src/lib/route-access.ts` in `toir-front`
- `sed -n '1,300p' src/components/layout/nav-access.ts && sed -n '1,240p' src/components/auth/permission-gate.tsx` in `toir-front`
- `rg -n 'hasPermission|hasAnyPermission|hasAllPermissions|canAccess|requirePermission|Permission|SYSTEM_ADMIN|\\*|permissions\\.|PermissionCode|permission:' src -S` in `toir-front`
- Python comparison of backend and frontend permission constants
- Python scan for constants not found outside `PermissionConstants`
- Python scan for backend `hasAuthority(...)` values not present in permission constants
- Python scan for frontend constants not found outside `permissions.ts`
- Python scan for backend guard permission usage
- Python scan for frontend permission usage
- `rg -n '@PreAuthorize|RequiresAdmin|RequiresSensitiveAccess' src/main/java/com/toir/controller src/main/java/com/toir/security -S`
- `sed -n '60,390p' src/components/layout/app-shell.tsx` in `toir-front`
