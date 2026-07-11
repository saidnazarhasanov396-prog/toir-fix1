CREATE TEMPORARY TABLE equipment_responsible_identity_classification (
    equipment_id uuid PRIMARY KEY,
    original_responsible_id uuid NOT NULL,
    classification varchar(48) NOT NULL,
    mapped_employee_id uuid
) ON COMMIT DROP;

INSERT INTO equipment_responsible_identity_classification (
    equipment_id,
    original_responsible_id,
    classification,
    mapped_employee_id
)
SELECT e.id,
       e.responsible_id,
       CASE
           WHEN direct_employee.id IS NOT NULL THEN 'VALID_EMPLOYEE'
           WHEN legacy_user.id IS NOT NULL AND employee_mapping.match_count = 1
               THEN 'LEGACY_USER_WITH_UNIQUE_EMPLOYEE'
           WHEN legacy_user.id IS NOT NULL AND employee_mapping.match_count > 1
               THEN 'AMBIGUOUS'
           ELSE 'UNRESOLVED'
       END,
       CASE
           WHEN direct_employee.id IS NULL
                AND legacy_user.id IS NOT NULL
                AND employee_mapping.match_count = 1
               THEN employee_mapping.employee_id
           ELSE NULL
       END
FROM equipment e
LEFT JOIN hr_employees direct_employee
       ON direct_employee.id = e.responsible_id
LEFT JOIN users legacy_user
       ON legacy_user.id = e.responsible_id
LEFT JOIN LATERAL (
    SELECT count(*) AS match_count,
           min(employee.id::text)::uuid AS employee_id
    FROM hr_employees employee
    WHERE employee.user_id = e.responsible_id
) employee_mapping ON true
WHERE e.responsible_id IS NOT NULL;

DO $$
DECLARE
    ambiguous_count bigint;
    unresolved_count bigint;
    ambiguous_examples text;
    unresolved_examples text;
BEGIN
    SELECT count(*) FILTER (WHERE classification = 'AMBIGUOUS'),
           count(*) FILTER (WHERE classification = 'UNRESOLVED')
    INTO ambiguous_count, unresolved_count
    FROM equipment_responsible_identity_classification;

    IF ambiguous_count > 0 OR unresolved_count > 0 THEN
        SELECT string_agg(equipment_id::text, ', ' ORDER BY equipment_id)
        INTO ambiguous_examples
        FROM (
            SELECT equipment_id
            FROM equipment_responsible_identity_classification
            WHERE classification = 'AMBIGUOUS'
            ORDER BY equipment_id
            LIMIT 10
        ) bounded_ambiguous;

        SELECT string_agg(equipment_id::text, ', ' ORDER BY equipment_id)
        INTO unresolved_examples
        FROM (
            SELECT equipment_id
            FROM equipment_responsible_identity_classification
            WHERE classification = 'UNRESOLVED'
            ORDER BY equipment_id
            LIMIT 10
        ) bounded_unresolved;

        RAISE EXCEPTION
            'equipment responsible identity migration blocked: AMBIGUOUS=% (equipment ids: %); UNRESOLVED=% (equipment ids: %)',
            ambiguous_count,
            coalesce(ambiguous_examples, 'none'),
            unresolved_count,
            coalesce(unresolved_examples, 'none');
    END IF;
END
$$;

UPDATE equipment e
SET responsible_id = c.mapped_employee_id, updated_at = now()
FROM equipment_responsible_identity_classification c
WHERE e.id = c.equipment_id
  AND c.classification = 'LEGACY_USER_WITH_UNIQUE_EMPLOYEE';

CREATE INDEX IF NOT EXISTS idx_equipment_responsible_id
    ON equipment(responsible_id);

ALTER TABLE equipment
    ADD CONSTRAINT fk_equipment_responsible_employee
    FOREIGN KEY (responsible_id) REFERENCES hr_employees(id) NOT VALID;

ALTER TABLE equipment
    VALIDATE CONSTRAINT fk_equipment_responsible_employee;

COMMENT ON COLUMN equipment.responsible_id IS
    'Operational responsible Employee identity; references hr_employees.id';
