CREATE INDEX IF NOT EXISTS idx_knowledge_articles_active_updated
    ON knowledge_articles (updated_at DESC)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_knowledge_articles_active_created
    ON knowledge_articles (created_at DESC)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_knowledge_articles_active_kind
    ON knowledge_articles (kind)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_knowledge_articles_active_equipment
    ON knowledge_articles (equipment_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_knowledge_articles_active_equipment_type
    ON knowledge_articles (equipment_type_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_knowledge_articles_active_defect
    ON knowledge_articles (defect_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_knowledge_articles_active_work_order
    ON knowledge_articles (work_order_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_knowledge_articles_tags_gin
    ON knowledge_articles USING gin (tags)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_knowledge_article_links_active_article_target
    ON knowledge_article_links (knowledge_article_id, target_type, target_id)
    WHERE is_deleted = false;
