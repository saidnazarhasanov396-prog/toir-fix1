CREATE TABLE IF NOT EXISTS attachment_groups (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    target_type VARCHAR(64) NOT NULL,
    target_id UUID NOT NULL,
    document_type VARCHAR(64),
    document_number VARCHAR(128),
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS attachment_group_items (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES attachment_groups(id) ON DELETE CASCADE,
    file_id UUID NOT NULL REFERENCES uploaded_files(id),
    order_number INTEGER NOT NULL DEFAULT 0,
    label VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_attachment_group_items_group_file UNIQUE (group_id, file_id)
);

CREATE INDEX IF NOT EXISTS idx_attachment_groups_target
    ON attachment_groups (target_type, target_id, deleted, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_attachment_group_items_group_order
    ON attachment_group_items (group_id, order_number);

CREATE INDEX IF NOT EXISTS idx_attachment_group_items_file
    ON attachment_group_items (file_id);

INSERT INTO attachment_groups (
    id,
    title,
    description,
    target_type,
    target_id,
    document_type,
    document_number,
    created_by,
    created_at,
    deleted
)
SELECT
    ed.id,
    ed.document_name,
    NULL,
    'EQUIPMENT',
    ed.equipment_id,
    ed.document_type,
    ed.document_number,
    uf.uploaded_by,
    ed.created_at,
    false
FROM equipment_documents ed
JOIN uploaded_files uf ON uf.id = ed.file_id
WHERE NOT EXISTS (
    SELECT 1
    FROM attachment_groups ag
    WHERE ag.id = ed.id
);

INSERT INTO attachment_group_items (
    id,
    group_id,
    file_id,
    order_number,
    label,
    created_at
)
SELECT
    gen_random_uuid(),
    edf.equipment_document_id,
    edf.file_id,
    edf.sort_order,
    NULL,
    edf.created_at
FROM equipment_document_files edf
JOIN equipment_documents ed ON ed.id = edf.equipment_document_id
WHERE NOT EXISTS (
    SELECT 1
    FROM attachment_group_items agi
    WHERE agi.group_id = edf.equipment_document_id
      AND agi.file_id = edf.file_id
);

INSERT INTO attachment_group_items (
    id,
    group_id,
    file_id,
    order_number,
    label,
    created_at
)
SELECT
    gen_random_uuid(),
    ed.id,
    ed.file_id,
    0,
    NULL,
    ed.created_at
FROM equipment_documents ed
WHERE NOT EXISTS (
    SELECT 1
    FROM attachment_group_items agi
    WHERE agi.group_id = ed.id
      AND agi.file_id = ed.file_id
);

INSERT INTO attachment_groups (
    id,
    title,
    description,
    target_type,
    target_id,
    document_type,
    document_number,
    created_by,
    created_at,
    deleted
)
SELECT
    vd.id,
    vd.document_name,
    NULL,
    'VEHICLE',
    details.equipment_id,
    vd.document_type,
    vd.document_number,
    uf.uploaded_by,
    vd.created_at,
    false
FROM vehicle_documents vd
JOIN vehicle_details details ON details.id = vd.vehicle_details_id
JOIN uploaded_files uf ON uf.id = vd.file_id
WHERE NOT EXISTS (
    SELECT 1
    FROM attachment_groups ag
    WHERE ag.id = vd.id
);

INSERT INTO attachment_group_items (
    id,
    group_id,
    file_id,
    order_number,
    label,
    created_at
)
SELECT
    gen_random_uuid(),
    vd.id,
    vd.file_id,
    0,
    NULL,
    vd.created_at
FROM vehicle_documents vd
WHERE NOT EXISTS (
    SELECT 1
    FROM attachment_group_items agi
    WHERE agi.group_id = vd.id
      AND agi.file_id = vd.file_id
);

INSERT INTO attachment_groups (
    id,
    title,
    description,
    target_type,
    target_id,
    document_type,
    document_number,
    created_by,
    created_at,
    deleted
)
SELECT
    wod.id,
    wod.document_name,
    NULL,
    'WORK_ORDER',
    wod.work_order_id,
    wod.document_type,
    wod.document_number,
    uf.uploaded_by,
    wod.created_at,
    false
FROM work_order_documents wod
JOIN uploaded_files uf ON uf.id = wod.file_id
WHERE NOT EXISTS (
    SELECT 1
    FROM attachment_groups ag
    WHERE ag.id = wod.id
);

INSERT INTO attachment_group_items (
    id,
    group_id,
    file_id,
    order_number,
    label,
    created_at
)
SELECT
    gen_random_uuid(),
    wod.id,
    wod.file_id,
    0,
    NULL,
    wod.created_at
FROM work_order_documents wod
WHERE NOT EXISTS (
    SELECT 1
    FROM attachment_group_items agi
    WHERE agi.group_id = wod.id
      AND agi.file_id = wod.file_id
);

INSERT INTO attachment_groups (
    id,
    title,
    description,
    target_type,
    target_id,
    created_by,
    created_at,
    deleted
)
SELECT
    gen_random_uuid(),
    'Stock movement documents',
    NULL,
    'STOCK_MOVEMENT',
    smf.stock_movement_id,
    MIN(uf.uploaded_by),
    MIN(smf.created_at),
    false
FROM stock_movement_files smf
JOIN uploaded_files uf ON uf.id = smf.file_id
WHERE NOT EXISTS (
    SELECT 1
    FROM attachment_groups ag
    WHERE ag.target_type = 'STOCK_MOVEMENT'
      AND ag.target_id = smf.stock_movement_id
      AND ag.deleted = false
)
GROUP BY smf.stock_movement_id;

INSERT INTO attachment_group_items (
    id,
    group_id,
    file_id,
    order_number,
    label,
    created_at
)
SELECT
    gen_random_uuid(),
    ag.id,
    smf.file_id,
    smf.sort_order,
    NULL,
    smf.created_at
FROM stock_movement_files smf
JOIN attachment_groups ag
  ON ag.target_type = 'STOCK_MOVEMENT'
 AND ag.target_id = smf.stock_movement_id
 AND ag.deleted = false
WHERE NOT EXISTS (
    SELECT 1
    FROM attachment_group_items agi
    WHERE agi.group_id = ag.id
      AND agi.file_id = smf.file_id
);
