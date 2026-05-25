# Equipment Average Operating Life Phase 1 - Backend Result

## Summary

Implemented Phase 1 backend support for `averageOperatingLifeHours` as a core equipment field only. No dynamic Technical Passport rendering, vehicle metrics refactor, dynamic attribute storage change, cycles/revolutions, or unit enum work was implemented.

## Files Changed

- `src/main/java/com/toir/entity/equipment/Equipment.java`
- `src/main/java/com/toir/dto/equipment/EquipmentCreateRequest.java`
- `src/main/java/com/toir/dto/equipment/EquipmentUpdateRequest.java`
- `src/main/java/com/toir/dto/equipment/EquipmentDto.java`
- `src/main/java/com/toir/service/equipment/EquipmentService.java`
- `src/main/resources/db/migration/V20260525_5__equipment_average_operating_life_hours.sql`
- `src/test/java/com/toir/controller/EquipmentControllerContractTest.java`
- `src/test/java/com/toir/dto/equipment/EquipmentDtoTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentServiceTest.java`
- `src/test/java/com/toir/migration/EquipmentAverageOperatingLifeMigrationContractTest.java`
- `logs/equipment-average-life-phase1-backend-result.md`

## Migration

- `V20260525_5__equipment_average_operating_life_hours.sql`
- Adds nullable column:
  - table: `equipment`
  - column: `average_operating_life_hours`
  - type: `BIGINT`
- Column is nullable intentionally to avoid breaking existing rows/demo data.
- Existing migration versions were checked; latest previous migration was `V20260525_4__brigade_member_qualifications_jsonb_compat.sql`.

## DTO, Entity, Service Changes

- Added `Equipment.averageOperatingLifeHours` mapped to `average_operating_life_hours`.
- Added `averageOperatingLifeHours` to:
  - `EquipmentCreateRequest`
  - `EquipmentUpdateRequest`
  - `EquipmentDto`
- Create validation:
  - DTO uses `@NotNull @Positive`
  - service also rejects missing value with `averageOperatingLifeHours is required`
  - service rejects non-positive value with `averageOperatingLifeHours must be positive`
- Update validation:
  - DTO uses `@Positive`
  - service validates positive only when provided
  - omitted update value preserves the current entity value
- `EquipmentService.apply(...)` persists the value on create.
- `EquipmentService.applyForUpdate(...)` updates the value when provided.
- `EquipmentDto.from(...)` returns the value in list/detail DTO mapping.

## Tests Added or Updated

- Controller contract tests:
  - create success returns `averageOperatingLifeHours`
  - create missing `averageOperatingLifeHours` returns 400
  - create zero returns 400
  - create negative returns 400
- Service tests:
  - create persists `averageOperatingLifeHours`
  - update changes `averageOperatingLifeHours`
- DTO test:
  - `EquipmentDto.from(...)` includes `averageOperatingLifeHours`
- Migration contract test:
  - validates the migration file adds nullable `equipment.average_operating_life_hours BIGINT`

## Verification

- `git diff --check`: passed.
- `mvn -q -DskipTests compile`: not run; local shell returned `zsh:1: command not found: mvn`.
- `mvn test`: not run; local shell returned `zsh:1: command not found: mvn`.
- Targeted Maven tests could not be run for the same reason.

## Remaining Risks

- Backend compile/test verification still needs to be run in an environment with Maven installed.
- The DB column is nullable by design; existing equipment can still have no value until backfilled or edited.
- Backward-compatible constructors were retained for existing Java tests/call sites, but external API create requests are now expected to provide `averageOperatingLifeHours`.
