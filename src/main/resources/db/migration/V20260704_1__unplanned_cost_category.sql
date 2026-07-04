-- UNPLANNED cost category for department budget lines used by unplanned WO / PR costs.
INSERT INTO cost_categories (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, description)
SELECT gen_random_uuid(), now(), now(), false,
       'UNPLANNED',
       'Unplanned work',
       'Unplanned work',
       'Rejalashtirilmagan ishlar',
       'Catch-all budget line category for unplanned work-order and procurement costs'
WHERE NOT EXISTS (
    SELECT 1 FROM cost_categories WHERE code = 'UNPLANNED' AND is_deleted = false
);
