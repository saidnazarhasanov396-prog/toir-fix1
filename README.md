# TOIR Backend (Java / Spring Boot)

Monolithic backend for ТОиР Navoiyazot — equipment maintenance & repair management system.

## Stack
- Java 21, Spring Boot 3.3.5, Maven
- Spring Data JPA (Hibernate) + PostgreSQL
- Spring Security + JWT (jjwt 0.12)
- springdoc-openapi (Swagger UI)

## Architecture
Layered Spring Boot application under `com.toir`:
- `controller`: REST endpoints
- `service`: business logic and orchestration
- `repository`: Spring Data JPA repositories
- `entity`: JPA entities
- `dto`: request/response DTOs grouped by domain
- `config`, `security`, `exception`, `util`: shared infrastructure

## Running locally

```bash
mvn spring-boot:run
```

Or with Docker Compose (PostgreSQL + backend):

```bash
docker compose up --build
```

API base URL: `http://localhost:8080/api/v1`
Swagger UI: `http://localhost:8080/swagger-ui.html`
OpenAPI docs: `http://localhost:8080/api/v1/v3/api-docs`

## Testing

Use the Maven wrapper when local Maven is not installed:

```bash
./mvnw test
```

Full repository tests include `@DataJpaTest`/Testcontainers suites and require Docker to be running. Targeted pure unit tests can run without Docker.

## Default credentials
When `create-default-admin: true`, `DataBootstrap` creates a default admin only if that user does not already exist:
- username: `admin`
- password: `Root123456`

**Change the password immediately in production.**

## Configuration
Config is split by profile:
- `application.yml`: shared defaults
- `application-dev.yml`: development
- `application-prod.yml`: production overrides

Use `dev` or `prod` Spring profile. The current default profile is `prod`.

Docker Compose is for local/demo verification and runs the backend with `SPRING_PROFILES_ACTIVE=dev,demo-seed`.
- DB URL source: `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/toir_demo` in `docker-compose.yml`.
- JWT source: the fake test-only local secret in `application-dev.yml`.
- No `.env` file is required for local/demo compose startup.

Production must run with the `prod` profile and provide `TOIR_DB_URL`, `TOIR_DB_USERNAME`, `TOIR_DB_PASSWORD`, and `TOIR_JWT_SECRET` through environment configuration or a secret manager. `TOIR_JWT_SECRET` must be at least 32 bytes / 256 bits for HS256. Generate production/runtime values outside the repo, for example with `openssl rand -hex 32`.

### Firebase Cloud Messaging

FCM push is additive to the existing in-app notification store. Firebase Admin is initialized on startup from `classpath:firebase-service-account.json`, matching the simple bootstrap style used by the Tinder backend.

The service account file currently lives at `src/main/resources/firebase-service-account.json` and is packaged into the application artifact.

DB schema source of truth is Flyway migrations under `db/migration`.
- `spring.flyway.enabled=true`
- `spring.flyway.validate-on-migrate=true`
- `spring.jpa.hibernate.ddl-auto=validate`

Demo/business seed data is **not automatic** in `dev` or `prod`.
- Demo seeders are gated by `demo-seed` profile.
- Run demo seed manually with `dev,demo-seed`.
- Production profile must not include `demo-seed`.

## Production-Safe Reset And Seed

1. Backup database (required before any reset):
   ```bash
   pg_dump -Fc -h <host> -U <user> -d <db> -f toir-backup.dump
   ```
2. Local/demo reset only (never production):
   ```bash
   dropdb -h <host> -U <user> toir_demo
   createdb -h <host> -U <user> toir_demo
   ```
3. Start backend with schema migration only (no demo data):
   ```bash
   mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
   ```
4. Run demo seed manually:
   ```bash
   mvn spring-boot:run "-Dspring-boot.run.profiles=dev,demo-seed"
   ```
5. Smoke-test critical endpoints after startup:
   - auth login
   - departments/equipment list
   - work orders and ppr plan list

Warning: universal `truncate`/`drop` operations are for local/demo reset only and are not allowed in production migrations.

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
| `technicaldocument` | Technical passport documents |
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

Thrown via `com.toir.exception.RestException` and mapped by `GlobalExceptionHandler`.

## Security
- All endpoints require `Authorization: Bearer <token>` except: `/api/v1/auth/login`, Swagger, `/actuator/health`
- JWT signing uses HS256 and requires a secret of at least 32 bytes / 256 bits. Local/demo uses a fake test-only dev value; production must supply `TOIR_JWT_SECRET` externally.
- JWT payload: `sub`, `username`, `authorities` (role codes), `permissions`, `departmentId`, `primaryRoleCode`
- `@CurrentUser` parameter resolver injects `AuthenticatedUser` into controllers
- `JwtAuthenticationEntryPoint` and `RestAccessDeniedHandler` return the unified error format
