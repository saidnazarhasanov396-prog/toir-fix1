# TOIR Backend (Java / Spring Boot)

Monolithic backend for ТОиР Navoiyazot — equipment maintenance & repair management system.

## Stack
- Java 21, Spring Boot 3.3.5, Maven
- Spring Data JPA (Hibernate) + PostgreSQL
- Spring Security + JWT (jjwt 0.12)
- springdoc-openapi (Swagger UI)

## Architecture
Package-by-feature. Each feature package contains its own `controller`, `service`, `repository`, entity and `dto/` sub-package. Shared concerns (JWT, exception handling, base entity, request context, audit) live under `com.toir.common`.

## Running locally

```bash
cp .env.example .env
# Edit .env with real values, then:
./mvnw spring-boot:run
```

Or with Docker Compose (PostgreSQL + backend):

```bash
docker compose up --build
```

API base URL: `http://localhost:8080/api`
Swagger UI: `http://localhost:8080/api/swagger-ui.html`

## Default credentials
On first run `DataBootstrap` seeds system roles and a default admin:
- username: `admin`
- password: `admin`

**Change the password immediately in production.**

## Configuration
All runtime config lives in `src/main/resources/application-prod.yml` and is overridable via environment variables (see `.env.example`).

DB schema is auto-managed by Hibernate (`ddl-auto: update`). No Flyway/Liquibase.

## Feature modules

| Package | Purpose |
|---|---|
| `auth` | Login, /me, JWT issuance |
| `user`, `role` | User accounts, RBAC roles with JSON permissions |
| `department`, `location` | Org structure & physical hierarchy |
| `equipment`, `equipmenttype`, `equipmentpassport`, `equipmentnode` | Equipment registry, industrial passport, internal nodes |
| `manufacturer`, `criticalityclass`, `serviceclass`, `uom` | Equipment dictionaries |
| `maintenanceregulation`, `maintenancetemplate` | Maintenance templates + operations + regulations with periodicity |
| `pprplanning` | PPR plans, tasks, approval, postpone with reason |
| `repairrequest`, `defect`, `defectcategory`, `defectseverity`, `failurereason`, `rootcause` | Requests, defects and their classifiers |
| `workorder`, `workexecution`, `laborentry`, `safetypermit`, `completionact`, `materialusage` | Work orders and execution flow |
| `sparepart`, `material`, `warehouse`, `stockmovement`, `reservation` | Inventory, stock, reservations, write-offs |
| `contractor`, `contractorcontract`, `contractorwork` | Contractors, contracts, accepted works |
| `budget`, `actualcost`, `costcategory` | Budgets, lines, actual costs with review |
| `downtime`, `reliability` | Downtime events, MTBF/MTTR metrics |
| `notification` | User notifications |
| `file`, `technicaldocument` | File uploads + technical passport documents |
| `auditlog` | Audit trail, wired into login |
| `dashboard` | KPI summary |

## Business rules enforced

- Cannot create a work order without equipment
- Cannot close a work order without a result
- Cannot close a repair request without closeResult
- Cannot postpone a PPR task without a reason
- Cannot write off / reserve more material than available in stock
- Cannot delete Department / Location / EquipmentNode that has children
- System roles cannot be modified or deleted
- Unique `code` / `number` validation on all entities with natural keys

## Error format
All errors return:

```json
{
  "message": "Error message",
  "path": "/api/example",
  "timestamp": "2026-01-01T12:00:00",
  "code": 400
}
```

Thrown via `com.toir.common.exception.RestException` and mapped by `GlobalExceptionHandler`.

## Security
- All endpoints require `Authorization: Bearer <token>` except: `/auth/login`, Swagger, `/actuator/health`
- JWT payload: `sub`, `username`, `authorities` (role codes), `permissions`, `departmentId`, `primaryRoleCode`
- `@CurrentUser` parameter resolver injects `AuthenticatedUser` into controllers
- `JwtAuthenticationEntryPoint` and `RestAccessDeniedHandler` return the unified error format
