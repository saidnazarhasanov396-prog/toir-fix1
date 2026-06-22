CREATE TABLE IF NOT EXISTS knowledge_article_links (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    knowledge_article_id uuid NOT NULL,
    target_type varchar(50) NOT NULL,
    target_id uuid NOT NULL,
    CONSTRAINT fk_knowledge_article_links_article
        FOREIGN KEY (knowledge_article_id) REFERENCES knowledge_articles (id),
    CONSTRAINT chk_knowledge_article_links_target_type
        CHECK (target_type IN ('EQUIPMENT', 'REPAIR_REQUEST', 'WORK_ORDER', 'DEFECT'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_knowledge_article_links_active_target
    ON knowledge_article_links (knowledge_article_id, target_type, target_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_knowledge_article_links_article
    ON knowledge_article_links (knowledge_article_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_knowledge_article_links_target
    ON knowledge_article_links (target_type, target_id)
    WHERE is_deleted = false;

INSERT INTO knowledge_article_links (
    id, created_at, updated_at, is_deleted, knowledge_article_id, target_type, target_id
)
SELECT gen_random_uuid(), now(), now(), false, id, 'EQUIPMENT', equipment_id
FROM knowledge_articles
WHERE equipment_id IS NOT NULL
  AND is_deleted = false
ON CONFLICT DO NOTHING;

INSERT INTO knowledge_article_links (
    id, created_at, updated_at, is_deleted, knowledge_article_id, target_type, target_id
)
SELECT gen_random_uuid(), now(), now(), false, id, 'WORK_ORDER', work_order_id
FROM knowledge_articles
WHERE work_order_id IS NOT NULL
  AND is_deleted = false
ON CONFLICT DO NOTHING;

INSERT INTO knowledge_article_links (
    id, created_at, updated_at, is_deleted, knowledge_article_id, target_type, target_id
)
SELECT gen_random_uuid(), now(), now(), false, id, 'DEFECT', defect_id
FROM knowledge_articles
WHERE defect_id IS NOT NULL
  AND is_deleted = false
ON CONFLICT DO NOTHING;
