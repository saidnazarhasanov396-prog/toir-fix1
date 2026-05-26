ALTER TABLE public.maintenance_regulations
    ADD COLUMN IF NOT EXISTS template_id UUID;

CREATE INDEX IF NOT EXISTS idx_maintenance_regulations_template_id
    ON public.maintenance_regulations (template_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_maintenance_regulations_template'
          AND conrelid = 'public.maintenance_regulations'::regclass
    ) THEN
ALTER TABLE public.maintenance_regulations
    ADD CONSTRAINT fk_maintenance_regulations_template
        FOREIGN KEY (template_id)
            REFERENCES public.maintenance_templates(id);
END IF;
END $$;