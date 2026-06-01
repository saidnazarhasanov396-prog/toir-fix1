# Denza N9 JSON To SQL Seed Result

## Scope

- Input JSON: `src/main/resources/reference-data/denza-n9-real-technical-passport.json`
- Output SQL: `scripts/manual/denza_n9_official_attributes_seed.sql`
- No Flyway migration was created.
- No tests were run.
- No SQL was executed.

## What was implemented

The seed script is idempotent and re-runnable, and it uses only official lifecycle tables:

- `equipment_types`
- `equipment`
- `vehicle_details`
- `equipment_attribute_definitions`
- `equipment_attribute_values`

It does all of the following:

1. Creates or reuses equipment type `DENZA_N9_PHEV`.
2. Creates or reuses Denza N9 reference equipment row (`DENZA_N9_REF` / `DENZA-N9-REF`).
3. Creates or reuses `vehicle_details` for that equipment and upserts common vehicle fields:
   - brand: `DENZA`
   - model: `N9`
   - fuel_type: `Plug-in hybrid gasoline/electric`
   - fuel_tank_capacity: `65`
4. Inserts or updates all official attribute definitions from JSON.
5. Inserts or updates all official attribute values from JSON.
6. Stores source references in `equipment_attribute_values.value_json` as:
   - `{"sourceRefs":[...]}`
7. Includes final verification `SELECT` queries for:
   - equipment type row
   - equipment row
   - vehicle details row
   - definition count
   - value count
   - full seeded attribute/value listing

## Policy preserved

- Manual key/value attribute policy is preserved:
  - the script does **not** write to `equipment_manual_attributes`.

## Notes

- The script revives soft-deleted matching rows by setting `is_deleted = false`.
- The script uses deterministic UUID generation from `md5(...)` for new inserts when needed, while reusing existing rows by business keys when present.
