-- READ ONLY: classify every active Vehicle driver assignment before Flyway runs.
-- The legacy V20260616_4 migration can nondeterministically rewrite AMBIGUOUS
-- User UUIDs to one matching Employee and can overwrite a direct Employee UUID
-- through a conflicting User mapping. Only values still unresolved afterward
-- are nulled. Deployment must stop until AMBIGUOUS and UNRESOLVED rows are reviewed.
WITH driver_identity AS (
    SELECT
        vd.id AS vehicle_details_id,
        vd.equipment_id,
        vd.assigned_driver_id AS responsible_uuid,
        EXISTS (
            SELECT 1
            FROM users u
            WHERE u.id = vd.assigned_driver_id
        ) AS user_match,
        EXISTS (
            SELECT 1
            FROM hr_employees direct_employee
            WHERE direct_employee.id = vd.assigned_driver_id
              AND direct_employee.is_deleted = false
        ) AS direct_employee_match,
        (
            SELECT count(*)
            FROM hr_employees linked_employee
            WHERE linked_employee.user_id = vd.assigned_driver_id
              AND linked_employee.is_deleted = false
        ) AS employee_link_count,
        EXISTS (
            SELECT 1
            FROM hr_employees conflicting_employee
            WHERE conflicting_employee.user_id = vd.assigned_driver_id
              AND conflicting_employee.id <> vd.assigned_driver_id
              AND conflicting_employee.is_deleted = false
        ) AS conflicting_employee_link
    FROM vehicle_details vd
    WHERE vd.assigned_driver_id IS NOT NULL
      AND vd.is_deleted = false
)
SELECT
    vehicle_details_id,
    equipment_id,
    responsible_uuid,
    user_match,
    employee_link_count,
    CASE
        WHEN employee_link_count > 1
            OR (direct_employee_match AND conflicting_employee_link)
            THEN 'AMBIGUOUS'
        WHEN direct_employee_match
            THEN 'VALID_EMPLOYEE'
        WHEN user_match AND employee_link_count = 1
            THEN 'LEGACY_USER_WITH_UNIQUE_EMPLOYEE'
        ELSE 'UNRESOLVED'
    END AS classification
FROM driver_identity
ORDER BY classification, equipment_id, vehicle_details_id;
